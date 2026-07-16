package com.micklab.pdf.domain.edit

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream

/**
 * Images added as **PDF annotations** (rubber stamps) rather than flattened into
 * the page content, so they survive saving and can be moved/deleted after
 * re-opening. Each is tagged with a stable id in its dictionary so it can be
 * found again regardless of ordering.
 *
 * EXPERIMENTAL: whether the annotation image is visible depends on the viewer
 * (and, for the in-app preview, on android's PdfRenderer) rendering annotation
 * appearance streams. Validate on-device.
 */
object PdfImageLayer {

    private val TAG = COSName.getPDFName("MicklabImageLayer")

    fun newId(): String = "img_${System.nanoTime()}"

    /** Adds an image annotation fitted (aspect-preserved) inside the user-space [box] = [llx,lly,urx,ury]. */
    fun add(document: PDDocument, page: PDPage, box: FloatArray, image: PDImageXObject, id: String) {
        val bw = (box[2] - box[0]).coerceAtLeast(1f)
        val bh = (box[3] - box[1]).coerceAtLeast(1f)
        val iw = image.width.toFloat().coerceAtLeast(1f)
        val ih = image.height.toFloat().coerceAtLeast(1f)
        val scale = minOf(bw / iw, bh / ih)
        val w = iw * scale
        val h = ih * scale
        val cx = box[0] + (bw - w) / 2f
        val cy = box[1] + (bh - h) / 2f

        val apStream = PDAppearanceStream(document).apply {
            setBBox(PDRectangle(0f, 0f, w, h))
            resources = PDResources()
        }
        PDPageContentStream(document, apStream).use { cs -> cs.drawImage(image, 0f, 0f, w, h) }

        val stamp = PDAnnotationRubberStamp().apply {
            rectangle = PDRectangle(cx, cy, w, h)
            setPrinted(true)
            appearance = PDAppearanceDictionary().apply { setNormalAppearance(apStream) }
            cosObject.setString(TAG, id)
        }
        val annotations = page.annotations
        annotations.add(stamp)
        page.annotations = annotations
    }

    /** Our tagged image annotations on [page], paired with their id, in page order. */
    fun tagged(page: PDPage): List<Pair<PDAnnotation, String>> =
        runCatching { page.annotations }.getOrDefault(emptyList())
            .mapNotNull { annotation -> annotation.cosObject.getString(TAG)?.let { annotation to it } }

    fun moveTo(page: PDPage, id: String, box: FloatArray): Boolean {
        val annotation = tagged(page).firstOrNull { it.second == id }?.first ?: return false
        annotation.rectangle = PDRectangle(box[0], box[1], box[2] - box[0], box[3] - box[1])
        return true
    }

    fun remove(page: PDPage, id: String): Boolean {
        val annotations = page.annotations
        val target = annotations.firstOrNull { runCatching { it.cosObject.getString(TAG) }.getOrNull() == id }
            ?: return false
        annotations.remove(target)
        page.annotations = annotations
        return true
    }
}
