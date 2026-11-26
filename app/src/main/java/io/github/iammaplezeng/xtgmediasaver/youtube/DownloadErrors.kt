package io.github.iammaplezeng.xtgmediasaver.youtube

import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException

internal object DownloadErrors {
    fun classify(error: Throwable): DownloadError {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        causes.filterIsInstance<DownloadFailure>().firstOrNull()?.let { return it.reason }
        if (causes.any { it is SignInConfirmNotBotException || it is ReCaptchaException }) return DownloadError.VERIFICATION
        if (causes.any { it is ContentNotAvailableException }) return DownloadError.RESTRICTED
        if (causes.any { it is ParsingException }) return DownloadError.PARSER
        return DownloadError.NETWORK
    }
}
