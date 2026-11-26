package io.github.iammaplezeng.maplemediasaver.youtube

import org.junit.Assert.*
import org.junit.After
import org.junit.Test

class DownloadSelectionTest {
    private val link = "https://www.youtube.com/watch?v=abcdefgh_12"
    private val sd = VideoChoice("360p · MP4", 360, "18")
    private val hd = VideoChoice("1080p · MP4", 1080, "137+140")
    @After fun reset() { YouTubeSession.update(YouTubeState.Idle) }
    @Test fun asyncRefreshCannotChangeTheUsersSelectedQuality() {
        val displayed = VideoManifest(link, "Video", listOf(hd, sd))
        val refreshed = displayed.copy(choices = listOf(VideoChoice("2160p · MP4", 2160, "401+140"), hd, sd))
        YouTubeSession.update(YouTubeState.Ready(refreshed))
        val selected = YouTubeSession.prepareDownload(displayed, displayed.choices[1])
        assertNotNull(selected)
        assertEquals(360, selected?.choice?.height)
        assertNull(YouTubeSession.takeDownload("wrong-id"))
        assertEquals(selected, YouTubeSession.takeDownload(selected?.id))
        assertNull(YouTubeSession.takeDownload(selected?.id))
    }
    @Test fun refusesAnOptionThatNoLongerBelongsToCurrentVideo() {
        val original = VideoManifest(link, "Video", listOf(sd))
        YouTubeSession.update(YouTubeState.Ready(original.copy(link = "https://www.youtube.com/watch?v=abcdefgh_13")))
        assertNull(YouTubeSession.prepareDownload(original, sd))
    }
}
