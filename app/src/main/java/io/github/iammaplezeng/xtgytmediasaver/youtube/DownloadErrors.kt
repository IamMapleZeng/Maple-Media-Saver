package io.github.iammaplezeng.xtgytmediasaver.youtube

internal object DownloadErrors {
    fun classify(error: Throwable): DownloadError {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        causes.filterIsInstance<DownloadFailure>().firstOrNull()?.let { return it.reason }
        return DownloadError.NETWORK
    }
}
