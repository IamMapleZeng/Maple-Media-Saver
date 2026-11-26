# X-TG-YT-Media-Saver

[简体中文](README.md) | **English**

**v1.2.6 — X · Telegram · YouTube Media Downloader**

YouTube now has an independent Block video ads switch, off by default for new settings. Restart YouTube after changing it. Device testing confirmed player ad-slot filtering and normal playback; sponsored feed cards and creator sponsorships are outside its scope. The card matches X/Telegram styling and no longer includes Open YouTube.

HD work fixes module-request caching, a client-protobuf field number, and in-flight cleanup, updates public client profiles, and stops repeated legacy VR requests. **HD downloading remains unresolved on the target device:** adaptive formats lack direct URLs, supplementary extraction requires human verification, and the tested selector still has one low-resolution MP4 option. See [validation](VALIDATION.md).

The verified YouTube 21.47.483 adapter now adds Download video directly to the native share sheet, reads the current shared video ID without clipboard operations, and opens a floating confirmation window. Enable the YouTube LSPosed scope and restart YouTube. The label follows the system language. Downloads run in the module app foreground service after explicit permission confirmation.

v1.2.4 first uses the official player's successful response for the exact shared video ID. Complete sign-in or verification in the official YouTube app, play the video, then share it. No passwords, cookies or authorization headers are read or transferred. The bounded in-memory cache expires after ten minutes. A native 360p MP4 option was observed on the target device; actual downloading is left to the user and has not passed acceptance testing. Only directly supplied AVC/AAC MP4 formats are offered, which may exclude higher playback qualities. Anonymous fallback still receives LOGIN_REQUIRED / SignInConfirmNotBotException on this device.

New three-platform launcher icon with a YouTube play symbol. The home screen now includes App language: 中文 / English / Follow system, with immediate updates and persistent selection.

Incremental upgrade of XTGMediaSaver: all maintained Java business sources and tests migrated to Kotlin. The application ID, LSPosed entry class, X/Telegram hooks, settings, defaults and X photo path remain compatible.

YouTube uses **Share → Download video** in the native share menu on the exact adapted version. The standard ACTION_SEND receiver remains available for compatibility. Confirm that the content is public, non-paid, without age restrictions and that platform and rights-holder download permission has been granted. AVC/AAC media is muxed without re-encoding and saved via MediaStore to `Movies/X-TG-YT-Media-Saver`. Progress, cancellation, result opening and retry are included. No account cookies, login or restriction bypass is provided. The anonymous branch checks privacy, age, paid and live metadata. The native branch checks successful playback, live flags, DRM licenses and supported formats, but relies on the user's explicit eligibility confirmation because not all eligibility fields in this native response have verified mappings. Both branches reject PSSH and encrypted samples before publication; public playback alone does not establish download permission.

Exact X adapter: 12.46.1-prod.01 / 312461001. Exact Telegram adapter: 13.3.1 / 73461. The native YouTube adapter supports the connected device's 21.47.483 / 1561317016; other versions retain standard system sharing. NewPipeExtractor v0.27.1 can fail due to network, bot checks or platform changes, in which case the operation stops.

The existing Material 3 UI now groups three platforms. System resources select Simplified Chinese, Traditional Chinese or the default English name. YouTube adds Internet, foreground data sync and notification permissions, without broad storage permissions.

Build with JDK 17 / SDK 35 / Gradle 8.10.2 / AGP 8.7.3 / Kotlin 2.1.0:

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

Sign the release APK with the existing key for in-place updates. Download and host-hook acceptance testing is left to the user as requested. See [validation](VALIDATION.md), [migration](docs/MIGRATION.md), [license](LICENSE) and [dependency source](docs/SOURCES.md). The distribution is GPL-3.0-or-later with corresponding source.
