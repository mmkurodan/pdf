package com.micklab.pdf.domain.edit

import android.net.Uri

/** A rectangle in visual page fractions (0..1), top-left origin (renderer-agnostic). */
data class FractionRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

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
    ) : EditOp

    /** Overlay an image (PNG/JPEG). The background is untouched. */
    data class AddImage(
        override val pageIndex: Int,
        override val rect: FractionRect,
        val source: Uri,
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
        /** Size/colour was changed: in-place can't restyle, so regenerate the whole run. */
        val restyled: Boolean = false,
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
