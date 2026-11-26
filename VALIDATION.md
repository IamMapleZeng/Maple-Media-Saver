# 版本验证记录

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
