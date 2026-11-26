# 版本验证记录

## v1.3.0 yt-dlp、包名迁移与默认去广告

- 新增 YtDlpRuntime、YtDlpFormats、YtDlpEngine、YouTubeCookieFile、YouTubeCookies；IO 协程执行元数据查询/传输，固定 yt-dlp 2026.11.23（校验 SHA-256）、QuickJS 与 FFmpeg 7.1.1。独立应用进程，不向宿主加载原生运行时。选择精确视频/音频 format ID，无转码合并为 MP4；MediaExtractor 检查容器与加密样本后才发布到 MediaStore。
- 清晰度查询保留原生可用选项；下载采用不可变选择快照，避免异步列表更新改变用户的选项。进程组负责取消及超时清理。可选文件导入只保留 youtube.com 的 Netscape Cookie，存入私有且不备份的目录，支持清除，不自动读取宿主账号或记录凭据。
- 真机在旧包和新包中均成功执行 yt-dlp --version、QuickJS JS 求值及 FFmpeg -version，日志确认三个运行时就绪。当前 YouTube 真实分享视频的匿名查询仍返回 VERIFICATION，确认窗实际回退到 360p；没有有效 Cookie 实测，**尚不能声明高清下载成功**。没有代替用户勾选下载许可或传输真实网络媒体。
- 用户指定应用 ID 改为 `io.github.iammaplezeng.xtgytmediasaver`。源码 package/import、目录、namespace、Manifest 组件、ProGuard、测试与 xposed_init 一致迁移，入口为 `io.github.iammaplezeng.xtgytmediasaver.XTGMediaSaverModule`。全部自维护业务源码仍为 Kotlin，没有重复 Java/Kotlin 实现。
- 包名变更需新应用安装。先以原签名覆盖旧包为迁移桥（旧包 xposedmodule=false），再安装新包。Provider 核对新包 UID 和相同签名，新包也核对旧包签名；ADB shell 未授权调用实际返回 SecurityException。新包启动日志实际出现 `[MIGRATION] previous package settings imported`，旧包未卸载/清除，原媒体路径不变。具体见 docs/PACKAGE_MIGRATION.md。
- YouTube 去广告缺省值改为 true，UI 和 Hook 一致，显式保存的 false 保留。XMediaHook、TelegramMediaHook、DownloadScope、HostVersions 已与原提交规范化比对：除包名外一致，没有为集成下载引擎改写 X/TG 行为。
- 43 项 JVM 测试通过，失败/错误 0；包含高清格式（1080p/4K）、双轨精确选择、DRM/资格限制、域名/ID 校验、选择快照和 Cookie 格式验证。Release、lintDebug、assembleDebugAndroidTest 构建通过；最终 lint 为 **0 errors / 30 warnings**。构建曾遇到 Windows classes.dex 文件占用，停止当前 Gradle daemon 后干净构建解决，没有跳过检查。
- 最终 APK 签名验证通过，证书 SHA-256 沿用 `63928b7f98bdf60e3be741f8807bd6ae4370e2451ccb88f2b6f7e8446f6f2c62`。adb install -r 新包成功，MainActivity 冷启动成功；设备与 aapt 均确认 1.3.0 / 10300、新应用 ID、arm64-v8a。APK 大小 68,774,027 字节，SHA-256 `33a0e3293048eed86906d27526b0a33bf2232826d6bc3429b6d47ec436300e22`。
- 用户回复“我稍后自己测试”，因此未继续操作手机。新包 LSPosed 启用/作用域和新入口加载待用户设置后验证；实际高清下载、合并文件的音画同步/画质/播放、Cookie 会话有效性和新包 X/TG 保存回归均待实测。AndroidTest APK 仅编译，没有重新尝试安装此前被手机策略拒绝的测试包。

以下为历史版本记录，旧包名、默认值和限制描述属于相应历史版本。

## v1.2.8 分享入口顺序和颜色

- 最终按用户要求放置为 Copy link → 下载视频 → Remix，新增 CopyLinkActionPlacement.kt，以原生 Copy link 视图为锚点，为新增操作留出空间，保留后续原生操作的点击处理，菜单分离时恢复原布局。
- 采用正常启用状态的文字颜色，移除等待期间的禁用和降低透明度操作；用独立状态拦截重复点击，菜单关闭或分享视频改变后不再弹出过期的确认窗。
- Release 和 lintDebug 构建成功。APK 签名验证通过，已覆盖安装 1.2.8 / 10208 并重启 YouTube；真机日志出现 `native download row placed after Copy link`。用户已确认点击正常。
- 位置调整前的 v1.2.7 已在设备看到菜单底部完整可见的正常颜色入口；最终采用 v1.2.8 的 Copy link 后位置。
- 本次仅修改分享菜单布局、交互和版本文档。高清下载仍沿用 v1.2.6 的未解决状态，不能把本次菜单验收当作高清下载验收。

## v1.2.6 视频广告开关与高清请求修正

- ADB 确认 YouTube 21.47.483 / 1561317016。新增广告 Hook 位于精确版本保护后，独立异常隔离，未修改 X/TG 源码。
- 新增配置 yt_ad_block_enabled，默认 false；三套语言资源和 YouTube 卡片显示独立开关。手机实际开启并重启后，日志显示 `[YT ADS] READY ... enabled=true`，实际播放时出现 `player ad slots filtered`，截图确认正常播放。只复制响应并清除已验证的 PlayerResponse.n 广告位列表，不更改会员状态或原始媒体字段。Sponsored 推荐卡片仍可见，不声称全广告过滤。
- 修复模块自建请求沿用宿主缓存策略、客户端字符串字段 108 误写为消息字段 109、CompletableFuture 完成过快时清理竞争及潜在不可变请求头 Map 写入问题。实际宿主 proto 自检通过，观察到备用请求 `serialized ... changed=true`，但旧 VR 路径仍失败；最终已停止自动调用，更新公共解析客户端配置。请求成功不等于高清下载成功。
- 最终真机分享入口、视频标题及低清格式显示正常；测试视频实际显示 `320p · MP4`（画面比例对应的尺寸），65 个 adaptive 格式没有直接地址，adapUrls=0、audio=false。补充解析最终为 SignInConfirmNotBotException / LOGIN_REQUIRED。**高清仍未解决，未验证到 720p/1080p/4K 下载。**
- 33 项 JVM 单元测试通过，含新增广告列表复制、原对象与媒体字段保留测试；Release 和 lintDebug 成功，0 errors / 32 warnings。未重试此前设备拒绝的 AndroidTest 包安装。
- 最终 APK 签名验证通过，已 adb install -r 覆盖安装 1.2.6 / 10206 并重新启动 YouTube；本机去广告开关保持开启。未代替用户确认下载许可或执行网络媒体下载。
- 新增 PlayerAdSlots.kt、YouTubeAdsHook.kt、PlayerAdSlotsTest.kt 和三套 ads_strings.xml；其他改动为 YouTube 卡片、配置及高清请求链路的增量修正。

## v1.2.4 官方播放器响应与原生会话

- ADB 确认 YouTube 仍为 21.47.483 / 1561317016。基于该 APK 的 `allq(bhsp)` 构造器观察官方成功播放响应，未修改宿主返回值、未执行额外登录请求、未读取 cookie / OAuth / 密码。
- 真机日志观察到 34 个原生格式、1 个可用 AVC MP4 清晰度；原生分享实体的视频 ID 与播放响应精确匹配后，浮窗实际显示当前视频标题、`360p · MP4` 及“正在使用 YouTube 当前播放会话”。这次没有再触发匿名解析失败。
- 仅在内存保存最多 12 个响应、10 分钟，跨进程字段限定为视频链接、标题及可用媒体信息。再次校验 HTTPS Googlevideo 播放路径、字段数量、总长度、时间和视频 ID，不记录签名媒体 URL。
- 仍要求逐次确认公开、非付费、无年龄限制和下载许可。原生响应资格字段尚未全部映射，不能声称已自动验证所有这些属性；已验证原生直播分类标志并拒绝额外许可列表，保存前沿用 PSSH / 加密样本拒绝逻辑，不实现 DRM 解密、付费或访问控制绕过。
- 新增 NativePlaybackCapture.kt、NativePlaybackPayload.kt、YouTubeQuickActivity.kt、NativePlaybackPayloadTest.kt；增量调整 YouTubeMenuHook、YouTubeShareActivity、YouTubeSession、Manifest 和三套字符串资源。X/TG 下载和 Hook 文件无变更。
- 28 项 JVM 单元测试通过，Release、lintDebug、AndroidTest APK 编译通过；lint 为 0 errors / 29 warnings。修复取消中的匿名请求覆盖新原生会话状态的竞争。
- 真机 Bundle 边界检查代码已编译；安装 AndroidTest 包被设备以 INSTALL_FAILED_USER_RESTRICTED 拒绝，未执行，未改动设备的安装安全设置。
- 最终 APK v3 签名验证通过，adb install -r 成功覆盖安装，设备查询版本 1.2.4 / 10204。YouTube 已重启并观察到新版 native player observer 和 native share adapter READY。
- 检查中发现高分享面板底部入口裁切，最终改为插入宿主列表前；发现动态设置透明主题仍造成黑色背景，最终增加 Manifest 声明透明主题的独立 QuickActivity。最终这两项视觉调整仍待用户复核，不能表述为已真机验收。
- 未勾选用户的下载许可、未发起实际网络媒体下载。当前只验证到原生播放响应 → 分享 → 对应视频清晰度展示；媒体 HTTP、保存和播放结果仍由用户测试。播放器不提供直接 MP4 时不保证可用或具备高清选项。

## v1.2.3 原生 YouTube 菜单、浮动确认与错误分类

- 真机日志已确认 `[YT] READY`、`share video captured`、`native download row added; parent=avpq` 和 `native download tap`，UI 层级实际存在 Download video。当前分享视频直接传入，无剪贴板读写。
- 实际分享实体为 8420 字符 / 6310 字节，包含 field 1 的 11 字节视频 ID、field 12 的 6291 字节元数据和 field 20 varint。已修正原先 4096 字符限制，添加同尺寸回归测试。
- 26 项单元测试通过；Release 构建和完整 lint 通过，0 errors / 29 warnings。
- 浮动确认 UI 已在设备显示并自动检查公开元数据；随后修正 native 点击强制 NEW_TASK 导致的黑背景，改为解包宿主 Activity 在 YouTube 任务中打开。最终任务归属/背景效果待用户复核，不表述为已完成最终视觉验收。
- APK 已成功覆盖安装（1.2.3 / 10203），v3 签名验证通过；YouTube 已重启载入新版。
- 本次实际解析失败为 `SignInConfirmNotBotException` / `LOGIN_REQUIRED`，平台要求登录确认非机器人。已单独显示“登录或真人验证”提示，不再只显示笼统网络失败，也未实现任何登录、cookie 读取或验证绕过。
- 实际网络媒体下载仍未通过验收，不能声称下载故障已经修复。X/TG 的原下载实现保持不变。

## v1.2.1 图标与应用语言

- 图标包含 X、Telegram、YouTube 与下载动作，使用 Android 原生矢量与自适应图标。
- 新增应用语言卡片及选择菜单，AppCompatDelegate 对接系统应用语言，兼容自动保存。
- 19 项单元测试全部通过；Release 构建、lintDebug 成功（0 errors / 26 warnings）。
- APK 签名验证通过，已通过 adb install -r 覆盖安装到同一手机，版本 1.2.1 / 10201。
- 在手机实际点击菜单，中文切换后系统应用语言为 zh-Hans、界面显示中文；强行停止并重开后仍保留中文。
- 实际点击 English 后系统应用语言为 en、界面显示英文；点击 Follow system 后恢复空应用语言列表及系统语言界面。测试结束恢复用户原有的跟随系统状态。
- 原 X/TG/YouTube 下载实现未改动；本次没有操作视频下载。

## v1.2.0 历史验证记录

日期：2026-11-26（Asia/Shanghai）。仓库基线为 `45856eb`，保留其完整提交历史；origin 为 `https://github.com/IamMapleZeng/X-TG-YT-Media-Saver.git`。

## 已完成

- 原始 Java 基线：8 项测试与 Release 构建通过。
- Kotlin 环境及分阶段迁移：工具/配置、图片下载器、Hook、UI 分别编译并运行原测试后再继续。
- 最终单元测试：19 项，失败 0、错误 0、跳过 0。含原有 8 项、3 项 JVM 入口/配置契约、8 项链接/下载策略测试。
- `assembleRelease`、`lintDebug`、`assembleDebugAndroidTest` 均成功。完整 lint 为 0 errors / 32 warnings；主要是沿用的非公开 attach Hook、旧 target/dependency 提醒、布局及兼容资源建议，没有跳过或屏蔽 lint 错误。
- 最终资源变更后重新执行 Release 构建及 lintVital，成功。
- JVM 检查：入口仍为 public 无参 `XTGMediaSaverModule`，实现 IXposedHookLoadPackage；`ModuleStatus.isActivated()` 为原名称的 JVM static boolean 方法。
- 应用 ID 不变：`io.github.iammaplezeng.xtgmediasaver`；versionName 1.2.0，versionCode 10200。
- 签名证书与已安装 v1.1.0 相同，APK v3 签名验证通过；通过 `adb install -r` 成功覆盖安装并 `am start` 启动 MainActivity，没有卸载或清除数据。
- 安装后读取本应用进程日志，实际观察到 `[SELF] READY: Kotlin activation hook installed`。说明 Kotlin 模块入口已被 LSPosed 加载并安装自进程 Hook，而非仅编译通过。
- 自维护 Java 源码文件数为 0；第三方 API jar/依赖和自动生成 Java 不属于迁移范围。

## 真机环境

设备：小米 15 Ultra（ADB 型号 25019PNF3C），HyperOS 4 / LSPosed 为用户提供的环境。

YouTube 由 ADB 查询：`com.google.android.youtube`，21.47.483 / 1561317016。Telegram 由 ADB 查询：13.3.1 / 73461。没有假设 YouTube 版本。

## 交付 APK

文件名：`X-TG-YT-Media-Saver-v1.2.0.apk`

SHA-256：`c7d50e5c9363c64aab9b3b1245a4dc4ef858a15f8b63429308a741e9af19c1f4`

签名证书 SHA-256：`63928b7f98bdf60e3be741f8807bd6ae4370e2451ccb88f2b6f7e8446f6f2c62`。沿用本机测试签名以兼容已安装版本，不公开私钥。

## 明确尚未验证

用户明确要求自行测试，因此没有操作 X/Telegram 内容、没有为测试下载网络视频，也没有执行已编译的真机合成媒体测试包。

- X/TG 的真实保存结果及宿主进程 READY/回归日志由用户验收；目标、参数、顺序、版本保护和配置按原实现保持。
- YouTube 实际分享目标排序、网络解析、视频质量及下载播放由用户验收。标准接收入口不保证出现在 YouTube 第一层内部菜单。
- 五种系统语言资源的设备切换、AVC/AAC 合并及 MediaStore 回读测试代码已提供并编译，尚未在设备执行；不能表述为这些运行测试已经通过。
- 网络或平台反自动化限制可能使 NewPipeExtractor 解析失败。只展示错误和重试，不绕过访问保护。下载权限由用户确认，不能由软件自动证明法律授权。
