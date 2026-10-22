package com.nhdeveloper.nhfiles.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class LoadedText(
    val text: String,
    val charset: String,
    val hasBom: Boolean,
    val crlf: Boolean,
    val truncated: Boolean,
    val binary: Boolean,
)

object TextFileIO {
    const val EDIT_LIMIT = 2L * 1024 * 1024
    const val PREVIEW_BYTES = 1024 * 1024

    val ENCODINGS = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "ISO-8859-1", "windows-1252")

    fun load(file: File, charsetOverride: String? = null): LoadedText {
        val size = file.length()
        val truncated = size > EDIT_LIMIT
        val toRead = if (truncated) PREVIEW_BYTES else size.toInt()
        val bytes = ByteArray(toRead)
        var len = 0
        FileInputStream(file).use { input ->
            while (len < toRead) {
                val n = input.read(bytes, len, toRead - len)
                if (n < 0) break
                len += n
            }
        }

        var offset = 0
        var detected: String? = null
        var bom = false
        if (len >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            detected = "UTF-8"; bom = true; offset = 3
        } else if (len >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            detected = "UTF-16LE"; bom = true; offset = 2
        } else if (len >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            detected = "UTF-16BE"; bom = true; offset = 2
        }

        val isUtf16 = detected?.startsWith("UTF-16") == true || charsetOverride?.startsWith("UTF-16") == true
        var binary = false
        if (!isUtf16) {
            val check = minOf(len, 8000)
            for (i in 0 until check) {
                if (bytes[i].toInt() == 0) { binary = true; break }
            }
        }

        val cs: String
        val raw: String
        if (charsetOverride != null) {
            cs = charsetOverride
            raw = String(bytes, offset, len - offset, Charset.forName(cs))
        } else if (detected != null) {
            cs = detected
            raw = String(bytes, offset, len - offset, Charset.forName(cs))
        } else {
            val strict = decodeStrict(bytes, offset, len - offset, Charsets.UTF_8)
            if (strict != null) {
                cs = "UTF-8"; raw = strict
            } else if (truncated) {
                cs = "UTF-8"; raw = String(bytes, offset, len - offset, Charsets.UTF_8)
            } else {
                cs = "windows-1252"; raw = String(bytes, offset, len - offset, Charset.forName("windows-1252"))
            }
        }
        val crlf = raw.contains("\r\n")
        val text = if (crlf) raw.replace("\r\n", "\n") else raw
        return LoadedText(text, cs, bom, crlf, truncated, binary)
    }

    private fun decodeStrict(b: ByteArray, off: Int, len: Int, cs: Charset): String? = try {
        cs.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(b, off, len))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /**
     * Writes to a temporary file in the same folder and renames it over [target] only after the write
     * succeeded, so a failed save can never damage the original file.
     */
    fun save(target: File, text: String, charsetName: String, bom: Boolean, crlf: Boolean) {
        val out = if (crlf) text.replace("\n", "\r\n") else text
        val body = out.toByteArray(Charset.forName(charsetName))
        val prefix = when {
            !bom -> ByteArray(0)
            charsetName == "UTF-8" -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            charsetName == "UTF-16LE" -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
            charsetName == "UTF-16BE" -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
            else -> ByteArray(0)
        }
        val dir = target.parentFile ?: throw IOException("Invalid target folder")
        if (dir.usableSpace < body.size + prefix.size + 4096L) throw IOException("Not enough free storage space")
        val tmp = File(dir, "." + target.name + ".nhsave")
        try {
            FileOutputStream(tmp).use { os ->
                os.write(prefix)
                os.write(body)
                os.fd.sync()
            }
            if (!tmp.renameTo(target)) throw IOException("Could not replace \"${target.name}\"")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
