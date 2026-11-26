package io.github.iammaplezeng.maplemediasaver.youtube

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class DownloadErrorsTest {
    @Test fun retainsDownloadFailureReasons() {
        assertEquals(DownloadError.VERIFICATION, DownloadErrors.classify(DownloadFailure(DownloadError.VERIFICATION)))
        assertEquals(DownloadError.VERIFICATION, DownloadErrors.classify(IOException("wrapped", DownloadFailure(DownloadError.VERIFICATION))))
        assertEquals(DownloadError.PARSER, DownloadErrors.classify(DownloadFailure(DownloadError.PARSER)))
        assertEquals(DownloadError.STORAGE, DownloadErrors.classify(DownloadFailure(DownloadError.STORAGE)))
    }
    @Test fun unclassifiedIoErrorFallsBackToNetwork() {
        assertEquals(DownloadError.NETWORK, DownloadErrors.classify(IOException("timeout")))
    }
}
