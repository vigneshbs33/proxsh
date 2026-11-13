package app.proxsh.core.util

import java.io.File
import java.security.MessageDigest

object Hex {
    private val digits = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xff
            out[i * 2] = digits[v ushr 4]
            out[i * 2 + 1] = digits[v and 0x0f]
        }
        return String(out)
    }
}

fun sha256Hex(bytes: ByteArray): String = Hex.encode(MessageDigest.getInstance("SHA-256").digest(bytes))

fun sha256Hex(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(1 shl 20)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return Hex.encode(md.digest())
}

/** Cleans a file name sent by another device so it can never escape the receive folder. */
object FileNames {
    private val reserved = Regex("^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\\..*)?$", RegexOption.IGNORE_CASE)
    private val badChars = Regex("[\\u0000-\\u001f<>:\"|?*]")
    private const val MAX_LENGTH = 200

    fun sanitize(name: String): String {
        var base = name.substringAfterLast('/').substringAfterLast('\\')
        base = base.replace(badChars, "_").trim().trimEnd('.', ' ')
        if (base.isEmpty() || base == "." || base == "..") base = "file"
        if (reserved.matches(base)) base = "_$base"
        if (base.length > MAX_LENGTH) {
            val ext = base.substringAfterLast('.', "").let { if (it.isNotEmpty() && it.length <= 20) ".$it" else "" }
            base = base.take(MAX_LENGTH - ext.length) + ext
        }
        return base
    }

    /** dir/name, or dir/name (1).ext, (2).ext... if that already exists. */
    fun uniqueIn(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$stem ($i)$ext")
            i++
        }
        return candidate
    }
}
