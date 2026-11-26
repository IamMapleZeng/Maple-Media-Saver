package io.github.iammaplezeng.xtgmediasaver;

import java.lang.reflect.Method;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Unified Xposed entry point dispatching X and Telegram platform hooks independently. */
public final class XTGMediaSaverModule implements IXposedHookLoadPackage {
    private final XMediaHook xHook = new XMediaHook();
    private final TelegramMediaHook tgHook = new TelegramMediaHook();

    static void log(String message) {
        XposedBridge.log("[XTGMediaSaver] " + message);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        if (BuildConfig.APPLICATION_ID.equals(p.packageName)) {
            hookSelfActivation(p.classLoader);
            return;
        }
        if (HostVersions.X_PACKAGE.equals(p.packageName) && HostVersions.X_PACKAGE.equals(p.processName)) {
            xHook.handleLoadPackage(p);
            return;
        }
        if (HostVersions.TG_PACKAGE.equals(p.packageName) && HostVersions.TG_PACKAGE.equals(p.processName)) {
            tgHook.handleLoadPackage(p);
        }
    }

    private static void hookSelfActivation(ClassLoader loader) {
        try {
            Class<?> statusClass = Class.forName(ModuleStatus.class.getName(), false, loader);
            Method isActivated = statusClass.getDeclaredMethod("isActivated");
            XposedBridge.hookMethod(isActivated, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });
        } catch (Throwable error) {
            log("SELF HOOK FAILED: " + error.getClass().getSimpleName());
        }
    }
}
