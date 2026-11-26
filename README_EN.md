# X-TG-YT-Media-Saver

[简体中文](README.md) | **English**

**v1.2.3 — X · Telegram · YouTube Media Downloader**

The verified YouTube 21.47.483 adapter now adds Download video directly to the native share sheet, reads the current shared video ID without clipboard operations, and opens a floating confirmation window. Enable the YouTube LSPosed scope and restart YouTube. The label follows the system language. Downloads run in the module app foreground service after explicit permission confirmation.

Current device testing received LOGIN_REQUIRED / SignInConfirmNotBotException from YouTube. Anonymous extraction is blocked by human verification; no bypass is provided and actual video downloading has not passed acceptance testing.

New three-platform launcher icon with a YouTube play symbol. The home screen now includes App language: 中文 / English / Follow system, with immediate updates and persistent selection.

Incremental upgrade of XTGMediaSaver: all maintained Java business sources and tests migrated to Kotlin. The application ID, LSPosed entry class, X/Telegram hooks, settings, defaults and X photo path remain compatible.

YouTube uses **Share → Download video** in the native share menu on the exact adapted version. The standard ACTION_SEND receiver remains available for compatibility. Confirm platform and rights-holder permission and select MP4 quality before downloading. AVC/AAC media is muxed without re-encoding and saved via MediaStore to `Movies/X-TG-YT-Media-Saver`. Progress, cancellation, result opening and retry are included. No account cookies, login or restriction bypass is provided. Public playback alone does not establish download permission. Private, paid, age-restricted, live and encrypted content is rejected.

Exact X adapter: 12.46.1-prod.01 / 312461001. Exact Telegram adapter: 13.3.1 / 73461. The connected device has YouTube 21.47.483 / 1561317016; compatibility uses standard system sharing. NewPipeExtractor v0.27.1 can fail due to network, bot checks or platform changes, in which case the operation stops.

The existing Material 3 UI now groups three platforms. System resources select Simplified Chinese, Traditional Chinese or the default English name. YouTube adds Internet, foreground data sync and notification permissions, without broad storage permissions.

Build with JDK 17 / SDK 35 / Gradle 8.10.2 / AGP 8.7.3 / Kotlin 2.1.0:

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

Sign the release APK with the existing key for in-place updates. Download and host-hook acceptance testing is left to the user as requested. See [validation](VALIDATION.md), [migration](docs/MIGRATION.md), [license](LICENSE) and [dependency source](docs/SOURCES.md). The distribution is GPL-3.0-or-later with corresponding source.
