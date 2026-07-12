package com.micklab.pdf.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.micklab.pdf.PdfToolsApp
import com.micklab.pdf.domain.model.OutputFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : FileRepository {

    private val resolver get() = context.contentResolver
    private val authority get() = "${context.packageName}.fileprovider"

    override fun displayName(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                c.getString(idx)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    override fun size(uri: Uri): Long {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) {
                return c.getLong(idx)
            }
        }
        return -1L
    }

    override fun mimeType(uri: Uri): String? = resolver.getType(uri)

    override fun openInput(uri: Uri) =
        resolver.openInputStream(uri) ?: throw IOException("入力ストリームを開けません: $uri")

    override fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor =
        resolver.openFileDescriptor(uri, mode)
            ?: throw IOException("ファイル記述子を開けません: $uri")

    override fun writeFile(
        destination: OutputDestination,
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): OutputFile = when (destination) {
        is OutputDestination.Tree -> writeToTree(destination.treeUri, displayName, mimeType, writer)
        is OutputDestination.Cache -> writeToCache(destination.subdir, displayName, mimeType, writer)
        is OutputDestination.Downloads -> writeToDownloads(destination.subFolder, displayName, mimeType, writer)
    }

    private fun writeToDownloads(
        subFolder: String,
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): OutputFile {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: scoped storage via MediaStore. No permission required.
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
                ?: throw IOException("Download フォルダに作成できません")
            (resolver.openOutputStream(itemUri) ?: throw IOException("出力ストリームを開けません")).use(writer)
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            Log.i(PdfToolsApp.TAG, "Wrote $displayName to Downloads (MediaStore)")
            return OutputFile(itemUri, displayName, mimeType, size(itemUri))
        }

        // API 24-28: requires WRITE_EXTERNAL_STORAGE (requested at app start).
        @Suppress("DEPRECATION")
        val downloadsRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(downloadsRoot, subFolder).apply { mkdirs() }
        val file = File(dir, displayName)
        file.outputStream().use(writer)
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mimeType), null)
        val uri = FileProvider.getUriForFile(context, authority, file)
        Log.i(PdfToolsApp.TAG, "Wrote $displayName to Downloads (legacy)")
        return OutputFile(uri, displayName, mimeType, file.length())
    }

    private fun writeToTree(
        treeUri: Uri,
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): OutputFile {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IOException("出力フォルダにアクセスできません")
        // Replace an existing same-named file so re-runs don't accumulate.
        tree.findFile(displayName)?.takeIf { it.isFile }?.delete()
        val doc = tree.createFile(mimeType, displayName)
            ?: throw IOException("出力ファイルを作成できません: $displayName")
        (resolver.openOutputStream(doc.uri) ?: throw IOException("出力ストリームを開けません"))
            .use(writer)
        val size = doc.length()
        Log.i(PdfToolsApp.TAG, "Wrote ${doc.name} ($size bytes) to tree")
        return OutputFile(doc.uri, doc.name ?: displayName, mimeType, size)
    }

    private fun writeToCache(
        subdir: String,
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): OutputFile {
        val dir = File(context.cacheDir, subdir).apply { mkdirs() }
        val file = File(dir, displayName)
        file.outputStream().use(writer)
        val uri = FileProvider.getUriForFile(context, authority, file)
        Log.i(PdfToolsApp.TAG, "Wrote ${file.name} (${file.length()} bytes) to cache")
        return OutputFile(uri, displayName, mimeType, file.length())
    }

    override fun persistReadPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { Log.w(PdfToolsApp.TAG, "persistReadPermission failed", it) }
    }

    override fun persistTreePermission(treeUri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Log.w(PdfToolsApp.TAG, "persistTreePermission failed", it) }
    }
}
