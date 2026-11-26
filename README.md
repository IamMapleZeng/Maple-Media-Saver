# X-TG-YT-Media-Saver

**简体中文** | [English](README_EN.md)

版本 **v1.2.3**，在现有 XTGMediaSaver 上增量升级。应用包名保持 `io.github.iammaplezeng.xtgmediasaver`，保留原 Git 历史、配置键、用户数据与下载路径。

v1.2.2–1.2.3 新增 YouTube 原生分享面板内的“下载视频”，直接读取当前分享的视频 ID，不调用剪贴板。菜单文字跟随系统语言；点击后使用浮动确认窗，解析公开元数据、确认许可和清晰度后启动后台下载，随后关闭确认窗。需要在 LSPosed 作用域新增勾选 YouTube，并重启 YouTube。

v1.2.1 重做三平台图标，加入 YouTube 红色播放元素；首页顶部新增“应用语言”，支持中文、English、跟随系统。选择立即生效并保存，不修改手机系统语言或 X/Telegram 宿主设置。

## 功能与使用

- **X**：保留原生视频/GIF 下载、全部图片批量保存、混合媒体菜单及设置；图片仍保存到 `Pictures/XTGMediaSaver`。
- **Telegram**：保留原有消息菜单、PhotoViewer、六个方法与消息构造器 Hook、版本检查及设置。
- **YouTube**：视频点击 **分享 → 下载视频**，在浮动确认窗中选择可用 MP4 清晰度并确认许可后下载，无需复制粘贴。支持通知进度、AVC/AAC 合并、取消和失败重试；文件保存到 `Movies/X-TG-YT-Media-Saver`。

X / Telegram 继续保留原作用域。YouTube 21.47.483 使用新增的精确版本原生分享菜单 Hook，下载器在本应用后台服务中执行；标准 ACTION_SEND 接收作为兼容入口保留。

| 平台 | 实际适配/观察版本 | 兼容策略 |
| --- | --- | --- |
| X / com.twitter.android | 12.46.1-prod.01 / 312461001 | 原精确版本保护 |
| Telegram / org.telegram.messenger | 13.3.1 / 73461 | 原精确版本保护 |
| YouTube / com.google.android.youtube | 21.47.483 / 1561317016（ADB 查询） | 原生分享菜单精确适配；其他版本不安装此 Hook |

## Kotlin 与多语言

全部 10 个自维护业务 Java 文件及原测试文件已逐个迁移为 Kotlin，没有重复 Java 实现。保留 `XTGMediaSaverModule` 入口、public 无参构造、static 激活方法、原 Hook 目标与顺序。界面沿用 Material 3，增加第三个平台卡片。应用名和界面通过 Android 字符串资源随系统语言选择：

- 简体中文：**X、TG、YT媒体下载器**。
- 繁体中文：**X、TG、YT媒體下載器**。
- 其他语言：**X TG YT Media Saver**。

## YouTube 下载许可与限制

只处理用户已获得平台及权利人许可的内容。公开观看不等于下载授权，软件不能自动证明用户的法律授权，因此需要逐次确认。拒绝非公开、付费/会员、年龄限制、直播和加密媒体；不提供账号 cookie、登录或保护绕过。仅保存无保护 AVC 视频和 AAC 音频组成的 MP4，不强行转换其他编码/流协议。

解析器固定 NewPipeExtractor v0.27.1。网络、平台反自动化检查或接口变化可能导致失败；失败时停止并提供重试，不进行限制绕过。本次设备实际返回 LOGIN_REQUIRED / SignInConfirmNotBotException，要求登录确认非机器人；当前匿名解析因此不能完成下载。这个问题不应被描述为已经修复或下载已验收。

## 权限与兼容

新增网络、前台数据同步服务和通知权限，没有统计上传、账号读取或广泛存储权限。Android 10+ 使用 MediaStore 保存本应用创建的媒体。`xtg_prefs` 原配置、默认值和图片路径不变；YouTube 使用新配置键及独立下载结果记录。可用原签名直接覆盖升级，不应卸载或清除数据。

## 构建与验证

JDK 17、SDK 35、Gradle 8.10.2、AGP 8.7.3、Kotlin 2.1.0；在本机 local.properties 配置 sdk.dir：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

Release 为 `app/build/outputs/apk/release/app-release-unsigned.apk`，需使用原签名签名后安装。私钥和本机路径不进入仓库。

测试包内提供生成合成音视频的真机测试，安装后可执行：

```powershell
adb shell am instrument -w io.github.iammaplezeng.xtgmediasaver.test/io.github.iammaplezeng.xtgmediasaver.SmokeInstrumentation
```

它只使用合成媒体，验证语言、入口、启动、合并、MediaStore 可读性并清理测试文件。按本次用户要求，实际下载由用户自行测试；不能把编译成功当作宿主 Hook 和网络下载验收通过。详见 [迁移清单](docs/MIGRATION.md) 和 [验证记录](VALIDATION.md)。

## 源码许可

包含 NewPipeExtractor 的发行按 **GPL-3.0-or-later** 提供完整对应源码。见 [LICENSE](LICENSE)、[依赖源码](docs/SOURCES.md) 和 [第三方说明](THIRD_PARTY.md)。第三方 Java 库不属于自维护业务源码迁移范围。
