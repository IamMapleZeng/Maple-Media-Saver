package io.github.iammaplezeng.xtgytmediasaver.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class ChromeCookieImporterTest {
    @Test fun convertsChromeTimestampToUnixEpoch() {
        assertEquals(0L, ChromeCookieImporter.convertChromeExpiry(0L))
        // 2026-01-01T00:00:00Z = 1767225600 Unix seconds -> (1767225600 + 11644473600) * 1_000_000
        assertEquals(1767225600L, ChromeCookieImporter.convertChromeExpiry(13411700000_000_000L - 800_000_000L))
    }

    @Test fun formatsNetscapeLineCompatibleWithSanitizer() {
        val line = ChromeCookieImporter.formatNetscapeLine(
            hostKey = ".youtube.com",
            path = "/",
            isSecure = true,
            expiresUtc = 0L,
            name = "SID",
            value = "synthetic_value",
            isHttpOnly = true
        )
        val sanitized = YouTubeCookieFile.sanitize("# Netscape HTTP Cookie File\n$line\n")
        assertTrue(sanitized.contains("#HttpOnly_.youtube.com\tTRUE\t/\tTRUE\t0\tSID\tsynthetic_value"))
    }

    @Test fun decryptsV10CookieWithAndWithoutMeta24DomainHash() {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val key = SecretKeySpec(factory.generateSecret(PBEKeySpec("peanuts".toCharArray(), "saltysalt".toByteArray(), 1, 128)).encoded, "AES")
        val iv = IvParameterSpec(ByteArray(16) { 0x20 })
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

        // Plain v10 (meta < 24)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encryptedOld = "v10".toByteArray() + cipher.doFinal("cookie_val_old".toByteArray())
        assertEquals("cookie_val_old", ChromeCookieImporter.decryptCookieValue(encryptedOld, ".youtube.com", 20))

        // Meta >= 24 with 32-byte SHA-256(host_key) prefix
        val hostHash = MessageDigest.getInstance("SHA-256").digest(".youtube.com".toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encryptedNew = "v10".toByteArray() + cipher.doFinal(hostHash + "cookie_val_new".toByteArray())
        assertEquals("cookie_val_new", ChromeCookieImporter.decryptCookieValue(encryptedNew, ".youtube.com", 24))
    }
}
