package io.github.iammaplezeng.xtgytmediasaver

import android.app.LocaleManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle

/** Temporary old-package bridge. Only the same-signed replacement app can read settings. */
class SettingsMigrationProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val ctx = context ?: error("No context")
        val caller = Binder.getCallingUid()
        val packages = ctx.packageManager.getPackagesForUid(caller).orEmpty()
        if (ctx.packageName != SettingsMigration.OLD_PACKAGE || SettingsMigration.NEW_PACKAGE !in packages ||
            ctx.packageManager.checkSignatures(caller, ctx.applicationInfo.uid) != PackageManager.SIGNATURE_MATCH) {
            throw SecurityException("Settings migration caller not allowed")
        }
        require(method == "settings-v1")
        return Bundle().apply {
            val prefs = ModulePrefs.openForUi(ctx)
            for (key in SettingsMigration.FLAGS) if (prefs.contains(key)) putBoolean(key, prefs.getBoolean(key, true))
            val history = ctx.getSharedPreferences("yt_download_history", 0)
            putBundle("history", Bundle().apply {
                for (key in SettingsMigration.HISTORY) history.getString(key, null)?.takeIf { it.length <= 2000 }?.let { putString(key, it) }
            })
            if (Build.VERSION.SDK_INT >= 33) putString("locale", ctx.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
