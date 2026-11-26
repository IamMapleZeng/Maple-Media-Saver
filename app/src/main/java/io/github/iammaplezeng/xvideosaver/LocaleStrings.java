package io.github.iammaplezeng.xvideosaver;

import java.util.Locale;

/** Localized strings matching the device's system language. */
final class LocaleStrings {
    private LocaleStrings() {}

    static String downloadAllImagesLabel() {
        return downloadAllImagesLabel(Locale.getDefault());
    }

    static String downloadAllImagesLabel(Locale locale) {
        if (locale == null) return "Save All Photo";
        String lang = locale.getLanguage();
        if ("zh".equals(lang)) {
            return isTraditionalChinese(locale) ? "下載全部圖片" : "下载全部图片";
        }
        if ("ja".equals(lang)) return "すべての画像を保存";
        if ("ko".equals(lang)) return "모든 사진 저장";
        if ("es".equals(lang)) return "Guardar todas las fotos";
        if ("fr".equals(lang)) return "Enregistrer toutes les photos";
        if ("de".equals(lang)) return "Alle Fotos speichern";
        if ("pt".equals(lang)) return "Salvar todas as fotos";
        if ("ru".equals(lang)) return "Сохранить все фото";
        if ("ar".equals(lang)) return "حفظ جميع الصور";
        return "Save All Photo";
    }

    static String downloadingImagesToast(int count, Locale locale) {
        if (locale != null && "zh".equals(locale.getLanguage())) {
            return isTraditionalChinese(locale)
                    ? "正在下載 " + count + " 張圖片…"
                    : "正在下载 " + count + " 张图片…";
        }
        if (locale != null && "ja".equals(locale.getLanguage())) {
            return count + " 枚の画像をダウンロード中…";
        }
        if (locale != null && "ko".equals(locale.getLanguage())) {
            return "이미지 " + count + "장 다운로드 중…";
        }
        return "Downloading " + count + (count == 1 ? " image…" : " images…");
    }

    static String downloadedImagesToast(int saved, int total, Locale locale) {
        if (locale != null && "zh".equals(locale.getLanguage())) {
            if (saved == total) {
                return isTraditionalChinese(locale)
                        ? "已儲存 " + saved + " 張圖片到相簿"
                        : "已保存 " + saved + " 张图片到相册";
            }
            return isTraditionalChinese(locale)
                    ? "已儲存 " + saved + "/" + total + " 張圖片"
                    : "已保存 " + saved + "/" + total + " 张图片";
        }
        if (locale != null && "ja".equals(locale.getLanguage())) {
            return saved == total
                    ? saved + " 枚の画像を保存しました"
                    : saved + "/" + total + " 枚の画像を保存しました";
        }
        if (locale != null && "ko".equals(locale.getLanguage())) {
            return saved == total
                    ? "이미지 " + saved + "장을 저장했습니다"
                    : "이미지 " + saved + "/" + total + "장을 저장했습니다";
        }
        if (saved == total) {
            return "Saved " + saved + (saved == 1 ? " image" : " images");
        }
        return "Saved " + saved + "/" + total + " images";
    }

    private static boolean isTraditionalChinese(Locale locale) {
        String script = locale.getScript();
        if ("Hant".equalsIgnoreCase(script)) return true;
        if ("Hans".equalsIgnoreCase(script)) return false;
        String country = locale.getCountry();
        return "TW".equalsIgnoreCase(country)
                || "HK".equalsIgnoreCase(country)
                || "MO".equalsIgnoreCase(country);
    }
}
