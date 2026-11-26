package io.github.iammaplezeng.xtgmediasaver.youtube

import android.os.Bundle
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.iammaplezeng.xtgmediasaver.XTGMediaSaverModule

/** Version-specific, read-only inspection of the host's own player response. No credentials. */
internal object NativePlaybackCapture {
    private data class Entry(val manifest: VideoManifest, val capturedAt: Long)
    private val recent = LinkedHashMap<String, Entry>()

    @Synchronized fun forVideo(link: String): Bundle? {
        val canonical = YouTubeLink.parse(link) ?: return null
        val entry = recent[canonical] ?: return null
        if (SystemClock.elapsedRealtime() - entry.capturedAt !in 0..MAX_AGE) return null
        return NativePlaybackPayload.encode(entry.manifest)
    }

    fun install(loader: ClassLoader) {
        val response = Class.forName("bhsp", false, loader)
        val builder = Class.forName("allq", false, loader)
        XposedBridge.hookMethod(builder.getDeclaredConstructor(response), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                try {
                    val player = param.args[0] ?: return
                    val details = field(player, "g") ?: return
                    val streams = field(player, "h") ?: return
                    val id = field(details, "c") as? String ?: return
                    val canonical = YouTubeLink.parse("https://www.youtube.com/watch?v=$id") ?: return
                    val status = field(player, "f") ?: return
                    // Live/post-live/head-playable flags used by the host's allq/alls playback classifier.
                    val live = listOf("f", "g", "i").any { field(details, it) == true }
                    if (field(status, "c") != 0 || live || items(streams, "g").isNotEmpty()) {
                        synchronized(this@NativePlaybackCapture) { recent.remove(canonical) }; return
                    }
                    val progressive = items(streams, "e")
                    val adaptive = items(streams, "f")
                    val audio = adaptive.filter { mime(it).startsWith("audio/mp4") && mime(it).contains("mp4a", true) }
                        .filter { validUrl(it) != null }.maxByOrNull { (field(it, "h") as? Int) ?: 0 }?.let(::validUrl)
                    val choices = (progressive + adaptive).mapNotNull { format ->
                        val mime = mime(format)
                        val height = (field(format, "k") as? Int) ?: 0
                        val url = validUrl(format) ?: return@mapNotNull null
                        val separate = progressive.none { it === format }
                        if (!mime.startsWith("video/mp4") || !mime.contains("avc", true) || height !in 1..4320 || (separate && audio == null)) return@mapNotNull null
                        VideoChoice("${height}p · MP4", height, url, if (separate) audio else null)
                    }.sortedWith(compareByDescending<VideoChoice> { it.height }.thenBy { it.audioUrl != null }).distinctBy { it.height }
                    if (choices.isNotEmpty()) {
                        val manifest = VideoManifest(canonical, (field(details, "d") as? String).orEmpty().take(500), choices.take(20))
                        synchronized(this@NativePlaybackCapture) {
                            recent[canonical] = Entry(manifest, SystemClock.elapsedRealtime())
                            while (recent.size > 12) recent.remove(recent.keys.first())
                        }
                    } else synchronized(this@NativePlaybackCapture) { recent.remove(canonical) }
                    XTGMediaSaverModule.log("[YT] native player available: formats=${progressive.size + adaptive.size} mp4=${choices.size}")
                } catch (error: Throwable) {
                    XTGMediaSaverModule.log("[YT] native player inspection skipped ${error.javaClass.simpleName}")
                }
            }
        })
        XTGMediaSaverModule.log("[YT] native player observer installed")
    }

    private fun items(owner: Any, name: String) = (field(owner, name) as? Iterable<*>)?.filterNotNull().orEmpty()
    private fun mime(owner: Any) = (field(owner, "g") as? String).orEmpty()
    private fun validUrl(owner: Any) = (field(owner, "f") as? String)?.takeIf { NativePlaybackPayload.validMediaUrl(it) }
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private const val MAX_AGE = 10 * 60_000L
}
