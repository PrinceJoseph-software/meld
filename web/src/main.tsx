import { StrictMode } from 'react';
import { createRoot, hydrateRoot } from 'react-dom/client';
import { registerSW } from 'virtual:pwa-register';
import App from './App';
import { getLang, initI18n } from './i18n';
import './styles.css';

registerSW({ immediate: true });

// Load the visitor's language before the first render (English is bundled; others are small chunks).
initI18n().finally(() => {
  const root = document.getElementById('root')!;
  const app = (
    <StrictMode>
      <App />
    </StrictMode>
  );
  // The landing and privacy pages ship pre-rendered in English. For English visitors React
  // adopts that HTML (no flash, no re-render); other languages re-render in their language.
  const prerendered = root.hasChildNodes() && getLang() === 'en' && /^\/(privacy\/?)?$/.test(location.pathname);
  if (prerendered) hydrateRoot(root, app);
  else {
    root.textContent = '';
    createRoot(root).render(app);
  }
});
