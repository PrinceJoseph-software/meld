// Security-rule tests for firestore.rules, run against the Firestore emulator:
//   cd rules-tests && npm test
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, it } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  collection,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

let env;
const CODE = 'ABC234';

const room = (ownerUid, extra = {}) => ({
  question: 'Lunch?',
  mode: 'QUICK_POLL',
  allowMultiple: false,
  allowFriendsToAdd: false,
  isAnonymous: false,
  deadlineEpochMs: null,
  ownerUid,
  winnerOptionId: null,
  isLocked: false,
  createdAtEpochMs: 1,
  expiresAtEpochMs: 2,
  updatedAtEpochMs: 1,
  schemaVersion: 1,
  ...extra,
});

const ballot = (optionIds) => ({
  voterUid: 'app-uid',
  voterName: 'Ada',
  badgeColorIndex: 1,
  avatarStyleIndex: 0,
  optionIds,
  updatedAtEpochMs: Date.now(),
});

const db = (uid) => env.authenticatedContext(uid).firestore();

async function seed(roomExtra = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const f = ctx.firestore();
    await setDoc(doc(f, `rooms/${CODE}`), room('owner', roomExtra));
    await setDoc(doc(f, `rooms/${CODE}/options/o1`), { label: 'Pizza', sortOrder: 0, slotStartMs: null, slotEndMs: null, createdByUid: 'owner' });
    await setDoc(doc(f, `rooms/${CODE}/options/o2`), { label: 'Tacos', sortOrder: 1, slotStartMs: null, slotEndMs: null, createdByUid: 'owner' });
  });
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-meld',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});
after(async () => env.cleanup());
beforeEach(async () => env.clearFirestore());

describe('rooms', () => {
  it('lets a signed-in user create a room they own, with options in the same batch', async () => {
    const f = db('alice');
    const b = writeBatch(f);
    b.set(doc(f, `rooms/${CODE}`), room('alice'));
    b.set(doc(f, `rooms/${CODE}/options/o1`), { label: 'Pizza', sortOrder: 0, slotStartMs: null, slotEndMs: null, createdByUid: 'alice' });
    await assertSucceeds(b.commit());
  });

  it('rejects unauthenticated access and rooms owned by someone else', async () => {
    await assertFails(setDoc(doc(env.unauthenticatedContext().firestore(), `rooms/${CODE}`), room('x')));
    await assertFails(setDoc(doc(db('alice'), `rooms/${CODE}`), room('bob')));
  });

  it('rejects invalid codes and unexpected fields', async () => {
    await assertFails(setDoc(doc(db('alice'), 'rooms/bad-code'), room('alice')));
    await assertFails(setDoc(doc(db('alice'), `rooms/${CODE}`), { ...room('alice'), isAdmin: true }));
  });

  it('allows get by code but not listing all rooms', async () => {
    await seed();
    await assertSucceeds(getDoc(doc(db('bob'), `rooms/${CODE}`)));
    await assertFails(getDocs(collection(db('bob'), 'rooms')));
  });

  it('only the owner can close or reopen a room, and ownership cannot move', async () => {
    await seed();
    await assertFails(updateDoc(doc(db('bob'), `rooms/${CODE}`), { isLocked: true }));
    await assertSucceeds(updateDoc(doc(db('owner'), `rooms/${CODE}`), { isLocked: true, winnerOptionId: 'o1' }));
    await assertFails(updateDoc(doc(db('owner'), `rooms/${CODE}`), { ownerUid: 'bob' }));
  });
});

describe('ballots', () => {
  it('a voter writes only their own ballot', async () => {
    await seed();
    await assertSucceeds(setDoc(doc(db('bob'), `rooms/${CODE}/ballots/bob`), ballot(['o1'])));
    await assertFails(setDoc(doc(db('bob'), `rooms/${CODE}/ballots/carol`), ballot(['o1'])));
  });

  it('single-choice rooms accept one option only', async () => {
    await seed();
    await assertFails(setDoc(doc(db('bob'), `rooms/${CODE}/ballots/bob`), ballot(['o1', 'o2'])));
  });

  it('multiple-choice rooms accept several options', async () => {
    await seed({ allowMultiple: true });
    await assertSucceeds(setDoc(doc(db('bob'), `rooms/${CODE}/ballots/bob`), ballot(['o1', 'o2'])));
  });

  it('no voting after the room is closed or the deadline passes', async () => {
    await seed({ isLocked: true });
    await assertFails(setDoc(doc(db('bob'), `rooms/${CODE}/ballots/bob`), ballot(['o1'])));
    await env.clearFirestore();
    await seed({ deadlineEpochMs: Date.now() - 60_000 });
    await assertFails(setDoc(doc(db('bob'), `rooms/${CODE}/ballots/bob`), ballot(['o1'])));
  });

  it('the owner can reset (delete) other ballots; others cannot', async () => {
    await seed();
    await setDoc(doc(db('bob'), `rooms/${CODE}/ballots/bob`), ballot(['o1']));
    await assertFails(deleteDoc(doc(db('carol'), `rooms/${CODE}/ballots/bob`)));
    await assertSucceeds(deleteDoc(doc(db('owner'), `rooms/${CODE}/ballots/bob`)));
  });
});

describe('options', () => {
  it('friends may add options only when the owner allows it', async () => {
    await seed();
    const opt = { label: 'Sushi', sortOrder: 2, slotStartMs: null, slotEndMs: null, createdByUid: 'bob' };
    await assertFails(setDoc(doc(db('bob'), `rooms/${CODE}/options/o3`), opt));
    await env.clearFirestore();
    await seed({ allowFriendsToAdd: true });
    await assertSucceeds(setDoc(doc(db('bob'), `rooms/${CODE}/options/o3`), opt));
    await assertFails(updateDoc(doc(db('bob'), `rooms/${CODE}/options/o1`), { label: 'Hacked' }));
  });
});

describe('comments and audio', () => {
  const comment = (author) => ({
    authorAuthUid: author, voterUid: 'v', voterName: 'Ada', badgeColorIndex: 0, avatarStyleIndex: 0,
    optionId: null, transcript: 'Pizza is closer', durationSeconds: 3, createdAtEpochMs: 1,
    hasAudio: false, audioMimeType: null,
  });

  it('authors post as themselves and delete their own; the owner can moderate', async () => {
    await seed();
    await assertFails(setDoc(doc(db('bob'), `rooms/${CODE}/comments/c1`), comment('carol')));
    await assertSucceeds(setDoc(doc(db('bob'), `rooms/${CODE}/comments/c1`), comment('bob')));
    await assertFails(deleteDoc(doc(db('carol'), `rooms/${CODE}/comments/c1`)));
    await assertSucceeds(deleteDoc(doc(db('owner'), `rooms/${CODE}/comments/c1`)));
  });

  it('caps audio size', async () => {
    await seed();
    const big = 'A'.repeat(700_001);
    await assertFails(setDoc(doc(db('bob'), `rooms/${CODE}/audio/c1`), { authorAuthUid: 'bob', mimeType: 'audio/mp4', dataBase64: big }));
    await assertSucceeds(setDoc(doc(db('bob'), `rooms/${CODE}/audio/c1`), { authorAuthUid: 'bob', mimeType: 'audio/mp4', dataBase64: 'AAAA' }));
  });
});
