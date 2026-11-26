package io.github.iammaplezeng.xtgytmediasaver.youtube

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
    @Test fun rejectsCdnLookalikesAndUnsafeSchemes() {
        assertTrue(YouTubeNetwork.mediaAllowed("https://rr1---sn.example.googlevideo.com/videoplayback?id=1"))
        listOf("https://googlevideo.com.attacker.test/videoplayback", "http://rr.googlevideo.com/video", "https://user@rr.googlevideo.com/video", "https://127.0.0.1/video")
            .forEach { assertFalse(YouTubeNetwork.mediaAllowed(it)) }
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
    @Test fun mediaUserAgentMatchesSignedClientParameter() {
        assertEquals(YouTubeNetwork.VR_USER_AGENT, YouTubeNetwork.userAgentForMediaUrl("https://rr1.googlevideo.com/videoplayback?id=1&c=ANDROID_VR&itag=137"))
        assertEquals(YouTubeNetwork.ANDROID_USER_AGENT, YouTubeNetwork.userAgentForMediaUrl("https://rr1.googlevideo.com/videoplayback?id=1&c=ANDROID&itag=18"))
        assertEquals(YouTubeNetwork.IOS_USER_AGENT, YouTubeNetwork.userAgentForMediaUrl("https://rr1.googlevideo.com/videoplayback?id=1&c=IOS&itag=137"))
        assertEquals(YouTubeNetwork.IOS_PRODUCER_USER_AGENT, YouTubeNetwork.userAgentForMediaUrl("https://rr1.googlevideo.com/videoplayback?id=1&c=IOS_PRODUCER&itag=137"))
        assertEquals(YouTubeNetwork.VISIONOS_USER_AGENT, YouTubeNetwork.userAgentForMediaUrl("https://rr1.googlevideo.com/videoplayback?id=1&c=VISIONOS&itag=137"))
        assertEquals(YouTubeNetwork.USER_AGENT, YouTubeNetwork.userAgentForMediaUrl("https://rr1.googlevideo.com/videoplayback?id=1&c=WEB&itag=18"))
    }
    @Test fun botVerificationClassificationDistinguishesBotCheckFromRestrictedContent() {
        assertTrue(YouTubeResolver.isBotVerificationFailure("LOGIN_REQUIRED", "Sign in to confirm you\u2019re not a bot"))
        assertTrue(YouTubeResolver.isBotVerificationFailure("UNPLAYABLE", "Sign in to confirm you\u2019re not a bot"))
        assertTrue(YouTubeResolver.isBotVerificationFailure("LOGIN_REQUIRED", "登入以確認你不是機器人"))
        assertFalse(YouTubeResolver.isBotVerificationFailure("LOGIN_REQUIRED", "Sign in to confirm your age. This video may be inappropriate for some users."))
        assertFalse(YouTubeResolver.isBotVerificationFailure("UNPLAYABLE", "Join this channel to get access to members-only content"))
    }
    @Test fun vrClientProtoBytesEncodeExpectedWireFields() {
        val bytes = NativePlaybackCapture.buildVrClientInfoBytes("en", "US", "rollout_tok")
        assertTrue(bytes.isNotEmpty())
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("Oculus"))
        assertTrue(text.contains("Quest 3"))
        assertTrue(text.contains("1.60.19"))
        assertTrue(text.contains("rollout_tok"))
    }
}
