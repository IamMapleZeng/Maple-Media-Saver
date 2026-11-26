package io.github.iammaplezeng.xtgmediasaver.youtube

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal object YouTubeNetwork {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
    const val VR_USER_AGENT = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    const val ANDROID_USER_AGENT = "com.google.android.youtube/21.47.483 (Linux; U; Android 14) gzip"
    const val IOS_USER_AGENT = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    const val IOS_PRODUCER_USER_AGENT = "com.google.ios.youtubeproducer/0.147.945 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    const val VISIONOS_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    fun mediaAllowed(url: String): Boolean = valid(url, setOf("googlevideo.com"))

    fun userAgentForMediaUrl(url: String): String {
        val query = runCatching { URI(url).rawQuery.orEmpty() }.getOrDefault("")
        val client = query.split('&')
            .firstOrNull { it.startsWith("c=") }
            ?.substringAfter("c=")
            ?.uppercase()
            .orEmpty()
        return when (client) {
            "ANDROID_VR" -> VR_USER_AGENT
            "ANDROID" -> ANDROID_USER_AGENT
            "IOS" -> IOS_USER_AGENT
            "IOS_PRODUCER" -> IOS_PRODUCER_USER_AGENT
            "VISIONOS" -> VISIONOS_USER_AGENT
            else -> USER_AGENT
        }
    }

    private fun valid(url: String, domains: Set<String>): Boolean = try {
        val uri = URI(url)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
            domains.any { host == it || host.endsWith(".$it") }
    } catch (_: Exception) { false }

    fun open(
        url: String,
        media: Boolean = false,
        method: String = "GET",
        headers: Map<String, List<String>> = emptyMap(),
        body: ByteArray? = null
    ): HttpURLConnection {
        val domains = if (media) setOf("googlevideo.com") else setOf(
            "youtube.com", "youtube-nocookie.com", "youtu.be", "googlevideo.com",
            "googleapis.com", "ytimg.com", "ggpht.com", "google.com"
        )
        val defaultUa = if (media) userAgentForMediaUrl(url) else USER_AGENT
        val hasExplicitUa = headers.keys.any { it.equals("User-Agent", ignoreCase = true) }
        var current = url
        repeat(6) {
            if (!valid(current, domains)) throw IOException("Untrusted endpoint")
            val connection = URL(current).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false
                connection.requestMethod = method
                if (!hasExplicitUa) {
                    connection.setRequestProperty("User-Agent", defaultUa)
                }
                headers.forEach { (key, values) ->
                    if (!key.equals("Authorization", true)) {
                        values.forEach { connection.addRequestProperty(key, it) }
                    }
                }
                if (body != null) {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body) }
                }
                if (connection.responseCode in setOf(301, 302, 303, 307, 308)) {
                    if (method != "GET" && method != "HEAD") throw IOException("Unexpected request redirect")
                    val next = connection.getHeaderField("Location") ?: throw IOException("Missing redirect target")
                    current = URI(current).resolve(next).toString()
                    connection.disconnect()
                } else return connection
            } catch (error: Throwable) {
                connection.disconnect()
                throw error
            }
        }
        throw IOException("Too many redirects")
    }
}

internal class ExtractorDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = YouTubeNetwork.open(
            request.url(),
            method = request.httpMethod(),
            headers = request.headers(),
            body = request.dataToSend()
        )
        try {
            val code = connection.responseCode
            val input = if (code in 200..399) connection.inputStream else connection.errorStream
            // Metadata only: cap response bodies to avoid unbounded memory use.
            val bytes = input?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16_384)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 12 * 1024 * 1024) throw IOException("Metadata too large")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: byteArrayOf()
            val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key.orEmpty() }
            return Response(code, connection.responseMessage.orEmpty(), headers, bytes.toString(Charsets.UTF_8), connection.url.toString())
        } finally {
            connection.disconnect()
        }
    }
}
