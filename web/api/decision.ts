// POST /api/decision {"code":"ABC123"}: any client calls this when it sees a room close.
// The server re-checks the room itself and notifies (webhook / Slack / Discord) exactly once.
import { baseUrl, json } from './_lib/http.js';
import { notifyDecision } from './_lib/rooms.js';

export async function POST(request: Request): Promise<Response> {
  let code = '';
  try {
    code = String(((await request.json()) as { code?: string }).code ?? '');
  } catch { /* fallthrough */ }
  if (!/^[A-Z0-9]{4,10}$/.test(code)) return json({ error: 'invalid code' }, 400);
  const status = await notifyDecision(code, baseUrl(request));
  return json({ status }, status === 'missing' ? 404 : 200);
}
