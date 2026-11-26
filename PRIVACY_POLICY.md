# Privacy Policy for Meld

**Effective Date:** November 26, 2026

Meld is designed around privacy by default. You do not need an account, email address, phone number, or password to create, share, or vote in decision rooms.

## 1. Information Stored Locally on Your Device

Meld stores your app state locally on your device using Android Room and Jetpack DataStore:

- **Anonymous Device Identifier (`uid`)**: A randomly generated UUID created locally so Meld can track your votes and let you edit or delete your own voice opinions.
- **Display Profile & Avatar Preferences**: Your chosen display name, anonymous mask alias, Bauhaus avatar configuration, language preference, theme choice, and haptics toggle.
- **Decision Rooms, Votes & Voice Notes**: Rooms you create or join, poll options, vote selections, and local `.m4a` voice recordings saved in your app's private storage directory.

## 2. Anonymous Rooms & Anti-Spam Network Hashing

In rooms with Anonymous Mode enabled, Meld prevents duplicate ballot stuffing using a salted one-way SHA-256 hash of your network identifier combined with the room code. Raw IP addresses are **never** stored in the database or shared with other participants; only a masked label (e.g., `192.168.x.42`) is shown locally.

## 3. Optional Permissions

Meld only requests Android runtime permissions when you actively use the corresponding feature:

- **Microphone (`RECORD_AUDIO`)**: Used solely to record inline voice opinions inside a decision room. Audio is recorded locally using Android `MediaRecorder` without sending audio to third-party speech servers.
- **Calendar (`READ_CALENDAR`)**: Used solely when you tap Calendar Sync to suggest free time blocks or detect scheduling conflicts locally. Calendar event titles are not uploaded.
- **Notifications (`POST_NOTIFICATIONS`)**: Used to alert you when a timed poll deadline expires or reaches consensus.

## 4. No Ads, No Analytics, No Cloud Backup

- Meld contains **no third-party advertising SDKs**, **no behavioral tracking**, and **no analytics**.
- Android Auto Backup and device-to-device cloud transfer are explicitly disabled (`android:allowBackup="false"`) so your local databases and identity tokens never leave your device unexpectedly.

## 5. Deleting Your Data

You can delete any room or voice opinion directly inside the app (which also deletes associated local `.m4a` audio files), or clear all local data at any time via **Android Settings > Apps > Meld > Storage > Clear data**.

## 6. Contact & Issues

If you have privacy questions or feedback, please open an issue on the [Meld GitHub Repository](https://github.com/PrinceJoseph-software/meld/issues).
