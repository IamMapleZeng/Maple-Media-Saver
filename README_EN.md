# Maple's Media Saver

[简体中文](README.md) | **English**

[![Release](https://img.shields.io/github/v/release/IamMapleZeng/Maple-Media-Saver)](https://github.com/IamMapleZeng/Maple-Media-Saver/releases)
[![License](https://img.shields.io/github/license/IamMapleZeng/Maple-Media-Saver)](LICENSE)

An Android / LSPosed media downloader and playback enhancement module for **X (Twitter)**, **Telegram**, **YouTube**, and **YouTube Music**.

- **App Name**: `Maple's Media Saver` / 简体中文「枫之媒体下载器」 / 繁体中文「楓之媒體下載器」
- **Package Name**: `io.github.iammaplezeng.maplemediasaver`
- **Repository**: https://github.com/IamMapleZeng/Maple-Media-Saver

---

## Features

### X (Twitter)
- Native video and GIF downloads from the timeline long-press menu and video player
- Batch save all images from multi-image and mixed-media posts (saved to `Pictures/XTGMediaSaver`)

### Telegram
- Unlock native photo/video saving in restricted groups and channels

### YouTube
- **Native Share Sheet Integration**: Tap **Share → Download video** inside YouTube to open a floating download dialog powered by the bundled **yt-dlp + QuickJS + FFmpeg** engine; downloads the selected video/audio streams and muxes them into MP4 without transcoding (up to 4K / 8K, saved to `Movies/Maple-Media-Saver`)
- **Light & Dark Theme Support**: Share-menu entry automatically adapts to light and dark themes
- **Cookie Session Import**: Supports importing Netscape `cookies.txt` files via the system file picker, as well as one-tap **Import from Chrome** in both the download dialog and main app (reads `youtube.com` cookies from Android Chrome with Root access)
- **Block Video Ads**: Filters pre-roll and mid-roll player ad slots (enabled by default)

### YouTube Music
- **Block Ads**: Filters pre-roll and mid-roll player ads (enabled by default)
- **Background Playback**: Keeps music playing when switching apps or turning off the screen (enabled by default)

### UI & Localization
- Material 3 dynamic color UI with an in-app language switcher icon in the top-right toolbar (**Follow system / 中文 / English**)

---

## Supported Host Versions

| Platform | Package | Adapted Version | Notes |
| --- | --- | --- | --- |
| X (Twitter) | `com.twitter.android` | `12.46.1-prod.01` (`312461001`) | Exact version guard |
| Telegram | `org.telegram.messenger` | `13.3.1` (`73461`) | Exact version guard |
| YouTube | `com.google.android.youtube` | `21.47.483` (`1561317016`) | Native share sheet & ad filter; other versions still work via Android system share |
| YouTube Music | `com.google.android.apps.youtube.music` | `9.47.52` (`94752240`) | Ad filter & background playback |

---

## Setup & Usage

1. Download the latest `arm64-v8a` APK from [Releases](https://github.com/IamMapleZeng/Maple-Media-Saver/releases) and install it.
2. Enable **Maple's Media Saver** in **LSPosed** and select the desired host scopes:
   - `com.twitter.android` (X)
   - `org.telegram.messenger` (Telegram)
   - `com.google.android.youtube` (YouTube)
   - `com.google.android.apps.youtube.music` (YouTube Music)
3. Force stop and reopen the target apps to activate the hooks.
4. To use **Import from Chrome**, grant Superuser permission to this app in your root manager (SukiSU Ultra / KernelSU / APatch / Magisk).

---

## Building from Source

Requires JDK 17, Android SDK 35, Gradle 8.10+, and Kotlin 2.1.0. Configure `sdk.dir` in `local.properties` and run:

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Device smoke instrumentation test:

```bash
adb shell am instrument -w io.github.iammaplezeng.maplemediasaver.test/io.github.iammaplezeng.maplemediasaver.SmokeInstrumentation
```

---

## License

Licensed under **GPL-3.0-or-later**. See [LICENSE](LICENSE), [THIRD_PARTY.md](THIRD_PARTY.md), and [docs/SOURCES.md](docs/SOURCES.md) for third-party runtime details.
