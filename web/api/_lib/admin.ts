import { cert, getApps, initializeApp, type App } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, type Firestore } from 'firebase-admin/firestore';

let app: App | null = null;

/**
 * Firebase Admin (server-side, bypasses security rules). Credentials come from the
 * FIREBASE_SERVICE_ACCOUNT env var (the service-account JSON, pasted as one value).
 * Against the emulator (FIRESTORE_EMULATOR_HOST set) no credentials are needed.
 */
function adminApp(): App {
  if (app) return app;
  if (getApps().length) return (app = getApps()[0]);
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  const projectId = process.env.FIREBASE_PROJECT_ID || process.env.VITE_FIREBASE_PROJECT_ID;
  if (raw) {
    const sa = JSON.parse(raw);
    app = initializeApp({ credential: cert(sa), projectId: sa.project_id || projectId });
  } else if (process.env.FIRESTORE_EMULATOR_HOST) {
    app = initializeApp({ projectId: projectId || 'demo-meld' });
  } else {
    throw new Error('FIREBASE_SERVICE_ACCOUNT is not set');
  }
  return app;
}

export const db = (): Firestore => getFirestore(adminApp());
export const adminAuth = () => getAuth(adminApp());
