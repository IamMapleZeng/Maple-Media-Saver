# X-TG-YT-Media-Saver

**简体中文** | [English](README_EN.md)

版本 **v1.2.6**，在现有 XTGMediaSaver 上增量升级。应用包名保持 `io.github.iammaplezeng.xtgmediasaver`，保留原 Git 历史、配置键、用户数据与下载路径。

新增 YouTube“去除视频广告”独立开关（新配置默认关闭），切换后重启 YouTube。真机已观察到播放器广告位过滤和正常播放；暂不覆盖首页/推荐的赞助卡片或视频内植入广告。YouTube 卡片与 X、Telegram 样式统一，不再提供“打开 YouTube”按钮。

高清请求已修复宿主缓存、客户端序列化字段及异步任务清理问题，更新公共解析配置并停止重复调用旧 VR 通道。**真机仍只有一个低清 MP4 选项，高清下载尚未解决**：当前高清格式不附直接地址，补充解析仍遇到真人验证。详见 [验证记录](VALIDATION.md)。

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

只处理用户已获得平台及权利人许可的公开、非付费、无年龄限制内容，逐次确认后才能开始媒体传输。匿名解析分支检查公开性、年龄、付费和直播状态；原生响应分支检查成功播放状态、直播标志、DRM 许可及格式，并由用户确认公开性、非付费、无年龄限制与下载许可，因为此原生响应未提供已验证映射的全部资格字段，不能声称这些属性均已自动判定。所有分支均在保存前检查 PSSH 与加密样本并拒绝加密媒体，不提供账号 cookie、登录或保护绕过。仅保存无保护 AVC 视频和 AAC 音频组成的 MP4，不强行转换其他编码/流协议。

匿名解析器固定 NewPipeExtractor v0.27.1。网络、平台反自动化检查或接口变化可能导致失败；失败时停止并提供重试，不进行限制绕过。本次设备匿名请求实际返回 LOGIN_REQUIRED / SignInConfirmNotBotException。v1.2.4 已增加独立的官方播放响应路径，不会把 App 的已登录身份假装成匿名请求；若播放器没有直接 MP4 地址、会话过期或没有匹配视频，则仍可能无法下载。

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
