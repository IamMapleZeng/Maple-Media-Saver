package io.github.iammaplezeng.maplemediasaver

/** Keep this JVM static method for the self-process activation hook. */
internal object ModuleStatus {
    @JvmStatic fun isActivated(): Boolean = false
}
