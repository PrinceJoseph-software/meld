// POST /api/claim {"code","token"} + Authorization: Bearer <Firebase ID token>
// Makes the signed-in device the owner of a room created from Slack/Discord.
import { adminAuth, db } from './_lib/admin.js';
import { json } from './_lib/http.js';
import { sha256 } from './_lib/rooms.js';
import { timingSafeEqual } from 'node:crypto';

export async function POST(request: Request): Promise<Response> {
  const auth = request.headers.get('authorization') ?? '';
  const idToken = auth.startsWith('Bearer ') ? auth.slice(7) : '';
  let uid: string;
  try {
    uid = (await adminAuth().verifyIdToken(idToken)).uid;
  } catch {
    return json({ error: 'not signed in' }, 401);
  }
  const { code, token } = (await request.json().catch(() => ({}))) as { code?: string; token?: string };
  if (!code || !/^[A-Z0-9]{4,10}$/.test(code) || !token) return json({ error: 'invalid request' }, 400);

  const roomRef = db().doc(`rooms/${code}`);
  const claimRef = roomRef.collection('private').doc('claim');
  const result = await db().runTransaction(async (tx) => {
    const [room, claim] = await Promise.all([tx.get(roomRef), tx.get(claimRef)]);
    if (!room.exists || !claim.exists) return 'invalid';
    const a = Buffer.from(String(claim.get('tokenHash')));
    const b = Buffer.from(sha256(token));
    if (a.length !== b.length || !timingSafeEqual(a, b)) return 'invalid';
    tx.update(roomRef, { ownerUid: uid, updatedAtEpochMs: Date.now() });
    tx.delete(claimRef); // one-time
    const seriesId = room.get('seriesId');
    if (seriesId) tx.set(db().doc(`series/${seriesId}`), { ownerUid: uid }, { merge: true });
    return 'claimed';
  });
  return result === 'claimed' ? json({ status: 'claimed' }) : json({ error: 'invalid or used claim link' }, 403);
}
