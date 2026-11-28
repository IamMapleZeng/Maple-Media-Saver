package io.github.iammaplezeng.maplemediasaver.youtube

import org.junit.Assert.*
import org.junit.Test

class YtDlpFormatsTest {
    private val link = "https://www.youtube.com/watch?v=abcdefgh_12"
    private val audio = """{"format_id":"140","url":"https://rr1.googlevideo.com/videoplayback?itag=140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2","abr":128}"""
    private fun video(id: String, height: Int, codec: String = "avc1.640028", drm: Boolean = false) =
        """{"format_id":"$id","height":$height,"url":"https://rr1.googlevideo.com/videoplayback?itag=$id","ext":"mp4","vcodec":"$codec","acodec":"none","has_drm":$drm}"""
    private fun info(formats: String, eligibility: String = "\"availability\":\"public\",\"age_limit\":0,\"is_live\":false") =
        """{"id":"abcdefgh_12","title":"Test","formats":[$formats],$eligibility}"""
    private fun rejected(json: String, reason: DownloadError) {
        try { YtDlpFormats.parse(json, link); fail("Expected rejection") }
        catch (failure: DownloadFailure) { assertEquals(reason, failure.reason) }
    }
    @Test fun listsHdAnd4kWithSeparateAudioInsteadOfLimitingToProgressive() {
        val manifest = YtDlpFormats.parse(info("$audio,${video("137", 1080)},${video("401", 2160, "av01.0.12M.08")}"), link)
        assertEquals(listOf(2160, 1080), manifest.choices.map { it.height })
        assertEquals(listOf("2160p · MP4", "1080p · MP4"), manifest.choices.map { it.label })
        assertEquals(listOf("401+140", "137+140"), manifest.choices.map { it.ytDlpFormat })
    }
    @Test fun picksPreferredTracksAndKeepsTheFirstEquallyRankedFormat() {
        val webm = video("248", 1080).replace("mp4", "webm")
        val av1 = video("399", 1080, "av01")
        val lowRate = video("136", 1080).replace("\"height\":", "\"tbr\":200,\"height\":")
        val highRate = lowRate.replace("200", "500").replace("136", "137")
        val tied = highRate.replace("137", "138")
        val preferredAudio = audio.replace("128", "192").replace("140", "141")
        val webmAudio = preferredAudio.replace("m4a", "webm").replace("192", "256").replace("141", "251")
        val formats = listOf(webm, av1, lowRate, highRate, tied, audio, preferredAudio, webmAudio)
        val manifest = YtDlpFormats.parse(info(formats.joinToString()), link)
        assertEquals("137+141", manifest.choices.single().ytDlpFormat)
    }

    @Test fun fallsBackToMuxedVideoWhenNoAudioTrackIsUsable() {
        val muxed = video("18", 360).replace("\"acodec\":\"none\"", "\"acodec\":\"mp4a\"")
        val manifest = YtDlpFormats.parse(info("${video("137", 1080)},$muxed"), link)
        assertEquals("18", manifest.choices.single().ytDlpFormat)
    }

    @Test fun encryptedFormatsNeverBecomeDownloadChoices() {
        val manifest = YtDlpFormats.parse(info("$audio,${video("137", 1080)},${video("401", 2160, drm = true)}"), link)
        assertEquals(listOf(1080), manifest.choices.map { it.height })
    }
    @Test fun rejectsRestrictedMetadataAndMismatchedVideo() {
        val formats = "$audio,${video("137", 1080)}"
        listOf("\"availability\":\"private\"", "\"availability\":\"premium_only\"", "\"availability\":\"public\",\"age_limit\":18",
            "\"availability\":\"public\",\"is_live\":true", "\"availability\":\"public\",\"has_drm\":true", "\"availability\":null")
            .forEach { rejected(info(formats, it), DownloadError.RESTRICTED) }
        rejected(info(formats).replace("abcdefgh_12", "abcdefgh_13"), DownloadError.RESTRICTED)
    }
    @Test fun rejectsVideoOnlyWithoutAudioAndUntrustedMediaUrls() {
        rejected(info(video("137", 1080)), DownloadError.NO_FORMAT)
        rejected(info("$audio,${video("137", 1080)}").replace("rr1.googlevideo.com", "googlevideo.com.attacker.test"), DownloadError.NO_FORMAT)
        rejected(info("$audio,${video("137", 1080)}").replace("https://", "file:///"), DownloadError.NO_FORMAT)
    }
    @Test fun formatSelectorsAreOnlyOneOrTwoExactFormatIds() {
        assertTrue(YtDlpFormats.validSelection("137+140"))
        assertTrue(YtDlpFormats.validSelection("18"))
        listOf("bestvideo", "137;echo hi", "../../file", "137+140+999", "137/18", "").forEach {
            assertFalse(it, YtDlpFormats.validSelection(it))
        }
    }
}
