# 第三方说明

v1.3.0 增加未修改的 youtubedl-android library/ffmpeg 0.18.1（GPL-3.0），包括其提供的 Python、QuickJS、FFmpeg 原生运行时。使用官方 yt-dlp 2026.11.23 zipapp 替换库内旧 payload，包含其 EJS 依赖。业务集成代码仍为 Kotlin。准确版本、校验值、上游许可及源码/构建入口见 docs/SOURCES.md。yt-dlp/FFmpeg 及其第三方依赖按各自许可使用，不把这些上游代码声明为本项目原创。

v1.2.0 集成未修改的 NewPipeExtractor v0.27.1（GPL-3.0-or-later）；包含该库的发行及自维护代码按 GPL-3.0-or-later 提供，许可全文见 LICENSE，准确依赖源码见 docs/SOURCES.md。第三方 Java 库不属于自维护 Kotlin 迁移范围。AndroidX、Material、Kotlin、Coroutines 和 desugar_jdk_libs 按各自上游许可使用。

- Xposed API 82：编译接口 jar 来自官方 https://api.xposed.info/ ，上游 https://github.com/rovo89/XposedBridge ，Apache License 2.0。只作编译依赖，APK 不包含该接口实现。
- Android Gradle Plugin、JUnit 和 Gradle 为构建/测试依赖，按各自上游许可使用。
- 调研参考了 piko 的下载功能说明 https://github.com/crimera/piko ，本项目没有复制或打包其补丁代码。最终适配依据为用户手机提取的 X 实际 APK。
- X、Twitter、HyperOS、LSPosed 商标属于各自权利人，本模块是独立第三方项目。
