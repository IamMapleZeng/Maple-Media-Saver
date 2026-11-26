package io.github.iammaplezeng.xtgytmediasaver

internal object HostVersions {
    const val X_PACKAGE = "com.twitter.android"
    const val X_NAME = "12.46.1-prod.01"
    const val X_CODE = 312461001L
    const val TG_PACKAGE = "org.telegram.messenger"
    const val TG_NAME = "13.3.1"
    const val TG_CODE = 73461L
    const val YT_PACKAGE = "com.google.android.youtube"
    const val YT_OBSERVED_NAME = "21.47.483"
    const val YT_OBSERVED_CODE = 1561317016L
    @JvmStatic fun xSupported(name: String?, code: Long) = name == X_NAME && code == X_CODE
    @JvmStatic fun telegramSupported(name: String?, code: Long) = name == TG_NAME && code == TG_CODE
}
