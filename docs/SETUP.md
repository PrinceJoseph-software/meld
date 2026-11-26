# Going live: step-by-step setup

This guide takes Meld from code to a live product:
- the **Android app** syncs worldwide;
- the **web app** runs at your own URL on Vercel;
- **GitHub** builds signed releases automatically.

**Time:** about 45 minutes the first time.

**Cost:** free. The Firebase *Spark* and Vercel *Hobby* plans are enough to launch.

**You need:**
- a Google account (for Firebase);
- your GitHub account;
- a computer with **Node.js 20+** (https://nodejs.org) and **Java 17+**;
- Android Studio, which includes `keytool`.

> Screens in the Firebase and Vercel consoles change from time to time. If a button has moved, look for the same words nearby; the steps stay the same.

---

## How the pieces fit together

```
 Android app ──┐                         ┌── Vercel hosts the web app (PWA)
               ├──►  Firebase project  ◄─┤     https://your-app.vercel.app
 Web app ──────┘     • Firestore (data)   └── GitHub builds the Android APK
                     • Anonymous Auth (silent, no login screen)
```

- **Firebase** is the shared database. Both apps read and write the same rooms.
- **Vercel** serves the web app, so anyone with a link can vote from an iPhone or laptop.
- **GitHub Actions** builds and signs the Android app every time you push a version tag.

---

## Part 1: Create the Firebase project (≈15 min)

### 1.1 Create the project
1. Open https://console.firebase.google.com and sign in with your Google account.
2. Click **Create a project** (or **Add project**).
3. Name it, for example `meld`. Firebase shows a *project ID* underneath, such as `meld-4f2a1`. **Write the project ID down**; you'll need it later.
4. When asked about **Google Analytics**, switch it **off**. Meld promises no analytics, and you can always add it later.
5. Click **Create project**, wait, then click **Continue**.

### 1.2 Turn on Anonymous sign-in
This is invisible to users: there's no login screen. Each device silently gets a random id, which lets the server enforce "one ballot per device" and "only the creator can close the room".
1. In the left menu, open **Build → Authentication**, then click **Get started**.
2. Open the **Sign-in method** tab.
3. Click **Anonymous**, switch **Enable** on, and click **Save**.

### 1.3 Create the Firestore database
1. In the left menu, open **Build → Firestore Database**, then click **Create database**.
2. **Location:** pick the region closest to most of your users, for example `eur3` (Europe), `nam5` (US) or `africa-south1`.
   ⚠️ You can't change this later.
3. Choose **Start in production mode**. Meld's own security rules replace the defaults in step 1.4.
4. Click **Create**.

### 1.4 Upload Meld's security rules (from your computer)
The rules in `firestore.rules` are what keep the data safe. Without them, production mode blocks everything.
```bash
npm install -g firebase-tools        # one time
firebase login                       # opens a browser; sign in with the same Google account
cd meld                              # your clone of this repository
firebase use --add                   # pick the project from 1.1; type "default" as the alias
firebase deploy --only firestore:rules,firestore:indexes
```
You should see `✔ Deploy complete!`. Repeat the last command whenever `firestore.rules` changes.

### 1.5 Register the Android app and get `google-services.json`

**What is `google-services.json`?** It's a small settings file that Firebase generates for your Android app. It tells the app *which* Firebase project to talk to: the project id, the app id and an API key.
- It's **not a password**. The API key in it only identifies your project; what users can do is controlled by `firestore.rules` and Anonymous Auth.
- It's still good practice to keep it out of the public repo (this repo git-ignores it) and give it to the build through a GitHub secret.

Without this file the app still builds, but it runs **local-only**, like the original version.

1. In Firebase, click the **gear icon → Project settings**.
2. Under **Your apps**, click the **Android** icon.
3. **Android package name:** `io.github.princejoseph.meld` (exactly this).
4. **App nickname:** `Meld Android`.
5. **Debug signing certificate SHA-1:** leave it empty (Meld doesn't use Google Sign-In).
6. Click **Register app**, then **Download google-services.json**.
7. Click **Next** through the remaining screens. The Gradle setup they describe is already done in this repo.
8. For local builds, copy the file into the `app/` folder: `meld/app/google-services.json`.

### 1.6 Register the web app (for Vercel)
1. In **Project settings → Your apps**, click **Add app**, then the **Web** icon (`</>`).
2. **Nickname:** `Meld Web`. Leave *Firebase Hosting* unticked; you're using Vercel. Click **Register app**.
3. Firebase shows a `firebaseConfig` block. Copy these four values somewhere safe:
   ```js
   apiKey: "AIza...",
   authDomain: "meld-4f2a1.firebaseapp.com",
   projectId: "meld-4f2a1",
   appId: "1:1234567890:web:abc123..."
   ```
   You can find them again any time under Project settings → Your apps → Meld Web.

---

## Part 2: Create your Android signing key (≈5 min, on your computer)

Android only accepts an app update if it is signed with **the same key** as the installed version. You create this key once and keep it forever. Full details are in [RELEASING.md](RELEASING.md); the short version:

```bash
keytool -genkeypair -v -keystore meld-upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` asks for a password and for your name and organisation (any values are fine).

**Back up three things in a password manager:**
- the `meld-upload.jks` file;
- the keystore password;
- the key password. If it said "press RETURN if same as keystore password", it's the same one.

If you lose these, you can never ship an update that installs over the existing app.

Get two values you'll need below:
```bash
# 1) The key as text, for GitHub
base64 -w0 meld-upload.jks > meld-upload.b64.txt     # macOS: base64 -i meld-upload.jks -o meld-upload.b64.txt

# 2) The SHA-256 fingerprint, for Vercel App Links
keytool -list -v -keystore meld-upload.jks -alias upload | grep "SHA256:"
#   SHA256: 12:AB:34:...:EF   <- copy everything after "SHA256: "
```

---

## Part 3: Put the web app on Vercel (≈10 min)

1. Go to https://vercel.com and sign up with **Continue with GitHub**.
2. Click **Add New… → Project**, find `meld` in the list, and click **Import**. If it isn't listed, click **Adjust GitHub App Permissions** and allow the repo.
3. **Root Directory:** click **Edit**, choose **`web`**, then **Continue**. Vercel should detect the framework as **Vite**.
4. Open **Environment Variables** and add one row per value (name, then value):

   | Name | Value |
   |---|---|
   | `VITE_FIREBASE_API_KEY` | `apiKey` from 1.6 |
   | `VITE_FIREBASE_AUTH_DOMAIN` | `authDomain` from 1.6 |
   | `VITE_FIREBASE_PROJECT_ID` | `projectId` from 1.6 |
   | `VITE_FIREBASE_APP_ID` | `appId` from 1.6 |
   | `ANDROID_CERT_SHA256` | the fingerprint from Part 2, such as `12:AB:34:…` |

5. Click **Deploy**. After about a minute you get a URL like `https://meld-abc123.vercel.app`. **Copy it.**
6. Back in Firebase, open **Authentication → Settings → Authorized domains → Add domain**, and add `meld-abc123.vercel.app`, without `https://`.
7. Open your URL on your phone, create a room, and open the link on a second device to vote. ✅

**Optional custom domain:** in Vercel, go to **Project → Settings → Domains**, add for example `meld.yourname.com`, and follow its DNS instructions. Then:
- add the new domain to Firebase *Authorized domains* (step 6);
- use the new URL in Part 4.

**To install it like an app:** on Android Chrome, use the menu → **Install app**. On iPhone Safari, tap **Share → Add to Home Screen**.

---

## Part 4: Connect GitHub so releases build themselves (≈10 min)

Open your repo on GitHub, then **Settings → Secrets and variables → Actions**.

**Secrets** tab: click **New repository secret** for each row.

| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | the entire contents of `meld-upload.b64.txt` (one long line) |
| `STORE_PASSWORD` | keystore password from Part 2 |
| `KEY_PASSWORD` | key password from Part 2 |
| `KEY_ALIAS` | `upload` |
| `GOOGLE_SERVICES_JSON` | open `google-services.json` in a text editor, copy **all** of it, paste |

**Variables** tab: click **New repository variable**.

| Name | Value |
|---|---|
| `MELD_WEB_BASE_URL` | your Vercel URL from Part 3, e.g. `https://meld-abc123.vercel.app` (no trailing slash) |

This URL goes into share links and QR codes. On Android, `https://…/r/CODE` opens the app directly if it's installed (App Links); otherwise it opens the web app.

### Publish a release
```bash
git tag v1.1.0
git push origin v1.1.0
```

Then:
1. Watch **Actions → Release APK**. In about 6 minutes, **Releases** has `meld-1.1.0.apk` and an `.aab` attached.
2. Install the APK on your phone. If an older Meld build is installed, uninstall it first: old builds were signed with a throwaway key.
3. Open the app and create a room.
4. Open the share link on another phone or a laptop and vote. You should see the vote appear on the first phone within a second or two.

For local builds, put this line in `local.properties` (in the repo root; it's git-ignored):
```
meld.webBaseUrl=https://meld-abc123.vercel.app
```

---

## Part 5: Check everything works

| Test | Expected |
|---|---|
| Create a room on the phone, open the link on a laptop | The room loads; votes show on both within ~2 s |
| Turn on airplane mode, vote, then turn it off | The vote shows immediately and reaches others after reconnecting |
| Tap a shared `https://…/r/CODE` link on an Android phone with the app installed | It opens in the app, not the browser. If it opens the browser, re-check `ANDROID_CERT_SHA256` |
| A non-creator tries to close the room | They don't see the controls; the server rejects it anyway |
| Firebase → Firestore → Data | You can see `rooms/{CODE}` documents appearing |

## Keeping an eye on it
- **Firebase → Usage and billing:** the free tier allows 50,000 reads and 20,000 writes per day. One vote is about one write; each person watching a room reads once per change.
- If you outgrow it, the **Blaze** plan is pay-as-you-go. Set a **budget alert** first, under Google Cloud → Billing → Budgets.
- **Vercel → Analytics** is off by default. Leave it off if you want to keep the no-analytics promise.

## Troubleshooting
- **The web app says "Connect Firebase":** a `VITE_FIREBASE_*` variable is missing. Fix it in Vercel → Settings → Environment Variables, then redeploy.
- **The web app shows `auth/unauthorized-domain`:** add the domain in Firebase → Authentication → Settings → Authorized domains.
- **The web app shows `permission-denied`:** the rules aren't deployed (step 1.4), or you're writing something the rules forbid.
- **The release workflow fails at "Restore upload keystore":** the `KEYSTORE_BASE64` secret is missing or truncated. Re-create it from `meld-upload.b64.txt`.
- **The Android app stays local-only:** the `GOOGLE_SERVICES_JSON` secret is missing, so the build log shows a warning. Locally, check that `app/google-services.json` exists.
