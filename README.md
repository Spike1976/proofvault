# ProofVault 9.0.0-dev

Android evidence recorder by Ain't Cloud Software.

**Development milestone only, not a production-ready v9 release.**

## Implemented in source
- Compose screens, manual foreground microphone recording, pause/resume
- Experimental recording audio-source selection and device compatibility report
- Call-state notifications, not two-sided call recording
- Audio import from Share, files, connected folders; optional periodic folder sync
- Evidence notes, search, playback, delete, ZIP export with SHA-256 manifest
- Separate AES-GCM Keystore encryption utility, not yet integrated into vault storage
- GitHub Actions build and unit tests

## Unfinished
Automatic two-sided call capture on arbitrary phones, end-to-end encrypted vault storage, biometric unlock, transcription, cloud sync, production security assessment, comprehensive device testing and signed production APK.

The phone-state permission does not grant protected call audio access. Recording legality and consent requirements vary by location. ZIP exports are not encrypted. Recordings remain in private app storage but are not yet encrypted by the standalone VaultCrypto utility.

## Build
GitHub Actions > Android build > latest successful run > proofvault-debug-apk. Debug APK is not a production release.
