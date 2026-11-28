package io.github.iammaplezeng.maplemediasaver

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
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
                    val published = downloadBestQuality(raw) { url ->
                        download(app, url, stamp, i + 1)
                    }
                    if (published) saved++
                } catch (_: Exception) { /* Continue the rest of this batch. */ }
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
    // Only a failed network read should trigger another download at the fallback URL.
    private class ImageReadFailure(cause: IOException) : IOException(cause)
    private inline fun <T> readFromNetwork(block: () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw ImageReadFailure(error)
    }

    internal fun downloadBestQuality(raw: String, download: (String) -> Boolean): Boolean {
        val original = toOriginalUrl(raw) ?: raw
        return try {
            download(original)
        } catch (error: ImageReadFailure) {
            if (original == raw) throw error
            download(raw)
        }
    }

    private fun download(context: Context, url: String, stamp: Long, index: Int): Boolean {
        val connection = readFromNetwork { URL(url).openConnection() as HttpURLConnection }
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
        try {
            val input = readFromNetwork {
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("HTTP $status")
                connection.inputStream
            }
            return input.use {
                saveToGallery(context, it, resolveMimeType(connection.contentType, url), stamp, index)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun saveToGallery(context: Context, input: InputStream, mime: String, stamp: Long, index: Int): Boolean {
        val ext = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "X_${stamp}_$index.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/XTGMediaSaver")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot open media output")
            stream.use { copyImage(input, it) }
            val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            if (resolver.update(uri, published, null, null) != 1) throw IOException("Cannot publish image")
            return true
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
    internal fun copyImage(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var count = readFromNetwork { input.read(buffer) }
        if (count < 0) throw ImageReadFailure(IOException("Empty image body"))
        while (count >= 0) {
            output.write(buffer, 0, count)
            count = readFromNetwork { input.read(buffer) }
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
}
