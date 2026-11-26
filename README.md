# XTG Media Saver (`XTGMediaSaver`)

<p>
  <img src="docs/icon.png" width="112" alt="XTG Media Saver icon" />
</p>

**English** | [简体中文](README_ZH.md)

Save photos and videos from X and Telegram.

Current version: **v1.1.0** — [Download APK](https://github.com/IamMapleZeng/XTGMediaSaver/releases/tag/v1.1.0)

## Supported Host Versions

This module uses exact static-analysis adapters verified against real APKs (hooks are disabled automatically if a host version does not match):

| Platform | Package | Adapted Version | Version Code |
| --- | --- | --- | --- |
| **X (Twitter)** | `com.twitter.android` | `12.46.1-prod.01` | `312461001` |
| **Telegram** | `org.telegram.messenger` | `13.3.1` | `73461` |

## Features

### X (Twitter)
- **Video & GIF Download**: Keeps X's native long-press menu and built-in downloader, unlocking local subscription eligibility during video/GIF download actions (both timeline long-press and video player menu).
- **Save All Photos in One Tap**: Adds a localized **Save All Photo** / **下载全部图片** option right below X's native `Save Photo` item when long-pressing image posts. Downloads full-resolution (`name=orig`) images in the background to `Pictures/XTGMediaSaver` via `MediaStore`.
- **Mixed Media Posts**: Posts containing both photos and videos show **both** video download and **Save All Photo** options; pure image posts only show photo save options (no Save Video).

### Telegram
- **Unlock Restricted Group / Channel Media Saving**: Bypasses client-side `noforwards` restrictions in protected groups and channels so Telegram's native **Save to Gallery** / **Save Video** / **Save to Downloads** options appear in both the message menu and full-screen `PhotoViewer`.

### App UI
- **Material 3 Design**: Supports Light/Dark themes, edge-to-edge layout, and Android 12+ Material You dynamic colors.
- **Localized App Name**: Displays `XTG Media Saver` in English and `X、TG 媒体下载器` in Simplified Chinese based on the system language.
- **Independent Dual-Platform Controls**: Displays live LSPosed activation status at the top, with separate configuration cards and feature toggles for **X (Twitter)** and **Telegram**.

## Installation & Usage

1. Download and install `XTGMediaSaver-v1.1.0.apk` from [GitHub Releases](https://github.com/IamMapleZeng/XTGMediaSaver/releases/tag/v1.1.0).
2. Enable **XTG Media Saver** in **LSPosed** and select **X** (`com.twitter.android`) and **Telegram** (`org.telegram.messenger`) in scope.
3. Force stop and reopen X or Telegram.
4. Use it directly inside the apps:
   - **X**: Long-press a video to save via the native downloader, or long-press an image post and tap **Save All Photo** to batch-save all images.
   - **Telegram**: Tap a photo/video in a restricted group/channel or open it in full-screen viewer and tap **Save to Gallery** / **Save Video**.

## Privacy & Permissions

- Runs strictly inside the scoped host processes (`com.twitter.android` and `org.telegram.messenger`).
- No analytics, no account reading, no background services, and no extra storage permissions required (`MediaStore` on Android 10+).

## Build

Requires **JDK 17**, **Android SDK 35**, and **Gradle 8.10+**. Configure `sdk.dir` in `local.properties`, then run:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

The unsigned release APK is generated at `app/build/outputs/apk/release/app-release-unsigned.apk` and should be signed with Android SDK `apksigner` before installation.

`app/libs/xposed-api-82.jar` is used as `compileOnly` and is not bundled into the APK (see [THIRD_PARTY.md](THIRD_PARTY.md)).
