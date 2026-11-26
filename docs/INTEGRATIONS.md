# Integrations: Slack, Discord, webhooks, embeds, calendars

Every integration runs on the **same Vercel deployment as the web app**. The server code is in `web/api/` and needs no extra hosting. First finish [SETUP.md](SETUP.md), including **Part 6** (server environment variables).

| Integration | What people get | Setup time |
|---|---|---|
| [Slack](#slack) | `/meld Lunch? \| Tacos \| Ramen` posts a poll; the decision is posted back to the channel | 10 min |
| [Discord](#discord) | Same, as a Discord slash command | 10 min |
| [Webhooks](#decision-webhooks) | Your own system gets a signed JSON POST when a poll is decided | 2 min, per room |
| [Embeds](#website-embeds) | A live, votable poll inside any website | 1 min, per room |
| [Calendar feeds](#calendar-feeds) | Decided time slots appear in Google or Apple Calendar automatically | 1 min |
| [Weekly polls](#weekly-recurring-polls) | A fresh poll every week behind one permanent link | — |

---

## Slack

### 1. Create the Slack app
1. Go to https://api.slack.com/apps and choose **Create New App → From scratch**. Name it `Meld` and pick your workspace.
2. **Basic Information → App Credentials:** copy the **Signing Secret**.
3. **Slash Commands → Create New Command:**
   - Command: `/meld`
   - Request URL: `https://YOUR-APP.vercel.app/api/slack`
   - Short description: `Start a group poll`
   - Usage hint: `Question? | Option 1 | Option 2 [--ranked] [--deadline 2h]`
4. **OAuth & Permissions → Scopes → Bot Token Scopes:** add `chat:write` and `commands`.
5. **Install App → Install to Workspace.** Copy the **Bot User OAuth Token** (`xoxb-…`).

### 2. Give Vercel the secrets
In Vercel, go to **Project → Settings → Environment Variables** and add:

| Name | Value |
|---|---|
| `SLACK_SIGNING_SECRET` | Signing Secret from step 1.2 (required: requests without a valid signature are rejected) |
| `SLACK_BOT_TOKEN` | `xoxb-…` from step 1.5 (optional: lets Meld post the result back) |

Then **redeploy**: Deployments → ⋯ → Redeploy.

### 3. Use it
```
/meld Team lunch? | Tacos | Ramen | Pizza --deadline 1h
/meld Offsite city? | Lisbon | Oslo | Rome --ranked --deadline 2d
/meld Standup day? | Mon | Wed | Fri --weekly
```
- **Flags:** `--ranked`, `--multi`, `--anon`, `--weekly`, `--deadline 30m|2h|1d` (default 24h), `--open` (no deadline).
- The channel gets the poll with a **Vote in Meld** button. Anyone can vote from Slack, a phone or a laptop, with no account.
- **Only you** get a private "manage" link. Open it once to become the poll's owner, so you can close it, crown the winner, or add a webhook.
- When the poll closes, the result is posted in the channel. Invite the bot to private channels first: `/invite @Meld`.

---

## Discord

### 1. Create the Discord application
1. Go to https://discord.com/developers/applications → **New Application** → `Meld`.
2. **General Information:** copy the **Application ID** and the **Public Key**.
3. **Bot → Reset Token:** copy the **bot token**.
4. **General Information → Interactions Endpoint URL:** `https://YOUR-APP.vercel.app/api/discord`.
   Discord verifies the URL immediately, so add `DISCORD_PUBLIC_KEY` to Vercel and redeploy **before** saving this field.
5. **Installation** (or OAuth2 → URL Generator): scopes `applications.commands` and `bot`, permission **Send Messages**. Open the URL and add the bot to your server.

### 2. Vercel environment variables
| Name | Value |
|---|---|
| `DISCORD_PUBLIC_KEY` | Public Key (required: verifies every request) |
| `DISCORD_BOT_TOKEN` | bot token (optional: posts the result back to the channel) |

### 3. Register the `/meld` command (one time, on your computer)
```bash
cd meld/web
DISCORD_APP_ID=123... DISCORD_BOT_TOKEN=... node scripts/register-discord-command.mjs
```
`200` means it worked. Global commands can take a few minutes to appear.

Then use `/meld question: Movie night? options: Dune | Up | Heat ranked: True`.

---

## Decision webhooks
Send poll results to your own system: a CRM, Zapier/Make, an internal tool, a database.

1. Open your room as its owner → **Integrations** → **Decision webhook**.
2. Paste an `https://` URL and keep the **signing secret** (`whsec_…`) somewhere safe. Click **Save webhook**.
3. When the room closes (owner closes it, or the deadline passes), Meld sends **one** POST:

```http
POST /your-endpoint
Content-Type: application/json
X-Meld-Event: decision.reached
X-Meld-Timestamp: 1764158400
X-Meld-Signature: sha256=8b1f…
```
```json
{
  "event": "decision.reached",
  "decidedAt": 1764158400123,
  "code": "K7Q2MZ",
  "question": "Offsite city?",
  "mode": "QUICK_POLL",
  "isRanked": true,
  "url": "https://meld.example.com/r/K7Q2MZ",
  "winner": { "id": "K7Q2MZ_opt_1a2b", "label": "Oslo", "votes": 3, "slotStartMs": null, "slotEndMs": null },
  "results": [ { "id": "…", "label": "Oslo", "votes": 3 }, { "id": "…", "label": "Lisbon", "votes": 1 } ],
  "totalVoters": 4,
  "tie": false
}
```
Weekly polls also send `"event": "poll.opened"` when next week's room opens.

**Verify the signature.** Always check it, so nobody can fake results:
```js
import { createHmac, timingSafeEqual } from 'node:crypto';
function isFromMeld(rawBody, headers, secret) {
  const ts = headers['x-meld-timestamp'];
  if (Math.abs(Date.now() / 1000 - Number(ts)) > 300) return false;          // reject replays
  const expected = 'sha256=' + createHmac('sha256', secret).update(`${ts}.${rawBody}`).digest('hex');
  const got = headers['x-meld-signature'] ?? '';
  return got.length === expected.length && timingSafeEqual(Buffer.from(got), Buffer.from(expected));
}
```
```python
import hmac, hashlib, time
def is_from_meld(raw_body: bytes, headers, secret: str) -> bool:
    ts = headers["X-Meld-Timestamp"]
    if abs(time.time() - int(ts)) > 300: return False
    expected = "sha256=" + hmac.new(secret.encode(), f"{ts}.".encode() + raw_body, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, headers.get("X-Meld-Signature", ""))
```

**Delivery details:**
- One attempt, 5 s timeout, no redirects.
- HTTPS URLs only, and private or internal addresses are refused.
- Reply with any `2xx`.
- "Exactly once" comes from the server checking the room before it sends: every device that sees the room close pings `/api/decision`, but only the first ping triggers delivery.

---

## Website embeds
Owner → **Integrations** → **Embed on a website**, then copy:
```html
<iframe src="https://YOUR-APP.vercel.app/embed/K7Q2MZ" width="100%" height="560"
        style="border:0;border-radius:16px" title="Offsite city?" loading="lazy"></iframe>
```
Visitors see the live poll and can vote right there.
- Only `/embed/*` pages may be framed by other sites; the rest of Meld refuses framing (clickjacking protection).
- Embeds keep data in memory rather than on disk, because browsers restrict storage in third-party frames.

---

## Calendar feeds
For **time-slot** polls, once a time is decided:
- **Add to Google Calendar** / **Apple / Outlook (.ics)** buttons appear on the result. These are one-off adds.
- **Subscribe** (also under Integrations → Calendar feed) adds a live feed:
  - one room: `https://YOUR-APP.vercel.app/cal/K7Q2MZ.ics`
  - a weekly series: `https://YOUR-APP.vercel.app/cal/series/K7Q2MZ.ics`. Every week's decided time appears automatically.
- **Android:** Settings → **Add decided times to my calendar** writes the winning slot of any time poll you took part in into your phone's calendar (asks for calendar permission once).

Google Calendar refreshes subscribed feeds every few hours (Google's own limit); Apple Calendar lets you choose the interval.

---

## Weekly recurring polls
Tick **Repeat every week** when creating a room (web or Android), or use `--weekly` in Slack/Discord.
- Every 7 days the scheduler closes last week's poll, announces its result, and opens a fresh copy with the same question and options. Time slots move forward a week, and webhooks and chat channels carry over.
- Share **`https://YOUR-APP.vercel.app/s/K7Q2MZ`**: it always opens the current week's poll. Old rooms show "This week's poll is open".
- The owner can **Pause / Resume** under Integrations.
- The scheduler runs once a day (Vercel Hobby plan limit), so a new week's poll opens within 24 h of its due time. On Vercel Pro you can change `"schedule"` in `web/vercel.json` to run hourly.
