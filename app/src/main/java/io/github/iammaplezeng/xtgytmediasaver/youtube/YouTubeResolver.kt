package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.content.Context

internal object YouTubeResolver {
    fun resolve(context: Context, link: String, permissionConfirmed: Boolean = true): VideoManifest {
        if (!permissionConfirmed) throw DownloadFailure(DownloadError.PERMISSION)
        val canonical = YouTubeLink.parse(link) ?: throw DownloadFailure(DownloadError.INVALID_LINK)
        return YtDlpEngine.resolve(context, canonical)
    }
}
