# Going live: Firebase + Vercel + Android

You need about 20 minutes and three accounts: Firebase (free Spark plan), Vercel (free Hobby plan) and GitHub.

## 1. Firebase project
1. Go to https://console.firebase.google.com and create a project, for example `meld-prod`. Analytics is optional; Meld doesn't use it.
2. **Build → Authentication → Get started → Sign-in method → Anonymous → Enable.**
3. **Build → Firestore Database → Create database.** Choose the location nearest your users; you can't change it later. Start in *production mode*.
4. Deploy the security rules from this repo:
   ```bash
   npm i -g firebase-tools
   firebase login
   firebase use --add            # pick your project
   firebase deploy --only firestore:rules,firestore:indexes
   ```
5. **Project settings → Your apps:**
   - **Android app:** package `io.github.princejoseph.meld`. Download `google-services.json`.
   - **Web app:** copy the `apiKey`, `authDomain`, `projectId` and `appId` values.

## 2. Web app on Vercel
1. In Vercel, select **Add New → Project** and import this GitHub repo.
2. Set the **Root Directory** to `web`. The framework is detected as Vite.
3. Add these environment variables (from step 1.5):
   ```
   VITE_FIREBASE_API_KEY, VITE_FIREBASE_AUTH_DOMAIN, VITE_FIREBASE_PROJECT_ID, VITE_FIREBASE_APP_ID
   ANDROID_CERT_SHA256    (see RELEASING.md; enables Android App Links)
   ```
4. Deploy. You'll get a URL like `https://meld-xyz.vercel.app`. You can add a custom domain later.
5. In Firebase, go to **Authentication → Settings → Authorized domains** and add your Vercel domain.

## 3. Android app
1. Put `google-services.json` in `app/` for local builds. It's git-ignored; CI reads it from a secret.
2. Point share links at your web app. Add this line to `local.properties` (local) or set it as a GitHub *variable* `MELD_WEB_BASE_URL` (CI):
   ```
   meld.webBaseUrl=https://meld-xyz.vercel.app
   ```
   Share links and QR codes then use `https://…/r/CODE`. That link opens the app if it's installed (App Links), and the PWA otherwise, so iPhone and desktop users can vote too.
3. Release signing: see [RELEASING.md](RELEASING.md).

Without `google-services.json` the app still builds and runs **local-only**, as it did before.

## Costs
- Firestore's free tier covers 50k reads and 20k writes per day. One vote is about one write, and each person watching a room reads on each change.
- Voice notes are stored inside Firestore as base64, so no Cloud Storage or Blaze plan is required.
- Watch the **Usage** tab once you have real traffic.
