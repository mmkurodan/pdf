package com.micklab.pdf.domain.edit

import android.net.Uri

/** Marked-content tag wrapping the (separately stroked) underline of our added text,
 *  so the text editor can remove it together with the text run instead of orphaning it. */
internal const val UNDERLINE_MC_TAG = "MicklabUL"

/** A rectangle in visual page fractions (0..1), top-left origin (renderer-agnostic). */
data class FractionRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Uniformly scale a rect about its centre, clamped to the page (0..1). */
fun FractionRect.scaledAboutCenter(scale: Float): FractionRect {
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val hw = (right - left) / 2f * scale
    val hh = (bottom - top) / 2f * scale
    return FractionRect(
        (cx - hw).coerceIn(0f, 1f), (cy - hh).coerceIn(0f, 1f),
        (cx + hw).coerceIn(0f, 1f), (cy + hh).coerceIn(0f, 1f),
    )
}

/** One editing operation, positioned in visual page fractions. */
sealed interface EditOp {
    val pageIndex: Int  // 0-based
    val rect: FractionRect

    /** Add new text, drawn with the embedded Noto font. The background is untouched. */
    data class AddText(
        override val pageIndex: Int,
        override val rect: FractionRect,
        val text: String,
        val fontSizePt: Float,
        val colorRgb: Int, // 0xRRGGBB
        /** Synthetic styling (font-agnostic): faux-bold stroke, italic shear, underline rule. */
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        /** Clockwise rotation in degrees (as the reader sees it), about the box centre. */
        val rotationDeg: Int = 0,
        /** Optional link: when non-blank, a URI link annotation is added over [rect]. */
        val url: String = "",
    ) : EditOp

    /** Overlay an image (PNG/JPEG). The background is untouched. */
    data class AddImage(
        override val pageIndex: Int,
        override val rect: FractionRect,
        val source: Uri,
        /** Clockwise rotation in degrees; applied to the image pixels before embedding. */
        val rotationDeg: Int = 0,
    ) : EditOp

    /**
     * Edit existing text *in place* using the page's own font. Only performed
     * when the text layer is genuinely editable; otherwise it is skipped — there
     * is deliberately no white-out / re-draw fallback (product decision).
     */
    data class EditExistingText(
        override val pageIndex: Int,
        override val rect: FractionRect,
        val target: String,
        val replacement: String,
        val fontSizePt: Float = 12f,
        val colorRgb: Int = 0x000000,
        /** Which occurrence of [target] on the page (0-based), for unique targeting. */
        val occurrence: Int = 0,
        /** The run was dragged: regenerate the whole run at [rect] instead of editing in place. */
        val moved: Boolean = false,
        /** Size/colour/style was changed: in-place can't restyle, so regenerate the whole run. */
        val restyled: Boolean = false,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val rotationDeg: Int = 0,
        /** Optional link over the (regenerated) run's rect. */
        val url: String = "",
    ) : EditOp

    /** Remove an existing text-layer run (no redraw). */
    data class DeleteExistingText(
        override val pageIndex: Int,
        override val rect: FractionRect,
        val target: String,
        val occurrence: Int = 0,
    ) : EditOp

    /** Move an existing image-annotation layer (identified by [id]) to [rect]. */
    data class MoveImage(
        override val pageIndex: Int,
        override val rect: FractionRect,
        val id: String,
        /** Non-zero: re-embed the layer's pixels rotated by this many degrees (quality may drop). */
        val rotationDeg: Int = 0,
    ) : EditOp

    /** Delete an existing image-annotation layer (identified by [id]). */
    data class DeleteImage(
        override val pageIndex: Int,
        override val rect: FractionRect,
        val id: String,
    ) : EditOp
}

/** Per-op outcome, so callers can show what was applied versus skipped and why. */
data class EditOpResult(val op: EditOp, val applied: Boolean, val detail: String)
