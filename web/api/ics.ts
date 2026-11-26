// Calendar feed: GET /api/ics?code=ROOM or ?series=SERIES  (also /cal/ROOM.ics, see vercel.json)
// Subscribe once in Google Calendar ("From URL") or Apple Calendar (webcal://…) and decided
// time slots appear automatically — including every week of a recurring poll.
import { db } from './_lib/admin.js';
import { baseUrl } from './_lib/http.js';
import { summarize, type RoomSummary } from './_lib/rooms.js';

const esc = (s: string) => s.replace(/\\/g, '\\\\').replace(/;/g, '\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n');
const stamp = (ms: number) => new Date(ms).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
/** RFC 5545 line folding at 75 octets. */
const fold = (line: string) => line.match(/.{1,73}/g)!.join('\r\n ');

export function buildCalendar(name: string, rooms: RoomSummary[], now = Date.now()): string {
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Meld//Decisions//EN', 'CALSCALE:GREGORIAN', 'METHOD:PUBLISH',
    `X-WR-CALNAME:${esc(name)}`, 'REFRESH-INTERVAL;VALUE=DURATION:PT1H', 'X-PUBLISHED-TTL:PT1H'];
  for (const r of rooms) {
    const w = r.winner;
    if (!w?.slotStartMs) continue;
    lines.push('BEGIN:VEVENT', `UID:${r.code}@meld`, `DTSTAMP:${stamp(now)}`, `DTSTART:${stamp(w.slotStartMs)}`,
      `DTEND:${stamp(w.slotEndMs ?? w.slotStartMs + 3_600_000)}`, `SUMMARY:${esc(r.question)}`,
      `DESCRIPTION:${esc(`Decided in Meld: ${w.label}\n${r.url}`)}`, `URL:${r.url}`, 'END:VEVENT');
  }
  lines.push('END:VCALENDAR');
  return lines.map(fold).join('\r\n') + '\r\n';
}

export async function GET(request: Request): Promise<Response> {
  const params = new URL(request.url).searchParams;
  const code = params.get('code')?.toUpperCase().replace(/\.ICS$/, '') ?? '';
  const series = params.get('series')?.toUpperCase() ?? '';
  const base = baseUrl(request);
  let codes: string[] = [];
  let name = 'Meld';
  if (/^[A-Z0-9]{4,10}$/.test(series)) {
    const snap = await db().collection('rooms').where('seriesId', '==', series).limit(60).get();
    codes = snap.docs.filter((d) => d.get('isLocked') || (d.get('deadlineEpochMs') ?? Infinity) <= Date.now()).map((d) => d.id);
    name = snap.docs[0]?.get('question') ?? name;
  } else if (/^[A-Z0-9]{4,10}$/.test(code)) {
    const room = await db().doc(`rooms/${code}`).get();
    if (!room.exists) return new Response('Not found', { status: 404 });
    name = room.get('question');
    const decided = room.get('isLocked') || (room.get('deadlineEpochMs') ?? Infinity) <= Date.now();
    if (decided) codes = [code];
  } else {
    return new Response('Pass ?code= or ?series=', { status: 400 });
  }
  const rooms = (await Promise.all(codes.map((c) => summarize(c, base)))).filter((r): r is RoomSummary => r !== null);
  return new Response(buildCalendar(name, rooms), {
    headers: {
      'content-type': 'text/calendar; charset=utf-8',
      'content-disposition': `inline; filename="meld-${series || code}.ics"`,
      'cache-control': 'public, max-age=600',
    },
  });
}
