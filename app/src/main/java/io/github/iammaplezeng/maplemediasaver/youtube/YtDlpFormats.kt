package io.github.iammaplezeng.maplemediasaver.youtube

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.net.URI

internal object YtDlpFormats {
    private val formatId = Regex("[0-9]{1,6}(?:-[A-Za-z0-9._-]{1,60})?")
    fun parse(json: String, link: String): VideoManifest {
        val canonical = YouTubeLink.parse(link) ?: throw DownloadFailure(DownloadError.INVALID_LINK)
        val info = JsonParser.`object`().from(json)
        if (info.getString("id") != canonical.substringAfter("v=")) throw DownloadFailure(DownloadError.RESTRICTED)
        val availability = info.getString("availability", "")
        val facts = AccessFacts(availability == "public", (info["age_limit"] as? Number)?.toInt() ?: 0,
            info.getBoolean("is_live", false) || info.getString("live_status", "") in setOf("is_live", "is_upcoming", "post_live"),
            availability in setOf("premium_only", "subscriber_only", "needs_auth"), info.getBoolean("has_drm", false))
        if (!DownloadPolicy.allowed(facts, true)) throw DownloadFailure(DownloadError.RESTRICTED)
        val formats = info.getArray("formats")?.filterIsInstance<JsonObject>().orEmpty().filter(::usable)
        val audio = formats.filter { it.getString("vcodec", "none") == "none" && it.getString("acodec", "none") != "none" }
            .sortedWith(compareByDescending<JsonObject> { it.getString("ext") == "m4a" }
                .thenByDescending { (it["abr"] as? Number)?.toDouble() ?: 0.0 }).firstOrNull()
        val choices = formats.mapNotNull { format ->
            val height = (format["height"] as? Number)?.toInt() ?: 0
            val codec = format.getString("vcodec", "none")
            if (height !in 1..4320 || listOf("avc", "av01", "vp9", "vp09", "hev", "hvc").none { codec.startsWith(it) }) return@mapNotNull null
            val separate = format.getString("acodec", "none") == "none"
            if (separate && audio == null) return@mapNotNull null
            val selection = format.getString("format_id") + if (separate) "+${audio?.getString("format_id")}" else ""
            format to VideoChoice("${height}p · MP4", height, selection)
        }.sortedWith(compareByDescending<Pair<JsonObject, VideoChoice>> { it.second.height }
            .thenByDescending { it.first.getString("ext") == "mp4" }
            .thenByDescending { it.first.getString("vcodec", "").startsWith("avc") }
            .thenByDescending { (it.first["tbr"] as? Number)?.toDouble() ?: 0.0 })
            .map { it.second }.distinctBy { it.height }
        if (choices.isEmpty()) throw DownloadFailure(DownloadError.NO_FORMAT)
        return VideoManifest(canonical, info.getString("title", "YouTube").take(500), choices)
    }
    fun validSelection(value: String) = value.split('+').let { it.size in 1..2 && it.all(formatId::matches) }
    private fun usable(format: JsonObject): Boolean {
        if (!formatId.matches(format.getString("format_id", "")) || format.getBoolean("has_drm", false)) return false
        return try {
            val uri = URI(format.getString("url", ""))
            val host = uri.host?.lowercase().orEmpty()
            uri.scheme == "https" && uri.rawUserInfo == null && uri.port in setOf(-1, 443) &&
                (host.endsWith(".googlevideo.com") || host == "googlevideo.com" || host.endsWith(".youtube.com"))
        } catch (_: Exception) { false }
    }
}
