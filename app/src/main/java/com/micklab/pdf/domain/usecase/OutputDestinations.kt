package com.micklab.pdf.domain.usecase

import android.net.Uri
import com.micklab.pdf.data.repository.OutputDestination

internal const val MIME_PDF = "application/pdf"

/** Default output subfolder used when the user hasn't picked a SAF tree. */
internal const val CACHE_OUTPUT_SUBDIR = "outputs"

/**
 * A picked SAF folder becomes a [OutputDestination.Tree]; otherwise output goes
 * to the app cache and is shared via FileProvider.
 */
internal fun Uri?.toDestination(): OutputDestination =
    if (this != null) OutputDestination.Tree(this) else OutputDestination.Cache(CACHE_OUTPUT_SUBDIR)
