package io.github.iammaplezeng.xtgmediasaver

import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.iammaplezeng.xtgmediasaver.youtube.YouTubeDownloadService

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    override fun onCreate(state: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(state)
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
        refreshStatuses()
    }
    override fun onResume() { super.onResume(); refreshStatuses() }

    private fun bindLanguageSetting() {
        val options = arrayOf(getString(R.string.language_system), getString(R.string.language_chinese), getString(R.string.language_english))
        findViewById<TextView>(R.id.language_value).text = options[LanguageSettings.selectedIndex()]
        findViewById<View>(R.id.button_language).setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.language_title)
                .setSingleChoiceItems(options, LanguageSettings.selectedIndex()) { dialog, index ->
                    dialog.dismiss()
                    LanguageSettings.select(index)
                }.setNegativeButton(android.R.string.cancel, null).show()
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
        bindSwitch(R.id.switch_yt_ad_block, ModulePrefs.KEY_YT_AD_BLOCK, default = false)
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
