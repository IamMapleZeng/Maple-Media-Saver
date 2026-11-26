package io.github.iammaplezeng.maplemediasaver

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/** Public class + public no-arg constructor are required by the legacy Xposed entry contract. */
class XTGMediaSaverModule : IXposedHookLoadPackage {
    private val xHook = XMediaHook()
    private val tgHook = TelegramMediaHook()
    private val ytHook by lazy { io.github.iammaplezeng.maplemediasaver.youtube.YouTubeMenuHook() }
    private val ytmHook by lazy { io.github.iammaplezeng.maplemediasaver.youtube.YouTubeMusicHook() }
    override fun handleLoadPackage(p: XC_LoadPackage.LoadPackageParam) {
        try {
            when {
                p.packageName == BuildConfig.APPLICATION_ID -> hookSelfActivation(p.classLoader)
                p.packageName == HostVersions.X_PACKAGE && p.processName == HostVersions.X_PACKAGE -> xHook.handleLoadPackage(p)
                p.packageName == HostVersions.TG_PACKAGE && p.processName == HostVersions.TG_PACKAGE -> tgHook.handleLoadPackage(p)
                p.packageName == HostVersions.YT_PACKAGE && p.processName == HostVersions.YT_PACKAGE -> ytHook.handleLoadPackage(p)
                p.packageName == HostVersions.YTM_PACKAGE && p.processName == HostVersions.YTM_PACKAGE -> ytmHook.handleLoadPackage(p)
            }
        } catch (error: Throwable) { log("DISPATCH FAILED: ${error.javaClass.simpleName}") }
    }
    private fun hookSelfActivation(loader: ClassLoader) {
        try {
            val status = Class.forName(ModuleStatus::class.java.name, false, loader)
            XposedBridge.hookMethod(status.getDeclaredMethod("isActivated"), object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) { param.result = true }
            })
            log("[SELF] READY: Kotlin activation hook installed")
        } catch (error: Throwable) { log("SELF HOOK FAILED: ${error.javaClass.simpleName}") }
    }
    companion object {
        @JvmStatic fun log(message: String) {
            XposedBridge.log("[XTGMediaSaver] $message")
            android.util.Log.i("XTGMediaSaver", message)
        }
    }
}
