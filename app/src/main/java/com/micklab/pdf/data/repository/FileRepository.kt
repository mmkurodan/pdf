package com.micklab.pdf.data.repository

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.micklab.pdf.domain.model.OutputFile
import java.io.InputStream
import java.io.OutputStream

/** Where a produced file should be written. */
sealed interface OutputDestination {
    /** A user-selected SAF tree (folder). */
    data class Tree(val treeUri: Uri) : OutputDestination

    /** App-private cache under [subdir]; shared out via FileProvider. */
    data class Cache(val subdir: String) : OutputDestination

    /**
     * The public Download folder, under `Download/[subFolder]`.
     * Uses MediaStore on Android 10+ (no permission); on API <= 28 it writes to
     * the public directory and requires WRITE_EXTERNAL_STORAGE.
     */
    data class Downloads(val subFolder: String) : OutputDestination
}

/**
 * All file access is funneled through here. It never touches user files
 * destructively — inputs are only read; outputs are always freshly created
 * (端末内のファイルを破壊しない安全設計).
 */
interface FileRepository {

    fun displayName(uri: Uri): String

    /** Size in bytes, or -1 if the provider doesn't report it. */
    fun size(uri: Uri): Long

    fun mimeType(uri: Uri): String?

    fun openInput(uri: Uri): InputStream

    fun openFileDescriptor(uri: Uri, mode: String = "r"): ParcelFileDescriptor

    /**
     * Creates a new file at [destination] and streams bytes into it via [writer].
     * The stream is closed for you. Returns metadata about the created file.
     */
    fun writeFile(
        destination: OutputDestination,
        displayName: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ): OutputFile

    /** Best-effort persist of a read grant so a picked file survives process death. */
    fun persistReadPermission(uri: Uri)

    /** Best-effort persist of a tree (folder) grant. */
    fun persistTreePermission(treeUri: Uri)
}
