package io.github.iammaplezeng.xvideosaver;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Exact adapter derived from the user's installed X APK, never guessed across versions. */
public final class XVideoSaverModule implements IXposedHookLoadPackage {
    private static final String HOST = "com.twitter.android";
    private static final String TOKEN = "io.github.iammaplezeng.xvideosaver.scope";
    private static final int RES_SAVE_VIDEO = 0x7f133426;
    private static final int RES_SAVE_GIF = 0x7f133422;

    private final AtomicBoolean installed = new AtomicBoolean();
    private final DownloadScope scope = new DownloadScope();
    private volatile Context hostContext;
    private volatile PostMediaContext activePostMedia;

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
                    hostContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
                    PackageInfo info = context.getPackageManager().getPackageInfo(HOST, 0);
                    log("host=" + info.versionName + " code=" + info.getLongVersionCode());
                    if (!HostVersion.supported(info.versionName, info.getLongVersionCode())) {
                        log("UNSUPPORTED: expected " + HostVersion.NAME + "; hooks disabled");
                        return;
                    }
                    install(context.getClassLoader());
                } catch (Throwable error) {
                    log("INSTALL FAILED: " + error.getClass().getSimpleName() + ": " + error.getMessage());
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
        Class<?> longPressEvent = Class.forName("com.x.urt.items.post.m4", false, loader);
        Class<?> mediaBase = Class.forName("com.x.models.c5", false, loader);
        Class<?> imageModel = Class.forName("com.x.models.u4", false, loader);
        Class<?> videoModel = Class.forName("com.x.models.y4", false, loader);
        Class<?> gifModel = Class.forName("com.x.models.r4", false, loader);
        Field media = saveAction.getDeclaredField("a"); media.setAccessible(true);
        Field imageUrlField = imageModel.getDeclaredField("c"); imageUrlField.setAccessible(true);
        Field postRecordField = post.getDeclaredField("b"); postRecordField.setAccessible(true);
        Field sheetStateField = post.getDeclaredField("k"); sheetStateField.setAccessible(true);
        Field postCoreField = Class.forName("com.x.models.r1", false, loader).getDeclaredField("a"); postCoreField.setAccessible(true);
        Method postMediaListMethod = Class.forName("com.x.models.v5", false, loader).getDeclaredMethod("g");
        Method stateSetValueMethod = Class.forName("androidx.compose.runtime.n1", false, loader).getDeclaredMethod("setValue", Object.class);
        Method postDispatch = post.getDeclaredMethod("invoke", Object.class);

        Class<?> unitClass = Class.forName("kotlin.Unit", false, loader);
        Object unitInstance = unitClass.getDeclaredField("a").get(null);

        // Long-press bottom sheet symbols
        Class<?> sheetsH = Class.forName("com.x.ui.common.sheets.h", false, loader);
        Class<?> immutableListClass = Class.forName("kotlinx.collections.immutable.c", false, loader);
        Class<?> function1Class = Class.forName("kotlin.jvm.functions.Function1", false, loader);
        Class<?> modifierClass = Class.forName("androidx.compose.ui.Modifier", false, loader);
        Class<?> composerClass = Class.forName("androidx.compose.runtime.Composer", false, loader);
        Method sheetRender = sheetsH.getDeclaredMethod(
                "b",
                immutableListClass,
                function1Class,
                modifierClass,
                String.class,
                String.class,
                composerClass,
                int.class,
                int.class
        );

        Class<?> sheetItemClass = Class.forName("com.x.ui.common.sheets.a", false, loader);
        Field sheetItemActionField = sheetItemClass.getDeclaredField("a"); sheetItemActionField.setAccessible(true);
        Class<?> iconType = Class.forName("com.x.icons.b", false, loader);
        Object downloadIcon = Class.forName("com.x.icons.a", false, loader).getField("A").get(null);
        Class<?> textIdBase = Class.forName("com.x.models.id", false, loader);
        Class<?> sheetStyleClass = Class.forName("com.x.ui.common.sheets.b", false, loader);
        Constructor<?> sheetItemCtor = sheetItemClass.getDeclaredConstructor(
                Object.class, iconType, textIdBase, textIdBase, boolean.class, sheetStyleClass, int.class);
        Constructor<?> stringTextCtor = Class.forName("com.x.models.bd", false, loader).getDeclaredConstructor(String.class);
        Constructor<?> resTextCtor = Class.forName("com.x.models.hd", false, loader).getDeclaredConstructor(int.class);

        Class<?> mediaMenuBase = Class.forName("com.x.urt.items.post.media.p", false, loader);
        Class<?> mediaSaveMenu = Class.forName("com.x.urt.items.post.media.g", false, loader);
        Class<?> mediaSave4kMenu = Class.forName("com.x.urt.items.post.media.f", false, loader);
        Constructor<?> mediaSaveMenuCtor = mediaSaveMenu.getDeclaredConstructor(mediaBase);
        Method mediaSaveMenuGetMedia = mediaSaveMenu.getDeclaredMethod("a");
        Method toImmutableList = Class.forName("com.x.urt.items.post.media.f1", false, loader).getDeclaredMethod("n", Iterable.class);

        // Resolve every symbol before installing any hooks. Nothing changes on a mismatch.
        XposedBridge.hookMethod(premium, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (scope.active()) {
                    param.setResult(true);
                    log("download eligibility applied");
                }
            }
        });

        XposedBridge.hookMethod(playerDispatch, tapHook(
                action -> downloadEvent.isInstance(action),
                "player"
        ));

        XposedBridge.hookMethod(postDispatch, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length == 0 || param.args[0] == null) return;
                    Object action = param.args[0];

                    // Capture post media list when user long-presses any media in a post
                    if (longPressEvent.isInstance(action)) {
                        activePostMedia = extractPostMedia(
                                param.thisObject,
                                postRecordField,
                                postCoreField,
                                postMediaListMethod,
                                sheetStateField,
                                imageModel,
                                imageUrlField,
                                videoModel,
                                gifModel
                        );
                        return;
                    }

                    if (!saveAction.isInstance(action)) return;
                    Object item = media.get(action);
                    if (videoModel.isInstance(item) || gifModel.isInstance(item)) {
                        int previous = scope.enter();
                        param.setObjectExtra(TOKEN, previous);
                        log("download tap=long-press");
                    }
                } catch (Throwable error) {
                    log("TAP FAILED: " + error.getClass().getSimpleName());
                }
            }

            @Override protected void afterHookedMethod(MethodHookParam param) {
                Object previous = param.getObjectExtra(TOKEN);
                if (previous instanceof Integer) scope.restore((Integer) previous);
            }
        });

        // Inject "Download all images" (and ensure video option on mixed posts) in media long-press sheet
        XposedBridge.hookMethod(sheetRender, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    PostMediaContext ctx = activePostMedia;
                    if (ctx == null || !ctx.hasImages()) return;
                    if (!(param.args[0] instanceof List)) return;

                    List<?> originalItems = (List<?>) param.args[0];
                    if (originalItems.isEmpty()) return;

                    // Confirm this bottom sheet is the post media long-press menu
                    boolean isMediaMenu = false;
                    boolean hasVideoDownloadItem = false;
                    for (Object item : originalItems) {
                        if (!sheetItemClass.isInstance(item)) continue;
                        Object payload = sheetItemActionField.get(item);
                        if (payload instanceof DownloadAllImagesPayload) {
                            return;
                        }
                        if (mediaMenuBase.isInstance(payload)) {
                            isMediaMenu = true;
                        }
                        if (mediaSaveMenu.isInstance(payload)) {
                            Object targetMedia = mediaSaveMenuGetMedia.invoke(payload);
                            if (videoModel.isInstance(targetMedia) || gifModel.isInstance(targetMedia)) {
                                hasVideoDownloadItem = true;
                            }
                        }
                    }
                    if (!isMediaMenu) return;

                    List<Object> updatedItems = new ArrayList<>(originalItems.size() + 2);
                    int insertIndex = -1;
                    for (Object item : originalItems) {
                        Object payload = sheetItemClass.isInstance(item) ? sheetItemActionField.get(item) : null;
                        // Pure image post: ensure no Save Video item appears
                        if (!ctx.hasVideo() && mediaSaveMenu.isInstance(payload)) {
                            Object targetMedia = mediaSaveMenuGetMedia.invoke(payload);
                            if (videoModel.isInstance(targetMedia) || gifModel.isInstance(targetMedia)) {
                                continue;
                            }
                        }
                        updatedItems.add(item);
                        if (mediaSaveMenu.isInstance(payload) || mediaSave4kMenu.isInstance(payload)) {
                            insertIndex = updatedItems.size();
                        }
                    }

                    // Mixed post (images + video/GIF): ensure Save Video/GIF option also appears
                    if (ctx.hasVideo() && !hasVideoDownloadItem) {
                        Object videoPayload = mediaSaveMenuCtor.newInstance(ctx.firstVideo);
                        int resId = gifModel.isInstance(ctx.firstVideo) ? RES_SAVE_GIF : RES_SAVE_VIDEO;
                        Object videoTitle = resTextCtor.newInstance(resId);
                        Object videoSheetItem = sheetItemCtor.newInstance(
                                videoPayload, downloadIcon, videoTitle, null, false, null, 120);
                        if (insertIndex >= 0 && insertIndex <= updatedItems.size()) {
                            updatedItems.add(insertIndex, videoSheetItem);
                            insertIndex++;
                        } else {
                            updatedItems.add(0, videoSheetItem);
                            insertIndex = 1;
                        }
                    }

                    // Add localized "Download all images" option
                    DownloadAllImagesPayload allImagesPayload = new DownloadAllImagesPayload(ctx.imageUrls, ctx.sheetState);
                    Object allImagesTitle = stringTextCtor.newInstance(LocaleStrings.downloadAllImagesLabel());
                    Object allImagesSheetItem = sheetItemCtor.newInstance(
                            allImagesPayload, downloadIcon, allImagesTitle, null, false, null, 120);

                    if (insertIndex >= 0 && insertIndex <= updatedItems.size()) {
                        updatedItems.add(insertIndex, allImagesSheetItem);
                    } else {
                        updatedItems.add(0, allImagesSheetItem);
                    }

                    param.args[0] = toImmutableList.invoke(null, updatedItems);

                    // Wrap onItemSelected Function1 to handle our custom DownloadAllImagesPayload
                    Object originalCallback = param.args[1];
                    if (originalCallback != null && function1Class.isInstance(originalCallback)) {
                        Method fnInvoke = function1Class.getMethod("invoke", Object.class);
                        param.args[1] = Proxy.newProxyInstance(
                                loader,
                                new Class<?>[]{function1Class},
                                (proxy, method, args) -> {
                                    if ("invoke".equals(method.getName()) && args != null && args.length == 1) {
                                        Object tapped = args[0];
                                        Object tappedPayload = sheetItemClass.isInstance(tapped)
                                                ? sheetItemActionField.get(tapped)
                                                : tapped;
                                        if (tappedPayload instanceof DownloadAllImagesPayload) {
                                            DownloadAllImagesPayload req = (DownloadAllImagesPayload) tappedPayload;
                                            dismissSheet(req.sheetState, stateSetValueMethod);
                                            log("download tap=all-images count=" + req.imageUrls.size());
                                            ImageBatchDownloader.downloadAll(hostContext, req.imageUrls);
                                            return unitInstance;
                                        }
                                        return fnInvoke.invoke(originalCallback, tapped);
                                    }
                                    return method.invoke(originalCallback, args);
                                }
                        );
                    }
                } catch (Throwable error) {
                    log("MENU HOOK FAILED: " + error.getClass().getSimpleName());
                }
            }
        });

        log("READY: player + long-press + all-images; exact adapter " + HostVersion.NAME);
    }

    private static void dismissSheet(Object sheetState, Method setValueMethod) {
        if (sheetState == null) return;
        try {
            setValueMethod.invoke(sheetState, new Object[]{null});
        } catch (Throwable ignored) {
        }
    }

    private static PostMediaContext extractPostMedia(
            Object m5Instance,
            Field postRecordField,
            Field postCoreField,
            Method postMediaListMethod,
            Field sheetStateField,
            Class<?> imageModel,
            Field imageUrlField,
            Class<?> videoModel,
            Class<?> gifModel
    ) {
        try {
            Object r1 = postRecordField.get(m5Instance);
            if (r1 == null) return null;
            Object v5 = postCoreField.get(r1);
            if (v5 == null) return null;
            Object mediaObj = postMediaListMethod.invoke(v5);
            if (!(mediaObj instanceof Iterable)) return null;

            List<String> imageUrls = new ArrayList<>();
            Object firstVideo = null;
            for (Object item : (Iterable<?>) mediaObj) {
                if (imageModel.isInstance(item)) {
                    Object urlObj = imageUrlField.get(item);
                    if (urlObj instanceof String && !((String) urlObj).isEmpty()) {
                        imageUrls.add((String) urlObj);
                    }
                } else if (firstVideo == null && (videoModel.isInstance(item) || gifModel.isInstance(item))) {
                    firstVideo = item;
                }
            }
            Object sheetState = sheetStateField.get(m5Instance);
            return new PostMediaContext(Collections.unmodifiableList(imageUrls), firstVideo, sheetState);
        } catch (Throwable error) {
            log("EXTRACT MEDIA FAILED: " + error.getClass().getSimpleName());
            return null;
        }
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

    private static final class PostMediaContext {
        final List<String> imageUrls;
        final Object firstVideo;
        final Object sheetState;

        PostMediaContext(List<String> imageUrls, Object firstVideo, Object sheetState) {
            this.imageUrls = imageUrls;
            this.firstVideo = firstVideo;
            this.sheetState = sheetState;
        }

        boolean hasImages() { return !imageUrls.isEmpty(); }
        boolean hasVideo() { return firstVideo != null; }
    }

    private static final class DownloadAllImagesPayload {
        final List<String> imageUrls;
        final Object sheetState;

        DownloadAllImagesPayload(List<String> imageUrls, Object sheetState) {
            this.imageUrls = imageUrls;
            this.sheetState = sheetState;
        }
    }
}
