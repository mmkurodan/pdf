package com.micklab.pdf.domain.usecase

import android.net.Uri
import com.micklab.pdf.data.repository.OutputDestination

internal const val MIME_PDF = "application/pdf"

/** Subfolder created under the public Download folder for this app's output. */
internal const val DOWNLOADS_SUBFOLDER = "PDFToolkit"

/**
 * A picked SAF folder becomes a [OutputDestination.Tree]; otherwise output goes
 * to the public Download folder (visible to the user, no FileProvider needed).
 */
internal fun Uri?.toDestination(): OutputDestination =
    if (this != null) OutputDestination.Tree(this) else OutputDestination.Downloads(DOWNLOADS_SUBFOLDER)
