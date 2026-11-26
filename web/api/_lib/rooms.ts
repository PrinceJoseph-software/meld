import { createHash, createHmac, randomBytes, randomInt } from 'node:crypto';
import { computeResults } from '../../src/shared/tally.js';
import { db } from './admin.js';
import { safeFetch } from './safeFetch.js';

const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const HOUR = 3_600_000;

export const sha256 = (s: string) => createHash('sha256').update(s).digest('hex');

export interface NewServerRoom {
  question: string;
  options: { label: string; slotStartMs?: number | null }[];
  mode?: 'QUICK_POLL' | 'TIME_SLOTS';
  allowMultiple?: boolean;
  isRanked?: boolean;
  isAnonymous?: boolean;
  allowFriendsToAdd?: boolean;
  deadlineMinutes?: number | null;
  repeatWeekly?: boolean;
  source: 'slack' | 'discord' | 'server';
  /** For bot rooms: a placeholder like "slack:T1:U2" until the creator claims it. */
  ownerUid: string;
  notify?: { slack?: { channelId: string }; discord?: { channelId: string } };
  /** Recurring instance: join an existing series instead of starting one; no claim token. */
  seriesId?: string;
}

export const clean = (raw: string, max: number) =>
  // eslint-disable-next-line no-control-regex
  raw.replace(/[\u0000-\u001F\u007F]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);

async function uniqueCode(): Promise<string> {
  for (let i = 0; i < 8; i++) {
    const code = Array.from({ length: 6 }, () => ALPHABET[randomInt(ALPHABET.length)]).join('');
    const snap = await db().doc(`rooms/${code}`).get();
    if (!snap.exists) return code;
  }
  throw new Error('could not allocate a room code');
}

/** Creates a room server-side. Returns the code and a one-time claim token for the creator. */
export async function createServerRoom(input: NewServerRoom, now = Date.now()) {
  const code = await uniqueCode();
  const claimToken = randomBytes(18).toString('base64url');
  const mode = input.mode ?? 'QUICK_POLL';
  const options = input.options.map((o) => ({ ...o, label: clean(o.label, 140) })).filter((o) => o.label).slice(0, 16);
  if (options.length < 2) throw new Error('need at least 2 options');
  const batch = db().batch();
  const roomRef = db().doc(`rooms/${code}`);
  batch.set(roomRef, {
    question: clean(input.question, 200),
    mode,
    allowMultiple: Boolean(input.allowMultiple) && !input.isRanked,
    allowFriendsToAdd: Boolean(input.allowFriendsToAdd),
    isAnonymous: Boolean(input.isAnonymous),
    isRanked: Boolean(input.isRanked),
    repeatWeekly: Boolean(input.repeatWeekly),
    seriesId: input.seriesId ?? (input.repeatWeekly ? code : null),
    nextRoomCode: null,
    notifiedAt: null,
    source: input.source,
    deadlineEpochMs: input.deadlineMinutes ? now + input.deadlineMinutes * 60_000 : null,
    ownerUid: input.ownerUid,
    winnerOptionId: null,
    isLocked: false,
    createdAtEpochMs: now,
    expiresAtEpochMs: now + 14 * 24 * HOUR,
    updatedAtEpochMs: now,
    schemaVersion: 1,
  });
  options.forEach((o, idx) => {
    const start = mode === 'TIME_SLOTS' && o.slotStartMs ? o.slotStartMs : null;
    batch.set(roomRef.collection('options').doc(`${code}_opt_${randomBytes(4).toString('hex')}`), {
      label: o.label, sortOrder: idx, slotStartMs: start, slotEndMs: start ? start + HOUR : null, createdByUid: input.ownerUid,
    });
  });
  if (!input.seriesId) {
    batch.set(roomRef.collection('private').doc('claim'), { tokenHash: sha256(claimToken), createdAtEpochMs: now });
  }
  if (input.notify) batch.set(roomRef.collection('private').doc('notify'), input.notify);
  if (input.repeatWeekly && !input.seriesId) {
    batch.set(db().doc(`series/${code}`), {
      ownerUid: input.ownerUid, latestCode: code, active: true, intervalDays: 7,
      nextRunAt: now + 7 * 24 * HOUR, createdAtEpochMs: now,
    });
  }
  await batch.commit();
  return { code, claimToken };
}

export interface RoomSummary {
  code: string;
  question: string;
  mode: string;
  isRanked: boolean;
  url: string;
  winner: { id: string; label: string; votes: number; slotStartMs: number | null; slotEndMs: number | null } | null;
  results: { id: string; label: string; votes: number }[];
  totalVoters: number;
  tie: boolean;
}

export async function summarize(code: string, base: string): Promise<RoomSummary | null> {
  const ref = db().doc(`rooms/${code}`);
  const [room, opts, ballots] = await Promise.all([ref.get(), ref.collection('options').get(), ref.collection('ballots').get()]);
  if (!room.exists) return null;
  const r = room.data()!;
  const options = opts.docs.map((d) => ({ id: d.id, ...(d.data() as { label: string; sortOrder: number; slotStartMs: number | null; slotEndMs: number | null }) }))
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const ballotLists = ballots.docs.map((d) => (d.get('optionIds') as string[]) ?? []);
  const res = computeResults(Boolean(r.isRanked), options, ballotLists);
  const winnerId: string | null = r.winnerOptionId ?? res.leaderId;
  const w = options.find((o) => o.id === winnerId);
  return {
    code,
    question: r.question,
    mode: r.mode,
    isRanked: Boolean(r.isRanked),
    url: `${base}/r/${code}`,
    winner: w ? { id: w.id, label: w.label, votes: res.counts[w.id] ?? 0, slotStartMs: w.slotStartMs ?? null, slotEndMs: w.slotEndMs ?? null } : null,
    results: options.map((o) => ({ id: o.id, label: o.label, votes: res.counts[o.id] ?? 0 })).sort((a, b) => b.votes - a.votes),
    totalVoters: ballots.size,
    tie: res.tiedLeaders.length > 1 || Boolean(res.irv?.tie),
  };
}

/**
 * Server-authoritative "decision reached". Verifies the room really is closed (owner locked it
 * or the deadline passed by the server clock), finalizes it if needed, and notifies exactly
 * once (transaction on notifiedAt). Safe to call from any client, any number of times.
 */
export async function notifyDecision(code: string, base: string, now = Date.now()): Promise<'notified' | 'already' | 'open' | 'missing'> {
  const ref = db().doc(`rooms/${code}`);
  const claimed = await db().runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) return 'missing' as const;
    const r = snap.data()!;
    const closed = r.isLocked === true || (typeof r.deadlineEpochMs === 'number' && now >= r.deadlineEpochMs);
    if (!closed) return 'open' as const;
    if (r.notifiedAt) return 'already' as const;
    tx.update(ref, { notifiedAt: now });
    return 'claimed' as const;
  });
  if (claimed !== 'claimed') return claimed;

  const summary = await summarize(code, base);
  if (!summary) return 'missing';
  const room = (await ref.get()).data()!;
  if (!room.isLocked) {
    // Deadline passed: lock it server-side so every client shows the same winner.
    await ref.update({ isLocked: true, winnerOptionId: summary.winner?.id ?? null, updatedAtEpochMs: now });
  }
  await deliver(code, { event: 'decision.reached', decidedAt: now, ...summary });
  return 'notified';
}

export async function deliver(code: string, payload: Record<string, unknown> & { question: string; url: string }) {
  const priv = db().collection(`rooms/${code}/private`);
  const [config, notify] = await Promise.all([priv.doc('config').get(), priv.doc('notify').get()]);
  const tasks: Promise<unknown>[] = [];

  const hook = config.get('webhookUrl') as string | undefined;
  if (hook) {
    const body = JSON.stringify(payload);
    const ts = String(Math.floor(Date.now() / 1000));
    const secret = (config.get('webhookSecret') as string | undefined) ?? '';
    const sig = createHmac('sha256', secret).update(`${ts}.${body}`).digest('hex');
    tasks.push(safeFetch(hook, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'user-agent': 'Meld-Webhooks/1.0',
        'x-meld-event': String(payload.event),
        'x-meld-timestamp': ts,
        'x-meld-signature': `sha256=${sig}`,
      },
      body,
    }));
  }

  const text = chatText(payload);
  const slack = notify.get('slack') as { channelId: string } | undefined;
  if (slack && process.env.SLACK_BOT_TOKEN) {
    tasks.push(fetch('https://slack.com/api/chat.postMessage', {
      method: 'POST',
      headers: { 'content-type': 'application/json; charset=utf-8', authorization: `Bearer ${process.env.SLACK_BOT_TOKEN}` },
      body: JSON.stringify({ channel: slack.channelId, text, unfurl_links: false }),
    }));
  }
  const discord = notify.get('discord') as { channelId: string } | undefined;
  if (discord && process.env.DISCORD_BOT_TOKEN) {
    tasks.push(fetch(`https://discord.com/api/v10/channels/${discord.channelId}/messages`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', authorization: `Bot ${process.env.DISCORD_BOT_TOKEN}` },
      body: JSON.stringify({ content: text, allowed_mentions: { parse: [] } }),
    }));
  }
  const results = await Promise.allSettled(tasks);
  results.forEach((r) => r.status === 'rejected' && console.warn('delivery failed', r.reason));
}

function chatText(p: Record<string, unknown> & { question: string; url: string }): string {
  if (p.event === 'poll.opened') return `🗳️ New poll: *${p.question}*\nVote: ${p.url}`;
  const w = p.winner as RoomSummary['winner'];
  const tie = p.tie ? ' (tie-break)' : '';
  return w
    ? `✅ Decided: *${p.question}*\nWinner: *${w.label}*${tie} — ${p.totalVoters} voter(s)\n${p.url}`
    : `⏹️ Closed: *${p.question}* — no votes were cast.\n${p.url}`;
}
