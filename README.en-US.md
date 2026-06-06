<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_tiger_comic.png" alt="Komgarot icon" width="144" height="144">
</p>

<p align="center">
  <a href="README.md">简体中文</a> | English
</p>

# Komgarot

Komgarot is an Android comic reader client for Komga servers, built with Kotlin and Jetpack Compose.

> This project is fully written by AI. The only human-written part is this README.
> Feel free to suggest features in the issues. I don't use every part of Komga, so I probably won't have considered adding them yet.

## Features

- Sign in to a self-hosted Komga server
- Browse libraries, series, collections, and read lists
- Home screen with on-deck books, recently added books, recently updated series, and new series
- Series search, author search, sorting, and filters
- Book detail pages with metadata viewing and editing
- Paged reading, vertical scroll reading, reading direction, page fit, and progress seeking
- Boundary pages for opening the previous or next book
- Reader page cache, cover cache, and image retry handling
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
</p>

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

## Usage

1. Launch the app.
2. Enter your Komga server URL, for example `https://komga.example.com`.
3. Enter your username and password.
4. Choose a library, series, or book to start reading.

The server URL and credentials are stored locally on the device. Credentials use Android encrypted storage. Image and reader-page caches are stored in the app cache directory and can be cleared from Settings.

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

## Layout

```text
app/src/main/java/fail/tiger/komgarot
├── data        # local storage, API, repositories
├── ui          # screens, components, navigation, reader
└── KomgarotApp.kt
```
