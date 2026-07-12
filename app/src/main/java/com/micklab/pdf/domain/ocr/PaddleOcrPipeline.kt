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
 * A pragmatic detector post-process (axis-aligned connected components instead of
 * rotated-box + polygon unclip) keeps it dependency-free (no OpenCV) and works
 * for horizontal document text. Preprocessing constants follow PaddleOCR.
 */
@Singleton
class PaddleOcrPipeline @Inject constructor(
    private val modelManager: PaddleModelManager,
) {
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var dict: List<String>? = null
    private val lock = Any()

    private fun ensureLoaded() = synchronized(lock) {
        if (detSession == null) {
            detSession = env.createSession(modelManager.detModelPath.absolutePath, OrtSession.SessionOptions())
        }
        if (recSession == null) {
            recSession = env.createSession(modelManager.recModelPath.absolutePath, OrtSession.SessionOptions())
        }
        if (dict == null) {
            dict = modelManager.dictPath.readLines().map { it.trimEnd('\n', '\r') }
        }
    }

    fun recognize(bitmap: Bitmap): OcrPageOutcome {
        ensureLoaded()
        val source = bitmap.ensureArgb()
        val boxes = detect(source).sortedWith(compareBy({ it.top }, { it.left }))

        val blocks = ArrayList<OcrBlock>()
        val builder = StringBuilder()
        var confSum = 0f
        var confCount = 0
        for (box in boxes) {
            val crop = cropBitmap(source, box) ?: continue
            val (text, score) = try {
                recognizeCrop(crop)
            } finally {
                if (crop !== source) crop.recycle()
            }
            if (text.isBlank()) continue
            blocks += OcrBlock(text, score, BoundingBox(box.left, box.top, box.right, box.bottom))
            builder.append(text).append('\n')
            confSum += score
            confCount++
        }
        if (source !== bitmap) source.recycle()

        val avg = if (confCount > 0) confSum / confCount else 0f
        Log.i(PdfToolsApp.TAG, "Paddle(ONNX) OCR: ${blocks.size} boxes, conf=$avg")
        return OcrPageOutcome(builder.toString().trim(), avg, blocks)
    }

    // --- Detection ---

    private fun detect(bitmap: Bitmap): List<Rect> {
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

        // Binarize + connected components -> axis-aligned boxes (in resized coords).
        val binary = BooleanArray(plane) { prob[it] > DET_BIN_THRESH }
        val boxes = connectedComponentBoxes(binary, newW, newH)

        // Scale back to original coords, with a small "unclip"-style pad.
        val scaleX = srcW.toFloat() / newW
        val scaleY = srcH.toFloat() / newH
        return boxes.mapNotNull { b ->
            val h = b.height()
            if (h < DET_MIN_BOX_SIDE || b.width() < DET_MIN_BOX_SIDE) return@mapNotNull null
            val pad = (h * DET_UNCLIP_RATIO).roundToInt()
            Rect(
                ((b.left - pad) * scaleX).roundToInt().coerceAtLeast(0),
                ((b.top - pad) * scaleY).roundToInt().coerceAtLeast(0),
                ((b.right + pad) * scaleX).roundToInt().coerceAtMost(srcW),
                ((b.bottom + pad) * scaleY).roundToInt().coerceAtMost(srcH),
            )
        }
    }

    private fun connectedComponentBoxes(binary: BooleanArray, w: Int, h: Int): List<Rect> {
        val visited = BooleanArray(binary.size)
        val boxes = ArrayList<Rect>()
        val queue = IntArray(binary.size)
        for (start in binary.indices) {
            if (!binary[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = w; var minY = h; var maxX = 0; var maxY = 0; var count = 0
            while (head < tail) {
                val idx = queue[head++]
                val x = idx % w
                val y = idx / w
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                count++
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
            if (count >= DET_MIN_AREA) boxes += Rect(minX, minY, maxX + 1, maxY + 1)
            if (boxes.size > DET_MAX_BOXES) break
        }
        return boxes
    }

    // --- Recognition ---

    private fun recognizeCrop(crop: Bitmap): Pair<String, Float> {
        val session = recSession!!
        val chars = dict!!
        val targetH = REC_HEIGHT
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
        const val DET_MIN_AREA = 20
        const val DET_MIN_BOX_SIDE = 3
        const val DET_UNCLIP_RATIO = 0.25f
        const val DET_MAX_BOXES = 2000
        val DET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val DET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

        const val REC_HEIGHT = 32 // PP-OCRv1 CRNN
        const val REC_MIN_WIDTH = 16
        const val REC_MAX_WIDTH = 1024
    }
}
