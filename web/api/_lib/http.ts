import { waitUntil } from '@vercel/functions';

export const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', ...headers } });

/** Public origin of the web app, for links in bot messages, webhooks and feeds. */
export function baseUrl(request?: Request): string {
  const env = process.env.PUBLIC_BASE_URL || process.env.VITE_PUBLIC_BASE_URL;
  if (env) return env.replace(/\/+$/, '');
  if (request) {
    const host = request.headers.get('x-forwarded-host') || request.headers.get('host');
    const proto = request.headers.get('x-forwarded-proto') || 'https';
    if (host) return `${proto}://${host}`;
  }
  if (process.env.VERCEL_PROJECT_PRODUCTION_URL) return `https://${process.env.VERCEL_PROJECT_PRODUCTION_URL}`;
  return 'http://localhost:4173';
}

/** Runs work after the response is sent (Vercel keeps the function alive until it settles). */
export function afterResponse(task: Promise<unknown>) {
  const guarded = task.catch((e) => console.warn('background task failed', e));
  try {
    waitUntil(guarded);
  } catch {
    /* not on Vercel: the promise still runs in this process */
  }
}
