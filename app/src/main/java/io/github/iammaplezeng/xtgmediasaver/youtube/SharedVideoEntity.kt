package io.github.iammaplezeng.xtgmediasaver.youtube

import java.net.URLDecoder
import java.util.Base64

/** YouTube 21.47 serializedSharedEntity: protobuf field 1 is the shared video ID. */
internal object SharedVideoEntity {
    fun videoLink(encoded: String): String? = try {
        if (encoded.length > 65_536) null else decode(bytes(encoded))
    } catch (_: Exception) { null }
    private fun bytes(encoded: String): ByteArray {
        val value = URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
        return try { Base64.getUrlDecoder().decode(value) } catch (_: IllegalArgumentException) { Base64.getDecoder().decode(value) }
    }
    /** Schema-only diagnostics: no video IDs, URLs or account data. */
    fun layout(encoded: String): String = try {
        val data = bytes(encoded)
        var cursor = 0
        fun number(): Int {
            var value = 0; var shift = 0
            while (cursor < data.size && shift < 28) {
                val part = data[cursor++].toInt() and 255
                value = value or ((part and 127) shl shift)
                if (part and 128 == 0) return value
                shift += 7
            }
            throw IllegalArgumentException()
        }
        val result = ArrayList<String>()
        while (cursor < data.size && result.size < 10) {
            val tag = number()
            when (tag and 7) {
                0 -> { number(); result.add("${tag ushr 3}:varint") }
                1 -> { cursor += 8; result.add("${tag ushr 3}:fixed64") }
                5 -> { cursor += 4; result.add("${tag ushr 3}:fixed32") }
                2 -> {
                    val length = number()
                    if (length < 0 || length > data.size - cursor) throw IllegalArgumentException()
                    val idShape = length == 11 && Regex("[A-Za-z0-9_-]{11}").matches(data.copyOfRange(cursor, cursor + length).toString(Charsets.UTF_8))
                    result.add("${tag ushr 3}:bytes($length,id=$idShape)"); cursor += length
                }
                else -> { result.add("${tag ushr 3}:wire${tag and 7}"); break }
            }
        }
        "chars=${encoded.length} bytes=${data.size} fields=${result.joinToString()}"
    } catch (_: Exception) { "unrecognized chars=${encoded.length}" }
    private fun decode(bytes: ByteArray): String? {
        var cursor = 0
        fun integer(): Long {
            var value = 0L
            for (shift in 0..63 step 7) {
                if (cursor >= bytes.size) throw IllegalArgumentException("Truncated entity")
                val part = bytes[cursor++].toInt() and 255
                value = value or ((part and 127).toLong() shl shift)
                if (part and 128 == 0) return value
            }
            throw IllegalArgumentException("Invalid entity")
        }
        var video: String? = null
        while (cursor < bytes.size) {
            val tag = integer().toInt()
            when (tag and 7) {
                0 -> integer()
                1 -> cursor += 8
                5 -> cursor += 4
                2 -> {
                    val length = integer()
                    if (length < 0 || length > bytes.size - cursor) return null
                    if (tag ushr 3 == 1) {
                        val id = bytes.copyOfRange(cursor, cursor + length.toInt()).toString(Charsets.UTF_8)
                        if (!Regex("[A-Za-z0-9_-]{11}").matches(id) || video != null) return null
                        video = id
                    }
                    cursor += length.toInt()
                }
                else -> return null
            }
            if (cursor > bytes.size) return null
        }
        return video?.let { "https://www.youtube.com/watch?v=$it" }
    }
}
