package io.github.iammaplezeng.xvideosaver;

/** Hooked by XVideoSaverModule in the module's own process to report LSPosed activation status. */
final class ModuleStatus {
    private ModuleStatus() {}

    static boolean isActivated() {
        return Boolean.FALSE;
    }
}
