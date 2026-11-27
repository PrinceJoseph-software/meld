# Privacy Policy for Meld

**Effective Date:** November 26, 2026

Meld is designed around privacy by default. You don't need an account, email address, phone number or password to create, share or vote in decision rooms.

## 1. What is stored on your device

The Android app keeps your data in local storage (Room and DataStore). The web app keeps it in your browser (IndexedDB and localStorage). This includes:

- **A random device identifier**, generated locally, so your votes and opinions can be told apart from other people's.
- **Your display profile:** display name (optional), anonymous mask name, avatar and colour, language, theme and haptics preferences.
- **Rooms you created or opened**, their options, votes and opinions, and voice-note recordings (`.m4a` files in the app's private storage).

## 2. What is shared through Meld's cloud service

To let other people vote in your rooms, Meld uses Google Firebase (Cloud Firestore and Firebase Anonymous Authentication).

When you create, join or vote in a room, the following is stored on Firebase servers and is visible to **anyone who has the room's code or link**:

- **The room:** question, options, settings and deadline.
- **Your ballot:** the options you chose, your display name or (in anonymous rooms) your mask name, and your avatar colour.
- **Opinions you post:** text, and voice-note audio if you record one.

Firebase Anonymous Authentication gives each device a random id. It isn't linked to your name, email or phone number. Meld uses it only to enforce rules such as "one ballot per device" and "only the creator can close the room".

Rooms can't be listed or searched: only people you share the code with can open them.

Firebase processes this data on Google's infrastructure. See [Google's Firebase privacy information](https://firebase.google.com/support/privacy).

If you never create or join a shared room, or you use a build without cloud configuration, nothing leaves your device.

### Integrations you choose to use
- **Slack or Discord:** if you start a poll with `/meld`, the question, options and link are posted in that channel. When the poll closes, the result is posted there too. Slack and Discord process those messages under their own privacy policies.
- **Decision webhooks:** a room's owner can add a webhook. When the room closes, Meld sends its question, options, vote counts and voter count (not names) to the owner's URL.
- **Calendar feeds and embeds:** anyone with a room's code can open its calendar feed, which lists the decided time, or its embed.
- **Android "Add decided times to my calendar":** off by default. When on, the winning time slot is written to your phone's calendar. Your calendar is never uploaded.

## 3. Anonymous rooms

In rooms with Anonymous Mode on, other participants see your mask name instead of your display name. Ballots are tied only to the random device id, so the service can stop duplicate ballots.

## 4. Optional permissions (Android)

Meld only asks for permissions when you use the matching feature:

- **Microphone (`RECORD_AUDIO`):** to record voice opinions. Audio is recorded on your device. It is uploaded only when you post it to a shared room.
- **Calendar (`READ_CALENDAR`):** to suggest free time slots and detect scheduling conflicts. Calendar events are read on your device and **never uploaded**.
- **Calendar write (`WRITE_CALENDAR`):** only if you turn on "Add decided times to my calendar". Used solely to add the winning time slot of polls you took part in.
- **Notifications (`POST_NOTIFICATIONS`):** requested when you set a poll deadline, so Meld can alert you when it's about to close and when a decision is reached.

## 5. No ads, no analytics, no tracking

Meld contains **no advertising SDKs**, **no analytics** and **no behavioural tracking**. Android Auto Backup is disabled, so your local data isn't copied to cloud backups.

## 6. Deleting your data

- **Deleting a room from your history** removes it from your device, together with its voice-note files.
- **Deleting an opinion you posted** removes it, and its audio, from your device and from the cloud.
- **Room creators** can delete any opinion in their room and reset all votes.
- To remove everything stored locally, use **Android Settings → Apps → Meld → Storage → Clear data**, or clear site data in your browser.
- To have shared room data deleted from the cloud, open an issue on the repository with the room code.

## 7. Contact

For privacy questions, open an issue on the [Meld GitHub repository](https://github.com/PrinceJoseph-software/meld/issues).
