# 枫之媒体下载器 (Maple's Media Saver)

**简体中文** | [English](README_EN.md)

[![Release](https://img.shields.io/github/v/release/IamMapleZeng/Maple-Media-Saver)](https://github.com/IamMapleZeng/Maple-Media-Saver/releases)
[![License](https://img.shields.io/github/license/IamMapleZeng/Maple-Media-Saver)](LICENSE)

一款面向 **X (Twitter)**、**Telegram**、**YouTube** 与 **YouTube Music** 的 Android / LSPosed 媒体下载与播放增强模块。

- **应用名称**：简体中文「枫之媒体下载器」 / 繁体中文「楓之媒體下載器」 / English `Maple's Media Saver`
- **应用包名**：`io.github.iammaplezeng.maplemediasaver`
- **项目地址**：https://github.com/IamMapleZeng/Maple-Media-Saver

---

## 核心功能

### X (Twitter)
- 信息流长按与视频播放页原生视频 / GIF 下载
- 多图与图文混合帖子一键保存全部图片（保存至 `Pictures/XTGMediaSaver`）

### Telegram
- 解锁禁止保存内容的群组与频道，恢复原生保存图片 / 视频选项

### YouTube
- **原生分享菜单集成**：在官方 YouTube 点击 **分享 → 下载视频**，弹出下载窗口，自动通过内置 **yt-dlp + QuickJS + FFmpeg** 引擎查询可用清晰度并无转码合并保存为 MP4（最高支持 4K / 8K，保存至 `Movies/Maple-Media-Saver`）
- **双色主题适配**：分享菜单入口自动适配浅色与深色模式
- **Cookie 会话导入**：支持通过文件选择器导入 Netscape 格式 `cookies.txt`，也支持在下载弹窗与主界面一键「从Chrome导入」手机 Chrome 中的 `youtube.com` 登录 Cookie（需 Root 权限）
- **去除视频广告**：过滤播放器片头与插播广告位（默认开启）

### YouTube Music
- **去除广告**：过滤播放器片头与插播广告位（默认开启）
- **后台播放**：息屏或切换应用后继续在后台播放音乐（默认开启）

### 界面与多语言
- Material 3 动态取色界面，主界面右上角提供语言切换图标按钮，支持 **跟随系统 / 中文 / English** 即时切换

---

## 适配宿主版本

| 平台 | 包名 | 适配版本 | 说明 |
| --- | --- | --- | --- |
| X (Twitter) | `com.twitter.android` | `12.46.1-prod.01` (`312461001`) | 精确版本校验 |
| Telegram | `org.telegram.messenger` | `13.3.1` (`73461`) | 精确版本校验 |
| YouTube | `com.google.android.youtube` | `21.47.483` (`1561317016`) | 原生分享菜单与广告过滤精确适配；其他版本仍可使用系统分享接收 |
| YouTube Music | `com.google.android.apps.youtube.music` | `9.47.52` (`94752240`) | 去广告与后台播放精确适配 |

---

## 安装与使用

1. 前往 [Releases](https://github.com/IamMapleZeng/Maple-Media-Saver/releases) 下载最新的 `arm64-v8a` APK 并安装。
2. 在 **LSPosed** 中启用「枫之媒体下载器」，并按需勾选作用域：
   - `com.twitter.android`（X）
   - `org.telegram.messenger`（Telegram）
   - `com.google.android.youtube`（YouTube）
   - `com.google.android.apps.youtube.music`（YouTube Music）
3. 强行停止并重新打开对应的宿主应用使 Hook 生效。
4. 如需使用「从Chrome导入」读取手机 Chrome 的 YouTube Cookie，请在 Root 管理器（如 SukiSU Ultra / KernelSU / APatch / Magisk）中为本应用授予超级用户权限。

---

## 本地构建

环境要求：JDK 17、Android SDK 35、Gradle 8.10+、Kotlin 2.1.0。在项目根目录配置 `local.properties`（`sdk.dir=...`）后执行：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

真机冒烟测试：

```bash
adb shell am instrument -w io.github.iammaplezeng.maplemediasaver.test/io.github.iammaplezeng.maplemediasaver.SmokeInstrumentation
```

---

## 开源许可

本项目以 **GPL-3.0-or-later** 协议开源。第三方运行时与依赖信息见 [LICENSE](LICENSE)、[THIRD_PARTY.md](THIRD_PARTY.md) 与 [docs/SOURCES.md](docs/SOURCES.md)。
