package io.github.iammaplezeng.maplemediasaver

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test

class ImageBatchDownloaderTest {
    private val raw = "https://pbs.twimg.com/media/photo?name=small"
    private val original = "https://pbs.twimg.com/media/photo?name=orig"

    @Test fun retriesAnEmptyOriginalAndCopiesTheEntireFallback() {
        val payload = ByteArray(256 * 1024 + 3) { it.toByte() }
        val requests = mutableListOf<String>()
        val output = ByteArrayOutputStream()
        val saved = ImageBatchDownloader.downloadBestQuality(raw) { url ->
            requests.add(url)
            val body = if (url == original) byteArrayOf() else payload
            ImageBatchDownloader.copyImage(body.inputStream(), output)
            true
        }
        assertTrue(saved)
        assertArrayEquals(payload, output.toByteArray())
        assertEquals(listOf(original, raw), requests)
    }

    @Test fun storageFailureDoesNotRetryTheNetwork() {
        val failure = IOException("storage full")
        val output = object : OutputStream() {
            override fun write(value: Int) { throw failure }
        }
        val requests = mutableListOf<String>()
        val thrown = assertThrows(IOException::class.java) {
            ImageBatchDownloader.downloadBestQuality(raw) { url ->
                requests.add(url)
                ImageBatchDownloader.copyImage(byteArrayOf(1).inputStream(), output)
                true
            }
        }
        assertSame(failure, thrown)
        assertEquals(listOf(original), requests)
    }
}
