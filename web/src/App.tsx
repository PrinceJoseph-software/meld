import { useEffect } from 'react';
import { ensureSignedIn, firebaseConfigured } from './firebase';
import { useOnline, usePath } from './router';
import { Home } from './pages/Home';
import { RoomPage } from './pages/Room';
import { TopBar } from './components/Logo';
import { sanitizeCode } from './model';
import { LaunchReveal } from './components/Mark';

export default function App() {
  const path = usePath();
  const online = useOnline();

  // Invisible anonymous sign-in, done once while online on the very first visit. The id is
  // stored on the device, so every later visit (online or offline) reuses it: no login screen.
  useEffect(() => {
    if (firebaseConfigured && online) ensureSignedIn().catch(() => {});
  }, [online]);

  if (!firebaseConfigured) {
    return (
      <main className="app">
        <TopBar />
        <section className="panel">
          <div className="eyebrow">Setup needed</div>
          <h1 className="title">Connect Firebase</h1>
          <p className="muted">
            Set the <span className="mono">VITE_FIREBASE_*</span> environment variables (see
            <span className="mono"> web/.env.example</span>) and redeploy.
          </p>
        </section>
      </main>
    );
  }

  const match = path.match(/^\/r\/([^/]+)\/?$/);
  const code = match ? sanitizeCode(decodeURIComponent(match[1])) : null;

  return (
    <main className="app">
      <LaunchReveal />
      {!online && (
        <div className="banner" role="status">
          You're offline. You can keep voting; changes sync when you reconnect.
        </div>
      )}
      {match ? <RoomPage key={code ?? 'invalid'} code={code} /> : <Home />}
    </main>
  );
}
