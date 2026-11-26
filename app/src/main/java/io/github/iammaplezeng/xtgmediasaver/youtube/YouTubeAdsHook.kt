package io.github.iammaplezeng.xtgmediasaver.youtube

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import io.github.iammaplezeng.xtgmediasaver.ModulePrefs
import io.github.iammaplezeng.xtgmediasaver.XTGMediaSaverModule
import java.util.concurrent.atomic.AtomicBoolean

/** Installed only behind YouTubeMenuHook's exact host-version guard. */
internal object YouTubeAdsHook {
    fun install(loader: ClassLoader) {
        val response = Class.forName("bhsp", false, loader)
        val default = response.getDeclaredField("a").apply { isAccessible = true }.get(null)
        val empty = response.getDeclaredField("n").apply { isAccessible = true }.get(default)
        check(empty is Collection<*> && empty.isEmpty())
        check(PlayerAdSlots.copyWithoutSlots(default, empty).javaClass == response)
        val notified = AtomicBoolean()
        fun sanitize(value: Any?): Any? {
            if (value == null || value.javaClass != response || !ModulePrefs.isYouTubeAdBlockEnabled()) return value
            return try {
                PlayerAdSlots.remove(value, empty).also { result ->
                    if (result !== value && notified.compareAndSet(false, true)) log("player ad slots filtered")
                }
            } catch (error: Throwable) { log("filter skipped ${error.javaClass.simpleName}"); value }
        }
        val wrapper = Class.forName("almb", false, loader)
        val constructors = wrapper.declaredConstructors.filter { it.parameterTypes.firstOrNull() == response }
        check(constructors.isNotEmpty())
        constructors.forEach { constructor ->
            XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) { param.args[0] = sanitize(param.args[0]) }
            })
        }
        // Some ad consumers receive the proto directly, before the player wrapper is created.
        Class.forName("afqz", false, loader).declaredMethods.filter {
            it.name in setOf("B", "C") && it.parameterTypes.firstOrNull() == response
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) { param.args[0] = sanitize(param.args[0]) }
            })
        }
        Class.forName("aqsx", false, loader).declaredMethods.filter {
            it.name in setOf("J", "G", "H", "at") && it.returnType.name == "com.google.protobuf.MessageLite"
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!param.hasThrowable()) param.result?.takeIf { it.javaClass == response }?.let { param.result = sanitize(it) }
                }
            })
        }
        log("READY: optional player-ad filter; enabled=${ModulePrefs.isYouTubeAdBlockEnabled()}")
    }
    private fun log(message: String) = XTGMediaSaverModule.log("[YT ADS] $message")
}
