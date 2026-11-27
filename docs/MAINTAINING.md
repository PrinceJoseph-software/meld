# Maintaining Meld

## Make the repository public
**Settings → General → Danger Zone → Change repository visibility → Public.**

- Public repositories get unlimited free GitHub Actions minutes. Private ones run out, which is why CI and releases stopped running.
- Secrets stay private: everything sensitive lives in GitHub Actions secrets and Vercel environment variables, not in the code. The full git history was scanned before going public and contains no keys, keystores or service-account files.

After going public, also turn on:
- **Settings → Code security → Private vulnerability reporting → Enable.** This powers the "Report a vulnerability" link in SECURITY.md.
- **Settings → Code security → Dependabot alerts** and **Secret scanning (with push protection)**.
- **Settings → General → Features → Discussions** (optional, for questions that aren't bugs).

## Protect the `main` branch (2 minutes)
A ready-made ruleset is in `.github/rulesets/protect-main.json`.

1. Download that file to your computer.
2. **Settings → Rules → Rulesets → New ruleset → Import a ruleset**, then choose the file.
3. Review it and click **Create**.

What it does:
- **No direct pushes, no force-pushes, no deleting `main`.** Every change goes through a pull request, merged with **squash** only.
- **CI must pass** before merging: `test-and-build`, `web-build`, `web-api` and `firestore-rules`.
- **Unresolved review comments block merging.**
- **Admins can still merge a pull request in an emergency** (bypass is limited to pull requests).

The `CODEOWNERS` file makes you the reviewer for every pull request.

## Releasing the Android app
See [RELEASING.md](RELEASING.md). In short: `git tag vX.Y.Z && git push origin vX.Y.Z`, or run **Actions → Release APK → Run workflow** on an existing tag.

## Labels used in issues
`bug`, `enhancement`, `triage`, `good first issue`, `help wanted`, `android`, `web`, `server`, `i18n`, `accessibility`, `docs`.

## Search and discoverability

### Website (Google)
The landing page is pre-rendered at build time, so crawlers see the full text without running JavaScript. It includes structured data (WebSite, SoftwareApplication and FAQPage) and a sitemap with the demo video. App screens (`/app`, `/r/…`) carry `noindex`, so shared polls don't show up in search.

1. Open [Google Search Console](https://search.google.com/search-console) → **Add property** → **URL prefix** → `https://meld-one.vercel.app/`.
2. Choose **HTML tag**, copy only the `content="…"` value, and add it in Vercel → Settings → Environment Variables as `GOOGLE_SITE_VERIFICATION` (Production). Redeploy, then click **Verify**.
3. **Sitemaps** → submit `sitemap.xml`.
4. **URL inspection** → `https://meld-one.vercel.app/` → **Request indexing**.
5. Optional: do the same in [Bing Webmaster Tools](https://www.bing.com/webmasters), which can import from Search Console in one click. Bing also powers DuckDuckGo and ChatGPT search.
6. Check the structured data with the [Rich Results Test](https://search.google.com/test/rich-results).

Indexing a new site usually takes a few days to a couple of weeks. Links from other sites (Product Hunt, Reddit, a blog post, the GitHub repo) speed it up.

### GitHub
On the repository page, click the gear next to **About**:
- **Description:** `Free, open-source group poll app: ranked-choice voting, time slots and live results. No sign-up, works offline. Android + web (PWA).`
- **Website:** `https://meld-one.vercel.app`
- **Topics:** `poll`, `polling-app`, `voting`, `ranked-choice-voting`, `scheduling`, `group-decision`, `pwa`, `offline-first`, `android`, `jetpack-compose`, `kotlin`, `react`, `firebase`, `slack-bot`, `discord-bot`

In Settings → General → **Social preview**, upload `web/public/og.png`. That image is shown wherever the repo link is shared.

