# Meld ⚡

<p align="center">
  <strong>Instant, zero-login group decisions and frictionless time coordination.</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?style=flat&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Min_SDK-24-orange?style=flat" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Target_SDK-36-blue?style=flat" alt="Target SDK" />
  <img src="https://img.shields.io/badge/Architecture-MVI%20%2F%20Clean-success?style=flat" alt="Architecture" />
</p>

---

## 📌 Overview

**Meld** eliminates the friction of group planning, polling, and event scheduling. No mandatory sign-ups, accounts, or friction-heavy invites: create a room in seconds, share the link or QR code, and let participants collaborate, vote, and lock in decisions in real time.

Built natively on Android using **Jetpack Compose**, **Kotlin Coroutines / Flow**, **Room Persistence**, and **Material Design 3**, Meld delivers fluid motion, tactile haptics, and robust offline-first synchronization.

---

## ✨ Features

- **⚡ Zero-Login Decision Rooms**: Create instant decision rooms or join using lightweight 4–10 character alphanumeric room codes or deep links (`meld://r/<code>`).
- **🕒 Smart Time Coordination & Calendar Sync**:
  - Connect with device calendars or import `.ics` files without sharing credentials.
  - Automatically analyze availability and surface conflict hints.
  - Time-slot distribution charts for group availability.
- **🗳️ Flexible Polling & Consensus**:
  - Single-choice, multiple-choice, ranked-choice, or time-window polling.
  - Runoff voting and instant creator controls (crown winner, close/reopen room).
- **🎨 Custom Geometric Avatars**: Built-in avatar generator ("Avatar Studio") producing deterministic, unique geometric avatars with home-screen widget support.
- **🎙️ Voice Comments**: Drop asynchronous audio notes directly inside decision rooms.
- **📱 Home Screen App Widgets**: Glanceable live poll updates and personal identity status directly on your Android launcher (`MeldPollWidget`, `MeldAvatarWidget`).
- **📴 Offline-First Architecture**: Vote and deliberate even when offline; pending actions are queued and synced cleanly when connectivity returns.
- **🌐 Multilingual Support**: Localized into 17+ languages (English, Arabic, German, Spanish, French, Hindi, Japanese, Portuguese, Russian, Chinese, etc.).
- **🔒 Security & Privacy by Design**:
  - Input sanitization against injection and malformed parameters.
  - Constant-time verification for creator authorization tokens.
  - Zero broad storage permissions; sensitive identity and creator tokens excluded from unencrypted device backup/extraction.

---

## 🛠️ Architecture & Tech Stack

```
app/src/main/java/com/example/
├── data/               # Room DB, DataStore, Repositories, Calendar Parser, Offline sync
├── notifications/      # WorkManager deadline schedulers, Android notification channels
├── ui/                 # Jetpack Compose UI (Screens, Theme, Custom Components, Haptics)
│   ├── components/     # Tactile controls, option cards, charts, bottom sheets, avatars
│   ├── screens/        # CreateRoomScreen, DecisionRoomScreen
│   └── theme/          # Material 3 colors, typography, dynamic themes
└── widget/             # Android AppWidget providers and bitmap renderers
```

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Language**: [Kotlin](https://kotlinlang.org/) (Coroutines, StateFlow / Flow)
- **Local Persistence**: [Room Database](https://developer.android.com/training/data-storage/room) & [Jetpack DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- **Background Tasks**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) for room deadlines & offline sync
- **Fonts & Design**: Instrument Serif, JetBrains Mono, Plus Jakarta Sans, Custom Tactile Haptics

---

## 🚀 Getting Started

### Prerequisites

- [Android Studio Ladybug | 2024.2+](https://developer.android.com/studio) or newer
- JDK 17+
- Android SDK (API Level 24 minimum, target API 36)

### Installation & Run

1. **Clone the repository**:
   ```bash
   git clone https://github.com/PrinceJoseph-software/meld.git
   cd meld
   ```

2. **Open in Android Studio**:
   - Launch Android Studio, select **Open**, and choose the `meld` directory.
   - Wait for Gradle sync to complete.

3. **Configure Environment (Optional)**:
   - Copy `.env.example` to `.env` if integrating with Gemini API or external AI capabilities:
     ```bash
     cp .env.example .env
     ```
   - Provide your API keys inside `.env`.

4. **Build and Run**:
   - Select your target device or Android emulator.
   - Click **Run** (`Shift + F10`) or execute via Gradle:
     ```bash
     ./gradlew assembleDebug
     ```

---

## 🔗 Deep Links & Integration

Meld supports direct deep-linking into decision rooms:

```
meld://r/{ROOM_CODE}
```

Example intent test via ADB:
```bash
adb shell am start -a android.intent.action.VIEW -d "meld://r/PROJ99"
```

---

## 🛡️ Security

Meld prioritizes data integrity and device security:
- Strict alphanumeric whitelist validation for room identifiers.
- Bounded input parsing on external ICS calendar files to prevent stream denial-of-service.
- Secure local token storage using Android DataStore with cloud backup exclusions.

---

## 📄 License

This project is open-source. See the repository for details and licensing terms.
