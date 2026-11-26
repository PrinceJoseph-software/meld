// Local, account-free identity (mirrors the Android IdentityStore). Stored in localStorage.

export const BADGE_COLORS = [
  '#FF451A', '#2563EB', '#0D9488', '#D97706', '#475569', '#7C3AED', '#E11D48', '#0284C7',
];

export interface Identity {
  uid: string;
  displayName: string;
  badgeColorIndex: number;
  maskName: string;
}

const KEY = 'meld.identity.v1';
const RECENT_KEY = 'meld.recentRooms.v1';

function safeGet(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function safeSet(key: string, value: string) {
  try { localStorage.setItem(key, value); } catch { /* private mode: keep in memory */ }
}

function hashIndex(s: string, mod: number) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h) % mod;
}

let cached: Identity | null = null;

export function getIdentity(): Identity {
  if (cached) return cached;
  const raw = safeGet(KEY);
  if (raw) {
    try {
      cached = JSON.parse(raw) as Identity;
      return cached;
    } catch { /* fall through */ }
  }
  const uid = crypto.randomUUID();
  cached = {
    uid,
    displayName: '',
    badgeColorIndex: hashIndex(uid, BADGE_COLORS.length),
    maskName: `Agent ${hashIndex(uid, 89) + 10}`,
  };
  safeSet(KEY, JSON.stringify(cached));
  return cached;
}

export function saveIdentity(next: Partial<Identity>) {
  const merged = { ...getIdentity(), ...next };
  merged.displayName = clean(merged.displayName, 28);
  merged.maskName = clean(merged.maskName, 24) || 'Incognito';
  cached = merged;
  safeSet(KEY, JSON.stringify(merged));
}

export function effectiveName(id: Identity, anonymous: boolean) {
  return anonymous ? id.maskName : id.displayName || 'Guest';
}

export function clean(raw: string, max: number) {
  // eslint-disable-next-line no-control-regex
  return raw.replace(/[\u0000-\u001F\u007F]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);
}

export function initials(name: string) {
  const parts = clean(name, 40).split(' ').filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export interface RecentRoom { code: string; question: string; visitedAt: number }

export function recentRooms(): RecentRoom[] {
  try { return JSON.parse(safeGet(RECENT_KEY) || '[]') as RecentRoom[]; } catch { return []; }
}

export function rememberRoom(code: string, question: string) {
  const list = recentRooms().filter((r) => r.code !== code);
  list.unshift({ code, question, visitedAt: Date.now() });
  safeSet(RECENT_KEY, JSON.stringify(list.slice(0, 20)));
}

export function forgetRoom(code: string) {
  safeSet(RECENT_KEY, JSON.stringify(recentRooms().filter((r) => r.code !== code)));
}
