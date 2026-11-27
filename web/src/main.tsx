import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { registerSW } from 'virtual:pwa-register';
import App from './App';
import { initI18n } from './i18n';
import './styles.css';

registerSW({ immediate: true });

// Load the visitor's language before the first render (English is bundled; others are small chunks).
initI18n().finally(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
