package io.github.iammaplezeng.xtgmediasaver.youtube

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.IdentityHashMap

/** Inserts an interactive row between existing mounted action views, preserving their click handlers. */
internal class CopyLinkActionPlacement private constructor(
    context: Context,
    private val hostList: ViewGroup,
    private val row: View,
    private val rowHeight: Int,
    private val locateCopy: () -> View?
) : FrameLayout(context), ViewTreeObserver.OnPreDrawListener {
    private data class Original(val height: Int, val translations: MutableMap<View, Float> = IdentityHashMap())
    private val originals = IdentityHashMap<ViewGroup, Original>()
    private val anchorLocation = IntArray(2)
    private val frameLocation = IntArray(2)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(this)
    }
    override fun onDetachedFromWindow() {
        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(this)
        originals.forEach { (body, original) ->
            body.layoutParams = body.layoutParams.apply { height = original.height }
            original.translations.forEach { (view, translation) -> view.translationY = translation }
        }
        originals.clear()
        super.onDetachedFromWindow()
    }
    override fun onPreDraw(): Boolean {
        try {
            val anchor = locateCopy() ?: return true.also { row.visibility = INVISIBLE }
            val body = anchor.parent as? ViewGroup ?: return true
            if (body.parent !== hostList || body.height <= 0 || anchor.height <= 0) return true
            if (!originals.containsKey(body)) {
                originals[body] = Original(body.layoutParams.height)
                body.layoutParams = body.layoutParams.apply { height = body.height + rowHeight }
            }
            val original = originals.getValue(body)
            for (index in 0 until body.childCount) {
                val child = body.getChildAt(index)
                if (child !== anchor && child.top >= anchor.bottom) {
                    val translation = original.translations.getOrPut(child) { child.translationY }
                    if (child.translationY != translation + rowHeight) child.translationY = translation + rowHeight
                }
            }
            anchor.getLocationInWindow(anchorLocation)
            getLocationInWindow(frameLocation)
            val target = (anchorLocation[1] + anchor.height - frameLocation[1] + 8 * resources.displayMetrics.density)
            if (row.translationY != target) row.translationY = target
            row.visibility = VISIBLE
        } catch (error: Throwable) {
            row.visibility = INVISIBLE
            io.github.iammaplezeng.xtgmediasaver.XTGMediaSaverModule.log("[YT] action placement skipped ${error.javaClass.simpleName}")
        }
        return true
    }

    companion object {
        fun install(parent: LinearLayout, list: ViewGroup, anchor: View, row: View, height: Int, locate: () -> View?): Boolean {
            val body = anchor.parent as? ViewGroup ?: return false
            if (body.parent !== list || body.height <= 0) return false
            val index = parent.indexOfChild(list)
            if (index < 0) return false
            val parameters = LinearLayout.LayoutParams(list.layoutParams).apply { weight = 1f }
            val frame = CopyLinkActionPlacement(parent.context, list, row, height, locate)
            parent.removeView(list)
            frame.addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            frame.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, height))
            parent.addView(frame, index, parameters)
            return true
        }
    }
}
