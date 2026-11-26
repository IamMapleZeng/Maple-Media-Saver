package io.github.iammaplezeng.xvideosaver;

final class HostVersion {
    static final String NAME = "12.46.1-prod.01";
    static final long CODE = 312461001L;
    static boolean supported(String name, long code) { return NAME.equals(name) && code == CODE; }
}
