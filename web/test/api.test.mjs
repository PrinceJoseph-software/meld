// API tests against the Firebase emulators (Firestore + Auth). Run: npm run test:api
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHmac, generateKeyPairSync, sign } from 'node:crypto';
import { createServer } from 'node:http';

process.env.FIREBASE_PROJECT_ID = 'demo-meld';
process.env.MELD_ALLOW_PRIVATE_WEBHOOKS = 'true';
process.env.PUBLIC_BASE_URL = 'https://meld.test';
process.env.SLACK_SIGNING_SECRET = 'slack-secret';
process.env.SLACK_BOT_TOKEN = 'xoxb-test';
process.env.DISCORD_BOT_TOKEN = 'discord-test';
process.env.CRON_SECRET = 'cron-secret';
const { publicKey, privateKey } = generateKeyPairSync('ed25519');
process.env.DISCORD_PUBLIC_KEY = publicKey.export({ format: 'der', type: 'spki' }).subarray(12).toString('hex');

// Capture outbound chat posts instead of calling Slack/Discord.
const outbound = [];
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init) => {
  const u = String(url);
  if (u.startsWith('https://slack.com/') || u.startsWith('https://discord.com/') || u.startsWith('https://hooks.slack.com/')) {
    outbound.push({ url: u, body: JSON.parse(init.body) });
    return new Response('{"ok":true}', { status: 200 });
  }
  return realFetch(url, init);
};

const slack = await import('../.api-test/slack.mjs');
const discord = await import('../.api-test/discord.mjs');
const decision = await import('../.api-test/decision.mjs');
const claim = await import('../.api-test/claim.mjs');
const ics = await import('../.api-test/ics.mjs');
const cron = await import('../.api-test/cron/recurring.mjs');
const { initializeApp, getApps } = await import('firebase-admin/app');
const { getFirestore } = await import('firebase-admin/firestore');
if (!getApps().length) initializeApp({ projectId: 'demo-meld' });
const db = getFirestore();

const webhookHits = [];
let hookServer;
let hookUrl;
before(async () => {
  hookServer = createServer((req, res) => {
    let body = '';
    req.on('data', (c) => (body += c));
    req.on('end', () => { webhookHits.push({ headers: req.headers, body }); res.end('ok'); });
  });
  await new Promise((r) => hookServer.listen(0, r));
  hookUrl = `http://127.0.0.1:${hookServer.address().port}/hook`;
});
after(() => hookServer.close());

function slackRequest(text, ts = Math.floor(Date.now() / 1000)) {
  const body = new URLSearchParams({ text, team_id: 'T1', user_id: 'U1', channel_id: 'C1', response_url: 'https://hooks.slack.com/commands/x' }).toString();
  const sig = 'v0=' + createHmac('sha256', 'slack-secret').update(`v0:${ts}:${body}`).digest('hex');
  return new Request('https://meld.test/api/slack', {
    method: 'POST', body,
    headers: { 'x-slack-request-timestamp': String(ts), 'x-slack-signature': sig, 'content-type': 'application/x-www-form-urlencoded' },
  });
}

function discordRequest(payload) {
  const body = JSON.stringify(payload);
  const ts = String(Math.floor(Date.now() / 1000));
  const sig = sign(null, Buffer.from(ts + body), privateKey).toString('hex');
  return new Request('https://meld.test/api/discord', {
    method: 'POST', body, headers: { 'x-signature-ed25519': sig, 'x-signature-timestamp': ts, 'content-type': 'application/json' },
  });
}

const codeFrom = (url) => url.match(/\/r\/([A-Z0-9]{6})/)[1];

test('slack: rejects bad signatures and replays', async () => {
  const bad = slackRequest('Lunch? | A | B');
  const forged = new Request(bad.url, { method: 'POST', body: await bad.clone().text(), headers: { ...Object.fromEntries(bad.headers), 'x-slack-signature': 'v0=00' } });
  assert.equal((await slack.POST(forged)).status, 401);
  assert.equal((await slack.POST(slackRequest('Lunch? | A | B', Math.floor(Date.now() / 1000) - 600))).status, 401);
});

test('slack: /meld creates a ranked room and posts it to the channel', async () => {
  const res = await slack.POST(slackRequest('Team lunch? | Tacos | Ramen | Pizza --ranked --deadline 2h'));
  assert.equal(res.status, 200);
  const msg = await res.json();
  assert.equal(msg.response_type, 'in_channel');
  const url = msg.blocks[2].elements[0].url;
  const code = codeFrom(url);
  const room = (await db.doc(`rooms/${code}`).get()).data();
  assert.equal(room.question, 'Team lunch?');
  assert.equal(room.isRanked, true);
  assert.equal(room.source, 'slack');
  assert.equal(room.ownerUid, 'slack:T1:U1');
  assert.ok(room.deadlineEpochMs > Date.now() + 100 * 60_000);
  assert.equal((await db.collection(`rooms/${code}/options`).get()).size, 3);
  await new Promise((r) => setTimeout(r, 50));
  assert.ok(outbound.some((o) => o.url.startsWith('https://hooks.slack.com/') && o.body.text.includes('?claim=')), 'ephemeral claim link sent');
});

test('slack: helpful errors for bad input', async () => {
  const res = await (await slack.POST(slackRequest('Only a question'))).json();
  assert.equal(res.response_type, 'ephemeral');
  assert.match(res.text, /at least two options/);
});

test('discord: PING, bad signature, and /meld creates a room', async () => {
  assert.deepEqual(await (await discord.POST(discordRequest({ type: 1 }))).json(), { type: 1 });
  const bad = discordRequest({ type: 1 });
  const forged = new Request(bad.url, { method: 'POST', body: await bad.clone().text(), headers: { ...Object.fromEntries(bad.headers), 'x-signature-ed25519': '00'.repeat(64) } });
  assert.equal((await discord.POST(forged)).status, 401);

  const res = await (await discord.POST(discordRequest({
    type: 2, token: 'tok', application_id: 'app', channel_id: 'D1', guild_id: 'G1', member: { user: { id: 'u9' } },
    data: { name: 'meld', options: [{ name: 'question', value: 'Movie night?' }, { name: 'options', value: 'Dune | Up | Heat' }, { name: 'weekly', value: true }] },
  }))).json();
  assert.equal(res.type, 4);
  const code = codeFrom(res.data.embeds[0].url);
  const room = (await db.doc(`rooms/${code}`).get()).data();
  assert.equal(room.repeatWeekly, true);
  assert.equal(room.seriesId, code);
  assert.equal((await db.doc(`series/${code}`).get()).get('active'), true);
});

async function signUp() {
  const r = await realFetch(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ returnSecureToken: true }),
  });
  const j = await r.json();
  return { idToken: j.idToken, uid: j.localId };
}

test('claim: the creator becomes owner with the one-time link; reuse fails', async () => {
  outbound.length = 0;
  const msg = await (await slack.POST(slackRequest('Offsite? | Lisbon | Oslo'))).json();
  await new Promise((r) => setTimeout(r, 50));
  const claimUrl = outbound.find((o) => o.url.startsWith('https://hooks.slack.com/')).body.text.match(/https:\S+/)[0];
  const code = codeFrom(claimUrl);
  const token = new URL(claimUrl).searchParams.get('claim');
  const me = await signUp();
  const call = (t, auth = me.idToken) => claim.POST(new Request('https://meld.test/api/claim', {
    method: 'POST', headers: { authorization: `Bearer ${auth}`, 'content-type': 'application/json' }, body: JSON.stringify({ code, token: t }),
  }));
  assert.equal((await call('wrong-token')).status, 403);
  assert.equal((await call(token, 'not-a-jwt')).status, 401);
  assert.equal((await call(token)).status, 200);
  assert.equal((await db.doc(`rooms/${code}`).get()).get('ownerUid'), me.uid);
  assert.equal((await call(token)).status, 403, 'one-time');
  assert.ok(msg.blocks);
});

test('decision: refuses open rooms, notifies once (signed webhook + Slack) when closed', async () => {
  outbound.length = 0;
  const msg = await (await slack.POST(slackRequest('Pick a day | Mon | Tue --deadline 1h'))).json();
  const code = codeFrom(msg.blocks[2].elements[0].url);
  await db.doc(`rooms/${code}/private/config`).set({ webhookUrl: hookUrl, webhookSecret: 'whsec', updatedAtEpochMs: 1 });
  const opts = await db.collection(`rooms/${code}/options`).get();
  const tue = opts.docs.find((d) => d.get('label') === 'Tue').id;
  await db.doc(`rooms/${code}/ballots/a`).set({ voterUid: 'a', voterName: 'A', badgeColorIndex: 0, avatarStyleIndex: 0, optionIds: [tue], updatedAtEpochMs: 1 });

  const ping = () => decision.POST(new Request('https://meld.test/api/decision', { method: 'POST', body: JSON.stringify({ code }) }));
  assert.equal((await (await ping()).json()).status, 'open');
  assert.equal(webhookHits.length, 0);

  await db.doc(`rooms/${code}`).update({ deadlineEpochMs: Date.now() - 1000 });
  assert.equal((await (await ping()).json()).status, 'notified');
  assert.equal((await (await ping()).json()).status, 'already');
  assert.equal(webhookHits.length, 1, 'exactly one webhook');

  const hit = webhookHits[0];
  const payload = JSON.parse(hit.body);
  assert.equal(payload.event, 'decision.reached');
  assert.equal(payload.winner.label, 'Tue');
  const expected = 'sha256=' + createHmac('sha256', 'whsec').update(`${hit.headers['x-meld-timestamp']}.${hit.body}`).digest('hex');
  assert.equal(hit.headers['x-meld-signature'], expected, 'HMAC signature verifies');
  assert.ok(outbound.some((o) => o.url === 'https://slack.com/api/chat.postMessage' && o.body.channel === 'C1' && o.body.text.includes('Tue')));
  const room = (await db.doc(`rooms/${code}`).get()).data();
  assert.equal(room.isLocked, true);
  assert.equal(room.winnerOptionId, tue);
});

test('ranked rooms: decision uses instant-runoff, not first choices', async () => {
  const msg = await (await slack.POST(slackRequest('Rank | A | B | C --ranked --deadline 1h'))).json();
  const code = codeFrom(msg.blocks[2].elements[0].url);
  const opts = Object.fromEntries((await db.collection(`rooms/${code}/options`).get()).docs.map((d) => [d.get('label'), d.id]));
  const ballots = [['A'], ['A'], ['B'], ['B'], ['C', 'B']];
  await Promise.all(ballots.map((b, i) => db.doc(`rooms/${code}/ballots/v${i}`).set({ voterUid: `v${i}`, voterName: 'x', badgeColorIndex: 0, avatarStyleIndex: 0, optionIds: b.map((l) => opts[l]), updatedAtEpochMs: 1 })));
  await db.doc(`rooms/${code}`).update({ deadlineEpochMs: Date.now() - 1 });
  await decision.POST(new Request('https://meld.test/api/decision', { method: 'POST', body: JSON.stringify({ code }) }));
  assert.equal((await db.doc(`rooms/${code}`).get()).get('winnerOptionId'), opts.B);
});

test('calendar feed: decided time slot becomes a VEVENT', async () => {
  const msg = await (await slack.POST(slackRequest('When? | Slot 1 | Slot 2 --deadline 1h'))).json();
  const code = codeFrom(msg.blocks[2].elements[0].url);
  const opts = (await db.collection(`rooms/${code}/options`).get()).docs;
  const start = Date.UTC(2026, 11, 3, 14, 0);
  await db.doc(`rooms/${code}`).update({ mode: 'TIME_SLOTS', isLocked: true, winnerOptionId: opts[0].id });
  await opts[0].ref.update({ slotStartMs: start, slotEndMs: start + 3_600_000 });
  const res = await ics.GET(new Request(`https://meld.test/api/ics?code=${code}`));
  const body = await res.text();
  assert.equal(res.headers.get('content-type'), 'text/calendar; charset=utf-8');
  assert.match(body, /BEGIN:VEVENT/);
  assert.match(body, /DTSTART:20261203T140000Z/);
  assert.match(body, /SUMMARY:When\?/);
});

test('scheduler: rolls a weekly series into a new room and backfills missed decisions', async () => {
  outbound.length = 0;
  const msg = await (await slack.POST(slackRequest('Standup day? | Mon | Wed --weekly --deadline 1d'))).json();
  const code = codeFrom(msg.blocks[2].elements[0].url);
  await db.doc(`rooms/${code}/private/config`).set({ webhookUrl: hookUrl, webhookSecret: 'k', updatedAtEpochMs: 1 });
  await db.doc(`series/${code}`).update({ nextRunAt: Date.now() - 1 });

  const unauth = await cron.GET(new Request('https://meld.test/api/cron/recurring'));
  assert.equal(unauth.status, 401);
  const res = await cron.GET(new Request('https://meld.test/api/cron/recurring', { headers: { authorization: 'Bearer cron-secret' } }));
  const report = await res.json();
  const rolled = report.rolled.find((r) => r.startsWith(code));
  assert.ok(rolled, JSON.stringify(report));
  const next = rolled.split('->')[1];
  const series = (await db.doc(`series/${code}`).get()).data();
  assert.equal(series.latestCode, next);
  assert.ok(series.nextRunAt > Date.now());
  assert.equal((await db.doc(`rooms/${code}`).get()).get('nextRoomCode'), next);
  assert.equal((await db.doc(`rooms/${code}`).get()).get('isLocked'), true);
  const newRoom = (await db.doc(`rooms/${next}`).get()).data();
  assert.equal(newRoom.seriesId, code);
  assert.equal(newRoom.question, 'Standup day?');
  assert.equal((await db.doc(`rooms/${next}/private/config`).get()).get('webhookUrl'), hookUrl, 'webhook carried over');
  assert.ok(webhookHits.some((h) => JSON.parse(h.body).event === 'poll.opened'));
  assert.ok(outbound.some((o) => o.body.text?.includes('New poll')), 'slack told about the new poll');
});
