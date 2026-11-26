import { initializeApp, type FirebaseApp } from 'firebase/app';
import { connectAuthEmulator, getAuth, signInAnonymously, type Auth } from 'firebase/auth';
import {
  connectFirestoreEmulator,
  initializeFirestore,
  memoryLocalCache,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from 'firebase/firestore';

const env = import.meta.env;

export const firebaseConfigured = Boolean(env.VITE_FIREBASE_API_KEY && env.VITE_FIREBASE_PROJECT_ID);

let app: FirebaseApp | null = null;
let db: Firestore | null = null;
let auth: Auth | null = null;

/**
 * Firestore with a persistent IndexedDB cache: rooms you have opened stay readable offline,
 * and votes cast offline are queued and sent when the connection returns.
 */
export function services(): { db: Firestore; auth: Auth } {
  if (!firebaseConfigured) throw new Error('Firebase is not configured');
  if (!app) {
    app = initializeApp({
      apiKey: env.VITE_FIREBASE_API_KEY,
      authDomain: env.VITE_FIREBASE_AUTH_DOMAIN,
      projectId: env.VITE_FIREBASE_PROJECT_ID,
      appId: env.VITE_FIREBASE_APP_ID,
    });
    // Embedded in another site (iframe): browsers partition or block third-party storage,
    // so use an in-memory cache there. Everywhere else, the persistent offline cache.
    const embedded = location.pathname.startsWith('/embed/');
    db = initializeFirestore(app, {
      localCache: embedded
        ? memoryLocalCache()
        : persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    });
    auth = getAuth(app);
    if (env.VITE_USE_FIREBASE_EMULATOR === 'true') {
      const host = location.hostname;
      connectFirestoreEmulator(db, host, 8080);
      connectAuthEmulator(auth, `http://${host}:9099`, { disableWarnings: true });
    }
  }
  return { db: db!, auth: auth! };
}

let signInPromise: Promise<string> | null = null;

/**
 * Anonymous sign-in: no account, but a server-verified id for the security rules.
 * Waits for the persisted session first, so a returning visitor keeps the same id
 * (and stays owner of the rooms they created), including when offline.
 */
export function ensureSignedIn(): Promise<string> {
  const { auth } = services();
  if (auth.currentUser) return Promise.resolve(auth.currentUser.uid);
  if (!signInPromise) {
    signInPromise = (async () => {
      await auth.authStateReady();
      if (auth.currentUser) return auth.currentUser.uid;
      const cred = await signInAnonymously(auth);
      return cred.user.uid;
    })().catch((err) => {
      signInPromise = null;
      throw err;
    });
  }
  return signInPromise;
}
