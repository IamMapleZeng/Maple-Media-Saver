# X-TG-YT-Media-Saver

[简体中文](README.md) | **English**

**v1.2.1 — X · Telegram · YouTube Media Downloader**

New three-platform launcher icon with a YouTube play symbol. The home screen now includes App language: 中文 / English / Follow system, with immediate updates and persistent selection.

Incremental upgrade of XTGMediaSaver: all maintained Java business sources and tests migrated to Kotlin. The application ID, LSPosed entry class, X/Telegram hooks, settings, defaults and X photo path remain compatible.

YouTube uses Android ACTION_SEND text sharing: **Share → More → Download video**. Confirm platform and rights-holder permission, inspect available MP4 formats, choose quality and download. AVC/AAC media is muxed without re-encoding and saved via MediaStore to `Movies/X-TG-YT-Media-Saver`. Progress, cancellation, result opening and retry are included. No YouTube process hooks, account cookies, login or restriction bypass are provided. Public playback alone does not establish download permission. Private, paid, age-restricted, live and encrypted content is rejected.

Exact X adapter: 12.46.1-prod.01 / 312461001. Exact Telegram adapter: 13.3.1 / 73461. The connected device has YouTube 21.47.483 / 1561317016; compatibility uses standard system sharing. NewPipeExtractor v0.27.1 can fail due to network, bot checks or platform changes, in which case the operation stops.

The existing Material 3 UI now groups three platforms. System resources select Simplified Chinese, Traditional Chinese or the default English name. YouTube adds Internet, foreground data sync and notification permissions, without broad storage permissions.

Build with JDK 17 / SDK 35 / Gradle 8.10.2 / AGP 8.7.3 / Kotlin 2.1.0:

```sh
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
```

Sign the release APK with the existing key for in-place updates. Download and host-hook acceptance testing is left to the user as requested. See [validation](VALIDATION.md), [migration](docs/MIGRATION.md), [license](LICENSE) and [dependency source](docs/SOURCES.md). The distribution is GPL-3.0-or-later with corresponding source.
