# TBFT Android

A minimal Android WebView shell for https://tbft.marzan.info.

## Features
- Full-screen TBFT experience without browser chrome
- JavaScript and DOM storage enabled
- Persistent cookies/login sessions
- File chooser support
- Android back navigation
- HTTPS-only navigation
- SSL errors are blocked
- Offline/network error screen with Retry
- No advertising SDKs and no third-party runtime dependencies

## Build locally
Open this folder in Android Studio, let Gradle sync, then use **Build > Build APK(s)**.

## Build with GitHub Actions
Push the project to a GitHub repository. The included workflow builds `app-debug.apk` and uploads it as an artifact.

Package: `info.marzan.tbft`
Target: Android 15 / API 35
Minimum: Android 8 / API 26
