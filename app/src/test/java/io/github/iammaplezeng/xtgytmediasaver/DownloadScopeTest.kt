package io.github.iammaplezeng.xtgytmediasaver

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class DownloadScopeTest {
    @Test fun restoresAfterTap() {
        val scope = DownloadScope(); assertFalse(scope.active())
        val token = scope.enter(); assertTrue(scope.active()); scope.restore(token); assertFalse(scope.active())
    }
    @Test fun nestingKeepsOuterTapActive() {
        val scope = DownloadScope(); val outer = scope.enter(); val inner = scope.enter()
        scope.restore(inner); assertTrue(scope.active()); scope.restore(outer); assertFalse(scope.active())
    }
    @Test fun doesNotAffectOtherThreads() {
        val scope = DownloadScope(); val token = scope.enter(); val other = AtomicBoolean(true)
        Thread { other.set(scope.active()) }.apply { start(); join() }
        assertFalse(other.get()); assertTrue(scope.active()); scope.restore(token)
    }
    @Test fun exceptionCleanupRestoresScope() {
        val scope = DownloadScope(); val token = scope.enter()
        try { throw IllegalStateException() } catch (_: IllegalStateException) { } finally { scope.restore(token) }
        assertFalse(scope.active())
    }
    @Test fun xVersionMustMatchBothFields() {
        assertTrue(HostVersions.xSupported("12.46.1-prod.01", 312461001))
        assertFalse(HostVersions.xSupported("12.46.1-prod.01", 312461002))
        assertFalse(HostVersions.xSupported("12.47.0", 312461001))
    }
    @Test fun telegramVersionMustMatchBothFields() {
        assertTrue(HostVersions.telegramSupported("13.3.1", 73461))
        assertFalse(HostVersions.telegramSupported("13.3.1", 73462))
        assertFalse(HostVersions.telegramSupported("13.4.0", 73461))
    }
    @Test fun localeStringsMatchSystemLanguage() {
        assertEquals("下载全部图片", LocaleStrings.downloadAllImagesLabel(Locale.SIMPLIFIED_CHINESE))
        assertEquals("下載全部圖片", LocaleStrings.downloadAllImagesLabel(Locale.TRADITIONAL_CHINESE))
        assertEquals("Save All Photo", LocaleStrings.downloadAllImagesLabel(Locale.US))
        assertEquals("すべての画像を保存", LocaleStrings.downloadAllImagesLabel(Locale.JAPAN))
    }
    @Test fun originalUrlUpgradesTwimgMedia() {
        assertEquals("https://pbs.twimg.com/media/ABC123.jpg?name=orig", ImageBatchDownloader.toOriginalUrl("https://pbs.twimg.com/media/ABC123.jpg"))
        assertEquals("https://pbs.twimg.com/media/ABC123?format=jpg&name=orig", ImageBatchDownloader.toOriginalUrl("https://pbs.twimg.com/media/ABC123?format=jpg&name=medium"))
        assertEquals("https://example.com/photo.png", ImageBatchDownloader.toOriginalUrl("https://example.com/photo.png"))
    }
}
