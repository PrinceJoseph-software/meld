// Daily scheduler (Vercel Cron, see vercel.json). Two jobs:
//  1. Recurring polls: for each active weekly series whose next run is due, close out the
//     current room (decision notification) and open next week's room with the same question
//     and options (time slots shifted by a week). Old room links point to the new one.
//  2. Backstop: send any decision notification that no client triggered (nobody had the room
//     open when its deadline passed).
// Protected by CRON_SECRET (Vercel sends "Authorization: Bearer <CRON_SECRET>").
import { db } from '../_lib/admin.js';
import { baseUrl, json } from '../_lib/http.js';
import { createServerRoom, deliver, notifyDecision, summarize } from '../_lib/rooms.js';

const WEEK = 7 * 24 * 3_600_000;

export async function GET(request: Request): Promise<Response> {
  const secret = process.env.CRON_SECRET;
  if (!secret || request.headers.get('authorization') !== `Bearer ${secret}`) return json({ error: 'unauthorized' }, 401);
  const now = Date.now();
  const base = baseUrl(request);
  return json(await runScheduler(now, base));
}

export async function runScheduler(now: number, base: string) {
  const report = { rolled: [] as string[], notified: [] as string[], errors: [] as string[] };

  const due = await db().collection('series').where('active', '==', true).where('nextRunAt', '<=', now).limit(25).get();
  for (const s of due.docs) {
    try {
      const series = s.data() as { latestCode: string; ownerUid: string; nextRunAt: number };
      const latestRef = db().doc(`rooms/${series.latestCode}`);
      const [latest, opts, notify, config] = await Promise.all([
        latestRef.get(),
        latestRef.collection('options').get(),
        latestRef.collection('private').doc('notify').get(),
        latestRef.collection('private').doc('config').get(),
      ]);
      if (!latest.exists) continue;
      const r = latest.data()!;
      // Close last week's room with its winner, then send its decision notification.
      if (!r.isLocked) {
        const summary = await summarize(series.latestCode, base);
        await latestRef.update({ isLocked: true, winnerOptionId: summary?.winner?.id ?? null, updatedAtEpochMs: now });
      }
      await notifyDecision(series.latestCode, base, now);

      const duration = r.deadlineEpochMs ? Math.max(60_000, r.deadlineEpochMs - r.createdAtEpochMs) : null;
      const shift = Math.max(WEEK, Math.ceil((now - r.createdAtEpochMs) / WEEK) * WEEK);
      const options = opts.docs
        .map((d) => d.data() as { label: string; sortOrder: number; slotStartMs: number | null })
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((o) => ({ label: o.label, slotStartMs: o.slotStartMs ? o.slotStartMs + shift : null }));
      const { code } = await createServerRoom({
        question: r.question,
        options,
        mode: r.mode,
        allowMultiple: r.allowMultiple,
        isRanked: r.isRanked,
        isAnonymous: r.isAnonymous,
        allowFriendsToAdd: r.allowFriendsToAdd,
        deadlineMinutes: duration ? Math.round(duration / 60_000) : null,
        repeatWeekly: true,
        seriesId: s.id,
        source: 'server',
        ownerUid: series.ownerUid,
        notify: notify.exists ? (notify.data() as never) : undefined,
      }, now);
      if (config.exists) await db().doc(`rooms/${code}/private/config`).set(config.data()!);
      let next = series.nextRunAt;
      while (next <= now) next += WEEK;
      await Promise.all([
        latestRef.update({ nextRoomCode: code }),
        s.ref.update({ latestCode: code, nextRunAt: next }),
      ]);
      await deliver(code, { event: 'poll.opened', code, question: r.question, url: `${base}/r/${code}`, seriesId: s.id });
      report.rolled.push(`${series.latestCode}->${code}`);
    } catch (e) {
      report.errors.push(`${s.id}: ${(e as Error).message}`);
    }
  }

  // Deadline passed in the last 3 days but nobody triggered the decision yet.
  const recent = await db().collection('rooms')
    .where('deadlineEpochMs', '<=', now)
    .where('deadlineEpochMs', '>=', now - 3 * 24 * 3_600_000)
    .limit(100)
    .get();
  for (const room of recent.docs) {
    if (room.get('notifiedAt')) continue;
    try {
      if ((await notifyDecision(room.id, base, now)) === 'notified') report.notified.push(room.id);
    } catch (e) {
      report.errors.push(`${room.id}: ${(e as Error).message}`);
    }
  }
  return report;
}
