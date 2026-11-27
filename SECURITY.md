# Security policy

## Reporting a vulnerability
Please **do not open a public issue** for security problems.

Report privately through **GitHub → Security → [Report a vulnerability](https://github.com/PrinceJoseph-software/meld/security/advisories/new)**. Include:
- what an attacker could do;
- the steps to reproduce;
- the affected part: Android app, web app, `web/api` server functions, or `firestore.rules`.

You'll get an acknowledgement within **72 hours** and a fix or a plan within **14 days** for confirmed issues. We're happy to credit you in the release notes.

## Scope
In scope:
- `firestore.rules`, for example reading or changing another person's room, ballot or webhook settings;
- the server functions in `web/api` (Slack/Discord signature checks, claim links, webhook delivery, the scheduler);
- the Android app and the web app.

Out of scope: denial of service by volume, social engineering, and issues in third-party services (Firebase, Vercel, Slack, Discord) themselves.

## Supported versions
Only the latest release and the live web app receive security fixes.

## How Meld protects data
See [docs/DATA_MODEL.md](docs/DATA_MODEL.md#what-the-server-enforces-firestorerules) for what the server enforces, and [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for what is stored.
