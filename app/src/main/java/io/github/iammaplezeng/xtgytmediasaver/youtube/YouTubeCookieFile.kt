package io.github.iammaplezeng.xtgytmediasaver.youtube

/** Validates user-selected Netscape cookies and discards all non-YouTube domains. */
internal object YouTubeCookieFile {
    const val MAX_BYTES = 256 * 1024
    fun sanitize(text: String): String {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val lines = text.removePrefix("\uFEFF").lineSequence().toList()
        require(lines.firstOrNull()?.let { it == "# Netscape HTTP Cookie File" || it == "# HTTP Cookie File" } == true)
        val cookies = lines.mapNotNull { original ->
            if (original.isBlank() || original.startsWith('#') && !original.startsWith("#HttpOnly_")) return@mapNotNull null
            val fields = original.removePrefix("#HttpOnly_").split('\t')
            require(fields.size == 7)
            val domain = fields[0].removePrefix(".").lowercase()
            if (domain != "youtube.com" && !domain.endsWith(".youtube.com")) return@mapNotNull null
            require(fields[1] in setOf("TRUE", "FALSE") && fields[3] in setOf("TRUE", "FALSE"))
            require(fields[2].startsWith('/') && fields[4].toLongOrNull()?.let { it >= 0 } == true)
            require(fields[5].matches(Regex("[A-Za-z0-9_!#$%&'*+.^`|~-]{1,256}")))
            require(fields.all { field -> field.none { it.code < 32 || it.code == 127 } })
            original
        }
        require(cookies.isNotEmpty() && cookies.size <= 256)
        return "# Netscape HTTP Cookie File\n" + cookies.joinToString("\n", postfix = "\n")
    }
}
