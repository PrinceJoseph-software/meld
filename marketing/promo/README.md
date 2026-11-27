# Promo video & GIF

The demo is **real footage of the web app**. Two browsers (Alice, the creator, in dark mode; Bob, a voter, in light mode) run against the Firebase emulators and are composited into branded videos.

Outputs (in `out/`, not committed):
- `meld-demo-16x9.mp4`: 1920×1080, about 46 s (X, LinkedIn, YouTube, Product Hunt)
- `meld-demo-9x16.mp4`: 1080×1920, about 46 s (Instagram Reels, TikTok, Shorts, Stories)
- `meld-demo.gif` (960 px) and `meld-demo-vertical.gif` (540 px): 25 s highlight cuts

## Re-render (for example once you have your real domain)
Requires Node 22, Python 3, ffmpeg, Playwright with Chromium, and Java (for the emulators).

```bash
# 1. Build the web app against the emulators and start everything
cd web
printf 'VITE_FIREBASE_API_KEY=demo-key\nVITE_FIREBASE_AUTH_DOMAIN=demo-meld.firebaseapp.com\nVITE_FIREBASE_PROJECT_ID=demo-meld\nVITE_FIREBASE_APP_ID=1:000:web:demo\nVITE_USE_FIREBASE_EMULATOR=true\n' > .env.e2e
npx vite build --mode e2e && npm run build:api
cd .. && firebase emulators:start --only auth,firestore --project demo-meld &
cd web && FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 \
  FIREBASE_PROJECT_ID=demo-meld PORT=80 node scripts/local-server.mjs &   # port 80 gives clean URLs

# 2. Record, render graphics, composite
cd ../marketing/promo
DEMO_DOMAIN=meld.yourdomain.com node record.cjs   # the domain shown in the share panel and result card
node assets.cjs
python3 compose.py            # writes the two MP4s and the two GIFs into out/
```

`compose.py` also writes both GIFs (25 s highlight cuts). Captions live in `out/timeline.json` after recording; edit the text there, then re-run `assets.cjs` and `compose.py`.

The recording injects two things that are not part of the app: visible tap ripples, and a `crypto.randomUUID` shim needed only because the demo hostname is served over plain http.
