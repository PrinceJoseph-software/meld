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

  isRanked, repeatWeekly, seriesId|null, nextRoomCode|null (server), notifiedAt|null (server), source

rooms/{CODE}/options/{optionId}
  label, sortOrder, slotStartMs|null, slotEndMs|null, createdByUid

rooms/{CODE}/ballots/{authUid}    exactly one ballot per device
  voterUid (app-level id), voterName, badgeColorIndex, avatarStyleIndex,
  optionIds[] (1 in single-choice rooms; the voter's ranking, best first, in ranked rooms), updatedAtEpochMs

rooms/{CODE}/comments/{id}        typed or voice opinions
  authorAuthUid, voterUid, voterName, badgeColorIndex, avatarStyleIndex, optionId|null,
  transcript, durationSeconds, createdAtEpochMs, hasAudio, audioMimeType

rooms/{CODE}/audio/{id}           voice-note bytes, fetched only when played
  authorAuthUid, mimeType, dataBase64 (≤ 700,000 chars ≈ 60 s at 32 kbps)

rooms/{CODE}/private/config       owner-only: webhookUrl (https), webhookSecret
rooms/{CODE}/private/notify       server-only: Slack/Discord channel to announce the result in
rooms/{CODE}/private/claim        server-only: hash of the one-time "manage" link for bot rooms

series/{SERIES_ID}                weekly recurring poll (SERIES_ID = first room's code)
  ownerUid, latestCode, active, intervalDays (7), nextRunAt, createdAtEpochMs
```

## Tallying (identical everywhere)

- **Normal rooms:** most votes wins; on a tie, the option listed first wins.
- **Ranked rooms: instant runoff.** In each round, every ballot counts for its highest-ranked option still in the race. An option with more than half of the active ballots wins. Otherwise the weakest option is eliminated and its ballots move to their next choice.
  - Ties for elimination: the option with the lower Borda score goes first; if still tied, the option listed later.
  - A final two-way tie is decided by the same rule and flagged as `tie`.
- Implemented in `web/src/shared/tally.ts` (web + server) and `data/RankedChoice.kt` (Android). Both have the same test cases, so all platforms always agree on the winner.

## What the server enforces (`firestore.rules`)

- Only the creator (`ownerUid`) can change settings, close or reopen the room, or reset votes.
- You can only write your own ballot.
- Ballots are only accepted while the room is open: not locked, and before the deadline by the **server clock**.
- Single-choice rooms accept one option per ballot.
- Field whitelists and size limits apply to every document. The full list of rooms cannot be enumerated.

These rules are tested against the Firestore emulator in `rules-tests/` (`npm test`).

## Anonymous rooms

Names are replaced by each person's mask name. Ballot documents are still keyed by the device's random anonymous-auth id, so the server can enforce one ballot per device. That id is not linked to any personal information.
