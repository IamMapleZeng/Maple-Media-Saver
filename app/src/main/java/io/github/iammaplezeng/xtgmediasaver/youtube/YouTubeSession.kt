package io.github.iammaplezeng.xtgmediasaver.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    fun inspect(text: String, permitted: Boolean) {
        if (mutable.value is YouTubeState.Downloading) return
        inspection?.cancel()
        val link = YouTubeLink.parse(text)
        if (link == null) { mutable.value = YouTubeState.Failed(DownloadError.INVALID_LINK); return }
        mutable.value = YouTubeState.Inspecting(link)
        inspection = scope.launch {
            try { mutable.value = YouTubeState.Ready(YouTubeResolver.resolve(link, permitted)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.value = YouTubeState.Failed((error as? DownloadFailure)?.reason ?: DownloadError.NETWORK) }
        }
    }
    fun update(state: YouTubeState) { mutable.value = state }
}
