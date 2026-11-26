package io.github.iammaplezeng.maplemediasaver

import android.app.Application
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** Function-equivalent adapter for X 12.46.1-prod.01, including the existing image menu. */
internal class XMediaHook {
    private val installed = AtomicBoolean()
    private val scope = DownloadScope()
    @Volatile private var hostContext: Context? = null
    @Volatile private var activePostMedia: PostMediaContext? = null

    fun handleLoadPackage(p: XC_LoadPackage.LoadPackageParam) {
        log("loaded module=${BuildConfig.VERSION_NAME}")
        XposedBridge.hookMethod(Application::class.java.getDeclaredMethod("attach", Context::class.java), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!installed.compareAndSet(false, true)) return
                try {
                    val context = param.args[0] as Context
                    hostContext = context.applicationContext ?: context
                    val info = context.packageManager.getPackageInfo(HostVersions.X_PACKAGE, 0)
                    log("host=${info.versionName} code=${info.longVersionCode}")
                    if (!HostVersions.xSupported(info.versionName, info.longVersionCode)) {
                        log("UNSUPPORTED: expected ${HostVersions.X_NAME}; hooks disabled"); return
                    }
                    install(context.classLoader)
                } catch (error: Throwable) { log("INSTALL FAILED: ${error.javaClass.simpleName}: ${error.message}") }
            }
        })
    }

    private fun install(loader: ClassLoader) {
        fun type(name: String): Class<*> = Class.forName(name, false, loader)
        fun field(owner: Class<*>, name: String): Field = owner.getDeclaredField(name).apply { isAccessible = true }
        val premium = type("com.x.subscriptions.a0").getDeclaredMethod("g")
        check(premium.returnType == java.lang.Boolean.TYPE) { "premium signature" }
        val player = type("com.x.video.tab.n0")
        val event = type("com.x.video.tab.d9")
        val downloadEvent = type("com.x.video.tab.j8")
        val playerDispatch = player.getDeclaredMethod("i", event)
        check(playerDispatch.returnType == java.lang.Void.TYPE) { "player signature" }

        val post = type("com.x.urt.items.post.m5")
        val saveAction = type("com.x.urt.items.post.y4")
        val longPressEvent = type("com.x.urt.items.post.m4")
        val mediaBase = type("com.x.models.c5")
        val imageModel = type("com.x.models.u4")
        val videoModel = type("com.x.models.y4")
        val gifModel = type("com.x.models.r4")
        val media = field(saveAction, "a")
        val imageUrlField = field(imageModel, "c")
        val postRecordField = field(post, "b")
        val sheetStateField = field(post, "k")
        val postCoreField = field(type("com.x.models.r1"), "a")
        val postMediaListMethod = type("com.x.models.v5").getDeclaredMethod("g")
        val stateSetValueMethod = type("androidx.compose.runtime.n1").getDeclaredMethod("setValue", Any::class.java)
        val postDispatch = post.getDeclaredMethod("invoke", Any::class.java)
        val unitInstance = type("kotlin.Unit").getDeclaredField("a").get(null)

        val immutableListClass = type("kotlinx.collections.immutable.c")
        val function1Class = type("kotlin.jvm.functions.Function1")
        val sheetRender = type("com.x.ui.common.sheets.h").getDeclaredMethod("b",
            immutableListClass, function1Class, type("androidx.compose.ui.Modifier"), String::class.java,
            String::class.java, type("androidx.compose.runtime.Composer"), Integer.TYPE, Integer.TYPE)
        val sheetItemClass = type("com.x.ui.common.sheets.a")
        val sheetItemActionField = field(sheetItemClass, "a")
        val iconType = type("com.x.icons.b")
        val downloadIcon = type("com.x.icons.a").getField("A").get(null)
        val textIdBase = type("com.x.models.id")
        val sheetStyleClass = type("com.x.ui.common.sheets.b")
        val sheetItemCtor = sheetItemClass.getDeclaredConstructor(Any::class.java, iconType, textIdBase,
            textIdBase, java.lang.Boolean.TYPE, sheetStyleClass, Integer.TYPE)
        val stringTextCtor = type("com.x.models.bd").getDeclaredConstructor(String::class.java)
        val resTextCtor = type("com.x.models.hd").getDeclaredConstructor(Integer.TYPE)
        val mediaMenuBase = type("com.x.urt.items.post.media.p")
        val mediaSaveMenu = type("com.x.urt.items.post.media.g")
        val mediaSave4kMenu = type("com.x.urt.items.post.media.f")
        val mediaSaveMenuCtor = mediaSaveMenu.getDeclaredConstructor(mediaBase)
        val mediaSaveMenuGetMedia = mediaSaveMenu.getDeclaredMethod("a")
        val toImmutableList = type("com.x.urt.items.post.media.f1").getDeclaredMethod("n", Iterable::class.java)

        // All host symbols have been resolved before the first hook is installed.
        XposedBridge.hookMethod(premium, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (scope.active() && ModulePrefs.isXVideoEnabled()) {
                    param.result = true; log("download eligibility applied")
                }
            }
        })
        XposedBridge.hookMethod(playerDispatch, tapHook("player") {
            downloadEvent.isInstance(it) && ModulePrefs.isXVideoEnabled()
        })
        XposedBridge.hookMethod(postDispatch, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val action = param.args.firstOrNull() ?: return
                    if (longPressEvent.isInstance(action)) {
                        activePostMedia = extractPostMedia(param.thisObject, postRecordField, postCoreField,
                            postMediaListMethod, sheetStateField, imageModel, imageUrlField, videoModel, gifModel)
                        return
                    }
                    if (!saveAction.isInstance(action) || !ModulePrefs.isXVideoEnabled()) return
                    val item = media.get(action)
                    if (videoModel.isInstance(item) || gifModel.isInstance(item)) {
                        param.setObjectExtra(TOKEN, scope.enter()); log("download tap=long-press")
                    }
                } catch (error: Throwable) { log("TAP FAILED: ${error.javaClass.simpleName}") }
            }
            override fun afterHookedMethod(param: MethodHookParam) {
                (param.getObjectExtra(TOKEN) as? Int)?.let(scope::restore)
            }
        })
        XposedBridge.hookMethod(sheetRender, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    if (!ModulePrefs.isXAllImagesEnabled()) return
                    val ctx = activePostMedia ?: return
                    if (ctx.imageUrls.isEmpty()) return
                    val original = param.args[0] as? List<*> ?: return
                    if (original.isEmpty()) return
                    var isMediaMenu = false
                    var hasVideoDownloadItem = false
                    original.forEach { item ->
                        if (sheetItemClass.isInstance(item)) {
                            val payload = sheetItemActionField.get(item)
                            if (payload is DownloadAllImagesPayload) return
                            if (mediaMenuBase.isInstance(payload)) isMediaMenu = true
                            if (mediaSaveMenu.isInstance(payload)) {
                                val target = mediaSaveMenuGetMedia.invoke(payload)
                                if (videoModel.isInstance(target) || gifModel.isInstance(target)) hasVideoDownloadItem = true
                            }
                        }
                    }
                    if (!isMediaMenu) return
                    val updatedItems = ArrayList<Any?>(original.size + 2)
                    var insertIndex = -1
                    original.forEach { item ->
                        val payload = if (sheetItemClass.isInstance(item)) sheetItemActionField.get(item) else null
                        var skip = false
                        if (ctx.firstVideo == null && mediaSaveMenu.isInstance(payload)) {
                            val target = mediaSaveMenuGetMedia.invoke(payload)
                            skip = videoModel.isInstance(target) || gifModel.isInstance(target)
                        }
                        if (!skip) {
                            updatedItems.add(item)
                            if (mediaSaveMenu.isInstance(payload) || mediaSave4kMenu.isInstance(payload)) insertIndex = updatedItems.size
                        }
                    }
                    if (ctx.firstVideo != null && !hasVideoDownloadItem && ModulePrefs.isXVideoEnabled()) {
                        val payload = mediaSaveMenuCtor.newInstance(ctx.firstVideo)
                        val title = resTextCtor.newInstance(if (gifModel.isInstance(ctx.firstVideo)) RES_SAVE_GIF else RES_SAVE_VIDEO)
                        val row = sheetItemCtor.newInstance(payload, downloadIcon, title, null, false, null, 120)
                        if (insertIndex in 0..updatedItems.size) { updatedItems.add(insertIndex, row); insertIndex++ }
                        else { updatedItems.add(0, row); insertIndex = 1 }
                    }
                    val payload = DownloadAllImagesPayload(ctx.imageUrls, ctx.sheetState)
                    val title = stringTextCtor.newInstance(LocaleStrings.downloadAllImagesLabel())
                    val row = sheetItemCtor.newInstance(payload, downloadIcon, title, null, false, null, 120)
                    updatedItems.add(if (insertIndex in 0..updatedItems.size) insertIndex else 0, row)
                    param.args[0] = toImmutableList.invoke(null, updatedItems)

                    val callback = param.args[1]
                    if (callback != null && function1Class.isInstance(callback)) {
                        val fnInvoke = function1Class.getMethod("invoke", Any::class.java)
                        param.args[1] = Proxy.newProxyInstance(loader, arrayOf(function1Class)) { _, method, args ->
                            if (method.name == "invoke" && args?.size == 1) {
                                val tapped = args[0]
                                val tappedPayload = if (sheetItemClass.isInstance(tapped)) sheetItemActionField.get(tapped) else tapped
                                if (tappedPayload is DownloadAllImagesPayload) {
                                    dismissSheet(tappedPayload.sheetState, stateSetValueMethod)
                                    log("download tap=all-images count=${tappedPayload.imageUrls.size}")
                                    ImageBatchDownloader.downloadAll(hostContext, tappedPayload.imageUrls)
                                    unitInstance
                                } else fnInvoke.invoke(callback, tapped)
                            } else method.invoke(callback, *(args ?: emptyArray()))
                        }
                    }
                } catch (error: Throwable) { log("MENU HOOK FAILED: ${error.javaClass.simpleName}") }
            }
        })
        log("READY: player + long-press + all-images; exact adapter ${HostVersions.X_NAME}")
    }
    private fun dismissSheet(state: Any?, method: Method) {
        if (state != null) try { method.invoke(state, null as Any?) } catch (_: Throwable) { }
    }
    private fun extractPostMedia(receiver: Any, recordField: Field, coreField: Field, listMethod: Method,
        stateField: Field, imageModel: Class<*>, urlField: Field, videoModel: Class<*>, gifModel: Class<*>): PostMediaContext? = try {
        val record = recordField.get(receiver)
        val core = if (record != null) coreField.get(record) else null
        val list = if (core != null) listMethod.invoke(core) as? Iterable<*> else null
        if (list == null) null else {
            val urls = ArrayList<String>()
            var firstVideo: Any? = null
            for (item in list) {
                if (imageModel.isInstance(item)) {
                    (urlField.get(item) as? String)?.takeIf { it.isNotEmpty() }?.let(urls::add)
                } else if (firstVideo == null && (videoModel.isInstance(item) || gifModel.isInstance(item))) firstVideo = item
            }
            PostMediaContext(Collections.unmodifiableList(urls), firstVideo, stateField.get(receiver))
        }
    } catch (error: Throwable) { log("EXTRACT MEDIA FAILED: ${error.javaClass.simpleName}"); null }

    private fun tapHook(path: String, matches: (Any?) -> Boolean) = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            try {
                if (param.args.isNotEmpty() && matches(param.args[0])) {
                    param.setObjectExtra(TOKEN, scope.enter()); log("download tap=$path")
                }
            } catch (error: Throwable) { log("TAP FAILED: ${error.javaClass.simpleName}") }
        }
        override fun afterHookedMethod(param: MethodHookParam) { (param.getObjectExtra(TOKEN) as? Int)?.let(scope::restore) }
    }
    private data class PostMediaContext(val imageUrls: List<String>, val firstVideo: Any?, val sheetState: Any?)
    private data class DownloadAllImagesPayload(val imageUrls: List<String>, val sheetState: Any?)
    private fun log(message: String) = XTGMediaSaverModule.log("[X] $message")
    companion object {
        private const val TOKEN = "io.github.iammaplezeng.maplemediasaver.x.scope"
        private const val RES_SAVE_VIDEO = 0x7f133426
        private const val RES_SAVE_GIF = 0x7f133422
    }
}
