# v1.3.0 高清引擎

## 使用

1. 新包名为 io.github.iammaplezeng.xtgytmediasaver。包名变更后，在 LSPosed 启用新模块并勾选 X、Telegram、YouTube，再重启相关应用。YouTube 去广告对缺省配置默认开启，手动关闭的值保留。
2. 视频 → 分享 → Copy link 后的 Download video（菜单文字跟随系统语言）。
3. 原生选项立即可用，yt-dlp 在后台查询更多画质。选中清晰度并确认本人拥有下载许可后开始保存。
4. 若提示登录/真人验证，可在本应用 YouTube 卡片中导入自己的 Netscape 格式 cookies.txt，再回到分享菜单重试。不读取剪贴板或其他应用的登录数据。

Cookie 必须来自自己正常登录并完成验证的浏览器会话。参考 [yt-dlp 官方说明](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies)。Cookie 具有会话权限，应保护导出文件，导入后移除共享存储中的副本。仅保存 youtube.com 域，最多 256 KB / 256 条；应用私有目录且不进入备份，按请求复制到私有临时文件并在结束时删除，不在日志输出内容。“清除会话”只影响新请求，先取消进行中的任务。使用账号与 yt-dlp 交互有账号风险，导入不保证高清可用。不要把 Cookie 提交到仓库或发到聊天中。

## 实现

- youtubedl-android 0.18.1 提取 Python、QuickJS、FFmpeg 到本应用私有目录；不在宿主进程运行这些库。
- 固定官方 yt-dlp 2026.11.23 zipapp，以 SHA-256 校验，不自动下载或执行未知更新脚本。已内置 EJS，使用随包 QuickJS。
- 查询仅提取元数据，无媒体下载；校验视频 ID、公开性、年龄、直播、付费及 DRM，再以精确 format ID 组成视频+音频选择。支持的画质以实际元数据为准。
- FFmpeg 合并/重封装为 MP4，不转码；最终由 Android MediaExtractor 检查音视频轨、PSSH 和加密样本，再发布到 MediaStore。
- 独立 IO 协程和进程组，取消/超时终止 Python、JS 和 FFmpeg 子进程。进度通知沿用前台服务。
- 清晰度确认后创建不可变选择快照，避免异步格式列表更新改变用户选定的画质。
- 保留原生播放器选项与原有 X/TG 实现；平台要求验证时提示错误，不改为其他客户端绕过访问要求。
- arm64-v8a APK，minSdk 29。部分 AV1/HEVC/VP9 视频的系统解码能力取决于设备；原生 AVC/AAC 分支保持不变。

## 真机现状

小米 15 Ultra 已实际执行 yt-dlp --version、QuickJS JS 求值、FFmpeg -version，均通过。真实分享链接的匿名 yt-dlp 查询被要求登录/真人验证，回退显示 360p；尚无有效 Cookie 实测，不能声称高清下载成功。用户自行测试真实媒体保存；本次未代替用户确认许可或下载视频。JVM 测试验证了 1080p/4K 格式解析与精确双轨选择，但它不是网络可用性证明。

## 构建

沿用 JDK 17 / Gradle 8.10.2 / AGP 8.7.3 / Kotlin 2.1.0 / SDK 35。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease :app:lintDebug
```

Manifest 的 extractNativeLibs 与 legacy JNI packaging 为本地解释器的可执行路径所需。新包继续使用原签名；与旧包的设置迁移见 PACKAGE_MIGRATION.md。源码版本及第三方构建材料见 [SOURCES.md](SOURCES.md)。
