package io.github.iammaplezeng.xtgmediasaver;

/** Exact verified host versions for X and Telegram. */
final class HostVersions {
    static final String X_PACKAGE = "com.twitter.android";
    static final String X_NAME = "12.46.1-prod.01";
    static final long X_CODE = 312461001L;

    static final String TG_PACKAGE = "org.telegram.messenger";
    static final String TG_NAME = "13.3.1";
    static final long TG_CODE = 73461L;

    private HostVersions() {}

    static boolean xSupported(String name, long code) {
        return X_NAME.equals(name) && code == X_CODE;
    }

    static boolean telegramSupported(String name, long code) {
        return TG_NAME.equals(name) && code == TG_CODE;
    }
}
