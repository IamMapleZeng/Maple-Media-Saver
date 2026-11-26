package io.github.iammaplezeng.xtgmediasaver;

/** Hooked by XTGMediaSaverModule in the module's own process to report LSPosed activation status. */
final class ModuleStatus {
    private ModuleStatus() {}

    static boolean isActivated() {
        return Boolean.FALSE;
    }
}
