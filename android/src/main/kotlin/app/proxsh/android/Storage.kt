package app.proxsh.android

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import app.proxsh.core.transfer.ByteSource
import app.proxsh.core.util.sha256Hex
import java.io.File
import java.io.IOException
import java.io.InputStream

/** Something on the phone chosen to send: a content URI from the file picker or the Share menu. */
class UriSource private constructor(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val name: String,
    override val size: Long,
) : ByteSource {
    override val key: String = sha256Hex("$uri|$size".toByteArray())

    override fun open(offset: Long): InputStream {
        val input = resolver.openInputStream(uri) ?: throw IOException("Can't read $name")
        var left = offset
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) break
            left -= skipped
        }
        return input
    }

    companion object {
        fun of(resolver: ContentResolver, uri: Uri): UriSource? {
            var name: String? = null
            var size = -1L
            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) }
                        c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
                    }
                }
            }
            if (size < 0) {
                // Some providers don't say how big the file is, so count it once.
                size = runCatching { resolver.openInputStream(uri)?.use { countBytes(it) } }.getOrNull() ?: return null
            }
            val fileName = name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            return UriSource(resolver, uri, fileName, size)
        }

        private fun countBytes(input: InputStream): Long {
            val buffer = ByteArray(1 shl 16)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) return total
                total += n
            }
        }
    }
}

object Storage {
    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /**
     * Moves a received file out of the app's private folder into Downloads/ProxSh, where the user
     * and other apps can see it. Returns its new address, or null if it could not be saved.
     */
    fun saveToDownloads(context: Context, file: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mimeOf(file.name))
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ProxSh")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out, 1 shl 16) } }
                ?: throw IOException("Can't write to Downloads")
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            file.delete()
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
