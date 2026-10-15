# Home Income & Expense Manager (Android)

An offline-first native Android app for tracking one person's household
finances in INR: daily expenses, income (such as monthly shop rent),
recurring transactions, bills and reminders, a daily milk tracker, and
monthly summaries. There is no login, backend, or cloud; all data stays on
the device, with JSON backup/restore and CSV export. See [PRD.md](PRD.md)
for the full requirements.

Stack: Kotlin, Jetpack Compose, Material 3, Hilt, Room, DataStore, WorkManager.

## Requirements

- JDK 21
- Android SDK with platform 37 (set `ANDROID_HOME` or `sdk.dir` in `local.properties`)

## Build and test

```bash
./gradlew assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # unit tests
```

CI runs both on every pull request and on pushes to `master`
(`.github/workflows/android-ci.yml`).
