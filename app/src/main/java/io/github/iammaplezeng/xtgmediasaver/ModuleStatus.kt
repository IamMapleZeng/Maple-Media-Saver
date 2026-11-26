package io.github.iammaplezeng.xtgmediasaver

/** Keep this JVM static method for the self-process activation hook. */
internal object ModuleStatus {
    @JvmStatic fun isActivated(): Boolean = false
}
