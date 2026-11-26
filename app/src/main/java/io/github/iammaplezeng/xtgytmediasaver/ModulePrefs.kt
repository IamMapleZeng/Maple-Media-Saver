package io.github.iammaplezeng.xtgytmediasaver

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import de.robv.android.xposed.XSharedPreferences

internal object ModulePrefs {
    const val PREFS_NAME = "xtg_prefs"
    const val KEY_X_ENABLED = "x_enabled"
    const val KEY_X_VIDEO = "x_video_enabled"
    const val KEY_X_ALL_IMAGES = "x_all_images_enabled"
    const val KEY_TG_ENABLED = "tg_enabled"
    const val KEY_TG_SAVE_RESTRICTED = "tg_save_restricted_enabled"
    const val KEY_YT_ENABLED = "yt_enabled"
    const val KEY_YT_AD_BLOCK = "yt_ad_block_enabled"
    @Volatile private var xPrefs: XSharedPreferences? = null

    @SuppressLint("WorldReadableFiles")
    @Suppress("DEPRECATION")
    @JvmStatic fun openForUi(context: Context): SharedPreferences = try {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
    } catch (_: SecurityException) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun shared(): XSharedPreferences {
        val current = xPrefs ?: synchronized(this) {
            xPrefs ?: XSharedPreferences(BuildConfig.APPLICATION_ID, PREFS_NAME).also {
                it.makeWorldReadable(); xPrefs = it
            }
        }
        current.reload()
        return current
    }
    private fun readFlag(key: String, default: Boolean): Boolean = try {
        val prefs = shared()
        if (prefs.file.canRead()) prefs.getBoolean(key, default) else default
    } catch (_: Throwable) { default }
    @JvmStatic fun isXVideoEnabled() = readFlag(KEY_X_ENABLED, true) && readFlag(KEY_X_VIDEO, true)
    @JvmStatic fun isXAllImagesEnabled() = readFlag(KEY_X_ENABLED, true) && readFlag(KEY_X_ALL_IMAGES, true)
    @JvmStatic fun isTelegramSaveRestrictedEnabled() = readFlag(KEY_TG_ENABLED, true) && readFlag(KEY_TG_SAVE_RESTRICTED, true)
    @JvmStatic fun isYouTubeEnabled() = readFlag(KEY_YT_ENABLED, true)
    @JvmStatic fun isYouTubeAdBlockEnabled() = readFlag(KEY_YT_AD_BLOCK, true)
}
