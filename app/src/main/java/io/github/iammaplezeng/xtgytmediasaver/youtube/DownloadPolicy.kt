package io.github.iammaplezeng.xtgytmediasaver.youtube

internal data class AccessFacts(val publicVideo: Boolean, val ageLimit: Int, val live: Boolean, val paid: Boolean, val drm: Boolean)
internal object DownloadPolicy {
    fun allowed(facts: AccessFacts, permissionConfirmed: Boolean) = permissionConfirmed && facts.publicVideo &&
        facts.ageLimit == 0 && !facts.live && !facts.paid && !facts.drm
}
internal enum class DownloadError { INVALID_LINK, PERMISSION, RESTRICTED, NO_FORMAT, NETWORK, STORAGE, INTERRUPTED, VERIFICATION, PARSER }
internal class DownloadFailure(val reason: DownloadError) : Exception(reason.name)
internal data class VideoChoice(val label: String, val height: Int, val videoUrl: String, val audioUrl: String?, val ytDlpFormat: String? = null)
internal data class VideoManifest(val link: String, val title: String, val choices: List<VideoChoice>)
