package io.github.iammaplezeng.xtgytmediasaver.youtube

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import java.io.IOException

class DownloadErrorsTest {
    @Test fun loginChallengeIsNotReportedAsOrdinaryNetworkFailure() {
        val denied = SignInConfirmNotBotException("LOGIN_REQUIRED")
        assertEquals(DownloadError.VERIFICATION, DownloadErrors.classify(denied))
        assertEquals(DownloadError.VERIFICATION, DownloadErrors.classify(IOException("wrapped", denied)))
    }
    @Test fun parserErrorsRemainDistinct() {
        assertEquals(DownloadError.PARSER, DownloadErrors.classify(ParsingException("missing field")))
    }
    @Test fun retainsStorageAndNetworkFailures() {
        assertEquals(DownloadError.STORAGE, DownloadErrors.classify(DownloadFailure(DownloadError.STORAGE)))
        assertEquals(DownloadError.NETWORK, DownloadErrors.classify(IOException("timeout")))
    }
}
