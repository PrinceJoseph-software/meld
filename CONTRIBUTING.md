# Contributing to Meld

Thanks for helping! Meld is an Android app (Kotlin, Jetpack Compose), a web app (React + Vite PWA) and a small server (Vercel functions) sharing one Firestore database.

## Ways to help
- **Report a bug or suggest a feature:** [open an issue](https://github.com/PrinceJoseph-software/meld/issues/new/choose).
- **Pick something up:** issues labelled [`good first issue`](https://github.com/PrinceJoseph-software/meld/labels/good%20first%20issue) or [`help wanted`](https://github.com/PrinceJoseph-software/meld/labels/help%20wanted).
- **Translate:** Android strings live in `app/src/main/res/values-*/strings.xml`.
- **Security issues:** report them privately, as described in [SECURITY.md](SECURITY.md).

## Project layout
```
app/            Android app (Kotlin, Compose, Room, WorkManager, Glance widgets)
web/            Web app (React + Vite PWA) — web/src
web/api/        Server functions on Vercel (Slack, Discord, webhooks, calendar feeds, scheduler)
firestore.rules Security rules shared by every client — tested in rules-tests/
docs/           Setup, releasing, data model, integrations
```

## Run it locally
**Android**: JDK 21 and the Android SDK (API 36), or just open the folder in Android Studio.
```bash
./gradlew testDebugUnitTest     # unit + Robolectric tests
./gradlew assembleDebug         # debug APK in app/build/outputs/apk/debug/
```
Without `app/google-services.json` the app runs **local-only** (no cloud sync), which is fine for most UI work.

**Web**: Node 22.
```bash
cd web
npm install
cp .env.example .env.local      # add a Firebase web config, or use the emulator (below)
npm run dev
```

**Against local Firebase emulators**: Java plus `npm i -g firebase-tools`.
```bash
firebase emulators:start --only auth,firestore --project demo-meld
# in web/.env.local:  VITE_USE_FIREBASE_EMULATOR=true  and any non-empty VITE_FIREBASE_* values
```

## Tests to run before a pull request
| Area | Command |
|---|---|
| Android | `./gradlew testDebugUnitTest` |
| Web | `cd web && npm test && npm run typecheck && npm run build` |
| Server functions | `cd web && npm run test:api` (starts the emulators itself) |
| Security rules | `cd rules-tests && npm install && npm test` |

CI runs all of these on every pull request.

## Guidelines
- Keep changes focused; one topic per pull request.
- **Tallying must stay identical** on all platforms: change `web/src/shared/tally.ts` and `app/.../data/RankedChoice.kt` together, and update both test files.
- **Data model changes** need a Room migration (with a `MigrationTestHelper` test), `firestore.rules` updates plus rule tests, and a note in `docs/DATA_MODEL.md`.
- User-facing Android text goes in `strings.xml`, never hard-coded.
- Never commit secrets: keystores, `google-services.json`, service-account JSON or `.env` files.

By contributing you agree your work is released under the [MIT License](LICENSE) and that you'll follow the [Code of Conduct](CODE_OF_CONDUCT.md).
