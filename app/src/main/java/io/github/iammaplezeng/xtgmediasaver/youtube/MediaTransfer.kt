package io.github.iammaplezeng.xtgmediasaver.youtube

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

internal object MediaTransfer {
    suspend fun fetch(url: String, destination: File, progress: (Long, Long) -> Unit) {
        if (!YouTubeNetwork.mediaAllowed(url)) throw DownloadFailure(DownloadError.RESTRICTED)
        val connection = YouTubeNetwork.open(url, media = true, headers = mapOf("Range" to listOf("bytes=0-")))
        try {
            if (connection.responseCode !in setOf(200, 206)) throw IOException("Media HTTP ${connection.responseCode}")
            val total = if (connection.responseCode == 206) {
                val range = Regex("bytes 0-(\\d+)/(\\d+)").matchEntire(connection.getHeaderField("Content-Range").orEmpty())
                    ?: throw IOException("Invalid content range")
                val end = range.groupValues[1].toLong(); val size = range.groupValues[2].toLong()
                if (end + 1 != size) throw IOException("Incomplete media response")
                size
            } else connection.contentLengthLong
            if (total > 0 && destination.parentFile?.usableSpace?.let { it < total + 32L * 1024 * 1024 } == true)
                throw DownloadFailure(DownloadError.STORAGE)
            var received = 0L
            connection.inputStream.use { input -> destination.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = input.read(buffer)
                    if (size < 0) break
                    output.write(buffer, 0, size); received += size
                    progress(received, total)
                }
            } }
            if (received == 0L || (total > 0 && total != received)) throw IOException("Truncated media")
        } finally { connection.disconnect() }
    }
}
