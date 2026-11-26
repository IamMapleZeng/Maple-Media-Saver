# X-TG-YT-Media-Saver

[简体中文](README.md) | **English**

**v1.3.0 — X · Telegram · YouTube Media Downloader**

The YouTube share-menu order is **Copy link → Download video → Remix**. The download action retains its normal text and icon colors during format queries; duplicate clicks are ignored without dimming the row. Closing the menu prevents a stale query from opening its confirmation window.

YouTube now has an independent Block video ads switch, on by default for new settings; explicit user opt-outs are retained. Restart YouTube after changing it. Device testing confirmed player ad-slot filtering and normal playback; sponsored feed cards and creator sponsorships are outside its scope. The card matches X/Telegram styling and no longer includes Open YouTube.

v1.3.0 integrates yt-dlp 2026.11.23, QuickJS and FFmpeg in the companion app process. Available HD video/audio tracks are selected by exact format IDs and merged/remuxed to MP4 without video transcoding. Native low-resolution playback options and the existing X/Telegram features remain available. This APK includes arm64-v8a runtimes and is approximately 66 MiB.

**Device status: all three runtimes start successfully, but the tested anonymous request still requires sign-in/human verification and falls back to 360p. Actual HD saving is not yet verified.** Optional import/clear controls accept the user's own Netscape YouTube cookies, keep only youtube.com domains in private non-backed-up storage, and never read another app's credentials. Importing does not guarantee access. See [engine details](docs/YTDLP.md) and [validation](VALIDATION.md).

The verified YouTube 21.47.483 adapter now adds Download video directly to the native share sheet, reads the current shared video ID without clipboard operations, and opens a floating confirmation window. Enable the YouTube LSPosed scope and restart YouTube. The label follows the system language. Downloads run in the module app foreground service after explicit permission confirmation.

v1.2.4 first uses the official player's successful response for the exact shared video ID. Complete sign-in or verification in the official YouTube app, play the video, then share it. No passwords, cookies or authorization headers are read or transferred. The bounded in-memory cache expires after ten minutes. A native 360p MP4 option was observed on the target device; actual downloading is left to the user and has not passed acceptance testing. The native path offers directly supplied AVC/AAC MP4; the new yt-dlp path separately searches for HD formats. Anonymous fallback still receives LOGIN_REQUIRED / SignInConfirmNotBotException on this device.

New three-platform launcher icon with a YouTube play symbol. The home screen now includes App language: 中文 / English / Follow system, with immediate updates and persistent selection.

Incremental upgrade of XTGMediaSaver: all maintained Java business sources and tests migrated to Kotlin. The application ID is now io.github.iammaplezeng.xtgytmediasaver, with corresponding Kotlin package paths and LSPosed entry updates. X/Telegram hook behavior, preference keys and media paths remain compatible. A same-signed legacy-package bridge copies settings, language and recent download metadata without deleting old app data. Cookies are not automatically migrated. Enable the new package in LSPosed and select its host scopes after installation.

YouTube uses **Share → Download video** in the native share menu on the exact adapted version. The standard ACTION_SEND receiver remains available for compatibility. Confirm that the content is public, non-paid, without age restrictions and that platform and rights-holder download permission has been granted. AVC/AAC media is muxed without re-encoding and saved via MediaStore to `Movies/X-TG-YT-Media-Saver`. Progress, cancellation, result opening and retry are included. User-selected login cookie import is optional. No DRM, paid-access or access-control bypass is provided. The anonymous branch checks privacy, age, paid and live metadata. The native branch checks successful playback, live flags, DRM licenses and supported formats, but relies on the user's explicit eligibility confirmation because not all eligibility fields in this native response have verified mappings. Both branches reject PSSH and encrypted samples before publication; public playback alone does not establish download permission.

Exact X adapter: 12.46.1-prod.01 / 312461001. Exact Telegram adapter: 13.3.1 / 73461. The native YouTube adapter supports the connected device's 21.47.483 / 1561317016; other versions retain standard system sharing. NewPipeExtractor v0.27.1 can fail due to network, bot checks or platform changes, in which case the operation stops.

The existing Material 3 UI now groups three platforms. System resources select Simplified Chinese, Traditional Chinese or the default English name. YouTube adds Internet, foreground data sync and notification permissions, without broad storage permissions.

Build with JDK 17 / SDK 35 / Gradle 8.10.2 / AGP 8.7.3 / Kotlin 2.1.0:

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

Sign the release APK with the existing key for in-place updates. Download and host-hook acceptance testing is left to the user as requested. See [validation](VALIDATION.md), [migration](docs/MIGRATION.md), [license](LICENSE) and [dependency source](docs/SOURCES.md). The distribution is GPL-3.0-or-later with corresponding source.
