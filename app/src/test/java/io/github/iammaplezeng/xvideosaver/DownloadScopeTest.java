package io.github.iammaplezeng.xvideosaver;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadScopeTest {
    @Test public void restoresAfterTap() {
        DownloadScope scope = new DownloadScope();
        assertFalse(scope.active()); int token = scope.enter(); assertTrue(scope.active());
        scope.restore(token); assertFalse(scope.active());
    }
    @Test public void nestingKeepsOuterTapActive() {
        DownloadScope scope = new DownloadScope();
        int outer = scope.enter(), inner = scope.enter();
        scope.restore(inner); assertTrue(scope.active());
        scope.restore(outer); assertFalse(scope.active());
    }
    @Test public void doesNotAffectOtherThreads() throws Exception {
        DownloadScope scope = new DownloadScope(); int token = scope.enter();
        AtomicBoolean other = new AtomicBoolean(true);
        Thread thread = new Thread(() -> other.set(scope.active())); thread.start(); thread.join();
        assertFalse(other.get()); assertTrue(scope.active()); scope.restore(token);
    }
    @Test public void exceptionCleanupRestoresScope() {
        DownloadScope scope = new DownloadScope(); int token = scope.enter();
        try { throw new IllegalStateException(); }
        catch (IllegalStateException expected) { /* Hook after-callback also runs on a host exception. */ }
        finally { scope.restore(token); }
        assertFalse(scope.active());
    }
    @Test public void versionMustMatchBothFields() {
        assertTrue(HostVersion.supported("12.46.1-prod.01", 312461001));
        assertFalse(HostVersion.supported("12.46.1-prod.01", 312461002));
        assertFalse(HostVersion.supported("12.47.0", 312461001));
    }
}
