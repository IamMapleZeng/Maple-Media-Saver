package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.iammaplezeng.xtgytmediasaver.BuildConfig
import io.github.iammaplezeng.xtgytmediasaver.HostVersions
import io.github.iammaplezeng.xtgytmediasaver.ModulePrefs
import io.github.iammaplezeng.xtgytmediasaver.XTGMediaSaverModule
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Adds a native Android row to the verified share-sheet, without clipboard access. */
internal class YouTubeMenuHook {
    private data class Capture(val link: String, val time: Long)
    @Volatile private var captured: Capture? = null
    private val installed = AtomicBoolean()
    private val main = Handler(Looper.getMainLooper())
    private val observers = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    fun handleLoadPackage(p: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.hookMethod(Application::class.java.getDeclaredMethod("attach", Context::class.java), object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (!installed.compareAndSet(false, true)) return
                try {
                    val context = param.args[0] as Context
                    val info = context.packageManager.getPackageInfo(HostVersions.YT_PACKAGE, 0)
                    if (info.versionName != HostVersions.YT_OBSERVED_NAME || info.longVersionCode != HostVersions.YT_OBSERVED_CODE) {
                        log("UNSUPPORTED native share adapter ${info.versionName}"); return
                    }
                    try { YouTubeAdsHook.install(context.classLoader) }
                    catch (error: Throwable) { log("ad filter unavailable ${error.javaClass.simpleName}") }
                    val request = Class.forName("alsy", false, context.classLoader)
                    val entity = request.getDeclaredField("a").apply { isAccessible = true }
                    check(entity.type == String::class.java)
                    XposedBridge.hookMethod(request.getDeclaredMethod("a"), object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) { capture(entity.get(param.thisObject) as? String) }
                    })
                    request.declaredMethods.filter { it.name == "e" && it.parameterCount == 5 && it.parameterTypes[0] == String::class.java }.forEach { method ->
                        XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) { capture(param.args[0] as? String) }
                        })
                    }
                    XposedBridge.hookMethod(Dialog::class.java.getDeclaredMethod("show"), object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val dialog = param.thisObject as Dialog
                                val weak = WeakReference(dialog)
                                val root = dialog.window?.decorView ?: return
                                root.viewTreeObserver.addOnGlobalLayoutListener {
                                    weak.get()?.let { it.window?.decorView?.let { view -> scanSafely(view, it.context) } }
                                }
                                root.post { weak.get()?.let { scanSafely(root, it.context) } }
                            } catch (error: Throwable) { log("DIALOG SKIPPED ${error.javaClass.simpleName}") }
                        }
                    })
                    (param.thisObject as Application).registerActivityLifecycleCallbacks(callbacks())
                    log("READY: native share menu adapter ${info.versionName}")
                } catch (error: Throwable) { log("INSTALL FAILED ${error.javaClass.simpleName}") }
            }
        })
    }

    private fun capture(encoded: String?) {
        val link = encoded?.let(SharedVideoEntity::videoLink)
        captured = link?.let { Capture(it, SystemClock.elapsedRealtime()) }
        log(if (link != null) "share video captured" else "share entity unavailable ${encoded?.let(SharedVideoEntity::layout)}")
    }

    private fun callbacks() = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            if (observers.containsKey(activity)) return
            val weak = WeakReference(activity)
            val listener = ViewTreeObserver.OnGlobalLayoutListener { weak.get()?.let(::scanSafely) }
            observers[activity] = listener
            activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(listener)
            main.post { weak.get()?.let(::scanSafely) }
        }
        override fun onActivityPaused(activity: Activity) { detach(activity) }
        override fun onActivityDestroyed(activity: Activity) { detach(activity) }
        override fun onActivityCreated(activity: Activity, state: Bundle?) { }
        override fun onActivityStarted(activity: Activity) { }
        override fun onActivityStopped(activity: Activity) { }
        override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) { }
    }

    private fun detach(activity: Activity) {
        observers.remove(activity)?.let { if (activity.window.decorView.viewTreeObserver.isAlive) activity.window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(it) }
    }

    private fun scanSafely(activity: Activity) {
        scanSafely(activity.window.decorView, activity)
    }

    private fun scanSafely(root: View, context: Context) {
        try { scan(root, context) } catch (error: Throwable) { log("MENU SKIPPED ${error.javaClass.simpleName}") }
    }

    private fun scan(root: View, context: Context) {
        val id = context.resources.getIdentifier("bottom_sheet_list", "id", HostVersions.YT_PACKAGE)
        if (id == 0) return
        val list = root.findViewById<View>(id) as? ViewGroup ?: return
        val parent = list.parent as? LinearLayout ?: return
        if (root.findViewWithTag<View>(ROW_TAG) != null) return
        val capture = captured ?: return
        if (SystemClock.elapsedRealtime() - capture.time > 60_000 || !ModulePrefs.isYouTubeEnabled()) return
        val copyAction = findCopyAction(list) ?: return
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val foreground = resolveForegroundColor(root, list, copyAction, context)
        val label = systemLabel()
        val text = TextView(context).apply {
            this.text = label
            textSize = 18f
            setTextColor(foreground)
        }
        val icon = ImageView(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor((foreground and 0x00FFFFFF) or 0x14000000)
            }
            setImageDrawable(Arrow(foreground, density))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val row = LinearLayout(context).apply {
            tag = ROW_TAG; orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            contentDescription = label; isClickable = true; isFocusable = true
            setPadding(dp(12), dp(6), dp(16), dp(12))
            addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
            addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener {
                val active = captured
                if (active?.link != capture.link || !ModulePrefs.isYouTubeEnabled()) return@setOnClickListener
                openQuickDialog(context, capture.link)
            }
        }
        if (CopyLinkActionPlacement.install(parent, list, copyAction, row, dp(68)) { findCopyAction(list) }) {
            log("native download row placed after Copy link")
            return
        }
        // Keep a usable fallback if an unsupported layout is encountered.
        (list.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.weight = 1f
            list.layoutParams = params
        }
        parent.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(68)))
        log("native download row added last; parent=${parent.javaClass.simpleName} list=${list.javaClass.simpleName}")
    }

    private fun resolveForegroundColor(root: View, list: ViewGroup, copyAction: View, context: Context): Int {
        findSampleTextColor(copyAction)?.let { return it }
        findSampleTextColor(list)?.let { return it }
        findBackgroundColor(list, root)?.let { bg ->
            return if (isDarkColor(bg)) Color.WHITE else LIGHT_MODE_TEXT_COLOR
        }
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (night) Color.WHITE else LIGHT_MODE_TEXT_COLOR
    }

    private fun findSampleTextColor(view: View): Int? {
        if (view is TextView && !view.text.isNullOrBlank()) {
            val color = view.currentTextColor
            if (Color.alpha(color) >= 128) return color
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findSampleTextColor(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun findBackgroundColor(start: View, stopAt: View): Int? {
        var current: View? = start
        while (current != null) {
            extractDrawableColor(current.background)?.let { return it }
            current.backgroundTintList?.defaultColor?.takeIf { Color.alpha(it) >= 128 }?.let { return it }
            if (current === stopAt) break
            current = current.parent as? View
        }
        return null
    }

    private fun extractDrawableColor(drawable: Drawable?): Int? {
        if (drawable == null) return null
        return when (drawable) {
            is ColorDrawable -> drawable.color.takeIf { Color.alpha(it) >= 128 }
            is GradientDrawable -> drawable.color?.defaultColor?.takeIf { Color.alpha(it) >= 128 }
            is LayerDrawable -> {
                for (i in drawable.numberOfLayers - 1 downTo 0) {
                    extractDrawableColor(drawable.getDrawable(i))?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    private fun isDarkColor(color: Int): Boolean {
        val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return luminance < 0.5
    }

    private fun openQuickDialog(context: Context, link: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .setComponent(ComponentName(BuildConfig.APPLICATION_ID, "${BuildConfig.APPLICATION_ID}.youtube.YouTubeQuickActivity"))
                .putExtra(Intent.EXTRA_TEXT, link).putExtra(EXTRA_NATIVE_SHARE, true)
            var owner: Context = context
            while (owner is ContextWrapper && owner !is Activity && owner.baseContext !== owner) owner = owner.baseContext
            if (owner is Activity) owner.startActivity(intent)
            else context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log("native download tap")
        } catch (error: Throwable) { log("OPEN FAILED ${error.javaClass.simpleName}") }
    }

    private fun findCopyAction(root: View): View? {
        if (root.contentDescription?.toString() in COPY_LABELS) return root
        if (root is TextView && root.text?.toString() in COPY_LABELS) return root
        val node = root.createAccessibilityNodeInfo()
        if (node?.contentDescription?.toString() in COPY_LABELS || node?.text?.toString() in COPY_LABELS) return root
        if (root is ViewGroup) for (index in 0 until root.childCount) findCopyAction(root.getChildAt(index))?.let { return it }
        return null
    }

    private fun systemLabel(): String {
        val locale = Resources.getSystem().configuration.locales[0]
        return if (locale.language == "zh") {
            if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "下載影片" else "下载视频"
        } else "Download video"
    }

    private class Arrow(color: Int, private val density: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 2 * density; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
        override fun draw(canvas: Canvas) {
            val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY(); val d = density
            canvas.drawLine(cx, cy - 7*d, cx, cy + 3*d, paint)
            canvas.drawLine(cx - 4*d, cy - d, cx, cy + 3*d, paint)
            canvas.drawLine(cx + 4*d, cy - d, cx, cy + 3*d, paint)
            canvas.drawLine(cx - 6*d, cy + 7*d, cx + 6*d, cy + 7*d, paint)
        }
        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun log(message: String) = XTGMediaSaverModule.log("[YT] $message")

    companion object {
        const val EXTRA_NATIVE_SHARE = "io.github.iammaplezeng.xtgytmediasaver.NATIVE_YT_SHARE"
        private const val ROW_TAG = "io.github.iammaplezeng.xtgytmediasaver.youtube.download"
        private const val LIGHT_MODE_TEXT_COLOR = 0xFF0F0F0F.toInt()
        private val COPY_LABELS = setOf("Copy link", "复制链接", "複製連結", "复制连结", "リンクをコピー", "링크 복사", "Copiar enlace", "Copier le lien", "Link kopieren")
    }
}
