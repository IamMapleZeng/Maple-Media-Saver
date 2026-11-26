package io.github.iammaplezeng.xtgmediasaver;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Downloads all images of a post into MediaStore (Pictures/XTGMediaSaver) on a background thread. */
final class ImageBatchDownloader {
    private static volatile ExecutorService executor;

    private ImageBatchDownloader() {}

    private static ExecutorService executor() {
        ExecutorService current = executor;
        if (current == null) {
            synchronized (ImageBatchDownloader.class) {
                current = executor;
                if (current == null) {
                    current = Executors.newSingleThreadExecutor();
                    executor = current;
                }
            }
        }
        return current;
    }

    static void downloadAll(Context context, List<String> imageUrls) {
        if (context == null || imageUrls == null || imageUrls.isEmpty()) return;
        final Context appContext = context.getApplicationContext();
        final Locale locale = Locale.getDefault();
        final int total = imageUrls.size();
        showToast(appContext, LocaleStrings.downloadingImagesToast(total, locale), Toast.LENGTH_SHORT);

        executor().execute(() -> {
            int saved = 0;
            long batchStamp = System.currentTimeMillis();
            for (int i = 0; i < total; i++) {
                String rawUrl = imageUrls.get(i);
                if (rawUrl == null || rawUrl.isEmpty()) continue;
                try {
                    DownloadedImage img = fetchBestQuality(rawUrl);
                    if (img != null && saveToGallery(appContext, img, batchStamp, i + 1)) {
                        saved++;
                    }
                } catch (Throwable ignored) {
                    // Continue saving remaining images in the batch.
                }
            }
            final int finalSaved = saved;
            showToast(appContext, LocaleStrings.downloadedImagesToast(finalSaved, total, locale), Toast.LENGTH_LONG);
        });
    }

    static void showToast(Context context, String message, int duration) {
        if (context == null || message == null) return;
        final Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(appContext, message, duration).show());
    }

    static String toOriginalUrl(String rawUrl) {
        if (rawUrl == null || !rawUrl.contains("pbs.twimg.com/media/")) return rawUrl;
        int qIndex = rawUrl.indexOf('?');
        if (qIndex < 0) {
            return rawUrl + "?name=orig";
        }
        String base = rawUrl.substring(0, qIndex);
        String query = rawUrl.substring(qIndex + 1);
        String[] parts = query.split("&");
        StringBuilder sb = new StringBuilder();
        boolean replacedName = false;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            if (part.startsWith("name=")) {
                sb.append("name=orig");
                replacedName = true;
            } else {
                sb.append(part);
            }
        }
        if (!replacedName) {
            if (sb.length() > 0) sb.append('&');
            sb.append("name=orig");
        }
        return base + "?" + sb;
    }

    private static DownloadedImage fetchBestQuality(String rawUrl) throws IOException {
        String origUrl = toOriginalUrl(rawUrl);
        try {
            return fetchUrl(origUrl);
        } catch (IOException firstError) {
            if (!origUrl.equals(rawUrl)) {
                return fetchUrl(rawUrl);
            }
            throw firstError;
        }
    }

    private static DownloadedImage fetchUrl(String urlString) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
        try {
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            String contentType = conn.getContentType();
            byte[] data;
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                data = out.toByteArray();
            }
            if (data.length == 0) throw new IOException("Empty image body");
            String mime = resolveMimeType(contentType, urlString);
            String ext = resolveExtension(mime);
            return new DownloadedImage(data, mime, ext);
        } finally {
            conn.disconnect();
        }
    }

    private static boolean saveToGallery(Context context, DownloadedImage img, long stamp, int index) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "X_" + stamp + "_" + index + "." + img.ext);
        values.put(MediaStore.Images.Media.MIME_TYPE, img.mime);
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/XTGMediaSaver");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return false;
        try {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null) return false;
                out.write(img.bytes);
                out.flush();
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, done, null, null);
            return true;
        } catch (IOException e) {
            resolver.delete(uri, null, null);
            throw e;
        }
    }

    private static String resolveMimeType(String contentType, String url) {
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.US);
            if (lower.contains("png")) return "image/png";
            if (lower.contains("webp")) return "image/webp";
            if (lower.contains("jpeg") || lower.contains("jpg")) return "image/jpeg";
        }
        String lowerUrl = url.toLowerCase(Locale.US);
        if (lowerUrl.contains("format=png") || lowerUrl.endsWith(".png")) return "image/png";
        if (lowerUrl.contains("format=webp") || lowerUrl.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static String resolveExtension(String mime) {
        if ("image/png".equals(mime)) return "png";
        if ("image/webp".equals(mime)) return "webp";
        return "jpg";
    }

    private static final class DownloadedImage {
        final byte[] bytes;
        final String mime;
        final String ext;

        DownloadedImage(byte[] bytes, String mime, String ext) {
            this.bytes = bytes;
            this.mime = mime;
            this.ext = ext;
        }
    }
}
