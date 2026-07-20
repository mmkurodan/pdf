package com.micklab.pdf.domain.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.domain.model.BoundingBox
import com.micklab.pdf.domain.model.OcrBlock
import java.nio.FloatBuffer
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * On-device PP-OCR pipeline over ONNX Runtime:
 *   detection (DB) → connected-component text boxes → recognition (CRNN) → CTC.
 *
 * A pragmatic detector post-process (axis-aligned connected components, reassembled
 * into text lines, with an approximate Vatti "unclip" expansion) keeps it
 * dependency-free (no OpenCV) and works for horizontal document text.
 * Preprocessing constants follow PaddleOCR.
 */
@Singleton
class PaddleOcrPipeline @Inject constructor(
    private val modelManager: PaddleModelManager,
) {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var detSession: OrtSession? = null
    private val recSessions = HashMap<PaddleRecProfile, OrtSession>()
    private val dicts = HashMap<PaddleRecProfile, List<String>>()
    private val lock = Any()

    private fun ensureLoaded(profile: PaddleRecProfile) = synchronized(lock) {
        if (detSession == null) {
            detSession = env.createSession(modelManager.detModelPath.absolutePath, OrtSession.SessionOptions())
        }
        if (recSessions[profile] == null) {
            recSessions[profile] = env.createSession(modelManager.recModelPath(profile).absolutePath, OrtSession.SessionOptions())
        }
        if (dicts[profile] == null) {
            dicts[profile] = modelManager.dictPath(profile).readLines().map { it.trimEnd('\n', '\r') }
        }
    }

    fun recognize(bitmap: Bitmap, profile: PaddleRecProfile): OcrPageOutcome {
        ensureLoaded(profile)
        val recSession = recSessions.getValue(profile)
        val chars = dicts.getValue(profile)
        val source = bitmap.ensureArgb()
        val lines = detect(source)

        val blocks = ArrayList<OcrBlock>()
        val builder = StringBuilder()
        var confSum = 0f
        var confCount = 0
        for (line in lines) {
            val parts = ArrayList<String>(line.size)
            for (box in line) {
                val crop = cropBitmap(source, box) ?: continue
                val (text, score) = try {
                    recognizeCrop(crop, recSession, chars, profile.recHeight)
                } finally {
                    if (crop !== source) crop.recycle()
                }
                if (text.isBlank()) continue
                blocks += OcrBlock(text, score, BoundingBox(box.left, box.top, box.right, box.bottom))
                parts += text
                confSum += score
                confCount++
            }
            if (parts.isNotEmpty()) builder.append(parts.joinToString(" ")).append('\n')
        }
        if (source !== bitmap) source.recycle()

        val avg = if (confCount > 0) confSum / confCount else 0f
        Log.i(PdfToolsApp.TAG, "Paddle(ONNX) OCR [${profile.name}]: ${blocks.size} boxes, conf=$avg")
        return OcrPageOutcome(builder.toString().trim(), avg, blocks)
    }

    // --- Detection ---

    private fun detect(bitmap: Bitmap): List<List<Rect>> {
        val session = detSession!!
        val srcW = bitmap.width
        val srcH = bitmap.height
        val ratio = if (max(srcW, srcH) > DET_MAX_SIDE) DET_MAX_SIDE.toFloat() / max(srcW, srcH) else 1f
        val newW = (srcW * ratio).roundToInt().coerceAtLeast(32).let { it / 32 * 32 }.coerceAtLeast(32)
        val newH = (srcH * ratio).roundToInt().coerceAtLeast(32).let { it / 32 * 32 }.coerceAtLeast(32)
        val resized = Bitmap.createScaledBitmap(bitmap, newW, newH, true)

        val input = FloatArray(3 * newH * newW)
        val pixels = IntArray(newW * newH)
        resized.getPixels(pixels, 0, newW, 0, 0, newW, newH)
        // BGR channel order + ImageNet mean/std (matches PaddleOCR's cv2 BGR input).
        val plane = newH * newW
        for (i in 0 until plane) {
            val p = pixels[i]
            val r = Color.red(p) / 255f
            val g = Color.green(p) / 255f
            val b = Color.blue(p) / 255f
            input[i] = (b - DET_MEAN[0]) / DET_STD[0]
            input[plane + i] = (g - DET_MEAN[1]) / DET_STD[1]
            input[2 * plane + i] = (r - DET_MEAN[2]) / DET_STD[2]
        }
        if (resized !== bitmap) resized.recycle()

        val shape = longArrayOf(1, 3, newH.toLong(), newW.toLong())
        val prob: FloatArray
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(Collections.singletonMap(session.inputNames.first(), tensor)).use { result ->
                val out = result[0] as OnnxTensor
                val buffer = out.floatBuffer
                prob = FloatArray(buffer.remaining())
                buffer.get(prob)
            }
        }

        // Binarize the probability map and group text pixels into components.
        val binary = BooleanArray(plane) { prob[it] > DET_BIN_THRESH }
        val boxes = connectedComponents(prob, binary, newW, newH)

        // Reassemble components into reading-order text lines (with column segments),
        // then unclip each segment and scale it back to original-image coords.
        val scaleX = srcW.toFloat() / newW
        val scaleY = srcH.toFloat() / newH
        return groupLines(boxes).mapNotNull { segments ->
            segments.mapNotNull { unclipAndScale(it, scaleX, scaleY, srcW, srcH) }.ifEmpty { null }
        }
    }

    private fun connectedComponents(prob: FloatArray, binary: BooleanArray, w: Int, h: Int): List<Rect> {
        val visited = BooleanArray(binary.size)
        val comps = ArrayList<Rect>()
        val queue = IntArray(binary.size)
        for (start in binary.indices) {
            if (!binary[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = w; var minY = h; var maxX = 0; var maxY = 0; var count = 0
            var probSum = 0f
            while (head < tail) {
                val idx = queue[head++]
                val x = idx % w
                val y = idx / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                count++
                probSum += prob[idx]
                if (x > 0) {
                    val n = idx - 1
                    if (binary[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (x < w - 1) {
                    val n = idx + 1
                    if (binary[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (y > 0) {
                    val n = idx - w
                    if (binary[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
                if (y < h - 1) {
                    val n = idx + w
                    if (binary[n] && !visited[n]) { visited[n] = true; queue[tail++] = n }
                }
            }
            // Filter tiny blobs and low-confidence regions (~PaddleOCR det_db_box_thresh).
            if (count >= DET_MIN_AREA && probSum / count >= DET_BOX_THRESH) {
                comps += Rect(minX, minY, maxX + 1, maxY + 1)
            }
            if (comps.size > DET_MAX_BOXES) break
        }
        return comps
    }

    /**
     * Reassemble raw detector components into reading-order text lines. Components
     * whose vertical spans overlap belong to the same line; within a line they are
     * split into segments wherever a wide horizontal gap appears (so separate
     * columns aren't merged), and each segment's components are unioned into one box.
     * Merging a line back together restores inter-character context the recognizer
     * needs — critical for Latin text, which the raw component map fragments at every
     * space and stroke gap.
     */
    private fun groupLines(boxes: List<Rect>): List<List<Rect>> {
        if (boxes.isEmpty()) return emptyList()
        val medianH = boxes.map { it.height() }.sorted()[boxes.size / 2].coerceAtLeast(1)

        val lines = ArrayList<MutableList<Rect>>()
        for (b in boxes.sortedBy { it.top }) {
            var best: MutableList<Rect>? = null
            var bestOverlap = 0
            for (grp in lines) {
                val top = grp.minOf { it.top }
                val bottom = grp.maxOf { it.bottom }
                val overlap = min(bottom, b.bottom) - max(top, b.top)
                val need = (VERT_OVERLAP_FRAC * min(b.height(), bottom - top)).roundToInt()
                if (overlap > need && overlap > bestOverlap) {
                    best = grp
                    bestOverlap = overlap
                }
            }
            if (best != null) best.add(b) else lines += mutableListOf(b)
        }

        val segGap = SEG_GAP_FRAC * medianH
        return lines.sortedBy { grp -> grp.minOf { it.top } }.map { grp ->
            val ordered = grp.sortedBy { it.left }
            val segments = ArrayList<Rect>()
            var cur = Rect(ordered.first())
            var prevRight = ordered.first().right
            for (i in 1 until ordered.size) {
                val r = ordered[i]
                if (r.left - prevRight > segGap) {
                    segments += cur
                    cur = Rect(r)
                } else {
                    cur.union(r)
                }
                prevRight = max(prevRight, r.right)
            }
            segments += cur
            segments
        }
    }

    private fun unclipAndScale(r: Rect, scaleX: Float, scaleY: Float, srcW: Int, srcH: Int): Rect? {
        val w = r.width()
        val h = r.height()
        if (w < DET_MIN_BOX_SIDE || h < DET_MIN_BOX_SIDE) return null
        // Vatti-style unclip: distance = area * ratio / perimeter (in detector coords).
        // Expand horizontally to recover clipped edge letters; cap vertical growth so
        // adjacent lines aren't pulled into the crop.
        val distance = (w.toFloat() * h) * DET_UNCLIP_RATIO / (2f * (w + h))
        val padX = distance.roundToInt().coerceIn(1, 2 * h)
        val padY = min(distance, DET_MAX_VPAD_FRAC * h).roundToInt().coerceAtLeast(1)
        return Rect(
            ((r.left - padX) * scaleX).roundToInt().coerceAtLeast(0),
            ((r.top - padY) * scaleY).roundToInt().coerceAtLeast(0),
            ((r.right + padX) * scaleX).roundToInt().coerceAtMost(srcW),
            ((r.bottom + padY) * scaleY).roundToInt().coerceAtMost(srcH),
        )
    }

    // --- Recognition ---

    private fun recognizeCrop(crop: Bitmap, session: OrtSession, chars: List<String>, targetH: Int): Pair<String, Float> {
        val targetW = ((crop.width.toFloat() / crop.height) * targetH).roundToInt().coerceIn(REC_MIN_WIDTH, REC_MAX_WIDTH)
        val resized = Bitmap.createScaledBitmap(crop, targetW, targetH, true)

        val input = FloatArray(3 * targetH * targetW)
        val pixels = IntArray(targetW * targetH)
        resized.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
        val plane = targetH * targetW
        for (i in 0 until plane) {
            val p = pixels[i]
            // (x/255 - 0.5)/0.5; channel order irrelevant (same mean/std).
            input[i] = (Color.blue(p) / 255f - 0.5f) / 0.5f
            input[plane + i] = (Color.green(p) / 255f - 0.5f) / 0.5f
            input[2 * plane + i] = (Color.red(p) / 255f - 0.5f) / 0.5f
        }
        if (resized !== crop) resized.recycle()

        val shape = longArrayOf(1, 3, targetH.toLong(), targetW.toLong())
        var timeSteps = 0
        var numClasses = 0
        val logits: FloatArray
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(Collections.singletonMap(session.inputNames.first(), tensor)).use { result ->
                val out = result[0] as OnnxTensor
                val outShape = out.info.shape // [1, T, C]
                timeSteps = outShape[1].toInt()
                numClasses = outShape[2].toInt()
                val buffer = out.floatBuffer
                logits = FloatArray(buffer.remaining())
                buffer.get(logits)
            }
        }
        return ctcDecode(logits, timeSteps, numClasses, chars)
    }

    private fun ctcDecode(data: FloatArray, timeSteps: Int, numClasses: Int, dict: List<String>): Pair<String, Float> {
        // character[0]=blank, [1..len]=dict, optional trailing space.
        val builder = StringBuilder()
        var prev = -1
        var scoreSum = 0f
        var scoreCount = 0
        for (t in 0 until timeSteps) {
            val base = t * numClasses
            var bestIdx = 0
            var bestVal = data[base]
            for (c in 1 until numClasses) {
                val v = data[base + c]
                if (v > bestVal) {
                    bestVal = v
                    bestIdx = c
                }
            }
            if (bestIdx != 0 && bestIdx != prev) {
                val ch = characterAt(bestIdx, numClasses, dict)
                if (ch != null) {
                    builder.append(ch)
                    scoreSum += bestVal
                    scoreCount++
                }
            }
            prev = bestIdx
        }
        val score = if (scoreCount > 0) (scoreSum / scoreCount).coerceIn(0f, 1f) else 0f
        return builder.toString() to score
    }

    private fun characterAt(index: Int, numClasses: Int, dict: List<String>): String? {
        // index 0 = blank. dict occupies 1..dict.size. A trailing space may exist.
        return when {
            index <= 0 -> null
            index <= dict.size -> dict[index - 1]
            numClasses == dict.size + 2 && index == dict.size + 1 -> " "
            else -> null
        }
    }

    // --- helpers ---

    private fun cropBitmap(bitmap: Bitmap, box: Rect): Bitmap? {
        val w = box.width()
        val h = box.height()
        if (w <= 1 || h <= 1) return null
        return runCatching { Bitmap.createBitmap(bitmap, box.left, box.top, w, h) }.getOrNull()
    }

    private fun Bitmap.ensureArgb(): Bitmap =
        if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)

    private companion object {
        const val DET_MAX_SIDE = 960
        const val DET_BIN_THRESH = 0.3f
        const val DET_BOX_THRESH = 0.5f       // drop components whose mean prob is below this
        const val DET_MIN_AREA = 20
        const val DET_MIN_BOX_SIDE = 3
        const val DET_UNCLIP_RATIO = 1.6f     // Vatti-style expansion (area * ratio / perimeter)
        const val DET_MAX_VPAD_FRAC = 0.4f    // cap vertical expansion (× box height) to avoid merging lines
        const val VERT_OVERLAP_FRAC = 0.4f    // min vertical overlap (× smaller height) to join one line
        const val SEG_GAP_FRAC = 1.6f         // horizontal gap (× median line height) that splits columns
        const val DET_MAX_BOXES = 2000
        val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        const val REC_MIN_WIDTH = 16
        const val REC_MAX_WIDTH = 1600
    }
}
