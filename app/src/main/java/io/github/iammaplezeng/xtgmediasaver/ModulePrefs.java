package io.github.iammaplezeng.xtgmediasaver;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;

/** Per-platform configuration shared between the settings UI and LSPosed hooks. */
final class ModulePrefs {
    static final String PREFS_NAME = "xtg_prefs";

    static final String KEY_X_ENABLED = "x_enabled";
    static final String KEY_X_VIDEO = "x_video_enabled";
    static final String KEY_X_ALL_IMAGES = "x_all_images_enabled";

    static final String KEY_TG_ENABLED = "tg_enabled";
    static final String KEY_TG_SAVE_RESTRICTED = "tg_save_restricted_enabled";

    private static volatile XSharedPreferences xPrefs;

    private ModulePrefs() {}

    @SuppressLint("WorldReadableFiles")
    static SharedPreferences openForUi(Context context) {
        try {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE);
        } catch (SecurityException ignored) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private static XSharedPreferences xPrefs() {
        XSharedPreferences current = xPrefs;
        if (current == null) {
            synchronized (ModulePrefs.class) {
                current = xPrefs;
                if (current == null) {
                    current = new XSharedPreferences(BuildConfig.APPLICATION_ID, PREFS_NAME);
                    current.makeWorldReadable();
                    xPrefs = current;
                }
            }
        }
        current.reload();
        return current;
    }

    private static boolean readFlag(String key, boolean defaultValue) {
        try {
            XSharedPreferences prefs = xPrefs();
            if (!prefs.getFile().canRead()) return defaultValue;
            return prefs.getBoolean(key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    static boolean isXVideoEnabled() {
        return readFlag(KEY_X_ENABLED, true) && readFlag(KEY_X_VIDEO, true);
    }

    static boolean isXAllImagesEnabled() {
        return readFlag(KEY_X_ENABLED, true) && readFlag(KEY_X_ALL_IMAGES, true);
    }

    static boolean isTelegramSaveRestrictedEnabled() {
        return readFlag(KEY_TG_ENABLED, true) && readFlag(KEY_TG_SAVE_RESTRICTED, true);
    }
}
