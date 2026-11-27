import { LANGS, setLang, t, useLang } from '../i18n';

/** Compact language switcher. The choice is remembered on this device. */
export function LanguagePicker() {
  const lang = useLang();
  return (
    <label className="lang-picker">
      <span aria-hidden="true">🌐</span>
      <span className="sr-only">{t('common.language')}</span>
      <select value={lang} onChange={(e) => setLang(e.target.value)} aria-label={t('common.language')}>
        {LANGS.map((l) => (
          <option key={l.code} value={l.code} lang={l.code}>{l.name}</option>
        ))}
      </select>
    </label>
  );
}
