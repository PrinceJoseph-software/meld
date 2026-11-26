// Firestore data model shared with the Android app (docs/DATA_MODEL.md, firestore.rules).
import {
  collection,
  deleteDoc,
  doc,
  getDocs,
  onSnapshot,
  setDoc,
  updateDoc,
  writeBatch,
  getDoc,
  type Unsubscribe,
} from 'firebase/firestore';
import { ensureSignedIn, services } from './firebase';
import { clean, effectiveName, getIdentity } from './identity';

export type RoomMode = 'QUICK_POLL' | 'TIME_SLOTS';

export interface Room {
  code: string;
  question: string;
  mode: RoomMode;
  allowMultiple: boolean;
  allowFriendsToAdd: boolean;
  isAnonymous: boolean;
  deadlineEpochMs: number | null;
  ownerUid: string;
  winnerOptionId: string | null;
  isLocked: boolean;
  createdAtEpochMs: number;
  expiresAtEpochMs: number;
}

export interface Option {
  id: string;
  label: string;
  sortOrder: number;
  slotStartMs: number | null;
  slotEndMs: number | null;
}

export interface Ballot {
  authUid: string;
  voterUid: string;
  voterName: string;
  badgeColorIndex: number;
  optionIds: string[];
  updatedAtEpochMs: number;
}

export interface Comment {
  id: string;
  authorAuthUid: string;
  voterUid: string;
  voterName: string;
  badgeColorIndex: number;
  optionId: string | null;
  transcript: string;
  durationSeconds: number;
  createdAtEpochMs: number;
  hasAudio: boolean;
  audioMimeType: string | null;
}

export interface RoomState {
  room: Room;
  options: Option[];
  ballots: Ballot[];
  comments: Comment[];
  fromCache: boolean;
  hasPendingWrites: boolean;
}

const HOUR = 60 * 60 * 1000;
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

export function sanitizeCode(raw: string): string | null {
  const c = raw.trim().replace(/^#/, '').toUpperCase();
  return /^[A-Z0-9]{4,10}$/.test(c) ? c : null;
}

function randomCode() {
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return Array.from(bytes, (b) => ALPHABET[b % ALPHABET.length]).join('');
}

export interface NewOption { label: string; slotStartMs?: number | null }

export interface NewRoom {
  question: string;
  mode: RoomMode;
  options: NewOption[];
  allowMultiple: boolean;
  allowFriendsToAdd: boolean;
  isAnonymous: boolean;
  deadlineMinutes: number | null;
}

/**
 * Creates a room. Works offline: the write is queued in the local cache and the room is
 * usable immediately; it reaches the server (and other people) when you reconnect.
 */
export async function createRoom(input: NewRoom): Promise<string> {
  const uid = await ensureSignedIn();
  const { db } = services();
  const now = Date.now();
  const code = randomCode();
  const options = input.options
    .map((o) => ({ ...o, label: clean(o.label, 140) }))
    .filter((o) => o.label)
    .slice(0, 16);
  const batch = writeBatch(db);
  batch.set(doc(db, 'rooms', code), {
    question: clean(input.question, 200),
    mode: input.mode,
    allowMultiple: input.allowMultiple,
    allowFriendsToAdd: input.allowFriendsToAdd,
    isAnonymous: input.isAnonymous,
    deadlineEpochMs: input.deadlineMinutes ? now + input.deadlineMinutes * 60_000 : null,
    ownerUid: uid,
    winnerOptionId: null,
    isLocked: false,
    createdAtEpochMs: now,
    expiresAtEpochMs: now + 14 * 24 * HOUR,
    updatedAtEpochMs: now,
    schemaVersion: 1,
  });
  options.forEach((o, idx) => {
    const id = `${code}_opt_${crypto.randomUUID().slice(0, 8)}`;
    const start = input.mode === 'TIME_SLOTS' && o.slotStartMs ? o.slotStartMs : null;
    batch.set(doc(db, 'rooms', code, 'options', id), {
      label: o.label,
      sortOrder: idx,
      slotStartMs: start,
      slotEndMs: start ? start + HOUR : null,
      createdByUid: uid,
    });
  });
  // Not awaited: resolves only on server acknowledgement, which never comes offline.
  batch.commit().catch((e) => console.warn('createRoom', e));
  return code;
}

/** Live room state (server or offline cache). Calls onMissing if the code doesn't exist. */
export function watchRoom(
  code: string,
  onState: (s: RoomState) => void,
  onMissing: () => void,
  onError: (e: Error) => void,
): () => void {
  let room: Room | null = null;
  let options: Option[] | null = null;
  let ballots: Ballot[] | null = null;
  let comments: Comment[] | null = null;
  let fromCache = true;
  let pending = false;
  const unsubs: Unsubscribe[] = [];
  let cancelled = false;

  const emit = () => {
    if (room && options && ballots && comments) {
      onState({ room, options, ballots, comments, fromCache, hasPendingWrites: pending });
    }
  };

  ensureSignedIn()
    .then(() => {
      if (cancelled) return;
      const { db } = services();
      const meta = { includeMetadataChanges: true };
      unsubs.push(onSnapshot(doc(db, 'rooms', code), meta, (snap) => {
        fromCache = snap.metadata.fromCache;
        if (!snap.exists()) {
          if (!snap.metadata.fromCache) onMissing();
          return;
        }
        room = { code, ...(snap.data() as Omit<Room, 'code'>) };
        emit();
      }, onError));
      unsubs.push(onSnapshot(collection(db, 'rooms', code, 'options'), meta, (snap) => {
        options = snap.docs
          .map((d) => ({ id: d.id, ...(d.data() as Omit<Option, 'id'>) }))
          .sort((a, b) => a.sortOrder - b.sortOrder);
        emit();
      }, onError));
      unsubs.push(onSnapshot(collection(db, 'rooms', code, 'ballots'), meta, (snap) => {
        pending = snap.metadata.hasPendingWrites;
        ballots = snap.docs.map((d) => ({ authUid: d.id, ...(d.data() as Omit<Ballot, 'authUid'>) }));
        emit();
      }, onError));
      unsubs.push(onSnapshot(collection(db, 'rooms', code, 'comments'), meta, (snap) => {
        comments = snap.docs
          .map((d) => ({ id: d.id, ...(d.data() as Omit<Comment, 'id'>) }))
          .sort((a, b) => a.createdAtEpochMs - b.createdAtEpochMs);
        emit();
      }, onError));
    })
    .catch(onError);

  return () => {
    cancelled = true;
    unsubs.forEach((u) => u());
  };
}

export function isVotingOpen(room: Room, now = Date.now()) {
  return !room.isLocked && (room.deadlineEpochMs == null || now < room.deadlineEpochMs);
}

/** Sets this device's ballot to exactly `optionIds` (empty = withdraw). */
export async function setMyBallot(room: Room, optionIds: string[]) {
  const uid = await ensureSignedIn();
  const { db } = services();
  const ref = doc(db, 'rooms', room.code, 'ballots', uid);
  const me = getIdentity();
  const write = optionIds.length === 0
    ? deleteDoc(ref)
    : setDoc(ref, {
        voterUid: me.uid,
        voterName: effectiveName(me, room.isAnonymous),
        badgeColorIndex: me.badgeColorIndex,
        avatarStyleIndex: 0,
        optionIds: room.allowMultiple ? optionIds.slice(0, 20) : optionIds.slice(0, 1),
        updatedAtEpochMs: Date.now(),
      });
  write.catch((e) => console.warn('setMyBallot', e));
}

export async function addOption(room: Room, label: string, nextSort: number) {
  const uid = await ensureSignedIn();
  const { db } = services();
  const id = `${room.code}_opt_${crypto.randomUUID().slice(0, 8)}`;
  setDoc(doc(db, 'rooms', room.code, 'options', id), {
    label: clean(label, 140),
    sortOrder: nextSort,
    slotStartMs: null,
    slotEndMs: null,
    createdByUid: uid,
  }).catch((e) => console.warn('addOption', e));
}

export async function closeRoom(room: Room, winnerOptionId: string | null) {
  const { db } = services();
  updateDoc(doc(db, 'rooms', room.code), {
    isLocked: true,
    winnerOptionId,
    updatedAtEpochMs: Date.now(),
  }).catch((e) => console.warn('closeRoom', e));
}

export async function reopenRoom(room: Room) {
  const { db } = services();
  const deadline = room.deadlineEpochMs && room.deadlineEpochMs > Date.now() ? room.deadlineEpochMs : null;
  updateDoc(doc(db, 'rooms', room.code), {
    isLocked: false,
    winnerOptionId: null,
    deadlineEpochMs: deadline,
    updatedAtEpochMs: Date.now(),
  }).catch((e) => console.warn('reopenRoom', e));
}

export async function resetVotes(room: Room) {
  const { db } = services();
  const snap = await getDocs(collection(db, 'rooms', room.code, 'ballots'));
  const batch = writeBatch(db);
  snap.docs.forEach((d) => batch.delete(d.ref));
  batch.commit().catch((e) => console.warn('resetVotes', e));
  if (room.isLocked) reopenRoom(room);
}

export async function addComment(
  room: Room,
  transcript: string,
  optionId: string | null,
  audio?: { base64: string; mimeType: string; seconds: number },
) {
  const uid = await ensureSignedIn();
  const { db } = services();
  const me = getIdentity();
  const id = crypto.randomUUID();
  const seconds = Math.min(60, Math.max(1, Math.round(audio?.seconds ?? 1)));
  const batch = writeBatch(db);
  batch.set(doc(db, 'rooms', room.code, 'comments', id), {
    authorAuthUid: uid,
    voterUid: me.uid,
    voterName: effectiveName(me, room.isAnonymous),
    badgeColorIndex: me.badgeColorIndex,
    avatarStyleIndex: 0,
    optionId,
    transcript: clean(transcript, 300) || `Voice note (0:${String(seconds).padStart(2, '0')})`,
    durationSeconds: seconds,
    createdAtEpochMs: Date.now(),
    hasAudio: Boolean(audio),
    audioMimeType: audio?.mimeType ?? null,
  });
  if (audio) {
    batch.set(doc(db, 'rooms', room.code, 'audio', id), {
      authorAuthUid: uid,
      mimeType: audio.mimeType,
      dataBase64: audio.base64,
    });
  }
  batch.commit().catch((e) => console.warn('addComment', e));
}

export async function deleteComment(room: Room, id: string) {
  const { db } = services();
  const batch = writeBatch(db);
  batch.delete(doc(db, 'rooms', room.code, 'comments', id));
  batch.delete(doc(db, 'rooms', room.code, 'audio', id));
  batch.commit().catch((e) => console.warn('deleteComment', e));
}

export async function fetchAudio(room: Room, id: string): Promise<string | null> {
  const { db } = services();
  const snap = await getDoc(doc(db, 'rooms', room.code, 'audio', id));
  const data = snap.data() as { mimeType?: string; dataBase64?: string } | undefined;
  if (!data?.dataBase64) return null;
  return `data:${data.mimeType || 'audio/mp4'};base64,${data.dataBase64}`;
}

export const MAX_AUDIO_BASE64 = 700_000;
