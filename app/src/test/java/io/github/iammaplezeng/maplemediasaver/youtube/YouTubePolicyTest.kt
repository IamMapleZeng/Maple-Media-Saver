package io.github.iammaplezeng.maplemediasaver.youtube

import org.junit.Assert.*
import org.junit.Test

class YouTubePolicyTest {
    private val id = "abcdefgh_12"
    private val canonical = "https://www.youtube.com/watch?v=$id"
    @Test fun acceptsShareVariantsAndRemovesTracking() {
        listOf("https://youtu.be/$id?si=tracking", "https://www.youtube.com/shorts/$id", "https://m.youtube.com/watch?v=$id&t=12", canonical)
            .forEach { assertEquals(canonical, YouTubeLink.parse(it)) }
    }
    @Test fun extractsLinkFromSharedText() { assertEquals(canonical, YouTubeLink.parse("A video: $canonical")) }
    @Test fun rejectsSpoofedHostsAndCredentials() {
        listOf("https://youtube.com.attacker.test/watch?v=$id", "https://youtube.com@attacker.test/watch?v=$id", "https://user@youtube.com/watch?v=$id", "http://youtube.com/watch?v=$id", "file:///tmp/$id")
            .forEach { assertNull(YouTubeLink.parse(it)) }
    }
    @Test fun rejectsPlaylistAndInvalidIds() {
        listOf("https://www.youtube.com/playlist?list=$id", "https://youtu.be/short", "https://www.youtube.com/watch?v=$id&v=abcdefgh_13")
            .forEach { assertNull(YouTubeLink.parse(it)) }
    }
    @Test fun rejectsMultipleDifferentVideosAndOversizedInput() {
        assertNull(YouTubeLink.parse("$canonical https://youtu.be/abcdefgh_13")); assertNull(YouTubeLink.parse("x".repeat(20_000) + canonical))
    }
    @Test fun publicPlaybackIsNotSufficientPermission() {
        val public = AccessFacts(true, 0, false, false, false)
        assertFalse(DownloadPolicy.allowed(public, false)); assertTrue(DownloadPolicy.allowed(public, true))
    }
    @Test fun rejectsEveryRestrictedCaseEvenWithPermission() {
        listOf(AccessFacts(false, 0, false, false, false), AccessFacts(true, 18, false, false, false),
            AccessFacts(true, 0, true, false, false), AccessFacts(true, 0, false, true, false), AccessFacts(true, 0, false, false, true))
            .forEach { assertFalse(DownloadPolicy.allowed(it, true)) }
    }
}
