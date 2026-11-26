package io.github.iammaplezeng.maplemediasaver.youtube

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File

internal object YouTubeCookies {
    private fun file(context: Context) = File(context.noBackupFilesDir, "youtube-session.txt")
    @Synchronized fun present(context: Context) = file(context).isFile
    @Synchronized fun clear(context: Context) { AtomicFile(file(context)).delete() }
    @Synchronized fun import(context: Context, uri: Uri) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(YouTubeCookieFile.MAX_BYTES + 1)
            var count = 0
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
            buffer.copyOf(count)
        }
            ?: error("Cannot read selected file")
        require(bytes.size <= YouTubeCookieFile.MAX_BYTES)
        saveSanitized(context, bytes.toString(Charsets.UTF_8))
    }

    @Synchronized fun saveSanitized(context: Context, rawNetscapeText: String) {
        val text = YouTubeCookieFile.sanitize(rawNetscapeText)
        val target = AtomicFile(file(context))
        val stream = target.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
        catch (error: Exception) { target.failWrite(stream); throw error }
    }

    @Synchronized fun snapshot(context: Context): File? {
        val source = file(context)
        if (!source.isFile) return null
        return File.createTempFile("yt-session-", ".txt", context.cacheDir).also {
            try { source.copyTo(it, overwrite = true) } catch (error: Exception) { it.delete(); throw error }
        }
    }
}
