# v1.2.0 迁移计划与基线

基线：`45856eb`，完整克隆新仓库，origin 已为 `https://github.com/IamMapleZeng/X-TG-YT-Media-Saver.git`，保留原提交历史。

1. 基线：原始 Java 工程的 8 项单元测试、Release 构建和 lintVital 通过。
2. Kotlin 环境：保留 AGP 8.7.3 / Gradle 8.10.2 / JDK 17，增加 Kotlin 2.1.0，JVM target 17。
3. 工具与配置：DownloadScope、HostVersions、ModulePrefs、ModuleStatus、LocaleStrings。
4. 下载与 Hook：ImageBatchDownloader、XMediaHook、TelegramMediaHook、XTGMediaSaverModule。先迁移图片下载器并运行原测试，再迁移 Hook。
5. UI：MainActivity；保留现有 Material 3 XML 布局和设置交互。
6. 新增独立 youtube 包：分享接收、链接校验、许可确认、解析、下载、音视频合并、进度、结果及重试。
7. 资源与文档：中英文、简繁中文名称与 UI 资源，三平台卡片，默认中文 README，版本 1.2.0。
8. 回归：构建、单元测试、Manifest/资源/入口检查、覆盖安装、启动和真实 LSPosed 加载日志。

## 必须保持的契约

以上文件已全部迁移，原 8 项测试在每个代码迁移阶段均通过。最终扩展到 19 项单元测试，Release、完整 lint 和 Android 测试 APK 构建通过。实际安装与自进程 LSPosed READY 日志已观察；按用户后续要求，下载及宿主功能由用户自行验收，详见 VALIDATION.md。

- Application ID/namespace：`io.github.iammaplezeng.xtgmediasaver`。
- 入口：`io.github.iammaplezeng.xtgmediasaver.XTGMediaSaverModule`，public 无参构造，实现 IXposedHookLoadPackage。
- 偏好：`xtg_prefs`，所有原有 key 与默认值保持不变；不清除数据。
- 图片保存路径：`Pictures/XTGMediaSaver`。
- X 的全部反射符号、构造参数、线程作用域与图片菜单注入逻辑保持等价。
- Telegram 的全部反射符号、版本保护、六个方法和消息构造器 Hook 保持等价。
- ModuleStatus.isActivated 保持 JVM static 方法，避免 Kotlin internal 名称改编。

## YouTube 方案依据

设备实际包：`com.google.android.youtube`，21.47.483 / 1561317016。其 APK 中 `agaw.R()` 创建 ACTION_SEND + text/plain，`awkf.c(...)` 等路径调用 Intent.createChooser；内部菜单由混淆类/动态 renderer 驱动，没有足够稳定的注入契约。因此选择标准 ACTION_SEND 接收目标，标签为“下载视频”，不向 YouTube 进程安装 Hook，不增加 YouTube LSPosed 作用域。不同版本只要继续支持标准文本分享即可兼容。

NewPipeExtractor 使用固定稳定版本；其解析结果不能自动证明下载许可。应用检查公开/非直播/无保护 MP4 流并要求用户确认已获得平台及权利人许可；拒绝需登录、付费、年龄/地区或 DRM 限制的内容，不提供账号 cookie 或绕过保护的流程。
