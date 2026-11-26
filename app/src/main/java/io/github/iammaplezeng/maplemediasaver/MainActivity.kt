package io.github.iammaplezeng.maplemediasaver

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import io.github.iammaplezeng.maplemediasaver.youtube.ChromeCookieImporter
import io.github.iammaplezeng.maplemediasaver.youtube.ChromeImportError
import io.github.iammaplezeng.maplemediasaver.youtube.ChromeImportException
import io.github.iammaplezeng.maplemediasaver.youtube.YtDlpRuntime
import io.github.iammaplezeng.maplemediasaver.youtube.YouTubeCookies
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.iammaplezeng.maplemediasaver.youtube.YouTubeDownloadService

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private val selectCookies = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val message = try {
                runInterruptible(Dispatchers.IO) { YouTubeCookies.import(applicationContext, uri) }
                R.string.yt_cookie_imported
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { R.string.yt_cookie_invalid }
            refreshCookieStatus()
            MaterialAlertDialogBuilder(this@MainActivity).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
        }
    }
    override fun onCreate(state: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(state)
        SettingsMigration.importOnce(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        prefs = ModulePrefs.openForUi(this)
        val appBar = findViewById<View>(R.id.app_bar)
        val content = findViewById<View>(R.id.content_container)
        val bottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root_coordinator)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.setPadding(bars.left, bars.top, bars.right, 0)
            content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, bottom + bars.bottom)
            insets
        }
        findViewById<TextView>(R.id.value_module_version).text = "v${BuildConfig.VERSION_NAME}"
        bindSwitches()
        bindLanguageSetting()
        bindCookieSettings()
        refreshStatuses()
        lifecycleScope.launch {
            val status = findViewById<TextView>(R.id.yt_engine_status)
            try {
                val version = runInterruptible(Dispatchers.IO) { YtDlpRuntime.probe(applicationContext) }
                status.text = getString(R.string.yt_engine_ready, version)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                status.setText(R.string.yt_engine_failed)
                android.util.Log.w("XTGMediaSaver", "[YTDLP] runtime probe failed: ${error.javaClass.simpleName}: ${error.message.orEmpty().take(160)}")
            }
        }
    }
    override fun onResume() { super.onResume(); refreshStatuses() }

    private fun bindCookieSettings() {
        findViewById<View>(R.id.yt_cookie_import).setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.yt_cookie_title).setMessage(R.string.yt_cookie_help)
                .setPositiveButton(R.string.yt_cookie_import) { _, _ -> selectCookies.launch(arrayOf("text/*", "application/octet-stream")) }
                .setNegativeButton(android.R.string.cancel, null).show()
        }
        val chromeButton = findViewById<View>(R.id.yt_cookie_import_chrome)
        chromeButton.setOnClickListener {
            chromeButton.isEnabled = false
            lifecycleScope.launch {
                var rootDenied = false
                val message = try {
                    runInterruptible(Dispatchers.IO) { ChromeCookieImporter.importFromChrome(applicationContext) }
                    R.string.yt_cookie_chrome_imported
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: ChromeImportException) {
                    if (error.reason == ChromeImportError.ROOT_DENIED) rootDenied = true
                    when (error.reason) {
                        ChromeImportError.ROOT_DENIED -> R.string.yt_cookie_chrome_root_denied
                        ChromeImportError.CHROME_NOT_FOUND -> R.string.yt_cookie_chrome_not_found
                        ChromeImportError.NO_COOKIES -> R.string.yt_cookie_chrome_no_cookies
                        ChromeImportError.FAILED -> R.string.yt_cookie_chrome_failed
                    }
                } catch (_: Exception) {
                    R.string.yt_cookie_chrome_failed
                }
                chromeButton.isEnabled = true
                refreshCookieStatus()
                val dialog = MaterialAlertDialogBuilder(this@MainActivity).setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                if (rootDenied) {
                    ChromeCookieImporter.rootManagerLaunchIntent(this@MainActivity)?.let { launchIntent ->
                        dialog.setNeutralButton(R.string.yt_cookie_chrome_open_root_manager) { _, _ ->
                            runCatching { startActivity(launchIntent) }
                        }
                    }
                }
                dialog.show()
            }
        }
        findViewById<View>(R.id.yt_cookie_clear).setOnClickListener {
            YouTubeCookies.clear(applicationContext)
            refreshCookieStatus()
        }
        refreshCookieStatus()
    }
    private fun refreshCookieStatus() {
        val present = YouTubeCookies.present(applicationContext)
        findViewById<TextView>(R.id.yt_cookie_status).setText(if (present) R.string.yt_cookie_present else R.string.yt_cookie_none)
        findViewById<View>(R.id.yt_cookie_clear).isEnabled = present
    }

    private fun bindLanguageSetting() {
        val options = arrayOf(getString(R.string.language_system), getString(R.string.language_chinese), getString(R.string.language_english))
        findViewById<MaterialToolbar>(R.id.toolbar).setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_language) {
                MaterialAlertDialogBuilder(this).setTitle(R.string.language_title)
                    .setSingleChoiceItems(options, LanguageSettings.selectedIndex()) { dialog, index ->
                        dialog.dismiss()
                        LanguageSettings.select(index)
                    }.setNegativeButton(android.R.string.cancel, null).show()
                true
            } else false
        }
    }

    private fun bindSwitches() {
        val xVideo = bindSwitch(R.id.switch_x_video, ModulePrefs.KEY_X_VIDEO)
        val xImages = bindSwitch(R.id.switch_x_all_images, ModulePrefs.KEY_X_ALL_IMAGES)
        val xEnabled = bindSwitch(R.id.switch_x_enabled, ModulePrefs.KEY_X_ENABLED) { enabled ->
            xVideo.isEnabled = enabled; xImages.isEnabled = enabled
        }
        xVideo.isEnabled = xEnabled.isChecked; xImages.isEnabled = xEnabled.isChecked
        val tgSave = bindSwitch(R.id.switch_tg_save_restricted, ModulePrefs.KEY_TG_SAVE_RESTRICTED)
        val tgEnabled = bindSwitch(R.id.switch_tg_enabled, ModulePrefs.KEY_TG_ENABLED) { tgSave.isEnabled = it }
        tgSave.isEnabled = tgEnabled.isChecked
        bindSwitch(R.id.switch_yt_enabled, ModulePrefs.KEY_YT_ENABLED)
        bindSwitch(R.id.switch_yt_ad_block, ModulePrefs.KEY_YT_AD_BLOCK, default = true)
        bindSwitch(R.id.switch_ytm_ad_block, ModulePrefs.KEY_YTM_AD_BLOCK, default = true)
        bindSwitch(R.id.switch_ytm_background_play, ModulePrefs.KEY_YTM_BACKGROUND_PLAY, default = true)
    }
    private fun bindSwitch(id: Int, key: String, default: Boolean = true, changed: (Boolean) -> Unit = {}): MaterialSwitch {
        return findViewById<MaterialSwitch>(id).apply {
            isChecked = prefs.getBoolean(key, default)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply(); changed(checked) }
        }
    }
    private fun refreshStatuses() {
        val active = ModuleStatus.isActivated()
        val card = findViewById<MaterialCardView>(R.id.status_card)
        val foreground = MaterialColors.getColor(card, if (active) com.google.android.material.R.attr.colorOnPrimaryContainer
            else com.google.android.material.R.attr.colorOnErrorContainer)
        card.setCardBackgroundColor(MaterialColors.getColor(card, if (active) com.google.android.material.R.attr.colorPrimaryContainer
            else com.google.android.material.R.attr.colorErrorContainer))
        findViewById<ImageView>(R.id.status_icon).apply {
            setImageResource(if (active) R.drawable.ic_status_check else R.drawable.ic_status_warning)
            imageTintList = ColorStateList.valueOf(foreground)
        }
        findViewById<TextView>(R.id.status_title).apply {
            setText(if (active) R.string.module_active else R.string.module_inactive); setTextColor(foreground)
        }
        findViewById<TextView>(R.id.status_subtitle).apply {
            setText(if (active) R.string.module_active_subtitle else R.string.module_inactive_subtitle)
            setTextColor(foreground)
        }
        findViewById<TextView>(R.id.value_module_version).setTextColor(foreground)
        refreshPlatformBadge(HostVersions.X_PACKAGE, HostVersions.X_NAME, HostVersions::xSupported, R.id.badge_x_status, R.id.value_x_version)
        refreshPlatformBadge(HostVersions.TG_PACKAGE, HostVersions.TG_NAME, HostVersions::telegramSupported, R.id.badge_tg_status, R.id.value_tg_version)
        refreshYouTube()
        refreshPlatformBadge(HostVersions.YTM_PACKAGE, HostVersions.YTM_OBSERVED_NAME, HostVersions::youtubeMusicSupported, R.id.badge_ytm_status, R.id.value_ytm_version)
    }
    private fun refreshPlatformBadge(pkg: String, target: String, checker: (String?, Long) -> Boolean, badgeId: Int, versionId: Int) {
        val info = try { packageManager.getPackageInfo(pkg, 0) } catch (_: Exception) { null }
        val supported = info != null && checker(info.versionName, info.longVersionCode)
        findViewById<TextView>(versionId).text = "${info?.versionName ?: getString(R.string.not_installed)} / $target"
        applyBadgeStyle(findViewById(badgeId), getString(if (info == null) R.string.not_installed else if (supported) R.string.version_matched else R.string.version_mismatch), supported)
    }
    private fun refreshYouTube() {
        val info = try { packageManager.getPackageInfo(HostVersions.YT_PACKAGE, 0) } catch (_: Exception) { null }
        val matched = info?.versionName == HostVersions.YT_OBSERVED_NAME && info.longVersionCode == HostVersions.YT_OBSERVED_CODE
        findViewById<TextView>(R.id.value_yt_version).text = "${info?.versionName ?: getString(R.string.not_installed)} / ${HostVersions.YT_OBSERVED_NAME}"
        applyBadgeStyle(findViewById(R.id.badge_yt_status), getString(if (info == null) R.string.not_installed else if (matched) R.string.version_matched else R.string.version_mismatch), matched)
        val history = getSharedPreferences(YouTubeDownloadService.HISTORY, MODE_PRIVATE)
        findViewById<TextView>(R.id.yt_diagnostics).setText(when (history.getString("last_state", "")) {
            "failed" -> R.string.yt_network_error
            "interrupted", "running" -> R.string.yt_interrupted
            "complete" -> R.string.yt_complete
            else -> R.string.yt_share_help
        })
    }
    private fun applyBadgeStyle(badge: TextView, text: String, ok: Boolean) {
        badge.text = text
        badge.backgroundTintList = ColorStateList.valueOf(MaterialColors.getColor(badge, if (ok)
            com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorErrorContainer))
        badge.setTextColor(MaterialColors.getColor(badge, if (ok) com.google.android.material.R.attr.colorOnPrimaryContainer
            else com.google.android.material.R.attr.colorOnErrorContainer))
    }
}
