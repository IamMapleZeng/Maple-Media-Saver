package io.github.iammaplezeng.xvideosaver;

/** Subscription override exists only on the current thread during a download tap. */
final class DownloadScope {
    private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
    int enter() { int previous = depth.get(); depth.set(previous + 1); return previous; }
    void restore(int previous) { if (previous == 0) depth.remove(); else depth.set(previous); }
    boolean active() { return depth.get() > 0; }
}
