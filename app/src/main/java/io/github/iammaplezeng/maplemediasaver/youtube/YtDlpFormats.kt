package io.github.iammaplezeng.maplemediasaver.youtube

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import java.net.URI

internal object YtDlpFormats {
    private val formatId = Regex("[0-9]{1,6}(?:-[A-Za-z0-9._-]{1,60})?")
    private val videoCodecs = listOf("avc", "av01", "vp9", "vp09", "hev", "hvc")
    private val audioPreference = compareBy<JsonObject> { it.getString("ext") == "m4a" }
        .thenBy { (it["abr"] as? Number)?.toDouble() ?: 0.0 }
    private val videoPreference = compareBy<JsonObject> { it.getString("ext") == "mp4" }
        .thenBy { it.getString("vcodec", "").startsWith("avc") }
        .thenBy { (it["tbr"] as? Number)?.toDouble() ?: 0.0 }

    fun parse(json: String, link: String): VideoManifest {
        val canonical = YouTubeLink.parse(link) ?: throw DownloadFailure(DownloadError.INVALID_LINK)
        val info = JsonParser.`object`().from(json)
        if (info.getString("id") != canonical.substringAfter("v=")) throw DownloadFailure(DownloadError.RESTRICTED)
        val availability = info.getString("availability", "")
        val facts = AccessFacts(availability == "public", (info["age_limit"] as? Number)?.toInt() ?: 0,
            info.getBoolean("is_live", false) || info.getString("live_status", "") in setOf("is_live", "is_upcoming", "post_live"),
            availability in setOf("premium_only", "subscriber_only", "needs_auth"), info.getBoolean("has_drm", false))
        if (!DownloadPolicy.allowed(facts, true)) throw DownloadFailure(DownloadError.RESTRICTED)
        val formats = info.getArray("formats").orEmpty().asSequence()
            .filterIsInstance<JsonObject>().filter(::usable).toList()
        val audio = formats.asSequence()
            .filter { it.getString("vcodec", "none") == "none" && it.getString("acodec", "none") != "none" }
            .maxWithOrNull(audioPreference)
        val bestByHeight = mutableMapOf<Int, JsonObject>()
        for (format in formats) {
            val height = (format["height"] as? Number)?.toInt() ?: continue
            val codec = format.getString("vcodec", "none")
            if (height !in 1..4320 || videoCodecs.none { codec.startsWith(it) }) continue
            if (audio == null && format.getString("acodec", "none") == "none") continue

            val current = bestByHeight[height]
            if (current == null || videoPreference.compare(format, current) > 0) bestByHeight[height] = format
        }
        val choices = bestByHeight.keys.sortedDescending().map { height ->
            val format = bestByHeight.getValue(height)
            val id = format.getString("format_id")
            val selection = if (format.getString("acodec", "none") == "none") "$id+${requireNotNull(audio).getString("format_id")}" else id
            VideoChoice("${height}p · MP4", height, selection)
        }
        if (choices.isEmpty()) throw DownloadFailure(DownloadError.NO_FORMAT)
        return VideoManifest(canonical, info.getString("title", "YouTube").take(500), choices)
    }
    fun validSelection(value: String) = value.split('+').let { it.size in 1..2 && it.all(formatId::matches) }
    private fun usable(format: JsonObject): Boolean {
        if (!formatId.matches(format.getString("format_id", "")) || format.getBoolean("has_drm", false)) return false
        return try {
            val uri = URI(format.getString("url", ""))
            val host = uri.host?.lowercase().orEmpty()
            uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
                (host.endsWith(".googlevideo.com") || host == "googlevideo.com" || host.endsWith(".youtube.com"))
        } catch (_: Exception) { false }
    }
}
