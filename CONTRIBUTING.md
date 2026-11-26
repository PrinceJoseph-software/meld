# Contributing to Meld

Thank you for your interest in contributing to Meld.

## Prerequisites

- **JDK 21** (Eclipse Temurin / Adoptium 21 recommended)
- **Android SDK** with API Level 36 (`compileSdk = 36`, `minSdk = 24`)
- **Gradle 9.8.1** (or Android Studio Ladybug+ / Narwhal)

## Local Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/PrinceJoseph-software/meld.git
   cd meld
   ```

2. Run the unit and Robolectric test suite:
   ```bash
   gradle testDebugUnitTest --no-daemon
   ```

3. Verify debug compilation:
   ```bash
   gradle compileDebugKotlin --no-daemon
   ```

## Optional Firebase Sync Setup

Meld runs offline-first out of the box using Room and `NoOpRemoteSyncGateway`. To enable multi-device Firestore sync:

1. Create a Firebase project and download `google-services.json` into `app/`.
2. Uncomment `firebase-bom` and `firebase-firestore` in `app/build.gradle.kts`.
3. Provide a `FirestoreRemoteSyncGateway` implementing `RemoteSyncGateway` in `io.github.princejoseph.meld.data` — `RemoteSyncGatewayFactory` will load it automatically.

## Code Quality Guidelines

- Follow official Kotlin coding conventions (`kotlin.code.style=official`).
- Keep functions focused and avoid duplicating UI or repository logic.
- Add unit tests in `ExampleUnitTest.kt`, Robolectric data tests in `ExampleRobolectricTest.kt`, or Compose UI tests in `MeldComposeUiTest.kt` for every new feature or bug fix.
- Ensure `gradle testDebugUnitTest` passes with zero failures before opening a pull request.
