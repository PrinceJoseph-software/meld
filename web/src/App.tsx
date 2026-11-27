import { useEffect, useState } from 'react';
import { ensureSignedIn, firebaseConfigured } from './firebase';
import { useOnline, usePath } from './router';
import { Home } from './pages/Home';
import { RoomPage } from './pages/Room';
import { Landing } from './pages/Landing';
import { Privacy } from './pages/Privacy';
import { TopBar } from './components/Logo';
import { getSeries, sanitizeCode } from './model';
import { navigate } from './router';
import { LaunchReveal } from './components/Mark';
import { t, useLang } from './i18n';

export default function App() {
  const path = usePath();
  const online = useOnline();
  useLang(); // re-render everything when the language changes

  // Invisible anonymous sign-in, done once while online on the very first visit. The id is
  // stored on the device, so every later visit (online or offline) reuses it: no login screen.
  useEffect(() => {
    if (firebaseConfigured && online) ensureSignedIn().catch(() => {});
  }, [online]);

  // Marketing page at the root; the app itself lives at /app. Installed PWAs start at /app.
  if (path === '/' || path === '') return <Landing />;
  if (/^\/privacy\/?$/.test(path)) return <Privacy />;

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

  const embed = path.match(/^\/embed\/([^/]+)\/?$/);
  if (embed) {
    // Website embed (iframe): just the live poll, no app chrome.
    const code = sanitizeCode(decodeURIComponent(embed[1]));
    return (
      <main className="app embedded">
        <RoomPage key={code ?? 'invalid'} code={code} embedded />
      </main>
    );
  }

  const series = path.match(/^\/s\/([^/]+)\/?$/);
  if (series) return <SeriesRedirect id={sanitizeCode(decodeURIComponent(series[1]))} />;

  const match = path.match(/^\/r\/([^/]+)\/?$/);
  const code = match ? sanitizeCode(decodeURIComponent(match[1])) : null;

  return (
    <main className="app">
      <LaunchReveal />
      {!online && (
        <div className="banner" role="status">
          {t('common.offline')}
        </div>
      )}
      {match ? <RoomPage key={code ?? 'invalid'} code={code} /> : <Home />}
    </main>
  );
}

/** /s/SERIES: a permanent link that always opens the current week's poll of a series. */
function SeriesRedirect({ id }: { id: string | null }) {
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    if (!id) return setFailed(true);
    getSeries(id)
      .then((s) => (s ? navigate(`/r/${s.latestCode}`, true) : setFailed(true)))
      .catch(() => setFailed(true));
  }, [id]);
  return (
    <main className="app">
      <TopBar />
      <section className="panel">
        <p className="muted">{failed ? t('series.missing') : t('series.opening')}</p>
      </section>
    </main>
  );
}
