<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" width="96" height="96" alt="Meld App Logo" />
</p>

<h1 align="center">Meld</h1>

<p align="center">
  <strong>Instant, zero-login group decisions and frictionless time coordination for Android.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.4+-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Min_SDK-24-orange?style=flat" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target_SDK-36-blue?style=flat" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2F%20Offline--First-success?style=flat" alt="Architecture" />
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat" alt="License: MIT" /></a>
</p>

---

## Overview

**Meld** eliminates the friction of group planning, polling, and event scheduling. With no mandatory sign-ups or account creation, users can spin up a decision room in seconds, share a link or QR code, and let participants collaborate, vote, and reach consensus in real time.

Built natively on Android with **Jetpack Compose**, **Kotlin Coroutines & Flow**, **Room**, **DataStore**, **WorkManager**, and **Material Design 3**, Meld combines tactile physics-driven UI with offline-first persistence and privacy-conscious defaults.

---

## Key Features

- **Zero-Login Decision Rooms**: Create instant rooms or join via 4–10 character alphanumeric codes and deep links (`meld://r/<code>`).
- **Time Coordination & Calendar Sync**:
  - Read device calendars (with runtime permission) or import `.ics` files locally without sharing credentials.
  - Surface free-time block suggestions, Yes/No distribution charts, consensus overlap windows, and high-priority participant conflict alerts.
- **Flexible Polling & Creator Controls**:
  - Supports Quick Polls and Time Slots, single or multiple picks, collaborative friend-added options, and configurable poll deadlines.
  - Creator tools for crowning winners, generating runoff tie-breakers, resetting votes, and reopening rooms.
- **Avatar Studio & Anonymous Disguise**:
  - Procedural Bauhaus geometric avatar system across 5 customizable layers (chassis, eyes, gear/accessories, surface pattern, border frame) and 8 color palettes.
  - Dedicated anonymous user badge customizer in Settings for secret-ballot rooms, paired with network-level anti-spam ballot locking.
- **Voice Opinions**:
  - Record spoken rationale via Android speech recognition, attach comments to a general thread or specific poll option, and play them back with animated waveform equalizers via Text-to-Speech.
- **Home Screen App Widgets**:
  - Glanceable widgets built with Jetpack Glance (`MeldPollWidget`, `MeldAvatarWidget`) for quick voting and status tracking directly from the launcher.
- **Offline-First Synchronization**:
  - Votes cast while offline are queued in a local outbox (`pending_votes`), reflected optimistically in the UI, and flushed automatically when connectivity returns.
- **Multilingual Localization**:
  - Localized across 18 language packs (English, Arabic, German, Spanish, French, Hindi, Indonesian, Italian, Japanese, Korean, Dutch, Polish, Portuguese, Russian, Swedish, Turkish, Vietnamese, Chinese).
- **Security & Privacy by Design**:
  - Strict input sanitization (`sanitizeMeldInput`) and room code validation (`^[A-Z0-9]{4,10}$`) at deep-link and repository boundaries.
  - Constant-time creator token verification (`MessageDigest.isEqual`).
  - Cloud backup and device transfer disabled (`android:allowBackup="false"`) with explicit exclusions for Room databases and DataStore identity files.

---

## Architecture & Project Structure

```text
app/src/main/java/io/github/princejoseph/meld/
├── data/               # Room entities/DAO/DB, DataStore IdentityStore, MeldRepository,
│                       # CalendarSync (.ics & device), ConsensusInsightCalculator,
│                       # ConflictDetector, AnonymousSpamGuard, ConnectivityObserver
├── notifications/      # WorkManager deadline workers and notification channels
├── ui/
│   ├── components/     # AvatarStudioSheet, SettingsSheet, GeometricAvatar,
│   │                   # VoiceCommentsSection, OptionCard, DecisionArenaComponents,
│   │                   # TimeSlotDistributionChart, CalendarSyncSheet, SharePopoverSheet
│   ├── screens/        # CreateRoomScreen, DecisionRoomScreen
│   ├── theme/          # Warm Archival Paper & Anodized Obsidian color tokens, typography
│   └── MeldViewModel.kt
└── widget/             # Glance AppWidget providers and bitmap avatar renderers
```

---

## Tech Stack

- **UI**: Jetpack Compose, Material 3, Custom Canvas Rendering, Tactile Haptics
- **Language**: Kotlin, Coroutines, StateFlow / Flow
- **Persistence**: Room (`androidx.room`), Jetpack Preferences DataStore
- **Background & Widgets**: WorkManager, Jetpack Glance
- **Testing**: JUnit 4, Robolectric, Kotlinx Coroutines Test, Room In-Memory Database

---

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug 2024.2+ or newer)
- JDK 17+
- Android SDK (Min SDK 24, Target SDK 36)

### Setup & Build

1. **Clone the repository**:
   ```bash
   git clone https://github.com/PrinceJoseph-software/meld.git
   cd meld
   ```

2. **Open in Android Studio**:
   - Select **Open** and choose the `meld` root directory.
   - Allow Gradle sync to complete.

3. **Build and Test**:
   ```bash
   gradle assembleDebug
   gradle testDebugUnitTest
   ```

---

## Deep Links

Meld supports direct deep-linking into decision rooms using the `meld://r/{ROOM_CODE}` scheme:

```bash
adb shell am start -a android.intent.action.VIEW -d "meld://r/PROJ99"
```

---

## Download

Pre-built Android APKs are published on the [GitHub Releases](https://github.com/PrinceJoseph-software/meld/releases) page. Download the latest `.apk` asset directly on your Android device (Android 7.0 / API 24+) to install.

---

## Issues & Support

Found a bug, UI glitch, or have a feature request?

- **Report a Bug**: Open a [Bug Report](https://github.com/PrinceJoseph-software/meld/issues/new?template=bug_report.md) with your Android version, device model, and steps to reproduce.
- **Request a Feature**: Submit a [Feature Request](https://github.com/PrinceJoseph-software/meld/issues/new?template=feature_request.md) describing the use case.
- **Browse Open Issues**: View active discussions and known issues on the [Issue Tracker](https://github.com/PrinceJoseph-software/meld/issues).

---

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) for local setup instructions, test commands, and code quality guidelines before opening a pull request.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for full details. For details on local-first data handling and permissions, see [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
