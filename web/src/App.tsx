import { lazy, Suspense } from 'react';
import { usePath } from './router';
import { useLang } from './i18n';
import { Landing } from './pages/Landing';
import { Privacy } from './pages/Privacy';

// The app (Firebase, rooms, avatars) is a separate chunk: the landing and privacy pages never
// download or start it. It is precached by the service worker, so it still works offline.
const AppRoutes = lazy(() => import('./AppRoutes'));

export default function App() {
  const path = usePath();
  useLang(); // re-render everything when the language changes

  // Marketing page at the root; the app itself lives at /app. Installed PWAs start at /app.
  if (path === '/' || path === '') return <Landing />;
  if (/^\/privacy\/?$/.test(path)) return <Privacy />;
  return (
    <Suspense fallback={<main className="app" aria-busy="true" />}>
      <AppRoutes />
    </Suspense>
  );
}
