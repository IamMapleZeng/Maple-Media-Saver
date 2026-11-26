package io.github.iammaplezeng.xtgytmediasaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/** Existing X batch path and file naming remain unchanged after migration. */
internal object ImageBatchDownloader {
    private val executor by lazy { Executors.newSingleThreadExecutor() }
    @JvmStatic fun downloadAll(context: Context?, imageUrls: List<String>?) {
        if (context == null || imageUrls.isNullOrEmpty()) return
        val app = context.applicationContext ?: context
        val urls = imageUrls.toList()
        val locale = Locale.getDefault()
        showToast(app, LocaleStrings.downloadingImagesToast(urls.size, locale), Toast.LENGTH_SHORT)
        executor.execute {
            var saved = 0
            val stamp = System.currentTimeMillis()
            urls.forEachIndexed { i, raw ->
                if (raw.isNotEmpty()) try {
                    if (saveToGallery(app, fetchBestQuality(raw), stamp, i + 1)) saved++
                } catch (_: Throwable) { /* Continue the rest of this batch. */ }
            }
            showToast(app, LocaleStrings.downloadedImagesToast(saved, urls.size, locale), Toast.LENGTH_LONG)
        }
    }
    @JvmStatic fun showToast(context: Context?, message: String?, duration: Int) {
        if (context == null || message == null) return
        val app = context.applicationContext ?: context
        Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, duration).show() }
    }
    @JvmStatic fun toOriginalUrl(rawUrl: String?): String? {
        if (rawUrl == null || !rawUrl.contains("pbs.twimg.com/media/")) return rawUrl
        val index = rawUrl.indexOf('?')
        if (index < 0) return "$rawUrl?name=orig"
        val parts = rawUrl.substring(index + 1).split('&').filter { it.isNotEmpty() }
        val query = parts.map { if (it.startsWith("name=")) "name=orig" else it }.toMutableList()
        if (parts.none { it.startsWith("name=") }) query.add("name=orig")
        return rawUrl.substring(0, index) + "?" + query.joinToString("&")
    }
    private fun fetchBestQuality(raw: String): DownloadedImage {
        val original = toOriginalUrl(raw) ?: raw
        return try { fetchUrl(original) } catch (error: IOException) {
            if (original != raw) fetchUrl(raw) else throw error
        }
    }
    private fun fetchUrl(url: String): DownloadedImage {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) throw IOException("Empty image body")
            val mime = resolveMimeType(connection.contentType, url)
            return DownloadedImage(bytes, mime, when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" })
        } finally { connection.disconnect() }
    }
    private fun saveToGallery(context: Context, image: DownloadedImage, stamp: Long, index: Int): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "X_${stamp}_$index.${image.ext}")
            put(MediaStore.Images.Media.MIME_TYPE, image.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XTGMediaSaver")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot open media output")
            stream.use { it.write(image.bytes); it.flush() }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return true
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
    private fun resolveMimeType(contentType: String?, url: String): String {
        val type = contentType?.lowercase(Locale.US).orEmpty()
        if ("png" in type) return "image/png"
        if ("webp" in type) return "image/webp"
        if ("jpeg" in type || "jpg" in type) return "image/jpeg"
        val lower = url.lowercase(Locale.US)
        if ("format=png" in lower || lower.endsWith(".png")) return "image/png"
        if ("format=webp" in lower || lower.endsWith(".webp")) return "image/webp"
        return "image/jpeg"
    }
    private data class DownloadedImage(val bytes: ByteArray, val mime: String, val ext: String)
}
