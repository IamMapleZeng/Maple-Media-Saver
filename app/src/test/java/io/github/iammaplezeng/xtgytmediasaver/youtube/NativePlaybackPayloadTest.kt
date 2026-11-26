package io.github.iammaplezeng.xtgytmediasaver.youtube

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativePlaybackPayloadTest {
    @Test fun acceptsOnlyDirectHttpsGoogleVideoPlayback() {
        assertTrue(NativePlaybackPayload.validMediaUrl("https://rr1.googlevideo.com/videoplayback?id=example&expire=123"))
        listOf("http://rr1.googlevideo.com/videoplayback", "https://rr1.googlevideo.com:8443/videoplayback",
            "https://rr1.googlevideo.com.attacker.test/videoplayback", "https://user@rr1.googlevideo.com/videoplayback",
            "https://127.0.0.1/videoplayback", "https://rr1.googlevideo.com/license", "file:///videoplayback")
            .forEach { assertFalse(it, NativePlaybackPayload.validMediaUrl(it)) }
    }
    @Test fun rejectsLicenseRoutesMalformedAndOversizedMetadata() {
        listOf("https://rr1.googlevideo.com/videoplayback?drm=1", "https://rr1.googlevideo.com/videoplayback?license=1",
            "https://rr1.googlevideo.com/videoplayback?x=%zz", "https://rr1.googlevideo.com/videoplayback?x=" + "a".repeat(12_300))
            .forEach { assertFalse(NativePlaybackPayload.validMediaUrl(it)) }
    }
}
