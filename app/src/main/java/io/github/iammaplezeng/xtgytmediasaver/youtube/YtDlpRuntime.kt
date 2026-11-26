package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import io.github.iammaplezeng.xtgytmediasaver.R
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** The third-party runtimes run only in the companion app, never in a hooked host process. */
internal object YtDlpRuntime {
    const val VERSION = "2026.11.23"
    private const val PAYLOAD_SHA256 = "e8665ed64d6b9172773e812f891fb707a0cb1ee8d9ef4fc1acb00cabee4c5256"
    private var initialized = false
    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        YoutubeDL.init(context.applicationContext)
        FFmpeg.init(context.applicationContext)
        val payload = payload(context)
        val installedHash = if (payload.isFile) hash(payload.readBytes()) else ""
        if (installedHash != PAYLOAD_SHA256) {
            val bundled = context.resources.openRawResource(R.raw.ytdlp).use { it.readBytes() }
            check(hash(bundled) == PAYLOAD_SHA256) { "Invalid bundled yt-dlp checksum" }
            val temporary = File(payload.parentFile, "yt-dlp.new")
            temporary.writeBytes(bundled)
            check(temporary.renameTo(payload)) { "Cannot install yt-dlp" }
        }
        for (name in listOf("libpython.so", "libqjs.so", "libffmpeg.so")) {
            check(File(context.applicationInfo.nativeLibraryDir, name).canExecute()) { "Missing native runtime $name" }
        }
        initialized = true
    }
    private fun payload(context: Context) = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun run(context: Context, options: List<String>, timeoutSeconds: Long, metadata: Boolean,
            onProgress: (Int) -> Unit = {}, onLine: (String) -> Unit = {}): String {
        initialize(context)
        val native = File(context.applicationInfo.nativeLibraryDir)
        val packages = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/packages")
        val python = File(packages, "python/usr")
        val command = listOf(File(native, "libpython.so").path, payload(context).path,
            "--ignore-config", "--no-playlist", "--no-colors", "--socket-timeout", "20", "--retries", "2",
            "--js-runtimes", "quickjs:${File(native, "libqjs.so").path}",
            "--ffmpeg-location", File(native, "libffmpeg.so").path, "--cache-dir", File(context.cacheDir, "yt-dlp-cache").path)
        val cookies = YouTubeCookies.snapshot(context)
        val cookieOptions = if (cookies == null) emptyList() else listOf("--cookies", cookies.path)
        val builder = ProcessBuilder(command + cookieOptions + options).redirectErrorStream(true)
        builder.environment().apply {
            put("PYTHONHOME", python.path)
            put("LD_LIBRARY_PATH", "${File(python, "lib").path}:${File(packages, "ffmpeg/usr/lib").path}")
            put("SSL_CERT_FILE", File(python, "etc/tls/cert.pem").path)
            put("PATH", "${System.getenv("PATH").orEmpty()}:${native.path}")
            put("TMPDIR", context.cacheDir.path)
            put("XDG_CACHE_HOME", File(context.cacheDir, "yt-dlp-cache").path)
        }
        return try { execute(builder, timeoutSeconds, metadata, onProgress, onLine) }
        finally { cookies?.delete() }
    }
    fun probe(context: Context): String {
        val version = run(context, listOf("--version"), 30, true).trim()
        check(version == VERSION) { "Unexpected yt-dlp version" }
        val native = File(context.applicationInfo.nativeLibraryDir)
        val packages = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/packages")
        fun nativeCommand(file: String, vararg arguments: String): ProcessBuilder =
            ProcessBuilder(listOf(File(native, file).path) + arguments)
                .redirectErrorStream(true).apply {
                    environment()["LD_LIBRARY_PATH"] = "${File(packages, "python/usr/lib").path}:${File(packages, "ffmpeg/usr/lib").path}"
                }
        check(execute(nativeCommand("libqjs.so", "-e", "console.log('XTG_QJS_OK')"), 15, true).trim() == "XTG_QJS_OK")
        val ffmpeg = execute(nativeCommand("libffmpeg.so", "-version"), 15, true)
        check(ffmpeg.startsWith("ffmpeg version"))
        android.util.Log.i("XTGMediaSaver", "[YTDLP] runtime ready: yt-dlp=$version; ${ffmpeg.lineSequence().first().take(100)}; QuickJS=ready")
        return version
    }
    private fun execute(builder: ProcessBuilder, timeoutSeconds: Long, metadata: Boolean,
                        onProgress: (Int) -> Unit = {}, onLine: (String) -> Unit = {}): String {
        val arguments = builder.command().toList()
        // Fixed shell bootstrap passes arguments verbatim; no URL or option is evaluated as shell code.
        val bootstrap = "printf 'XTG_PROCESS_GROUP:%s\\n' \"\$\$\"\nexec \"\$@\""
        builder.command(listOf("/system/bin/toybox", "setsid", "-w", "/system/bin/sh", "-c", bootstrap, "xtg-yt-dlp") + arguments)
        val process = builder.start()
        process.outputStream.close()
        val group = AtomicInteger()
        val full = StringBuilder()
        val tail = StringBuilder()
        val failure = AtomicReference<Throwable?>()
        val reader = thread(name = "xtg-yt-dlp-output", isDaemon = true) {
            try {
                process.inputStream.bufferedReader().useLines { lines -> lines.forEach { line ->
                    if (line.startsWith("XTG_PROCESS_GROUP:")) {
                        group.set(line.substringAfter(':').toIntOrNull() ?: 0)
                        return@forEach
                    }
                    if (metadata) {
                        if (full.length + line.length > 12 * 1024 * 1024) throw IOException("yt-dlp metadata exceeds limit")
                        full.append(line).append('\n')
                    }
                    tail.append(line).append('\n')
                    if (tail.length > 8_192) tail.delete(0, tail.length - 8_192)
                    if (line.startsWith("XTG_PROGRESS:")) {
                        Regex("([0-9]+(?:\\.[0-9]+)?)%").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                            ?.let { onProgress(it.toInt().coerceIn(0, 100)) }
                    }
                    onLine(line)
                } }
            } catch (error: Throwable) { failure.set(error); terminate(process, group.get()) }
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                terminate(process, group.get())
                throw IOException("yt-dlp timed out")
            }
            reader.join(5_000)
            failure.get()?.let { throw IOException("yt-dlp output failed", it) }
            if (process.exitValue() != 0) {
                val output = tail.toString().lowercase()
                val reason = when {
                    "drm" in output || "private video" in output || "members-only" in output || "requires premium" in output || "age-restricted" in output || "confirm your age" in output || "does not pass filter" in output -> DownloadError.RESTRICTED
                    "not a bot" in output || "confirm you're" in output || "cookies" in output || "sign in" in output -> DownloadError.VERIFICATION
                    "no video formats" in output || "requested format is not available" in output -> DownloadError.NO_FORMAT
                    else -> DownloadError.NETWORK
                }
                android.util.Log.w("XTGMediaSaver", "[YTDLP] exit=${process.exitValue()} reason=$reason")
                throw DownloadFailure(reason)
            }
            return if (metadata) full.toString() else tail.toString()
        } catch (interrupted: InterruptedException) {
            terminate(process, group.get())
            throw interrupted
        } finally {
            if (process.isAlive) terminate(process, group.get())
        }
    }
    private fun terminate(process: Process, group: Int) {
        if (!process.isAlive) return
        if (group > 1) runCatching { Os.kill(-group, OsConstants.SIGKILL) }
        process.destroyForcibly()
    }
}
