package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File

/** Never reads another app's account store. All input comes from the user's file picker. */
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
        val text = YouTubeCookieFile.sanitize(bytes.toString(Charsets.UTF_8))
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
