package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.color.DynamicColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.iammaplezeng.xtgytmediasaver.ModulePrefs
import io.github.iammaplezeng.xtgytmediasaver.R
import kotlinx.coroutines.launch

open class YouTubeShareActivity : AppCompatActivity() {
    private lateinit var link: TextInputEditText
    private lateinit var permission: MaterialCheckBox
    private lateinit var status: TextView
    private lateinit var titleText: TextView
    private lateinit var quality: Spinner
    private lateinit var progress: LinearProgressIndicator
    private lateinit var analyze: MaterialButton
    private lateinit var download: MaterialButton
    private lateinit var cancel: MaterialButton
    private lateinit var open: MaterialButton
    private lateinit var inputLayout: TextInputLayout
    private var nativeShare = false
    private var displayed: VideoManifest? = null
    private var pendingChoice: Pair<VideoManifest, VideoChoice>? = null
    private var nativeManifest: VideoManifest? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingChoice?.let { (manifest, choice) -> pendingChoice = null; launchService(manifest, choice) }
    }

    override fun onCreate(saved: Bundle?) {
        nativeShare = intent.getBooleanExtra(YouTubeMenuHook.EXTRA_NATIVE_SHARE, false)
        if (nativeShare) setTheme(R.style.Theme_XTGMediaSaver_Quick)
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(saved)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = ((if (nativeShare) 18 else 24) * resources.displayMetrics.density).toInt()
        content.setPadding(pad, pad, pad, pad)
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        if (nativeShare) {
            window.setLayout((resources.displayMetrics.widthPixels * .92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            window.setGravity(Gravity.CENTER)
        } else ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, pad + bars.bottom)
            insets
        }
        content.addView(TextView(this).apply { setText(R.string.yt_download_video); textSize = 26f })
        content.addView(TextView(this).apply { setText(R.string.yt_share_help); setPadding(0, pad / 2, 0, pad) })
        val input = TextInputLayout(this).apply { hint = getString(R.string.yt_link) }
        inputLayout = input
        link = TextInputEditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI }
        input.addView(link)
        content.addView(input)
        permission = MaterialCheckBox(this).apply { setText(R.string.yt_permission) }
        content.addView(permission)
        analyze = MaterialButton(this).apply { setText(R.string.yt_analyze); setOnClickListener { inspect() } }
        content.addView(analyze)
        titleText = TextView(this).apply { textSize = 18f }
        content.addView(titleText)
        quality = Spinner(this)
        content.addView(quality)
        progress = LinearProgressIndicator(this).apply { max = 100 }
        content.addView(progress)
        status = TextView(this).apply { setPadding(0, pad / 2, 0, pad / 2) }
        content.addView(status)
        download = MaterialButton(this).apply { setText(R.string.yt_download_video); setOnClickListener { startDownload() } }
        content.addView(download)
        cancel = MaterialButton(this).apply {
            setText(R.string.yt_cancel)
            setOnClickListener {
                startService(Intent(this@YouTubeShareActivity, YouTubeDownloadService::class.java).setAction(YouTubeDownloadService.ACTION_CANCEL))
            }
        }
        content.addView(cancel)
        open = MaterialButton(this).apply { setText(R.string.yt_open); setOnClickListener { openResult() } }
        content.addView(open)
        if (nativeShare) content.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            setText(android.R.string.cancel)
            setOnClickListener { finish() }
        })
        if (saved != null) {
            link.setText(saved.getString("link"))
            permission.isChecked = saved.getBoolean("permission")
        } else {
            receive(intent)
        }
        nativeShare = intent.getBooleanExtra(YouTubeMenuHook.EXTRA_NATIVE_SHARE, false) && YouTubeLink.parse(link.text?.toString().orEmpty()) != null
        nativeManifest = readNativeManifest()
        if (nativeShare) inputLayout.visibility = View.GONE
        // Native flow inspects public metadata immediately; permission is still mandatory before any media download.
        if (nativeShare && saved == null && enabled()) inspectIncoming()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { YouTubeSession.state.collect { render(it) } } }
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putString("link", link.text?.toString())
        out.putBoolean("permission", permission.isChecked)
        super.onSaveInstanceState(out)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receive(intent)
        if (nativeShare && enabled()) inspectIncoming()
    }

    private fun receive(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            YouTubeSession.resetIncoming()
            link.setText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())
            nativeShare = intent.getBooleanExtra(YouTubeMenuHook.EXTRA_NATIVE_SHARE, false) && YouTubeLink.parse(link.text?.toString().orEmpty()) != null
            nativeManifest = if (nativeShare) NativePlaybackPayload.decode(intent.getBundleExtra(NativePlaybackPayload.EXTRA), YouTubeLink.parse(link.text?.toString().orEmpty()).orEmpty()) else null
            inputLayout.visibility = if (nativeShare) View.GONE else View.VISIBLE
            permission.isChecked = false
        } else {
            link.setText(getSharedPreferences(YouTubeDownloadService.HISTORY, MODE_PRIVATE).getString("last_link", ""))
        }
    }

    private fun enabled() = ModulePrefs.openForUi(this).getBoolean(ModulePrefs.KEY_YT_ENABLED, true)

    private fun inspect() {
        if (!enabled()) { status.setText(R.string.yt_disabled); return }
        if (!nativeShare && !permission.isChecked) { status.setText(R.string.yt_permission_required); return }
        val raw = link.text?.toString().orEmpty()
        getSharedPreferences(YouTubeDownloadService.HISTORY, MODE_PRIVATE).edit().putString("last_link", raw.take(16_384)).apply()
        inspectIncoming()
    }

    private fun inspectIncoming() {
        nativeManifest = readNativeManifest()
        val manifest = nativeManifest
        val visitorData = intent.getStringExtra(NativePlaybackPayload.EXTRA_VISITOR)
        val rawLink = link.text?.toString().orEmpty()
        YouTubeSession.inspect(applicationContext, rawLink, true, fallback = manifest, visitorData = visitorData)
    }

    private fun readNativeManifest(): VideoManifest? = if (!nativeShare) null else runCatching {
        NativePlaybackPayload.decode(intent.getBundleExtra(NativePlaybackPayload.EXTRA), YouTubeLink.parse(link.text?.toString().orEmpty()).orEmpty())
    }.getOrNull()

    private fun startDownload() {
        if (!enabled()) {
            status.setText(R.string.yt_disabled)
            return
        }
        if (!permission.isChecked) {
            status.setText(R.string.yt_permission_required)
            Toast.makeText(this, R.string.yt_permission_required, Toast.LENGTH_SHORT).show()
            permission.requestFocus()
            return
        }
        val ready = displayed ?: return
        if (YouTubeLink.parse(link.text?.toString().orEmpty()) != ready.link) { inspect(); return }
        val choice = ready.choices.getOrNull(quality.selectedItemPosition) ?: return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingChoice = ready to choice
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        launchService(ready, choice)
    }

    private fun launchService(manifest: VideoManifest, choice: VideoChoice) {
        val selected = YouTubeSession.prepareDownload(manifest, choice) ?: return
        try {
            startForegroundService(Intent(this, YouTubeDownloadService::class.java).putExtra(YouTubeDownloadService.EXTRA_SELECTION, selected.id))
        } catch (_: Exception) {
            YouTubeSession.update(YouTubeState.Failed(DownloadError.INTERRUPTED))
        }
    }

    private fun render(state: YouTubeState) {
        analyze.visibility = if (nativeShare && state !is YouTubeState.Failed && (state as? YouTubeState.Ready)?.warning == null) View.GONE else View.VISIBLE
        val busy = state is YouTubeState.Downloading || state is YouTubeState.Inspecting
        analyze.isEnabled = !busy
        link.isEnabled = !busy
        permission.isEnabled = !busy
        download.visibility = if (state is YouTubeState.Ready) View.VISIBLE else View.GONE
        quality.visibility = download.visibility
        cancel.visibility = if (state is YouTubeState.Downloading) View.VISIBLE else View.GONE
        open.visibility = if (state is YouTubeState.Complete || (state is YouTubeState.Idle && lastUri() != null)) View.VISIBLE else View.GONE
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        when (state) {
            YouTubeState.Idle -> status.setText(if (getSharedPreferences(YouTubeDownloadService.HISTORY, MODE_PRIVATE).getString("last_state", "") == "running") R.string.yt_interrupted else R.string.yt_idle)
            is YouTubeState.Inspecting -> {
                progress.isIndeterminate = true
                status.setText(R.string.yt_inspecting)
            }
            is YouTubeState.Ready -> {
                analyze.setText(if (state.warning != null) R.string.yt_retry else R.string.yt_analyze)
                titleText.text = state.manifest.title
                if (displayed != state.manifest) {
                    displayed = state.manifest
                    quality.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, state.manifest.choices.map { it.label })
                }
                val onlyFallback360 = nativeManifest != null &&
                    state.manifest.choices.size == 1 &&
                    state.manifest.choices[0].audioUrl == null
                status.setText(when {
                    state.checkingHd -> R.string.yt_checking_hd
                    state.warning == DownloadError.VERIFICATION -> R.string.yt_hd_verification
                    state.warning != null -> R.string.yt_hd_unavailable
                    onlyFallback360 -> R.string.yt_native_session
                    else -> R.string.yt_choose_quality
                })
            }
            is YouTubeState.Downloading -> {
                progress.isIndeterminate = state.percent < 0
                if (state.percent >= 0) progress.setProgressCompat(state.percent, true)
                status.text = getString(when (state.phase) {
                    Phase.VIDEO -> R.string.yt_phase_video
                    Phase.AUDIO -> R.string.yt_phase_audio
                    Phase.MERGING -> R.string.yt_phase_merging
                    Phase.SAVING -> R.string.yt_phase_saving
                }) + if (state.percent >= 0) " ${state.percent}%" else ""
            }
            is YouTubeState.Complete -> status.setText(R.string.yt_complete)
            is YouTubeState.Failed -> {
                analyze.setText(R.string.yt_retry)
                status.setText(when (state.error) {
                    DownloadError.INVALID_LINK -> R.string.yt_invalid_link
                    DownloadError.PERMISSION -> R.string.yt_permission_required
                    DownloadError.RESTRICTED -> R.string.yt_restricted
                    DownloadError.NO_FORMAT -> R.string.yt_no_format
                    DownloadError.STORAGE -> R.string.yt_storage_error
                    DownloadError.INTERRUPTED -> R.string.yt_interrupted
                    DownloadError.NETWORK -> R.string.yt_network_error
                    DownloadError.VERIFICATION -> R.string.yt_verification_required
                    DownloadError.PARSER -> R.string.yt_parser_error
                })
            }
        }
    }

    private fun lastUri(): String? = getSharedPreferences(YouTubeDownloadService.HISTORY, MODE_PRIVATE).getString("last_uri", null)

    private fun openResult() {
        val uri = (YouTubeSession.state.value as? YouTubeState.Complete)?.uri ?: lastUri() ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) {
            status.setText(R.string.yt_open_failed)
        }
    }
}
