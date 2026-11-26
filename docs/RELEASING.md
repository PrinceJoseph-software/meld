# Releasing the Android app

Android only installs an update if it is signed with the **same key** as the installed version. Earlier GitHub releases were signed with a throwaway key generated on every build. Anyone who installed one of those must uninstall it once before installing a properly signed release.

## One-time: create your upload key (on your own computer)

```bash
keytool -genkeypair -v -keystore meld-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```
Pick strong passwords and **back up `meld-upload.jks` and both passwords** in a password manager or another safe place. If you lose the key, you can never publish an update to the same app again outside Google Play's key reset process. Never commit the key.

## One-time: add it to GitHub
Go to **Repo → Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | output of `base64 -w0 meld-upload.jks` (macOS: `base64 -i meld-upload.jks`) |
| `STORE_PASSWORD` | keystore password |
| `KEY_PASSWORD` | key password |
| `KEY_ALIAS` | `upload` |
| `GOOGLE_SERVICES_JSON` | full contents of `google-services.json` (enables cloud sync) |

Then add one repository **variable** (not secret): `MELD_WEB_BASE_URL` = your Vercel URL.

## App Links fingerprint (for Vercel)
```bash
keytool -list -v -keystore meld-upload.jks -alias upload | grep SHA256
```
Put that value (for example `AB:CD:…`) in Vercel's `ANDROID_CERT_SHA256` env var. If you later publish on Google Play with Play App Signing, add Play's app-signing fingerprint too, comma-separated.

## Each release
```bash
git tag v1.1.0 && git push origin v1.1.0
```
The workflow runs the tests and builds a signed APK and AAB:
- `versionName` comes from the tag.
- `versionCode` is the workflow run number.

Both files are attached to the GitHub Release.
