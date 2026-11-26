// Discord slash command (HTTP interactions): /meld question:"…" options:"A | B | C" …
// Set up: docs/INTEGRATIONS.md. Env: DISCORD_PUBLIC_KEY (required), DISCORD_BOT_TOKEN
// (optional, lets Meld post the decision back to the channel).
import { createPublicKey, verify } from 'node:crypto';
import { afterResponse, baseUrl, json } from './_lib/http.js';
import { parseCommand } from './_lib/command.js';
import { createServerRoom } from './_lib/rooms.js';
import { formatMinutes } from './slack.js';

export function verifyDiscord(body: string, signature: string | null, timestamp: string | null, publicKeyHex: string) {
  if (!signature || !timestamp || !publicKeyHex) return false;
  try {
    const key = createPublicKey({
      key: Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), Buffer.from(publicKeyHex, 'hex')]),
      format: 'der',
      type: 'spki',
    });
    return verify(null, Buffer.from(timestamp + body), key, Buffer.from(signature, 'hex'));
  } catch {
    return false;
  }
}

interface Interaction {
  type: number;
  token: string;
  application_id: string;
  channel_id?: string;
  guild_id?: string;
  member?: { user?: { id: string } };
  user?: { id: string };
  data?: { name: string; options?: { name: string; value: string | boolean }[] };
}

const EPHEMERAL = 64;

export async function POST(request: Request): Promise<Response> {
  const body = await request.text();
  if (!verifyDiscord(body, request.headers.get('x-signature-ed25519'), request.headers.get('x-signature-timestamp'), process.env.DISCORD_PUBLIC_KEY ?? '')) {
    return json({ error: 'bad signature' }, 401);
  }
  const i = JSON.parse(body) as Interaction;
  if (i.type === 1) return json({ type: 1 }); // PING
  if (i.type !== 2 || i.data?.name !== 'meld') return json({ type: 4, data: { content: 'Unknown command', flags: EPHEMERAL } });

  const opt = (n: string) => i.data?.options?.find((o) => o.name === n)?.value;
  const flags = [
    opt('ranked') ? '--ranked' : '',
    opt('multi') ? '--multi' : '',
    opt('anonymous') ? '--anon' : '',
    opt('weekly') ? '--weekly' : '',
    opt('deadline') ? `--deadline ${opt('deadline')}` : '',
  ].join(' ');
  const parsed = parseCommand(`${opt('question') ?? ''} | ${opt('options') ?? ''} ${flags}`);
  if ('error' in parsed) return json({ type: 4, data: { content: parsed.error, flags: EPHEMERAL } });

  const userId = i.member?.user?.id ?? i.user?.id ?? 'unknown';
  const base = baseUrl(request);
  const { code, claimToken } = await createServerRoom({
    question: parsed.question,
    options: parsed.options.map((label) => ({ label })),
    isRanked: parsed.isRanked,
    allowMultiple: parsed.allowMultiple,
    isAnonymous: parsed.isAnonymous,
    repeatWeekly: parsed.repeatWeekly,
    deadlineMinutes: parsed.deadlineMinutes,
    source: 'discord',
    ownerUid: `discord:${i.guild_id ?? 'dm'}:${userId}`,
    notify: i.channel_id ? { discord: { channelId: i.channel_id } } : undefined,
  });
  const url = `${base}/r/${code}`;

  // Private follow-up (only the creator sees it) with the link that makes them owner.
  afterResponse(fetch(`https://discord.com/api/v10/webhooks/${i.application_id}/${i.token}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      flags: EPHEMERAL,
      content: `Only you can see this. Manage your poll (close it, crown the winner, add a webhook): ${url}?claim=${claimToken}`,
    }),
  }));

  const how = parsed.isRanked ? 'Rank your choices' : parsed.allowMultiple ? 'Pick any' : 'Pick one';
  return json({
    type: 4,
    data: {
      allowed_mentions: { parse: [] },
      embeds: [{
        title: parsed.question.slice(0, 256),
        url,
        color: 0xff572e,
        description: parsed.options.map((o) => `• ${o}`).join('\n').slice(0, 4000),
        footer: { text: `${how}${parsed.deadlineMinutes ? ` · closes in ${formatMinutes(parsed.deadlineMinutes)}` : ''}${parsed.repeatWeekly ? ' · repeats weekly' : ''} · code ${code}` },
      }],
      components: [{ type: 1, components: [{ type: 2, style: 5, label: 'Vote in Meld', url }] }],
    },
  });
}
