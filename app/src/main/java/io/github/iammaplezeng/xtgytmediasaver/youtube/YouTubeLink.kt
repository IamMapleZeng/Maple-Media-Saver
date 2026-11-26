package io.github.iammaplezeng.xtgytmediasaver.youtube

import java.net.URI
import java.net.URLDecoder

/** Only a single canonical public YouTube video URL is accepted, never arbitrary fetch URLs. */
internal object YouTubeLink {
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")
    private val urls = Regex("https?://[^\\s<>]+")
    fun parse(text: String): String? {
        if (text.length > 16_384) return null
        val matches = urls.findAll(text).mapNotNull { canonical(it.value.trimEnd('.', ',', ')', ']', '。')) }.distinct().toList()
        return matches.singleOrNull()
    }
    private fun canonical(raw: String): String? = try {
        val uri = URI(raw)
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1) null else {
            val parts = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            val id = when (uri.host?.lowercase()) {
                "youtu.be" -> parts.singleOrNull()
                "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when {
                    uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
                        .filter { it.size == 2 && it[0] == "v" }.singleOrNull()?.get(1)?.let { URLDecoder.decode(it, "UTF-8") }
                    parts.size == 2 && parts[0] in setOf("shorts", "embed", "live") -> parts[1]
                    else -> null
                }
                else -> null
            }
            if (id != null && idPattern.matches(id)) "https://www.youtube.com/watch?v=$id" else null
        }
    } catch (_: Exception) { null }
}
