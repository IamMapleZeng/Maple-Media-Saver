# 对应源码

## v1.3.0 新增依赖

- youtubedl-android library/ffmpeg **0.18.1**，准确提交 `d725d5c9a18c3a99a13ee0308bf78275dc310760`，GPL-3.0：源码及其未修改的原生包在 https://github.com/yausername/youtubedl-android/tree/0.18.1 ，完整存档 https://github.com/yausername/youtubedl-android/archive/refs/tags/0.18.1.tar.gz 。本项目从 Maven Central 使用 `io.github.junkfood02.youtubedl-android` 发布物，不依赖本机另装 Python/FFmpeg。
- 原生 Python/FFmpeg 的上游构建材料为同一 tag 的 [BUILD_PYTHON.md](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_PYTHON.md)、[BUILD_FFMPEG.md](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md)，引用 Termux packages 的源码、补丁和构建系统 https://github.com/termux/termux-packages 。本次复用上游预编译运行时，没有重新构建或宣称这些旧说明能逐位重现其发布二进制。真机 FFmpeg 报告版本 7.1.1（源码 https://github.com/FFmpeg/FFmpeg/tree/n7.1.1 ）；Python 包为 3.12，QuickJS 来自同一 Android 库原生包。各运行时及附属库按其上游许可使用。
- 官方 **yt-dlp 2026.11.23**：源码/许可/构建脚本 https://github.com/yt-dlp/yt-dlp/tree/2026.11.23 ，完整源码 https://github.com/yt-dlp/yt-dlp/archive/refs/tags/2026.11.23.tar.gz 。按其 UNLICENSE 与第三方许可说明使用，包括 EJS 依赖。
- `app/src/main/res/raw/ytdlp` 为官方 release 中的 `yt-dlp` zipapp 原样复制；SHA-256 `e8665ed64d6b9172773e812f891fb707a0cb1ee8d9ef4fc1acb00cabee4c5256`，下载出处 https://github.com/yt-dlp/yt-dlp/releases/tag/2026.11.23 。APK 的第三方许可集合来自此 zipapp 与 Android 库发布物，不使用未知远程脚本更新。
- zipapp 包含 **yt-dlp-ejs 0.8.0** 的 Python 与 JS 包，版本从其 `_version.py` 核对；准确提交 `4fb477f4af56880cfd324c48bd4294a2d2294e50`，源码/许可见 https://github.com/yt-dlp/ejs/tree/0.8.0 。

自维护源码完整存于本仓库；第三方准确源码与构建材料以上述固定版本的上游存档提供。源码交付不包含任何用户 Cookie、账号或签名密钥。

v1.2.6 公共客户端配置参考 yt-dlp 上游的配置事实（未引入其 Python 业务源码或运行时）：https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/extractor/youtube/_base.py 。已停止自动请求旧 VR 路径；这些配置不保证平台始终返回可下载格式。

交付源码包含所有自维护 Kotlin 源码、资源、构建脚本、单元测试、真机测试和本次变更文档。保留原提交历史。不包含用户账号、宿主 APK、本机 SDK 和签名私钥。

NewPipeExtractor v0.27.1 未修改，GPL-3.0-or-later，准确提交 `6ce85c1af05bbb854bae73f8936fd9f75bb8f9e1`。

- https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.27.1
- https://github.com/TeamNewPipe/NewPipeExtractor/archive/refs/tags/v0.27.1.tar.gz
- NanoJSON（Apache-2.0）：https://github.com/TeamNewPipe/nanojson/tree/e9d656ddb49a412a5a0a5d5ef20ca7ef09549996
- Jsoup（MIT）：https://github.com/jhy/jsoup/tree/jsoup-1.24.1
- Rhino（MPL-2.0）：https://github.com/mozilla/rhino/tree/Rhino1_8_1_Release
- Protobuf（BSD-3-Clause）：https://github.com/protocolbuffers/protobuf/tree/v36.2

依赖版本及构建步骤见该 tag 的 README、gradle/libs.versions.toml 和本项目 app/build.gradle。其他 AndroidX、Material、Kotlin、Coroutines、desugar_jdk_libs、JUnit 与 Xposed API 按各自上游许可使用。签名密钥不属于对应源码。
