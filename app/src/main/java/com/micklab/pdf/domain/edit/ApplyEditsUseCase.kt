package com.micklab.pdf.domain.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.R
import com.micklab.pdf.core.DispatcherProvider
import com.micklab.pdf.core.LocaleManager
import com.micklab.pdf.core.NoProgress
import com.micklab.pdf.core.ProgressCallback
import com.micklab.pdf.data.repository.FileRepository
import com.micklab.pdf.data.repository.OutputDestination
import com.micklab.pdf.domain.model.OutputFile
import com.micklab.pdf.domain.pdf.PdfWorkspace
import com.micklab.pdf.domain.usecase.MIME_PDF
import com.micklab.pdf.domain.usecase.toDestination
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

data class ApplyEditsResult(val output: OutputFile, val ops: List<EditOpResult>)

/**
 * Applies a list of [EditOp]s to a PDF and writes a **new** file — the source is
 * never modified (端末内のファイルを破壊しない安全設計). Add-text / add-image draw
 * onto the existing page background; edit-existing-text is only performed when
 * the text layer is genuinely editable and is otherwise recorded as skipped.
 *
 * Runs fully offline; only [NotoFontManager] may need a one-time font download.
 */
class ApplyEditsUseCase @Inject constructor(
    private val workspace: PdfWorkspace,
    private val fileRepository: FileRepository,
    private val fontManager: NotoFontManager,
    private val contentEditor: PdfContentEditor,
    private val textEditor: PdfTextEditor,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val appContext: Context,
) {
    suspend operator fun invoke(
        source: Uri,
        edits: List<EditOp>,
        outputTree: Uri?,
        outputBaseName: String? = null,
        onProgress: ProgressCallback = NoProgress,
    ): ApplyEditsResult {
        val base = outputBaseName ?: fileRepository.displayName(source)
        val outName = base.substringBeforeLast('.', base) + "_edited.pdf"
        return applyInternal(source, edits, outputTree.toDestination(), outName, onProgress)
    }

    /** Applies [edits] to a throwaway cache PDF for live preview; returns its file. */
    suspend fun preview(source: Uri, edits: List<EditOp>): OutputFile =
        applyInternal(
            source, edits,
            OutputDestination.Cache(PREVIEW_DIR),
            "edit_preview_${System.currentTimeMillis()}.pdf",
            NoProgress,
        ).output

    private suspend fun applyInternal(
        source: Uri,
        edits: List<EditOp>,
        destination: OutputDestination,
        outName: String,
        onProgress: ProgressCallback,
    ): ApplyEditsResult = withContext(dispatchers.io) {
        val temp = workspace.copyUriToTemp(source)
        try {
            workspace.load(temp).use { document ->
                var font: PDType0Font? = null
                val results = ArrayList<EditOpResult>(edits.size)
                edits.forEachIndexed { index, op ->
                    coroutineContext.ensureActive()
                    onProgress(index.toFloat() / edits.size, LocaleManager.string(appContext, R.string.ae_applying, index + 1, edits.size))
                    results += runCatching {
                        applyOne(document, op) { font ?: fontManager.load(document).also { font = it } }
                    }.getOrElse { EditOpResult(op, applied = false, detail = it.message ?: LocaleManager.string(appContext, R.string.ae_failed)) }
                }

                val output = fileRepository.writeFile(destination, outName, MIME_PDF) { os -> document.save(os) }
                onProgress(1f, LocaleManager.string(appContext, R.string.ae_saved))
                Log.i(PdfToolsApp.TAG, "Applied ${results.count { it.applied }}/${edits.size} edit(s)")
                ApplyEditsResult(output, results)
            }
        } finally {
            workspace.delete(temp)
        }
    }

    private companion object {
        const val PREVIEW_DIR = "edit_preview"
    }

    private fun applyOne(document: PDDocument, op: EditOp, font: () -> PDType0Font): EditOpResult {
        val page = document.getPage(op.pageIndex)
        val crop = page.cropBox
        val placement = PdfCoordinateMapper.place(
            crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height,
            page.rotation,
            op.rect.left, op.rect.top, op.rect.right, op.rect.bottom,
        )
        return when (op) {
            is EditOp.AddText -> {
                contentEditor.addText(
                    document, page, placement, font(), op.text, op.fontSizePt, op.colorRgb,
                    bold = op.bold, italic = op.italic, underline = op.underline, rotationDeg = op.rotationDeg,
                )
                if (op.url.isNotBlank()) {
                    val box = PdfCoordinateMapper.toUserRect(
                        crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation,
                        op.rect.left, op.rect.top, op.rect.right, op.rect.bottom,
                    )
                    addLinkAnnotation(page, box, op.url.trim())
                }
                EditOpResult(op, applied = true, detail = LocaleManager.string(appContext, R.string.ae_text_added))
            }

            is EditOp.AddImage -> {
                // Add as an annotation (not flattened) so it stays a movable/deletable layer.
                val box = PdfCoordinateMapper.toUserRect(
                    crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation,
                    op.rect.left, op.rect.top, op.rect.right, op.rect.bottom,
                )
                PdfImageLayer.add(document, page, box, loadImage(document, op.source, op.rotationDeg), PdfImageLayer.newId())
                EditOpResult(op, applied = true, detail = LocaleManager.string(appContext, R.string.ae_image_added))
            }

            is EditOp.EditExistingText -> {
                // Delete the whole matched run and redraw it with the default font at [rect].
                // Used when the original font can't render the new text, or the run was moved.
                val regenerate = {
                    val defaultFont = font() // load first, so a missing font can't lose the text
                    val removed = textEditor.blank(document, page, op.target, op.occurrence)
                    contentEditor.addText(
                        document, page, placement, defaultFont, op.replacement, op.fontSizePt, op.colorRgb,
                        bold = op.bold, italic = op.italic, underline = op.underline, rotationDeg = op.rotationDeg,
                    )
                    EditOpResult(
                        op, applied = true,
                        detail = when {
                            op.moved -> LocaleManager.string(appContext, R.string.ae_regen_moved)
                            removed -> LocaleManager.string(appContext, R.string.ae_regen_default)
                            else -> LocaleManager.string(appContext, R.string.ae_appended_default)
                        },
                    )
                }
                if (op.moved || op.restyled) {
                    regenerate()
                } else {
                    when (val r = textEditor.replace(document, page, op.target, op.occurrence, op.replacement)) {
                        TextReplaceResult.Replaced -> EditOpResult(op, applied = true, detail = LocaleManager.string(appContext, R.string.ae_replaced))
                        TextReplaceResult.NeedsRedraw -> regenerate()
                        is TextReplaceResult.Skipped -> EditOpResult(op, applied = false, detail = LocaleManager.string(appContext, R.string.ae_skipped, r.reason))
                    }
                }
            }

            is EditOp.DeleteExistingText -> {
                val removed = textEditor.blank(document, page, op.target, op.occurrence)
                EditOpResult(op, applied = removed, detail = if (removed) LocaleManager.string(appContext, R.string.ae_deleted) else LocaleManager.string(appContext, R.string.ae_skip_no_target))
            }

            is EditOp.MoveImage -> {
                val box = PdfCoordinateMapper.toUserRect(
                    crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, page.rotation,
                    op.rect.left, op.rect.top, op.rect.right, op.rect.bottom,
                )
                val ok = PdfImageLayer.moveTo(document, page, op.id, box)
                EditOpResult(op, applied = ok, detail = if (ok) LocaleManager.string(appContext, R.string.ae_image_moved) else LocaleManager.string(appContext, R.string.ae_skip_no_image))
            }

            is EditOp.DeleteImage -> {
                val ok = PdfImageLayer.remove(document, page, op.id)
                EditOpResult(op, applied = ok, detail = if (ok) LocaleManager.string(appContext, R.string.ae_image_deleted) else LocaleManager.string(appContext, R.string.ae_skip_no_image))
            }
        }
    }

    /** Adds an invisible (border width 0) URI link over user-space [box]=[llx,lly,urx,ury]. */
    private fun addLinkAnnotation(page: PDPage, box: FloatArray, url: String) {
        val link = PDAnnotationLink()
        link.action = PDActionURI().apply { uri = url }
        link.setRectangle(PDRectangle(box[0], box[1], box[2] - box[0], box[3] - box[1]))
        link.borderStyle = PDBorderStyleDictionary().apply { width = 0f }
        link.setPrinted(true)
        page.annotations.add(link)
    }

    private fun loadImage(document: PDDocument, uri: Uri, rotationDeg: Int = 0): PDImageXObject {
        val bytes = fileRepository.openInput(uri).use { it.readBytes() }
        val isJpeg = bytes.size > 2 &&
            (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8
        // No rotation + JPEG: embed the original bytes directly (smallest, no re-encode).
        if (isJpeg && rotationDeg % 360 == 0) return JPEGFactory.createFromByteArray(document, bytes)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error(LocaleManager.string(appContext, R.string.ae_image_load_failed))
        // Rotate the pixels (transparent corners) so the placed layer stays axis-aligned.
        val bitmap = if (rotationDeg % 360 != 0) rotateTransparent(decoded, rotationDeg) else decoded
        // Lossless path keeps any alpha channel (SMask), so PNG / rotated overlays stay transparent.
        return try {
            LosslessFactory.createFromImage(document, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** Rotate [src] into a fresh ARGB_8888 bitmap whose uncovered corners are truly transparent
     *  (not black): a plain createBitmap rotation of an opaque source drops the alpha flag, so
     *  LosslessFactory would embed the corners as opaque black. */
    private fun rotateTransparent(src: Bitmap, rotationDeg: Int): Bitmap {
        val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        AndroidMatrix().apply { postRotate(rotationDeg.toFloat()) }.mapRect(bounds)
        val outW = ceil(bounds.width().toDouble()).toInt().coerceAtLeast(1)
        val outH = ceil(bounds.height().toDouble()).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888) // transparent, hasAlpha = true
        val draw = AndroidMatrix().apply {
            postRotate(rotationDeg.toFloat())
            postTranslate(-bounds.left, -bounds.top)
        }
        Canvas(out).drawBitmap(src, draw, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        src.recycle()
        return out
    }
}
