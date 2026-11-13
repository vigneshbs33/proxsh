package app.proxsh.core.transfer

import app.proxsh.core.util.sha256Hex
import java.io.File
import java.io.InputStream

/** Where outgoing bytes come from. A file on the laptop, or a content URI the Android app wraps. */
interface ByteSource {
    val name: String
    val size: Long

    /** Same for the same content every time, so an interrupted send can carry on where it stopped. */
    val key: String

    fun open(offset: Long): InputStream
}

class FileSource(private val file: File) : ByteSource {
    override val name: String get() = file.name
    override val size: Long = file.length()
    override val key: String = sha256Hex("${file.absolutePath}|$size|${file.lastModified()}".toByteArray())

    override fun open(offset: Long): InputStream {
        val input = file.inputStream()
        var left = offset
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) break
            left -= skipped
        }
        return input
    }
}

sealed interface OutgoingItem {
    class Content(val source: ByteSource) : OutgoingItem
    class Text(val text: String) : OutgoingItem

    companion object {
        fun file(file: File): OutgoingItem = Content(FileSource(file))
    }
}

class Progress(val name: String, val doneBytes: Long, val totalBytes: Long)

sealed interface SendOutcome {
    data object Done : SendOutcome
    data class Rejected(val reason: String) : SendOutcome
}
