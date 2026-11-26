package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.content.Context
import android.util.Log
import java.util.UUID
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
import kotlinx.coroutines.runInterruptible

internal sealed interface YouTubeState {
    data object Idle : YouTubeState
    data class Inspecting(val link: String) : YouTubeState
    data class Ready(val manifest: VideoManifest) : YouTubeState
    data class Downloading(val percent: Int, val phase: Phase) : YouTubeState
    data class Complete(val uri: String) : YouTubeState
    data class Failed(val error: DownloadError) : YouTubeState
}
internal enum class Phase { VIDEO, AUDIO, MERGING, SAVING }
internal data class DownloadSelection(val id: String, val manifest: VideoManifest, val choice: VideoChoice)

internal object YouTubeSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow<YouTubeState>(YouTubeState.Idle)
    val state = mutable.asStateFlow()
    private var inspection: Job? = null
    private var pending: DownloadSelection? = null

    @Synchronized fun inspect(context: Context, text: String) {
        if (mutable.value is YouTubeState.Downloading) return
        inspection?.cancel()
        val link = YouTubeLink.parse(text)
        if (link == null) {
            mutable.value = YouTubeState.Failed(DownloadError.INVALID_LINK)
            return
        }
        mutable.value = YouTubeState.Inspecting(link)
        inspection = scope.launch {
            try {
                val result = runInterruptible { YouTubeResolver.resolve(context.applicationContext, link, true) }
                currentCoroutineContext().ensureActive()
                finishInspection(YouTubeState.Ready(result), currentCoroutineContext()[Job])
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
                finishInspection(YouTubeState.Failed(DownloadErrors.classify(error)), currentCoroutineContext()[Job])
            }
        }
    }

    @Synchronized private fun finishInspection(state: YouTubeState, owner: Job?) {
        if (owner?.isActive == true && mutable.value !is YouTubeState.Downloading) mutable.value = state
    }
    @Synchronized fun prepareDownload(manifest: VideoManifest, choice: VideoChoice): DownloadSelection? {
        val ready = mutable.value as? YouTubeState.Ready ?: return null
        if (ready.manifest.link != manifest.link || choice !in ready.manifest.choices) return null
        inspection?.cancel()
        return DownloadSelection(UUID.randomUUID().toString(), ready.manifest, choice).also {
            pending = it
            mutable.value = YouTubeState.Downloading(0, Phase.VIDEO)
        }
    }
    @Synchronized fun takeDownload(id: String?): DownloadSelection? {
        val selected = pending?.takeIf { it.id == id } ?: return null
        pending = null
        return selected
    }
    @Synchronized fun update(state: YouTubeState) { mutable.value = state }

    @Synchronized fun resetIncoming() {
        if (mutable.value is YouTubeState.Downloading) return
        inspection?.cancel()
        mutable.value = YouTubeState.Idle
    }
}
