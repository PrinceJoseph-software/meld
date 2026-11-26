// Slack slash command: /meld Question? | A | B | C [--ranked] ...
// Set up: docs/INTEGRATIONS.md. Env: SLACK_SIGNING_SECRET (required), SLACK_BOT_TOKEN
// (optional, lets Meld post the decision back to the channel).
import { createHmac, timingSafeEqual } from 'node:crypto';
import { afterResponse, baseUrl, json } from './_lib/http.js';
import { parseCommand, USAGE } from './_lib/command.js';
import { createServerRoom } from './_lib/rooms.js';

export function verifySlack(body: string, timestamp: string | null, signature: string | null, secret: string, now = Date.now()) {
  if (!timestamp || !signature || !secret) return false;
  if (Math.abs(now / 1000 - Number(timestamp)) > 300) return false; // replay window
  const expected = `v0=${createHmac('sha256', secret).update(`v0:${timestamp}:${body}`).digest('hex')}`;
  const a = Buffer.from(expected);
  const b = Buffer.from(signature);
  return a.length === b.length && timingSafeEqual(a, b);
}

export async function POST(request: Request): Promise<Response> {
  const body = await request.text();
  const ok = verifySlack(
    body,
    request.headers.get('x-slack-request-timestamp'),
    request.headers.get('x-slack-signature'),
    process.env.SLACK_SIGNING_SECRET ?? '',
  );
  if (!ok) return json({ error: 'bad signature' }, 401);

  const form = new URLSearchParams(body);
  const text = form.get('text') ?? '';
  if (!text.trim() || text.trim() === 'help') {
    return json({ response_type: 'ephemeral', text: `${USAGE}\nExample: \`/meld Lunch? | Tacos | Ramen | Pizza --deadline 1h\`` });
  }
  const parsed = parseCommand(text);
  if ('error' in parsed) return json({ response_type: 'ephemeral', text: parsed.error });

  const team = form.get('team_id') ?? 'T';
  const user = form.get('user_id') ?? 'U';
  const channel = form.get('channel_id');
  const base = baseUrl(request);
  const { code, claimToken } = await createServerRoom({
    question: parsed.question,
    options: parsed.options.map((label) => ({ label })),
    isRanked: parsed.isRanked,
    allowMultiple: parsed.allowMultiple,
    isAnonymous: parsed.isAnonymous,
    repeatWeekly: parsed.repeatWeekly,
    deadlineMinutes: parsed.deadlineMinutes,
    source: 'slack',
    ownerUid: `slack:${team}:${user}`,
    notify: channel ? { slack: { channelId: channel } } : undefined,
  });
  const url = `${base}/r/${code}`;

  // Private follow-up to the creator only: the link that makes them the room's owner.
  const responseUrl = form.get('response_url');
  if (responseUrl?.startsWith('https://hooks.slack.com/')) {
    afterResponse(fetch(responseUrl, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({
        response_type: 'ephemeral',
        replace_original: false,
        text: `Only you can see this. Open to manage your poll (close it, crown the winner, add a webhook): ${url}?claim=${claimToken}`,
      }),
    }));
  }

  const how = parsed.isRanked ? 'Rank your choices' : parsed.allowMultiple ? 'Pick any' : 'Pick one';
  const when = parsed.deadlineMinutes ? ` · closes in ${formatMinutes(parsed.deadlineMinutes)}` : '';
  return json({
    response_type: 'in_channel',
    text: `${parsed.question} — vote: ${url}`,
    blocks: [
      { type: 'section', text: { type: 'mrkdwn', text: `*${escapeMd(parsed.question)}*\n${parsed.options.map((o) => `• ${escapeMd(o)}`).join('\n')}` } },
      { type: 'context', elements: [{ type: 'mrkdwn', text: `${how}${when}${parsed.repeatWeekly ? ' · repeats weekly' : ''} · code \`${code}\`` }] },
      { type: 'actions', elements: [{ type: 'button', style: 'primary', text: { type: 'plain_text', text: 'Vote in Meld' }, url }] },
    ],
  });
}

export function formatMinutes(m: number) {
  return m % 1440 === 0 ? `${m / 1440}d` : m % 60 === 0 ? `${m / 60}h` : `${m}m`;
}

const escapeMd = (s: string) => s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]!);
