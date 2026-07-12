package com.micklab.pdf.ui.common

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.micklab.pdf.domain.model.OutputFile

/** Shares one or more produced files through the system chooser. */
fun Context.shareOutputs(files: List<OutputFile>) {
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { it.uri })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = files.first().mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = files.map { it.mimeType }.distinct().singleOrNull() ?: "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, "共有").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens a produced file in a viewer, if any app can handle it. */
fun Context.openOutput(file: OutputFile) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(file.uri, file.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, "開けるアプリがありません", Toast.LENGTH_SHORT).show() }
}

/** Human-readable byte size. */
fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "-"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
