package io.github.iammaplezeng.maplemediasaver

import java.util.Locale

/** Strings used inside host processes, where module Android resources are not installed. */
internal object LocaleStrings {
    @JvmStatic fun downloadAllImagesLabel() = downloadAllImagesLabel(Locale.getDefault())
    @JvmStatic fun downloadAllImagesLabel(locale: Locale?): String = when (locale?.language) {
        "zh" -> if (traditional(locale)) "下載全部圖片" else "下载全部图片"
        "ja" -> "すべての画像を保存"
        "ko" -> "모든 사진 저장"
        "es" -> "Guardar todas las fotos"
        "fr" -> "Enregistrer toutes les photos"
        "de" -> "Alle Fotos speichern"
        "pt" -> "Salvar todas as fotos"
        "ru" -> "Сохранить все фото"
        "ar" -> "حفظ جميع الصور"
        else -> "Save All Photo"
    }
    @JvmStatic fun downloadingImagesToast(count: Int, locale: Locale?): String = when (locale?.language) {
        "zh" -> if (traditional(locale)) "正在下載 $count 張圖片…" else "正在下载 $count 张图片…"
        "ja" -> "$count 枚の画像をダウンロード中…"
        "ko" -> "이미지 ${count}장 다운로드 중…"
        else -> "Downloading $count ${if (count == 1) "image" else "images"}…"
    }
    @JvmStatic fun downloadedImagesToast(saved: Int, total: Int, locale: Locale?): String {
        val count = if (saved == total) "$saved" else "$saved/$total"
        return when (locale?.language) {
            "zh" -> if (traditional(locale)) "已儲存 $count 張圖片${if (saved == total) "到相簿" else ""}"
                    else "已保存 $count 张图片${if (saved == total) "到相册" else ""}"
            "ja" -> "$count 枚の画像を保存しました"
            "ko" -> "이미지 $count${if (saved == total) "장을" else "장을"} 저장했습니다"
            else -> "Saved $count ${if (saved == 1 && saved == total) "image" else "images"}"
        }
    }
    private fun traditional(locale: Locale): Boolean = when {
        locale.script.equals("Hant", true) -> true
        locale.script.equals("Hans", true) -> false
        else -> locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
    }
}
