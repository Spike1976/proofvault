# ProofVault Android 0.1.0

Ain't Cloud Software | Android-first local evidence recorder.

## Starter features
- Jetpack Compose dark/teal UI with Home, Record, Vault, Settings
- Permission-gated microphone recording, saved as AAC/M4A in app-private storage
- Import audio from Android's document picker
- Search, local playback and deletion
- SHA-256 helper (not yet a tamper-proof chain of custody)
- No network, cloud account, call-log or Accessibility permission

## Important limitations
Source-code starter, **not yet verified as a working APK**. Recording operates while the app stays open. This does not intercept both sides of phone calls. Files are app-private but not encrypted with an application-managed key. Biometrics, background recording, automatic call recording and transcription are NOT implemented.

## Build
Open this folder in Android Studio with Android SDK 35 and JDK 17. Sync and run the app on Android 8+. This source does not bundle a Gradle wrapper binary. You can generate one using Gradle 8.9.

## Roadmap
0.2 Foreground microphone service, notification and lifecycle reliability.
0.3 Evidence metadata, file integrity records, exports and tests.
0.4 Keystore-backed file encryption and biometric app lock.
0.5 Transcription, optional encrypted backups, lawfully supported call recording compatibility review.

## Privacy
Obtain any recording consent required by applicable laws. The app does not use AccessibilityService for call capture.
