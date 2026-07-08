<p align="center">
  <img src="app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp" alt="Komgarot icon" width="200" height="200">
</p>

<p align="center">
  <a href="README.md">简体中文</a> | English
</p>

# Komgarot

Komgarot is an Android comic reader client for Komga servers, built with Kotlin and Jetpack Compose.

> Feel free to suggest features in the issues. I don't use every part of Komga, so I probably won't have considered adding them yet.

## Features

- Sign in to a Komga server
- Browse libraries, series, collections, and read lists
- Series search, author search, sorting, and filters
- Book detail pages with metadata viewing and editing
- Paged reading, vertical scroll reading, reading direction, page fit, and progress seeking
- AI translation with local PaddleOCR detection, vision-model translation, cache, retry, timing, and failure details
- WebDAV ZIP backup and restore
- Long-press page actions: save, share, set as book cover, or set as series cover
- Incognito reading, app lock, and keep-screen-on mode
- Admin entry for libraries, users, server settings, maintenance, and activity

## Screenshots

<p>
  <img src="assets/screenshot/Screenshot_01.jpg" alt="Komgarot screenshot 1" width="180">
  <img src="assets/screenshot/Screenshot_02.jpg" alt="Komgarot screenshot 2" width="180">
  <img src="assets/screenshot/Screenshot_03.jpg" alt="Komgarot screenshot 3" width="180">
  <img src="assets/screenshot/Screenshot_04.jpg" alt="Komgarot screenshot 4" width="180">
  <img src="assets/screenshot/Screenshot_05.jpg" alt="Komgarot screenshot 5" width="180">
  <img src="assets/screenshot/Screenshot_06.jpg" alt="Komgarot screenshot 6" width="180">
  <img src="assets/screenshot/Screenshot_07.jpg" alt="Komgarot screenshot 7" width="180">
</p>

## AI Translation
> This feature requires your own OpenAI-compatible AI service and a vision-capable model. **Note: this feature consumes a large number of tokens.**
>
> Before using it, download the local PaddleOCR detection model in the app. Komgarot first detects page text regions locally and creates masks, then sends the current text-region crop and page context image to the vision model for translation.
>
> AI translation supports serial and parallel requests. Serial mode is the default, and parallel mode lets you configure the request count. Long-press the translation button to view per-page timing and failure details. Retrying a page reruns OCR, masking, and translation for that page.
>
> Detection and prompts can be tuned by source text type: auto, Japanese vertical manga, and Korean horizontal webtoon. Translation results are cached by book, and the cache/task screens support clearing data and purging stale entries.

### Translation Preview
<p>
  <img src="assets/screenshot/Screenshot_AI_01_origin.jpg" alt="Komgarot AI translation 1 original" width="180">
  <img src="assets/screenshot/Screenshot_AI_01_translated.jpg" alt="Komgarot AI translation 1 translated" width="180">
  <img src="assets/screenshot/Screenshot_AI_02_origin.jpg" alt="Komgarot AI translation 2 original" width="180">
  <img src="assets/screenshot/Screenshot_AI_02_translated.jpg" alt="Komgarot AI translation 2 translated" width="180">
  <img src="assets/screenshot/Screenshot_AI_03_origin.jpg" alt="Komgarot AI translation 3 original" width="180">
  <img src="assets/screenshot/Screenshot_AI_03_translated.jpg" alt="Komgarot AI translation 3 translated" width="180">
</p>

## WebDAV Backup And Restore

Komgarot creates a `Komgarot` folder under the configured WebDAV URL path and stores ZIP backups named like `Komgarot_backup_20260706_170802.zip`. The app appends a trailing `/` to the URL when needed.

The ZIP backup contains:

- `app-settings.json`: app settings, AI service settings, S3 image URL settings, and API key settings
- `ai-translate/`: AI translation JSON data split by book ID

Restore shows the latest 5 backups for selection. Komga account/server settings and WebDAV server/username/password stay local.

## Requirements

- Android 11+
- A reachable Komga server
- JDK 11+
- Android Studio or a Gradle command-line environment

## Build

Debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Lint:

```powershell
.\gradlew.bat :app:lintDebug
```

Release APK:

```powershell
.\gradlew.bat :app:assembleRelease
```

Debug output:

```text
app/build/outputs/apk/debug/
```

## Stack

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Retrofit + Gson
- OkHttp
- Coil
- DataStore Preferences
- AndroidX Security Crypto
- AndroidX Biometric

## Links
[LINUX DO](https://linux.do)
