# XVideoSaver

<img src="docs/icon.png" width="112" alt="XVideoSaver icon" />

包名：`io.github.iammaplezeng.xvideosaver`

当前版本：**v1.0.1**。

[下载 APK](https://github.com/IamMapleZeng/XVideoSaver/releases/tag/v1.0.1)

此版本专门适配从用户连接的手机提取的 **X 12.46.1-prod.01，versionCode 312461001**。X 更新后版本校验不通过时不安装 Hook，需要重新分析适配。

## 实现

保留 X 原有的长按菜单和下载器。仅在以下两个下载事件同步执行期间，针对当前线程，让本地会员检查通过：

- 信息流长按菜单保存视频或 GIF：`com.x.urt.items.post.m5.invoke(Object)`，事件 `com.x.urt.items.post.y4`，媒体为 `com.x.models.y4` 或 `com.x.models.r4`。
- 视频页下载：`com.x.video.tab.n0.i(com.x.video.tab.d9)`，事件 `com.x.video.tab.j8`。

会员谓词为 `com.x.subscriptions.a0.g(): boolean`。事件执行结束（包括抛出异常）时恢复线程状态。其他事件和其他线程保持原结果。模块只在 X 主进程加载，不包含网络上传、账号读取、后台服务或额外权限。

下载地址选择、文件命名、下载进度和保存目录由此版本的 X 原生下载器决定；模块不承诺具体清晰度或保存目录。不提供独立下载器或 HLS 合并实现。

## 安装

1. 从 GitHub Releases 安装 `XVideoSaver-v1.0.1.apk`。
2. 在 LSPosed 中启用“XVideoSaver”，作用域只勾选 X（`com.twitter.android`）。
3. 在系统设置中强行停止 X，然后重新打开。
4. 打开一个允许下载的视频，长按视频，点击原来的“下载视频”；也可以在视频页的菜单中下载。
5. 检查下载完成提示，并实际播放保存的文件确认视频和声音。

不要把“版本匹配”当作 Hook 已加载证明。LSPosed 日志中应出现 `[XVideoSaver] READY`；点击时应出现 `download tap=long-press` 或 `download tap=player`，随后出现 `download eligibility applied`。模块不记录账号、视频链接和视频内容。

## 验证边界

入口、媒体字段、会员判断和后续原生下载调用通过用户手机上的实际 APK 静态分析确认。编译和单元测试不能代替真机验收。模块需要在 LSPosed 启用后，验证不再弹会员提示、下载完成以及文件可播放。只有这些均完成，才能认为适配成功。

HyperOS 4、LSP 2.2.1 为用户提供的环境信息；尚未通过运行测试验证该组合。构建使用传统 Xposed API 82，要求框架保留该接口。

## 构建

JDK 17、Android SDK 35、Gradle 8.10.2。创建本机的 `local.properties` 并设置 `sdk.dir`，运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`，需使用 Android SDK 的 `apksigner` 签名后安装。v1.0.0 发布 APK 沿用此前测试版的本机 Android Debug 签名，支持覆盖此前已安装版本；它不是新的生产签名。后续覆盖更新必须使用相同签名。私钥不包含在仓库或源码压缩包中。

`app/libs/xposed-api-82.jar` 来自 https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar，仅作 compileOnly 使用，不打包入 APK。其上游为 XposedBridge，见 THIRD_PARTY.md。
