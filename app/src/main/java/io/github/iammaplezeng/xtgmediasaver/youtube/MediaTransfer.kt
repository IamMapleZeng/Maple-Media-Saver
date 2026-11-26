package io.github.iammaplezeng.xtgmediasaver.youtube

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

internal object MediaTransfer {
    private const val CHUNK_SIZE = 8L * 1024L * 1024L
    private val contentRangeRegex = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)")

    suspend fun fetch(url: String, destination: File, progress: (Long, Long) -> Unit) {
        if (!YouTubeNetwork.mediaAllowed(url)) throw DownloadFailure(DownloadError.RESTRICTED)
        var received = 0L
        var total = -1L
        var spaceChecked = false

        destination.outputStream().use { output ->
            while (total < 0 || received < total) {
                currentCoroutineContext().ensureActive()
                val endTarget = if (total > 0) minOf(received + CHUNK_SIZE - 1, total - 1) else received + CHUNK_SIZE - 1
                var attempt = 0
                var chunkRead = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val connection = YouTubeNetwork.open(
                        url,
                        media = true,
                        headers = mapOf("Range" to listOf("bytes=$received-$endTarget"))
                    )
                    try {
                        val code = connection.responseCode
                        if (code !in setOf(200, 206)) {
                            throw IOException("Media HTTP $code")
                        }
                        if (code == 206) {
                            val header = connection.getHeaderField("Content-Range").orEmpty()
                            val match = contentRangeRegex.matchEntire(header)
                                ?: throw IOException("Invalid content range: $header")
                            val rangeStart = match.groupValues[1].toLong()
                            val rangeEnd = match.groupValues[2].toLong()
                            val sizeToken = match.groupValues[3]
                            if (rangeStart != received || rangeEnd < rangeStart) {
                                throw IOException("Mismatched content range: $header")
                            }
                            if (total < 0 && sizeToken != "*") {
                                total = sizeToken.toLong()
                            }
                        } else if (received == 0L) {
                            total = connection.contentLengthLong
                        } else {
                            // Server ignored Range on a subsequent chunk; abort rather than corrupting file.
                            throw IOException("Server did not honor byte range at offset $received")
                        }

                        if (!spaceChecked && total > 0) {
                            if (destination.parentFile?.usableSpace?.let { it < total + 32L * 1024 * 1024 } == true) {
                                throw DownloadFailure(DownloadError.STORAGE)
                            }
                            spaceChecked = true
                        }

                        val startBeforeChunk = received
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                progress(received, total)
                            }
                        }
                        chunkRead = received - startBeforeChunk
                        if (code == 200) {
                            if (total <= 0) total = received
                        }
                        break
                    } catch (error: Throwable) {
                        if (error is DownloadFailure) throw error
                        currentCoroutineContext().ensureActive()
                        if (attempt >= 2) throw error
                        attempt++
                        delay(400L * attempt)
                    } finally {
                        connection.disconnect()
                    }
                }
                if (chunkRead <= 0L) {
                    throw IOException("Stalled media stream at $received/$total")
                }
            }
        }
        if (received == 0L || (total > 0 && received != total)) {
            throw IOException("Truncated media ($received/$total)")
        }
    }
}
