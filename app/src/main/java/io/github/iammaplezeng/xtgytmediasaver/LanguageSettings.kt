package io.github.iammaplezeng.xtgytmediasaver

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** AppCompat stores the selection and syncs it with Android 13+ app language settings. */
internal object LanguageSettings {
    private val tags = listOf("", "zh-Hans", "en")
    fun selectedIndex(): Int = when (AppCompatDelegate.getApplicationLocales()[0]?.language) {
        "zh" -> 1
        "en" -> 2
        else -> 0
    }
    fun select(index: Int) {
        val tag = tags.getOrNull(index) ?: return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
