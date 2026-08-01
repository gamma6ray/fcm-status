# FCM Status Checker

Android app that checks whether Firebase Cloud Messaging (FCM) can connect on your device. No Firebase project or `google-services.json` needed — it's a pure diagnostics tool.

## What it checks

- **FCM server reachability** — probes `mtalk.google.com` on ports 5228, 5229, 5230, 443 and shows which port connected and the round-trip time
- **Google Play Services** — availability status and installed version
- **Notifications** — whether notifications are enabled and `POST_NOTIFICATIONS` is granted (Android 13+)
- **Network & device** — online/offline, network type (Wi-Fi, Cellular, VPN), device model, Android version
- **Overall verdict** — green card if Play Services + internet + server reachable are all passing

## FCM Keep-Alive (Heartbeat)

Taps undocumented Play Services broadcasts to send FCM heartbeats on a configurable interval (1-10 minutes). Keeps the GCM/FCM connection warm, which helps when VPNs or other networks let the heartbeat drift apart.

- Foreground service with ongoing notification
- Self-rescheduling alarm that pierces Doze (uses `setAlarmClock` on Android 12+)
- Battery optimization exemption helper
- Survives boot if keep-alive was enabled
- Interval response** shown live in the app

These broadcasts are not a public API but are verified working reliably on Android 15 devices. The GCM internal FCM diagnostics screen (`*#*#426#*#*`) is available from the app — both via a direct Intent launch (if GMS allows) or a copy-to-clipboard fallback that opens the dialer.

### Battery optimization

Many manufacturers like Samsung, vivo, Xiaomi, OnePlus, and Huawei impose background restrictions that go beyond stock Android Doze. If the keep-alive stops running after the screen turns off:

1. Long-press the app icon and tap **App info**.
2. Go to **Battery** (or **Power usage**).
3. Set it to **Unrestricted** (or **Don't optimize / Not optimized**).

The app also has an in-app button that opens the system's Doze whitelist directly.

## Screenshots

<p align="center">
  <img src="assets/status-screen.png" alt="FCM Status Checker status screen" width="300">
  <img src="assets/settings-screen.png" alt="FCM Status Checker settings screen" width="300">
</p>

The current screens were verified on an Android 15 emulator at 1080 × 2400 and on a vivo Android 15 phone.

## Download

Pre-built APK: [Releases](https://github.com/gamma6ray/fcm-status/releases)

## Build from source

Requirements:

- JDK 17
- Gradle 8.9
- Android SDK (compileSdk 35, build-tools 35.0.0)

```bash
cd app-project
export ANDROID_SDK_ROOT=/path/to/android-sdk
gradle :app:assembleRelease
```

APK will be built at `app/build/outputs/apk/release/app-release.apk`.

### Build notes

- No `google-services.json` needed
- No Google Firebase services plugin
- Min SDK 26, target/compile SDK 35

## Tech stack

- Plain Java (no Kotlin / Compose)
- Programmatic UI via AppCompat
- AndroidX, Material Components, Play Services Base

## License

MIT
