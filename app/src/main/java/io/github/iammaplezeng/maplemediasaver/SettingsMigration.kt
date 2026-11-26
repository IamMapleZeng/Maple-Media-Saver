package io.github.iammaplezeng.maplemediasaver

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

internal object SettingsMigration {
    const val OLD_PACKAGE = "io.github.iammaplezeng.xtgmediasaver"
    const val NEW_PACKAGE = "io.github.iammaplezeng.maplemediasaver"
    val FLAGS = listOf(ModulePrefs.KEY_X_ENABLED, ModulePrefs.KEY_X_VIDEO, ModulePrefs.KEY_X_ALL_IMAGES,
        ModulePrefs.KEY_TG_ENABLED, ModulePrefs.KEY_TG_SAVE_RESTRICTED, ModulePrefs.KEY_YT_ENABLED, ModulePrefs.KEY_YT_AD_BLOCK)
    val HISTORY = listOf("last_link", "last_state", "last_uri", "last_error")
    fun importOnce(context: Context) {
        if (context.packageName != NEW_PACKAGE) return
        val marker = context.getSharedPreferences("package_migration", 0)
        if (marker.getBoolean("complete", false)) return
        try {
            if (context.packageManager.checkSignatures(OLD_PACKAGE, NEW_PACKAGE) != PackageManager.SIGNATURE_MATCH) return
            val values = context.contentResolver.call(Uri.parse("content://$OLD_PACKAGE.settings.migration"), "settings-v1", null, null) ?: return
            val prefs = ModulePrefs.openForUi(context)
            val editor = prefs.edit()
            for (key in FLAGS) if (!prefs.contains(key) && values.containsKey(key)) editor.putBoolean(key, values.getBoolean(key))
            check(editor.commit())
            values.getBundle("history")?.let { bundle ->
                val history = context.getSharedPreferences("yt_download_history", 0)
                val edit = history.edit()
                for (key in HISTORY) if (!history.contains(key)) bundle.getString(key)?.let { edit.putString(key, it) }
                check(edit.commit())
            }
            val locale = values.getString("locale").orEmpty()
            if (locale.isNotEmpty() && AppCompatDelegate.getApplicationLocales().isEmpty) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale))
            }
            marker.edit().putBoolean("complete", true).apply()
            Log.i("XTGMediaSaver", "[MIGRATION] previous package settings imported")
        } catch (error: Exception) {
            Log.i("XTGMediaSaver", "[MIGRATION] previous settings unavailable: ${error.javaClass.simpleName}")
        }
    }
}
