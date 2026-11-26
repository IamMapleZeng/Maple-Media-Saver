package io.github.iammaplezeng.maplemediasaver.youtube

import android.app.Application
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.iammaplezeng.maplemediasaver.HostVersions
import io.github.iammaplezeng.maplemediasaver.ModulePrefs
import io.github.iammaplezeng.maplemediasaver.XTGMediaSaverModule
import java.lang.reflect.Field
import java.util.concurrent.atomic.AtomicBoolean

/** Version-guarded ad filter and background playback hook for YouTube Music. */
internal class YouTubeMusicHook {
    private val installed = AtomicBoolean()

    fun handleLoadPackage(p: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.hookMethod(Application::class.java.getDeclaredMethod("attach", Context::class.java), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!installed.compareAndSet(false, true)) return
                try {
                    val context = param.args[0] as Context
                    val info = context.packageManager.getPackageInfo(HostVersions.YTM_PACKAGE, 0)
                    if (!HostVersions.youtubeMusicSupported(info.versionName, info.longVersionCode)) {
                        log("UNSUPPORTED host version ${info.versionName} (${info.longVersionCode})")
                        return
                    }
                    installAdFilter(context.classLoader)
                    installBackgroundPlayback(context.classLoader)
                    log("READY: ${info.versionName} adBlock=${ModulePrefs.isYouTubeMusicAdBlockEnabled()} bgPlay=${ModulePrefs.isYouTubeMusicBackgroundPlayEnabled()}")
                } catch (error: Throwable) {
                    log("INSTALL FAILED ${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}")
                }
            }
        })
    }

    private fun installAdFilter(loader: ClassLoader) {
        val response = Class.forName("amdi", false, loader)
        val defaultInstance = response.getDeclaredField("a").apply { isAccessible = true }.get(null)
        val adFields = listOf("m", "n", "p").map { name ->
            val field = response.getDeclaredField(name).apply { isAccessible = true }
            val empty = field.get(defaultInstance)
            check(empty is Collection<*> && empty.isEmpty()) { "Unexpected field amdi.$name" }
            field to empty
        }
        check(copyWithoutAds(defaultInstance, adFields).javaClass == response)
        val notified = AtomicBoolean()
        fun sanitize(value: Any?): Any? {
            if (value == null || value.javaClass != response || !ModulePrefs.isYouTubeMusicAdBlockEnabled()) return value
            val hasAds = adFields.any { (field, _) -> (field.get(value) as? Collection<*>)?.isNotEmpty() == true }
            if (!hasAds) return value
            return try {
                copyWithoutAds(value, adFields).also {
                    if (notified.compareAndSet(false, true)) log("player ads filtered")
                }
            } catch (error: Throwable) {
                log("ad filter skipped ${error.javaClass.simpleName}")
                value
            }
        }
        listOf("whq", "whg").forEach { wrapperName ->
            val wrapper = Class.forName(wrapperName, false, loader)
            wrapper.declaredConstructors.filter { it.parameterTypes.firstOrNull() == response }.forEach { ctor ->
                XposedBridge.hookMethod(ctor, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = sanitize(param.args[0])
                    }
                })
            }
        }
        val adSlotReader = Class.forName("nkq", false, loader)
        adSlotReader.declaredMethods.filter {
            it.name in setOf("c", "d") && it.parameterTypes.firstOrNull() == response
        }.forEach { method ->
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.args[0] = sanitize(param.args[0])
                }
            })
        }
    }

    private fun installBackgroundPlayback(loader: ClassLoader) {
        val playabilityStatus = Class.forName("amda", false, loader)
        val helper = Class.forName("aakx", false, loader)
        val method = helper.getDeclaredMethod("k", playabilityStatus)
        check(method.returnType == Boolean::class.javaPrimitiveType)
        val notified = AtomicBoolean()
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (param.args[0] != null && ModulePrefs.isYouTubeMusicBackgroundPlayEnabled()) {
                    param.result = true
                    if (notified.compareAndSet(false, true)) log("background playback enabled")
                }
            }
        })
    }

    private fun copyWithoutAds(player: Any, adFields: List<Pair<Field, Any>>): Any {
        val builder = player.javaClass.getMethod("toBuilder").invoke(player) ?: return player
        generateSequence(builder.javaClass as Class<*>?) { it.superclass }
            .firstNotNullOf { type -> runCatching { type.getDeclaredMethod("copyOnWrite") }.getOrNull() }
            .apply { isAccessible = true }.invoke(builder)
        val instanceField = generateSequence(builder.javaClass as Class<*>?) { it.superclass }
            .firstNotNullOf { type -> runCatching { type.getDeclaredField("instance") }.getOrNull() }
            .apply { isAccessible = true }
        val copy = instanceField.get(builder) ?: return player
        check(copy !== player)
        for ((field, empty) in adFields) {
            field.set(copy, empty)
        }
        return builder.javaClass.getMethod("build").invoke(builder) ?: player
    }

    private fun log(message: String) = XTGMediaSaverModule.log("[YTM] $message")
}
