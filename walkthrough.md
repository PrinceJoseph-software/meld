# Security Summary

**Reviewed Files**: 11
**Issues Resolved**: 5

### Issues Resolved
| Area | File | Line | What Was Happening | Impact | Status | How It Was Fixed |
|---|---|---|---|---|---|---|
| Cloud Backup Exposure | `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` | 10, 3, 4 | Application backup was enabled with empty exclusion rules, allowing local Room vote data, `meld_uid`, and `CREATOR_TOKENS` in DataStore to be backed up off-device. | Medium | Fixed | Set `android:allowBackup="false"` and explicitly excluded `database`, `sharedpref`, and `datastore/` domains from cloud backup and device transfer. |
| Deep-Link Parameter Validation | `app/src/main/java/com/example/MainActivity.kt`, `app/src/main/java/com/example/data/MeldRepository.kt` | 128, 93 | Incoming `meld://r/<id>` URI path segments and room lookup inputs accepted arbitrary non-room strings before database lookup. | Medium | Fixed | Added `sanitizeRoomCode` (`^[A-Z0-9]{4,10}$`) validation in `MeldModels.kt` and enforced it at `handleDeepLink` and `roomExists`. |
| Unbounded ICS Parsing & Unsanitized Titles | `app/src/main/java/com/example/data/CalendarSync.kt` | 40, 61, 186 | Malformed or oversized `.ics` imports could read unbounded content streams, parse unlimited `VEVENT` blocks, and preserve control characters in event summaries. | Medium | Fixed | Capped `readIcsUri` reads at 2 MB, bounded `IcsCalendarParser.parse` to 5,000 events, and sanitized event summaries/calendar titles with `sanitizeMeldInput`. |
| Creator Token Timing & Validation | `app/src/main/java/com/example/data/IdentityStore.kt` | 110, 122 | Creator token lookup used plain string equality without room-code normalization or blank-token guards. | Low | Fixed | Normalized room codes via `sanitizeRoomCode`, rejected blank tokens, and switched verification to constant-time `MessageDigest.isEqual`. |
| Option & Deadline Integrity + Safe Share Chooser | `app/src/main/java/com/example/data/MeldRepository.kt`, `app/src/main/java/com/example/ui/components/SharePopoverSheet.kt` | 397, 454, 279 | Collaborative option additions lacked a room option cap and deadline check; offline queue flushing did not skip expired-timer rooms; intent chooser launch lacked error handling. | Low | Fixed | Enforced `20`-option ceiling and active timer check in `addOptionToRoom`, skipped expired rooms in `flushPendingVotes`, and wrapped `startActivity` chooser in `runCatching`. |

### Safeguards Verified
| Area | File | How It's Protected |
|---|---|---|
| Package & Signing Configuration | `app/build.gradle.kts` | Application ID updated to `io.github.princejoseph.meld` while preserving existing keystore and signing configs without hardcoded secrets. |
| SQL & Persistence Queries | `app/src/main/java/com/example/data/MeldDao.kt`, `MeldRepository.kt` | All Room database reads and writes use parameterized `@Query` bindings (`:roomId`, `:voterUid`, `:optionId`) with zero raw string-concatenated SQL. |
| Permission Least Privilege | `app/src/main/AndroidManifest.xml`, `CreateRoomScreen.kt` | Declares only `ACCESS_NETWORK_STATE`, `VIBRATE`, and optional `READ_CALENDAR`; `READ_CALENDAR` is requested at runtime via `ActivityResultContracts.RequestPermission` with graceful `.ics` fallback and zero broad media/storage permissions. |
| Creator Authorization | `app/src/main/java/com/example/data/MeldRepository.kt` | Privileged actions (`crownWinner`, `createRunoff`, `reopenRoom`, `resetVotes`) require `identityStore.hasValidCreatorToken(roomId, room.creatorToken)` before modifying room state. |
| Input Sanitization | `app/src/main/java/com/example/data/MeldModels.kt` | Room questions, option labels, voter display names, and calendar titles pass through `sanitizeMeldInput` to strip control characters and enforce length limits. |

## PoC Verification

### Deep-Link Room Code Validation Bypass
#### Summary
| Field | Value |
|---|---|
| Issue Type | Missing Input Validation on Intent URI Path |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/MainActivity.kt:128` |
| Test Scenario | `adb` / intent launch with `meld://r/../../etc/passwd` or `meld://r/<script>alert(1)</script>` |

#### How It Was Fixed
Added `sanitizeRoomCode` (`^[A-Z0-9]{4,10}$`) in `MeldModels.kt:543` and enforced it at the deep-link entry point in `MainActivity.kt:128` and `MeldRepository.kt:93`.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Malformed deep-link intent reaches `handleDeepLink(intent)` at `MainActivity.kt:125`. | Scheme `meld` and host `r` match; `data.lastPathSegment` extracts `passwd` or `<script>alert(1)</script>`. |
| 2 | `sanitizeRoomCode(data.lastPathSegment)` runs at `MainActivity.kt:128` (`MeldModels.kt:543`). | Fails `ROOM_CODE_REGEX` (`^[A-Z0-9]{4,10}$`) and returns `null`. |
| 3 | Early `?: return` exits `handleDeepLink` before calling `viewModel.openRoom(...)`. | **Handled Safely** |

#### Conclusion
**Fix Verified** - Only uppercase alphanumeric room codes of 4–10 characters can reach room navigation or database lookups.

---

### Oversized `.ics` Calendar Import Resource Exhaustion
#### Summary
| Field | Value |
|---|---|
| Issue Type | Unbounded Stream Read and Parser Allocation |
| Impact | Medium |
| Affected File | `app/src/main/java/com/example/data/CalendarSync.kt:40` |
| Test Scenario | Importing a 25 MB `.ics` file containing 100,000 `VEVENT` entries with embedded control characters in `SUMMARY`. |

#### How It Was Fixed
Bounded `readIcsUri` to 2 MB (`2 * 1024 * 1024` characters) in `CalendarSync.kt:186`, capped `IcsCalendarParser.parse` at 5,000 events (`CalendarSync.kt:40, 64`), and sanitized extracted summaries with `sanitizeMeldInput(..., 120)` (`CalendarSync.kt:61`).

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | User picks a `.ics` URI; `DeviceCalendarReader.readIcsUri` opens `InputStream` at `CalendarSync.kt:188`. | Loop terminates once `totalChars` reaches `2 * 1024 * 1024`, preventing out-of-memory reads. |
| 2 | `IcsCalendarParser.parse` iterates unfolded lines at `CalendarSync.kt:41`. | Once `intervals.size >= 5_000`, `break` exits parsing (`CalendarSync.kt:64`). |
| 3 | Each parsed `SUMMARY` passes through `sanitizeMeldInput(summary, 120)` at `CalendarSync.kt:61`. | **Handled Safely** |

#### Conclusion
**Fix Verified** - Oversized or malformed calendar files cannot exhaust memory or inject control characters into conflict hints.

---

### Unencrypted Backup Exposure of Local Identity and Creator Tokens
#### Summary
| Field | Value |
|---|---|
| Issue Type | Permissive App Backup Configuration |
| Impact | Medium |
| Affected File | `app/src/main/AndroidManifest.xml:10` |
| Test Scenario | Cloud backup or ADB device-transfer extraction reading `meld_identity` DataStore (`CREATOR_TOKENS`, `meld_uid`) and Room database files. |

#### How It Was Fixed
Disabled `android:allowBackup="false"` in `AndroidManifest.xml:10` and added explicit `<exclude>` directives for `database`, `sharedpref`, and `datastore/` in `backup_rules.xml:3-5` and `data_extraction_rules.xml:4-11`.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | Backup manager inspects `<application>` attributes in `AndroidManifest.xml:10`. | Sees `android:allowBackup="false"`. |
| 2 | Backup rules in `backup_rules.xml` and `data_extraction_rules.xml` are evaluated. | `database`, `sharedpref`, and `datastore/` paths are explicitly excluded across both cloud backup and device transfer. |
| 3 | Local identity UID, creator tokens, and room records remain isolated on device. | **Handled Safely** |

#### Conclusion
**Fix Verified** - Local identity state, creator authorization tokens, and decision history are excluded from backup extraction.
