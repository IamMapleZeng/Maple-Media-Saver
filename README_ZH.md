# X-TG-YT-Media-Saver

**简体中文** | [English](README_EN.md)

版本 **v1.3.0**，在现有 XTGMediaSaver 上增量升级。应用包名改为 `io.github.iammaplezeng.xtgytmediasaver`，保留 Git 历史、原配置键和下载路径。包名变更属于新应用安装，需通过同签名旧包迁移桥导入设置，并在 LSPosed 重新启用新模块及作用域；旧包数据不清除。

新增 **yt-dlp 2026.11.23 + QuickJS + FFmpeg** 高清下载引擎：查询视频实际提供的清晰度，分别下载所选视频和音频，并无转码合并为 MP4。支持可用的 AVC、HEVC、VP9、AV1 格式，最高列出 4320p；这不代表每个视频都提供这些画质。引擎运行于本应用后台服务，不进入 X/Telegram/YouTube 宿主进程。本次 APK 包含 arm64-v8a 原生库，体积约 66 MiB，适用于目标小米手机。

**当前真机结果：引擎启动成功，但匿名查询仍遇到登录/真人验证，所测视频仍回退到 360p。尚未验证高清实际保存。** 新增可选的“导入登录 Cookie”：在浏览器正常登录并完成验证后，导入自己的 Netscape 格式 cookies.txt，再重新分享或点击重试。只保留 youtube.com Cookie，存于应用私有且排除备份的目录，可清除；不自动读取其他应用的登录数据。导入不保证解析或下载成功，说明见 [高清引擎](docs/YTDLP.md)。

YouTube 分享菜单顺序为 **Copy link → 下载视频 → Remix**。下载入口使用正常文字和图标颜色；等待格式查询期间不再变灰，仅拦截重复点击。菜单关闭后不再自动弹出过期查询的确认窗。

新增 YouTube“去除视频广告”独立开关（新配置默认开启，保留用户手动关闭的设置），切换后重启 YouTube。真机已观察到播放器广告位过滤和正常播放；暂不覆盖首页/推荐的赞助卡片或视频内植入广告。YouTube 卡片与 X、Telegram 样式统一，不再提供“打开 YouTube”按钮。

此前版本已修复宿主缓存、客户端序列化字段及异步任务清理问题，停止旧 VR 自动请求。v1.3.0 在此基础上增加独立高清引擎，保留原生低清选项和原有下载路径。详见 [验证记录](VALIDATION.md)。

v1.2.4 优先使用官方 YouTube 已取得的播放响应：在官方 App 正常登录或完成其要求的验证、播放视频，再点 **分享 → Download video / 下载视频**。响应按视频 ID 精确关联，仅在内存缓存最多 12 个视频、10 分钟，不读取或传递账号 cookie、密码或授权头。已在目标手机显示来自播放器的 360p MP4 选项，实际保存由用户测试，尚不声明下载已通过验收。可用清晰度以播放器提供的直接 AVC/AAC MP4 为限，不一定包含所有播放画质。

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

只处理用户已获得平台及权利人许可的公开、非付费、无年龄限制内容，逐次确认后才能开始媒体传输。yt-dlp 与公共解析分支检查公开性、年龄、付费和直播状态；原生响应分支检查成功播放状态、直播标志、DRM 许可及格式，其余资格由用户确认，因为原生响应未提供已验证映射的全部资格字段。所有分支均在保存前检查 PSSH 与加密样本并拒绝加密媒体；不提供 DRM、付费或访问控制绕过。原生分支继续使用 AVC/AAC，yt-dlp 分支增加其他已列出的无保护 MP4 视频编码，不进行耗时的视频转码。

保留 NewPipeExtractor v0.27.1 兼容解析。网络、平台反自动化检查或接口变化可能导致失败；失败时提示原因并提供重试。官方 App 的登录会话不会自动成为 yt-dlp 的浏览器 Cookie。用户可主动导入自己的会话，但平台验证、令牌及媒体地址限制仍可能使高清不可用。

## 权限与兼容

新增网络、前台数据同步服务和通知权限，没有统计上传、账号读取或广泛存储权限。Android 10+ 使用 MediaStore 保存本应用创建的媒体。`xtg_prefs` 原配置、默认值和图片路径不变；YouTube 使用新配置键及独立下载结果记录。新包沿用原签名，但包名变更无法直接覆盖旧包；同签名迁移桥导入开关、语言和最近下载记录，保留旧应用数据及本地文件。Cookie 不自动跨包迁移。后续相同新包名的版本可覆盖升级。

## 构建与验证

JDK 17、SDK 35、Gradle 8.10.2、AGP 8.7.3、Kotlin 2.1.0；在本机 local.properties 配置 sdk.dir：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

Release 为 `app/build/outputs/apk/release/app-release-unsigned.apk`，需使用原签名签名后安装。私钥和本机路径不进入仓库。

测试包内提供生成合成音视频的真机测试，安装后可执行：

```powershell
adb shell am instrument -w io.github.iammaplezeng.xtgytmediasaver.test/io.github.iammaplezeng.xtgytmediasaver.SmokeInstrumentation
```

它只使用合成媒体，验证语言、入口、启动、合并、MediaStore 可读性并清理测试文件。按本次用户要求，实际下载由用户自行测试；不能把编译成功当作宿主 Hook 和网络下载验收通过。详见 [迁移清单](docs/MIGRATION.md) 和 [验证记录](VALIDATION.md)。

## 源码许可

本项目按 **GPL-3.0-or-later** 提供源码。包含 NewPipeExtractor、youtubedl-android、yt-dlp 及运行时依赖；准确版本、上游源码与构建说明见 [LICENSE](LICENSE)、[依赖源码](docs/SOURCES.md) 和 [第三方说明](THIRD_PARTY.md)。第三方 Java/Python/C 依赖不属于自维护 Kotlin 业务源码迁移范围。
