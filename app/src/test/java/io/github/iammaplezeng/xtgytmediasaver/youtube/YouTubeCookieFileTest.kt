package io.github.iammaplezeng.xtgytmediasaver.youtube

import org.junit.Assert.*
import org.junit.Test

class YouTubeCookieFileTest {
    private val header = "# Netscape HTTP Cookie File\n"
    private val cookie = ".youtube.com\tTRUE\t/\tTRUE\t0\tSID\tsynthetic"
    @Test fun acceptsHttpOnlyAndFiltersUnrelatedDomains() {
        val result = YouTubeCookieFile.sanitize(header + "#HttpOnly_" + cookie + "\n.evil.com\tTRUE\t/\tTRUE\t0\tx\tother\n")
        assertTrue(result.contains("#HttpOnly_" + cookie))
        assertFalse(result.contains("evil.com"))
    }
    @Test fun rejectsDomainLookalikesAndEmptyFiles() {
        listOf(header, header + cookie.replace(".youtube.com", "youtube.com.evil.com"), header + cookie.replace(".youtube.com", "notyoutube.com")).forEach {
            assertThrows(IllegalArgumentException::class.java) { YouTubeCookieFile.sanitize(it) }
        }
    }
    @Test fun rejectsMalformedAndOversizedInput() {
        listOf(cookie, header + cookie.replace("\t0\t", "\t-1\t"), header + cookie + "\u0000", header + "x".repeat(YouTubeCookieFile.MAX_BYTES)).forEach {
            assertThrows(IllegalArgumentException::class.java) { YouTubeCookieFile.sanitize(it) }
        }
    }
}
