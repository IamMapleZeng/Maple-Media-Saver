package io.github.iammaplezeng.xtgmediasaver

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.res.Configuration
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import android.provider.MediaStore
import io.github.iammaplezeng.xtgmediasaver.youtube.GalleryPublisher
import io.github.iammaplezeng.xtgmediasaver.youtube.Mp4Merger
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import java.util.UUID

/** Device smoke tests use synthetic media only; never download an unlicensed network video. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val results = ArrayList<String>()
        val directory = File(targetContext.cacheDir, "smoke-${UUID.randomUUID()}")
        try {
            for ((tag, name) in listOf("en-US" to "X TG YT Media Saver", "zh-CN" to "X、TG、YT媒体下载器",
                "zh-TW" to "X、TG、YT媒體下載器", "zh-Hant-HK" to "X、TG、YT媒體下載器", "fr-FR" to "X TG YT Media Saver")) {
                val configuration = Configuration(targetContext.resources.configuration)
                configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
                check(targetContext.createConfigurationContext(configuration).getString(R.string.app_name) == name) { "Locale $tag" }
                results.add("PASS locale=$tag")
            }
            check(targetContext.resources.getStringArray(R.array.xposed_scope).toList() == listOf(HostVersions.X_PACKAGE, HostVersions.TG_PACKAGE, HostVersions.YT_PACKAGE))
            results.add("PASS original scopes preserved and YouTube added")
            val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://youtu.be/abcdefgh_12")
            check(targetContext.packageManager.queryIntentActivities(share, 0).any { it.activityInfo.name.endsWith("YouTubeShareActivity") })
            results.add("PASS ACTION_SEND receiver resolvable")
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            runOnMainSync { check(activity.findViewById<android.view.View>(R.id.switch_yt_enabled) != null) }
            results.add("PASS main UI launches with third platform")
            runOnMainSync { activity.finish() }
            check(directory.mkdirs())
            val video = File(directory, "video.mp4"); val audio = File(directory, "audio.m4a")
            encode(video, false); encode(audio, true)
            val merged = File(directory, "merged.mp4")
            runBlocking {
                Mp4Merger.merge(listOf(video, audio), merged, true)
                val extractor = MediaExtractor()
                try { extractor.setDataSource(merged.absolutePath); check(extractor.trackCount == 2) }
                finally { extractor.release() }
                results.add("PASS synthetic AVC/AAC merge has two tracks")
                val uri = GalleryPublisher.publish(targetContext, merged, "XTG-smoke-${System.currentTimeMillis()}.mp4")
                try {
                    targetContext.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.IS_PENDING), null, null, null).use { cursor ->
                        check(cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 0)
                    }
                    targetContext.contentResolver.openFileDescriptor(uri, "r").use { fd ->
                        check(fd != null)
                        val reader = MediaExtractor()
                        try { reader.setDataSource(fd.fileDescriptor); check(reader.trackCount == 2) }
                        finally { reader.release() }
                    }
                    results.add("PASS MediaStore publish, visibility and readable tracks")
                } finally { targetContext.contentResolver.delete(uri, null, null) }
            }
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", results.joinToString("\n") + "\n10 device checks passed\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", results.joinToString("\n") + "\nFAIL " + error.stackTraceToString()) })
        } finally { if (directory.canonicalFile.parentFile == targetContext.cacheDir.canonicalFile) directory.deleteRecursively() }
    }
    private fun encode(file: File, audio: Boolean) {
        val mime = if (audio) "audio/mp4a-latm" else "video/avc"
        val format = if (audio) MediaFormat.createAudioFormat(mime, 44100, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
        } else MediaFormat.createVideoFormat(mime, 64, 64).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000); setInteger(MediaFormat.KEY_FRAME_RATE, 10); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            var inputDone = false; var frames = 0; var outputTrack = -1
            val info = MediaCodec.BufferInfo(); val deadline = SystemClock.elapsedRealtime() + 20_000
            while (true) {
                check(SystemClock.elapsedRealtime() < deadline) { "Synthetic encoder timeout" }
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val pts = if (audio) frames * 1024L * 1_000_000 / 44100 else frames * 100_000L
                        if (frames >= if (audio) 20 else 10) {
                            codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true
                        } else {
                            val bytes = ByteArray(if (audio) 2048 else 64 * 64 * 3 / 2) { if (audio) 0 else 96 }
                            val input = codec.getInputBuffer(index) ?: error("Missing encoder input")
                            input.clear(); input.put(bytes); codec.queueInputBuffer(index, 0, bytes.size, pts, 0); frames++
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputTrack = muxer.addTrack(codec.outputFormat); muxer.start(); started = true
                } else if (index >= 0) {
                    val buffer = codec.getOutputBuffer(index) ?: error("Missing encoder output")
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(started); muxer.writeSampleData(outputTrack, buffer, info)
                    }
                    val done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                    if (done) break
                }
            }
        } finally {
            codec.stop(); codec.release()
            if (started) muxer.stop()
            muxer.release()
        }
    }
}
