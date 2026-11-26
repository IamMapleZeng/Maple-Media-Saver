package io.github.iammaplezeng.maplemediasaver.youtube

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer

/** Copies AVC/AAC samples without re-encoding; protected samples are always rejected. */
internal object Mp4Merger {
    private data class Track(val file: File, val source: Int, val target: Int)

    suspend fun validateEngineOutput(file: File) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            if (!extractor.psshInfo.isNullOrEmpty()) throw DownloadFailure(DownloadError.RESTRICTED)
            var videoTracks = 0
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    if (mime !in setOf("video/avc", "video/hevc", "video/av01", "video/x-vnd.on2.vp9")) throw DownloadFailure(DownloadError.NO_FORMAT)
                    videoTracks++
                }
                if (mime.startsWith("video/") || mime.startsWith("audio/")) extractor.selectTrack(index)
            }
            if (videoTracks != 1 || extractor.sampleTime < 0) throw DownloadFailure(DownloadError.NO_FORMAT)
            while (extractor.sampleTime >= 0) {
                currentCoroutineContext().ensureActive()
                if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) throw DownloadFailure(DownloadError.RESTRICTED)
                extractor.advance()
            }
        } finally { extractor.release() }
    }

    suspend fun merge(inputs: List<File>, output: File, audioRequired: Boolean) {
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val tracks = ArrayList<Track>()
        var videoCount = 0; var audioCount = 0; var started = false
        try {
            for (file in inputs) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(file.absolutePath)
                    if (!extractor.psshInfo.isNullOrEmpty()) throw DownloadFailure(DownloadError.RESTRICTED)
                    for (index in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(index)
                        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (mime.startsWith("video/")) {
                            if (mime != "video/avc") throw DownloadFailure(DownloadError.NO_FORMAT)
                            videoCount++
                            if (format.containsKey(MediaFormat.KEY_ROTATION)) muxer.setOrientationHint(format.getInteger(MediaFormat.KEY_ROTATION))
                        } else if (mime.startsWith("audio/")) {
                            if (mime != "audio/mp4a-latm") throw DownloadFailure(DownloadError.NO_FORMAT)
                            audioCount++
                        } else continue
                        tracks.add(Track(file, index, muxer.addTrack(format)))
                    }
                } finally { extractor.release() }
            }
            if (videoCount != 1 || audioCount > 1 || (audioRequired && audioCount != 1)) throw DownloadFailure(DownloadError.NO_FORMAT)
            muxer.start(); started = true
            for (track in tracks) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(track.file.absolutePath); extractor.selectTrack(track.source)
                    var buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024)
                    val info = MediaCodec.BufferInfo()
                    var lastTime = -1L
                    var samples = 0
                    while (extractor.sampleTime >= 0) {
                        currentCoroutineContext().ensureActive()
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) throw DownloadFailure(DownloadError.RESTRICTED)
                        val size = extractor.sampleSize
                        if (size > 32 * 1024 * 1024) throw DownloadFailure(DownloadError.NO_FORMAT)
                        if (size > buffer.capacity()) buffer = ByteBuffer.allocateDirect(size.toInt())
                        buffer.clear()
                        val read = extractor.readSampleData(buffer, 0)
                        if (read < 0) break
                        val time = extractor.sampleTime
                        if (time < 0L) break
                        var flags = 0
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                        }
                        if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
                            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
                        }
                        info.set(0, read, time, flags)
                        muxer.writeSampleData(track.target, buffer, info)
                        lastTime = time; samples++; extractor.advance()
                    }
                    if (samples == 0) throw DownloadFailure(DownloadError.NO_FORMAT)
                } finally { extractor.release() }
            }
            muxer.stop(); started = false
        } finally {
            if (started) try { muxer.stop() } catch (_: Exception) { }
            muxer.release()
        }
    }
}
