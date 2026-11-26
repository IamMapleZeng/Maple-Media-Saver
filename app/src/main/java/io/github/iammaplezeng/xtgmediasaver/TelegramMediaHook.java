package io.github.iammaplezeng.xtgmediasaver;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Exact adapter for Telegram 13.3.1 (73461) to unlock media saving in restricted groups/channels. */
final class TelegramMediaHook {
    private final AtomicBoolean installed = new AtomicBoolean();

    void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        XTGMediaSaverModule.log("[TG] loaded module=" + BuildConfig.VERSION_NAME);
        Method attach = Application.class.getDeclaredMethod("attach", Context.class);
        XposedBridge.hookMethod(attach, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!installed.compareAndSet(false, true)) return;
                try {
                    Context context = (Context) param.args[0];
                    PackageInfo info = context.getPackageManager().getPackageInfo(HostVersions.TG_PACKAGE, 0);
                    XTGMediaSaverModule.log("[TG] host=" + info.versionName + " code=" + info.getLongVersionCode());
                    if (!HostVersions.telegramSupported(info.versionName, info.getLongVersionCode())) {
                        XTGMediaSaverModule.log("[TG] UNSUPPORTED: expected " + HostVersions.TG_NAME + "; hooks disabled");
                        return;
                    }
                    install(context.getClassLoader());
                } catch (Throwable error) {
                    XTGMediaSaverModule.log("[TG] INSTALL FAILED: " + error.getClass().getSimpleName() + ": " + error.getMessage());
                }
            }
        });
    }

    private void install(ClassLoader loader) throws ReflectiveOperationException {
        // Resolve every verified symbol before hooking any method.
        Class<?> messagesController = Class.forName("org.telegram.messenger.tp", false, loader);
        Class<?> chatClass = Class.forName("org.telegram.tgnet.TLRPC$Chat", false, loader);
        Class<?> userFullClass = Class.forName("org.telegram.tgnet.TLRPC$UserFull", false, loader);
        Class<?> messageClass = Class.forName("org.telegram.tgnet.TLRPC$Message", false, loader);
        Class<?> messageObjectClass = Class.forName("org.telegram.messenger.jj", false, loader);
        Class<?> chatActivityClass = Class.forName("org.telegram.ui.pn", false, loader);
        Class<?> photoViewerClass = Class.forName("org.telegram.ui.PhotoViewer", false, loader);
        Class<?> viewerEntryClass = Class.forName("org.telegram.ui.xr0", false, loader);

        Method isChatNoForwardsChat = messagesController.getDeclaredMethod("r2", chatClass);
        Method isChatNoForwardsDialog = messagesController.getDeclaredMethod("G2", long.class);
        Method isUserNoForwards = messagesController.getDeclaredMethod("Q2", userFullClass);
        Method chatIsNoForwards = chatActivityClass.getDeclaredMethod("W9");
        Method hasSelectedNoForwards = chatActivityClass.getDeclaredMethod("z9");
        Method photoViewerIsNoForwards = photoViewerClass.getDeclaredMethod("H1", viewerEntryClass);

        Field msgObjMessageField = messageObjectClass.getDeclaredField("k");
        msgObjMessageField.setAccessible(true);
        Field msgNoForwardsField = messageClass.getDeclaredField("noforwards");
        msgNoForwardsField.setAccessible(true);

        XC_MethodHook returnFalseWhenEnabled = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (ModulePrefs.isTelegramSaveRestrictedEnabled()) {
                    param.setResult(false);
                }
            }
        };

        XposedBridge.hookMethod(isChatNoForwardsChat, returnFalseWhenEnabled);
        XposedBridge.hookMethod(isChatNoForwardsDialog, returnFalseWhenEnabled);
        XposedBridge.hookMethod(isUserNoForwards, returnFalseWhenEnabled);
        XposedBridge.hookMethod(chatIsNoForwards, returnFalseWhenEnabled);
        XposedBridge.hookMethod(hasSelectedNoForwards, returnFalseWhenEnabled);
        XposedBridge.hookMethod(photoViewerIsNoForwards, returnFalseWhenEnabled);

        // Clear client-side Message.noforwards on MessageObject construction so chat menu & PhotoViewer
        // show native Save to Gallery / Save Video / Save to Downloads options in restricted groups.
        XposedBridge.hookAllConstructors(messageObjectClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!ModulePrefs.isTelegramSaveRestrictedEnabled()) return;
                try {
                    Object msg = msgObjMessageField.get(param.thisObject);
                    if (msg != null && msgNoForwardsField.getBoolean(msg)) {
                        msgNoForwardsField.setBoolean(msg, false);
                    }
                } catch (Throwable ignored) {
                }
            }
        });

        XTGMediaSaverModule.log("[TG] READY: restricted chat/channel media saving unlocked; exact adapter " + HostVersions.TG_NAME);
    }
}
