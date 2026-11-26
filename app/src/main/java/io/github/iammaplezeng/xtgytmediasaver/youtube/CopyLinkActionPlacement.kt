package io.github.iammaplezeng.xtgytmediasaver.youtube

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.IdentityHashMap

/**
 * Places an interactive row right below the Copy Link action without detaching or reparenting
 * the host list, so existing share-sheet items keep their mounted icons and tint state.
 */
internal class CopyLinkActionPlacement private constructor(
    private val hostList: ViewGroup,
    private val overlayHost: ViewGroup,
    private val row: View,
    private val rowHeight: Int,
    private val locateCopy: () -> View?
) : ViewTreeObserver.OnPreDrawListener, View.OnAttachStateChangeListener {
    private data class Original(val height: Int, val translations: MutableMap<View, Float> = IdentityHashMap())
    private val originals = IdentityHashMap<ViewGroup, Original>()
    private val anchorLocation = IntArray(2)
    private val hostLocation = IntArray(2)
    private val listLocation = IntArray(2)

    private fun attach() {
        hostList.addOnAttachStateChangeListener(this)
        hostList.viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onViewAttachedToWindow(v: View) = Unit

    override fun onViewDetachedFromWindow(v: View) {
        if (hostList.viewTreeObserver.isAlive) hostList.viewTreeObserver.removeOnPreDrawListener(this)
        hostList.removeOnAttachStateChangeListener(this)
        originals.forEach { (body, original) ->
            body.layoutParams = body.layoutParams.apply { height = original.height }
            original.translations.forEach { (view, translation) -> view.translationY = translation }
        }
        originals.clear()
        if (row.parent === overlayHost) overlayHost.removeView(row)
    }

    override fun onPreDraw(): Boolean {
        try {
            val anchor = locateCopy() ?: return true.also { row.visibility = View.INVISIBLE }
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
            hostList.getLocationInWindow(listLocation)
            overlayHost.getLocationInWindow(hostLocation)
            val width = hostList.width
            if (width > 0 && row.layoutParams.width != width) {
                row.layoutParams = row.layoutParams.apply { this.width = width }
            }
            val targetX = (listLocation[0] - hostLocation[0] - row.left).toFloat()
            val targetY = (anchorLocation[1] + anchor.height - hostLocation[1] - row.top + 8 * hostList.resources.displayMetrics.density)
            if (row.translationX != targetX) row.translationX = targetX
            if (row.translationY != targetY) row.translationY = targetY
            val rowTopInWindow = anchorLocation[1] + anchor.height
            val listTop = listLocation[1]
            val listBottom = listTop + hostList.height
            row.visibility = if (rowTopInWindow >= listTop - rowHeight / 2 && rowTopInWindow <= listBottom) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
        } catch (error: Throwable) {
            row.visibility = View.INVISIBLE
            io.github.iammaplezeng.xtgytmediasaver.XTGMediaSaverModule.log("[YT] action placement skipped ${error.javaClass.simpleName}")
        }
        return true
    }

    companion object {
        private fun findOverlayHost(parent: LinearLayout): ViewGroup? {
            var current = parent.parent
            while (current is ViewGroup) {
                if (current is FrameLayout || current.javaClass.name.endsWith("CoordinatorLayout")) {
                    return current
                }
                current = current.parent
            }
            return null
        }

        fun install(parent: LinearLayout, list: ViewGroup, anchor: View, row: View, height: Int, locate: () -> View?): Boolean {
            val body = anchor.parent as? ViewGroup ?: return false
            if (body.parent !== list || body.height <= 0) return false
            val overlayHost = findOverlayHost(parent) ?: return false
            val width = if (list.width > 0) list.width else ViewGroup.LayoutParams.MATCH_PARENT
            val params = if (overlayHost is FrameLayout) {
                FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.START)
            } else {
                ViewGroup.LayoutParams(width, height)
            }
            overlayHost.addView(row, params)
            row.bringToFront()
            CopyLinkActionPlacement(list, overlayHost, row, height, locate).attach()
            return true
        }
    }
}
