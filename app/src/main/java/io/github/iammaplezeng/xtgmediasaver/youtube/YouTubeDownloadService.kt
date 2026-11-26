package io.github.iammaplezeng.xtgmediasaver.youtube

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import io.github.iammaplezeng.xtgmediasaver.ModulePrefs
import io.github.iammaplezeng.xtgmediasaver.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class YouTubeDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var job: Job? = null
    private var lastProgress = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.yt_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            if (job?.isActive == true) job?.cancel() else stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val manifest = (YouTubeSession.state.value as? YouTubeState.Ready)?.manifest
        val choice = manifest?.choices?.getOrNull(intent?.getIntExtra(EXTRA_CHOICE, -1) ?: -1)
        if (choice == null || !ModulePrefs.openForUi(this).getBoolean(ModulePrefs.KEY_YT_ENABLED, true)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(getString(R.string.yt_downloading), 0))
        YouTubeSession.update(YouTubeState.Downloading(0, Phase.VIDEO))
        val prefs = getSharedPreferences(HISTORY, MODE_PRIVATE)
        prefs.edit().putString("last_link", manifest.link).putString("last_state", "running").apply()
        job = scope.launch {
            val directory = File(cacheDir, "youtube-${UUID.randomUUID()}")
            var phase = Phase.VIDEO
            try {
                if (!directory.mkdirs()) throw DownloadFailure(DownloadError.STORAGE)
                val video = File(directory, "video.mp4")
                val audio = File(directory, "audio.m4a")
                MediaTransfer.fetch(choice.videoUrl, video) { bytes, total ->
                    progress(if (total > 0) ((bytes.toDouble() / total) * if (choice.audioUrl == null) 90 else 75).toInt() else -1, Phase.VIDEO)
                }
                val inputs = arrayListOf(video)
                choice.audioUrl?.let { url ->
                    phase = Phase.AUDIO
                    MediaTransfer.fetch(url, audio) { bytes, total ->
                        progress(if (total > 0) 75 + (bytes.toDouble() / total * 15).toInt() else -1, Phase.AUDIO)
                    }
                    inputs.add(audio)
                }
                phase = Phase.MERGING
                progress(92, phase, true)
                val output = if (choice.audioUrl == null) {
                    // Progressive MP4 already contains muxed AVC + AAC; validate container & encryption without stripping B-frame ctts.
                    Mp4Merger.validateProgressive(video)
                    video
                } else {
                    File(directory, "merged.mp4").also { merged ->
                        Mp4Merger.merge(inputs, merged, true)
                    }
                }
                phase = Phase.SAVING
                progress(98, phase, true)
                val title = manifest.title.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "_").take(80).ifBlank { "YouTube" }
                val uri = GalleryPublisher.publish(this@YouTubeDownloadService, output, "${title}_${System.currentTimeMillis()}.mp4")
                prefs.edit().putString("last_state", "complete").putString("last_uri", uri.toString()).apply()
                YouTubeSession.update(YouTubeState.Complete(uri.toString()))
                val msg = getString(R.string.yt_complete)
                showResult(msg)
                showToast(msg)
            } catch (cancelled: CancellationException) {
                prefs.edit().putString("last_state", "interrupted").apply()
                YouTubeSession.update(YouTubeState.Failed(DownloadError.INTERRUPTED))
            } catch (error: Exception) {
                val reason = (error as? DownloadFailure)?.reason ?: if (phase == Phase.SAVING) DownloadError.STORAGE
                else if (phase == Phase.MERGING) DownloadError.NO_FORMAT else DownloadError.NETWORK
                val sanitized = error.message.orEmpty().replace(Regex("https?://[^\\s]+"), "[url]").take(240)
                Log.w("XTGMediaSaver", "[YT] download failed: ${reason.name}/${error.javaClass.simpleName}: $sanitized")
                prefs.edit().putString("last_state", "failed").putString("last_error", reason.name).apply()
                YouTubeSession.update(YouTubeState.Failed(reason))
                val msg = getString(
                    if (reason == DownloadError.STORAGE) R.string.yt_storage_error
                    else if (reason == DownloadError.RESTRICTED) R.string.yt_restricted
                    else R.string.yt_network_error
                )
                showResult(msg)
                showToast(msg)
            } finally {
                // Unique app-private directory; never delete shared media or existing downloads.
                if (directory.canonicalFile.parentFile == cacheDir.canonicalFile) directory.deleteRecursively()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showToast(text: String) {
        mainHandler.post { Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show() }
    }

    private fun showResult(text: String) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, YouTubeShareActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService(NotificationManager::class.java).notify(
            1201,
            Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_status_check)
                .setContentTitle(getString(R.string.yt_download_video))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        )
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        job?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun progress(percent: Int, phase: Phase, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastProgress < 500) return
        lastProgress = now
        YouTubeSession.update(YouTubeState.Downloading(percent, phase))
        val text = getString(when (phase) {
            Phase.VIDEO -> R.string.yt_phase_video
            Phase.AUDIO -> R.string.yt_phase_audio
            Phase.MERGING -> R.string.yt_phase_merging
            Phase.SAVING -> R.string.yt_phase_saving
        })
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text, percent))
    }

    private fun notification(text: String, percent: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, YouTubeShareActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, YouTubeDownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_status_check)
            .setContentTitle(getString(R.string.yt_download_video))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent < 0)
            .addAction(Notification.Action.Builder(null, getString(R.string.yt_cancel), cancel).build())
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CHOICE = "choice"
        const val ACTION_CANCEL = "io.github.iammaplezeng.xtgmediasaver.youtube.CANCEL"
        const val HISTORY = "yt_download_history"
        private const val CHANNEL = "youtube_downloads"
        private const val NOTIFICATION_ID = 1200
    }
}
