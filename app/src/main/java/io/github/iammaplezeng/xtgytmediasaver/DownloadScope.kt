package io.github.iammaplezeng.xtgytmediasaver

/** Eligibility override stays within the synchronous download event and current thread. */
internal class DownloadScope {
    private val depth = ThreadLocal.withInitial { 0 }
    fun enter(): Int = (depth.get() ?: 0).also { depth.set(it + 1) }
    fun restore(previous: Int) { if (previous == 0) depth.remove() else depth.set(previous) }
    fun active(): Boolean = (depth.get() ?: 0) > 0
}
