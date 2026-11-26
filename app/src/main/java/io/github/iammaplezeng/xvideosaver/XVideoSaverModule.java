package io.github.iammaplezeng.xvideosaver;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Exact adapter derived from the user's installed X APK, never guessed across versions. */
public final class XVideoSaverModule implements IXposedHookLoadPackage {
    private static final String HOST = "com.twitter.android";
    private static final String TOKEN = "io.github.iammaplezeng.xvideosaver.scope";
    private final AtomicBoolean installed = new AtomicBoolean();
    private final DownloadScope scope = new DownloadScope();

    private static void log(String message) { XposedBridge.log("[XVideoSaver] " + message); }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if (!HOST.equals(p.packageName) || !HOST.equals(p.processName)) return;
        log("loaded module=" + BuildConfig.VERSION_NAME);
        Method attach = Application.class.getDeclaredMethod("attach", Context.class);
        XposedBridge.hookMethod(attach, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (!installed.compareAndSet(false, true)) return;
                try {
                    Context context = (Context) param.args[0];
                    PackageInfo info = context.getPackageManager().getPackageInfo(HOST, 0);
                    log("host=" + info.versionName + " code=" + info.getLongVersionCode());
                    if (!HostVersion.supported(info.versionName, info.getLongVersionCode())) {
                        log("UNSUPPORTED: expected " + HostVersion.NAME + "; hooks disabled");
                        return;
                    }
                    install(context.getClassLoader());
                } catch (Throwable error) {
                    log("INSTALL FAILED: " + error.getClass().getSimpleName());
                }
            }
        });
    }

    private void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> subscription = Class.forName("com.x.subscriptions.a0", false, loader);
        Method premium = subscription.getDeclaredMethod("g");
        if (premium.getReturnType() != boolean.class) throw new NoSuchMethodException("premium signature");

        Class<?> player = Class.forName("com.x.video.tab.n0", false, loader);
        Class<?> event = Class.forName("com.x.video.tab.d9", false, loader);
        Class<?> downloadEvent = Class.forName("com.x.video.tab.j8", false, loader);
        Method playerDispatch = player.getDeclaredMethod("i", event);
        if (playerDispatch.getReturnType() != void.class) throw new NoSuchMethodException("player signature");

        Class<?> post = Class.forName("com.x.urt.items.post.m5", false, loader);
        Class<?> saveAction = Class.forName("com.x.urt.items.post.y4", false, loader);
        Class<?> video = Class.forName("com.x.models.y4", false, loader);
        Class<?> gif = Class.forName("com.x.models.r4", false, loader);
        Field media = saveAction.getDeclaredField("a"); media.setAccessible(true);
        Method postDispatch = post.getDeclaredMethod("invoke", Object.class);

        // Resolve every symbol before installing any hooks. Nothing changes on a mismatch.
        XposedBridge.hookMethod(premium, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (scope.active()) { param.setResult(true); log("download eligibility applied"); }
            }
        });
        XposedBridge.hookMethod(playerDispatch, tapHook(action -> downloadEvent.isInstance(action), "player"));
        XposedBridge.hookMethod(postDispatch, tapHook(action -> {
            if (!saveAction.isInstance(action)) return false;
            Object item = media.get(action);
            return video.isInstance(item) || gif.isInstance(item);
        }, "long-press"));
        log("READY: player + long-press; exact adapter " + HostVersion.NAME);
    }

    private interface DownloadAction { boolean matches(Object action) throws ReflectiveOperationException; }

    private XC_MethodHook tapHook(DownloadAction action, String path) {
        return new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length > 0 && action.matches(param.args[0])) {
                        int previous = scope.enter();
                        param.setObjectExtra(TOKEN, previous);
                        log("download tap=" + path);
                    }
                } catch (Throwable error) {
                    log("TAP FAILED: " + error.getClass().getSimpleName());
                }
            }
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object previous = param.getObjectExtra(TOKEN);
                if (previous instanceof Integer) scope.restore((Integer) previous);
            }
        };
    }
}
