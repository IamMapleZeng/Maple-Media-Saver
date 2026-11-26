package io.github.iammaplezeng.xtgmediasaver

import android.app.Application
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.util.concurrent.atomic.AtomicBoolean

/** Exact 13.3.1 adapter: target symbols and hook order preserved from Java. */
internal class TelegramMediaHook {
    private val installed = AtomicBoolean()
    fun handleLoadPackage(p: XC_LoadPackage.LoadPackageParam) {
        log("loaded module=${BuildConfig.VERSION_NAME}")
        XposedBridge.hookMethod(Application::class.java.getDeclaredMethod("attach", Context::class.java), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!installed.compareAndSet(false, true)) return
                try {
                    val context = param.args[0] as Context
                    val info = context.packageManager.getPackageInfo(HostVersions.TG_PACKAGE, 0)
                    log("host=${info.versionName} code=${info.longVersionCode}")
                    if (!HostVersions.telegramSupported(info.versionName, info.longVersionCode)) {
                        log("UNSUPPORTED: expected ${HostVersions.TG_NAME}; hooks disabled"); return
                    }
                    install(context.classLoader)
                } catch (error: Throwable) { log("INSTALL FAILED: ${error.javaClass.simpleName}: ${error.message}") }
            }
        })
    }
    private fun install(loader: ClassLoader) {
        fun type(name: String) = Class.forName(name, false, loader)
        val controller = type("org.telegram.messenger.tp")
        val message = type("org.telegram.tgnet.TLRPC\$Message")
        val messageObject = type("org.telegram.messenger.jj")
        val chatActivity = type("org.telegram.ui.pn")
        val methods = listOf(
            controller.getDeclaredMethod("r2", type("org.telegram.tgnet.TLRPC\$Chat")),
            controller.getDeclaredMethod("G2", java.lang.Long.TYPE),
            controller.getDeclaredMethod("Q2", type("org.telegram.tgnet.TLRPC\$UserFull")),
            chatActivity.getDeclaredMethod("W9"),
            chatActivity.getDeclaredMethod("z9"),
            type("org.telegram.ui.PhotoViewer").getDeclaredMethod("H1", type("org.telegram.ui.xr0"))
        )
        val messageField = messageObject.getDeclaredField("k").apply { isAccessible = true }
        val noForwards = message.getDeclaredField("noforwards").apply { isAccessible = true }
        val returnFalse = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (ModulePrefs.isTelegramSaveRestrictedEnabled()) param.result = false
            }
        }
        methods.forEach { XposedBridge.hookMethod(it, returnFalse) }
        XposedBridge.hookAllConstructors(messageObject, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!ModulePrefs.isTelegramSaveRestrictedEnabled()) return
                try {
                    val msg = messageField.get(param.thisObject)
                    if (msg != null && noForwards.getBoolean(msg)) noForwards.setBoolean(msg, false)
                } catch (_: Throwable) { }
            }
        })
        log("READY: restricted chat/channel media saving unlocked; exact adapter ${HostVersions.TG_NAME}")
    }
    private fun log(message: String) = XTGMediaSaverModule.log("[TG] $message")
}
