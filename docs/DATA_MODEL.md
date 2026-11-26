# Meld cloud data model

The Android app and the web app (PWA) share one Firestore database. Both are **offline-first**:
- Every write goes to a local cache first: Room plus Firestore's cache on Android, IndexedDB on the web.
- Pending writes are delivered automatically when the device reconnects.
- Rooms you have opened stay readable offline.

Every client signs in with **Firebase Anonymous Auth**. There are no accounts or passwords, but every device gets a server-verified `uid` that the security rules use.

```
rooms/{CODE}                      CODE = 4–10 chars [A-Z0-9]; "knowing the code" is the invite
  question, mode (QUICK_POLL|TIME_SLOTS), allowMultiple, allowFriendsToAdd, isAnonymous,
  deadlineEpochMs|null, ownerUid, winnerOptionId|null, isLocked,
  createdAtEpochMs, expiresAtEpochMs, updatedAtEpochMs, schemaVersion

rooms/{CODE}/options/{optionId}
  label, sortOrder, slotStartMs|null, slotEndMs|null, createdByUid

rooms/{CODE}/ballots/{authUid}    exactly one ballot per device
  voterUid (app-level id), voterName, badgeColorIndex, avatarStyleIndex,
  optionIds[] (1 in single-choice rooms), updatedAtEpochMs

rooms/{CODE}/comments/{id}        typed or voice opinions
  authorAuthUid, voterUid, voterName, badgeColorIndex, avatarStyleIndex, optionId|null,
  transcript, durationSeconds, createdAtEpochMs, hasAudio, audioMimeType

rooms/{CODE}/audio/{id}           voice-note bytes, fetched only when played
  authorAuthUid, mimeType, dataBase64 (≤ 700,000 chars ≈ 60 s at 32 kbps)
```

## What the server enforces (`firestore.rules`)

- Only the creator (`ownerUid`) can change settings, close or reopen the room, or reset votes.
- You can only write your own ballot.
- Ballots are only accepted while the room is open: not locked, and before the deadline by the **server clock**.
- Single-choice rooms accept one option per ballot.
- Field whitelists and size limits apply to every document. The full list of rooms cannot be enumerated.

These rules are tested against the Firestore emulator in `rules-tests/` (`npm test`).

## Anonymous rooms

Names are replaced by each person's mask name. Ballot documents are still keyed by the device's random anonymous-auth id, so the server can enforce one ballot per device. That id is not linked to any personal information.
