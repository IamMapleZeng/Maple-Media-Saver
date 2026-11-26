package io.github.iammaplezeng.xtgmediasaver.youtube

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface YouTubeState {
    data object Idle : YouTubeState
    data class Inspecting(val link: String) : YouTubeState
    data class Ready(val manifest: VideoManifest) : YouTubeState
    data class Downloading(val percent: Int, val phase: Phase) : YouTubeState
    data class Complete(val uri: String) : YouTubeState
    data class Failed(val error: DownloadError) : YouTubeState
}
internal enum class Phase { VIDEO, AUDIO, MERGING, SAVING }

internal object YouTubeSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow<YouTubeState>(YouTubeState.Idle)
    val state = mutable.asStateFlow()
    private var inspection: Job? = null

    fun inspect(text: String, permitted: Boolean, fallback: VideoManifest? = null, visitorData: String? = null) {
        if (mutable.value is YouTubeState.Downloading) return
        inspection?.cancel()
        val link = YouTubeLink.parse(text)
        if (link == null) {
            mutable.value = YouTubeState.Failed(DownloadError.INVALID_LINK)
            return
        }
        val validFallback = fallback?.takeIf { it.link == link && it.choices.isNotEmpty() }
        if (validFallback != null) {
            mutable.value = YouTubeState.Ready(validFallback)
        } else {
            mutable.value = YouTubeState.Inspecting(link)
        }
        inspection = scope.launch {
            try {
                val result = YouTubeResolver.resolve(link, permitted, visitorData)
                currentCoroutineContext().ensureActive()
                val mergedChoices = if (validFallback != null) {
                    (result.choices + validFallback.choices)
                        .sortedWith(compareByDescending<VideoChoice> { it.height }.thenBy { it.audioUrl != null })
                        .distinctBy { it.height }
                } else result.choices
                val title = result.title.ifBlank { validFallback?.title.orEmpty() }
                mutable.value = YouTubeState.Ready(VideoManifest(link, title, mergedChoices))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                val chain = generateSequence(error as Throwable?) { it.cause }.take(6)
                    .joinToString(" | ") { item ->
                        item.javaClass.name + ": " + item.message.orEmpty()
                            .replace(Regex("https?://[^\\s]+"), "[url]")
                            .replace(link.substringAfter("v="), "[video]").take(400)
                    }
                Log.w("XTGMediaSaver", "[YT] resolve failed: $chain")
                if (validFallback != null) {
                    mutable.value = YouTubeState.Ready(validFallback)
                } else {
                    mutable.value = YouTubeState.Failed(DownloadErrors.classify(error))
                }
            }
        }
    }

    fun update(state: YouTubeState) { mutable.value = state }

    fun resetIncoming() {
        if (mutable.value is YouTubeState.Downloading) return
        inspection?.cancel()
        mutable.value = YouTubeState.Idle
    }
}
