import { lookup } from 'node:dns/promises';
import { isIP } from 'node:net';

function isPrivate(ip: string): boolean {
  if (ip.includes(':')) {
    const v = ip.toLowerCase();
    return v === '::1' || v === '::' || v.startsWith('fc') || v.startsWith('fd') || v.startsWith('fe80') ||
      v.startsWith('::ffff:') && isPrivate(v.slice(7));
  }
  const [a, b] = ip.split('.').map(Number);
  return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) || a >= 224;
}

/**
 * Fetch for user-supplied URLs (webhooks): HTTPS only, no redirects, 5 s timeout, and the
 * host must resolve to a public address (blocks SSRF into internal networks / metadata IPs).
 * MELD_ALLOW_PRIVATE_WEBHOOKS=true relaxes this for local testing only.
 */
export async function safeFetch(url: string, init: RequestInit): Promise<Response> {
  const u = new URL(url);
  const allowPrivate = process.env.MELD_ALLOW_PRIVATE_WEBHOOKS === 'true';
  if (u.protocol !== 'https:' && !(allowPrivate && u.protocol === 'http:')) throw new Error('webhook must be https');
  if (!allowPrivate) {
    const addrs = isIP(u.hostname) ? [{ address: u.hostname }] : await lookup(u.hostname, { all: true });
    if (addrs.length === 0 || addrs.some((a) => isPrivate(a.address))) throw new Error('webhook host is not public');
  }
  return fetch(url, { ...init, redirect: 'manual', signal: AbortSignal.timeout(5000) });
}
