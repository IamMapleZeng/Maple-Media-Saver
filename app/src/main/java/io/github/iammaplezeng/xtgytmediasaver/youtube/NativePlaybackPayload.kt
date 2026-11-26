package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.os.Bundle
import java.net.URI

/** Only bounded media metadata crosses processes; cookies and account headers never do. */
internal object NativePlaybackPayload {
    const val EXTRA = "io.github.iammaplezeng.xtgytmediasaver.NATIVE_PLAYBACK"
    const val EXTRA_VISITOR = "io.github.iammaplezeng.xtgytmediasaver.YT_VISITOR_DATA"
    fun encode(manifest: VideoManifest): Bundle {
        var budget = 60_000
        val choices = manifest.choices.take(20).takeWhile { choice ->
            budget -= choice.videoUrl.length + choice.audioUrl.orEmpty().length
            budget >= 0
        }
        return Bundle().apply {
        putString("link", manifest.link); putString("title", manifest.title)
        putLong("created", System.currentTimeMillis())
        putStringArrayList("video", ArrayList(choices.map { it.videoUrl }))
        putStringArrayList("audio", ArrayList(choices.map { it.audioUrl.orEmpty() }))
        putIntArray("height", choices.map { it.height }.toIntArray())
        }
    }
    fun decode(bundle: Bundle?, expectedLink: String): VideoManifest? = try {
        if (bundle == null || YouTubeLink.parse(expectedLink) != bundle.getString("link")) null
        else {
            val age = System.currentTimeMillis() - bundle.getLong("created")
            val video = bundle.getStringArrayList("video").orEmpty()
            val audio = bundle.getStringArrayList("audio").orEmpty()
            val heights = bundle.getIntArray("height") ?: intArrayOf()
            if (age !in 0..600_000L || video.size !in 1..20 || audio.size != video.size || heights.size != video.size ||
                video.sumOf { it.length } + audio.sumOf { it.length } > 60_000) null
            else {
                val choices = video.indices.mapNotNull { i ->
                    val a = audio[i].takeIf { it.isNotEmpty() }
                    if (heights[i] !in 1..4320 || !validMediaUrl(video[i]) || (a != null && !validMediaUrl(a))) null
                    else VideoChoice("${heights[i]}p · MP4", heights[i], video[i], a)
                }
                if (choices.size != video.size) null else VideoManifest(expectedLink, bundle.getString("title").orEmpty().take(500), choices)
            }
        }
    } catch (_: RuntimeException) { null }

    fun validMediaUrl(value: String): Boolean = try {
        val uri = URI(value)
        value.length <= 12_288 && uri.scheme == "https" && uri.rawUserInfo == null && uri.port in setOf(-1, 443) &&
            uri.host?.lowercase()?.endsWith(".googlevideo.com") == true && uri.path == "/videoplayback" &&
            !uri.rawQuery.orEmpty().contains(Regex("(?:^|&)(?:drm|license)=", RegexOption.IGNORE_CASE))
    } catch (_: Exception) { false }
}
