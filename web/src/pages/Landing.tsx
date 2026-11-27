import { useState } from 'react';
import { Mark } from '../components/Mark';
import { LanguagePicker } from '../components/LanguagePicker';
import { navigate } from '../router';
import { sanitizeCode } from '../code';
import { t, tr } from '../i18n';

const GITHUB = 'https://github.com/PrinceJoseph-software/meld';
const ANDROID = `${GITHUB}/releases/latest`;

const FEATURES = [
  { icon: '◎', n: 1 },
  { icon: '⇅', n: 2 },
  { icon: '①', n: 3 },
  { icon: '◷', n: 4 },
  { icon: '◐', n: 5 },
  { icon: '▶', n: 6 },
];
const STEPS = [1, 2, 3];
const INTEGRATIONS = [
  { name: () => 'Slack', body: () => '/meld Lunch? | Tacos | Ramen', mono: true },
  { name: () => 'Discord', body: () => '/meld question: Movie night?', mono: true },
  { name: () => t('land.iWebhooksT'), body: () => t('land.iWebhooks') },
  { name: () => t('land.iEmbedsT'), body: () => t('land.iEmbeds') },
  { name: () => t('land.iCalendar'), body: () => t('land.iCalendarBody') },
  { name: () => t('land.iWeekly'), body: () => t('land.iWeeklyBody') },
];

/** FAQ entries (keys faq.q1…/faq.a1…). Shown on the page and published as FAQPage structured data. */
export const FAQ_COUNT = 7;
export const faq = () =>
  Array.from({ length: FAQ_COUNT }, (_, i) => ({ q: t(`faq.q${i + 1}`), a: t(`faq.a${i + 1}`) }));

export function Landing() {
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [videoOk, setVideoOk] = useState(true);
  const join = (e: React.FormEvent) => {
    e.preventDefault();
    const c = sanitizeCode(code);
    if (!c) return setError(t('common.codeInvalid'));
    navigate(`/r/${c}`);
  };
  const go = (path: string) => (e: React.MouseEvent) => {
    e.preventDefault();
    navigate(path);
  };

  return (
    <div className="landing">
      <header className="l-nav">
        <a className="brand" href="/" onClick={go('/')}>
          <Mark size={34} />
          Meld
        </a>
        <nav className="l-links" aria-label={t('land.sections')}>
          <a href="#features">{t('land.features')}</a>
          <a href="#how">{t('land.how')}</a>
          <a href="#integrations">{t('land.integrations')}</a>
          <a href="#faq">{t('land.faq')}</a>
          <a href={GITHUB} target="_blank" rel="noreferrer">GitHub</a>
        </nav>
        <a className="btn primary small" href="/app" onClick={go('/app')}>{t('land.open')}</a>
      </header>

      <main>
        <section className="l-hero">
          <div className="eyebrow">{t('land.eyebrow')}</div>
          <h1 className="l-title">{t('land.title1')}<br /><span className="accent">{t('land.title2')}</span></h1>
          <p className="l-lede">{t('land.lede')}</p>
          <div className="l-cta">
            <a className="btn primary" href="/app" onClick={go('/app')}>{t('land.ctaStart')}</a>
            <a className="btn" href={ANDROID} target="_blank" rel="noreferrer">{t('land.ctaAndroid')}</a>
          </div>
          <form className="l-join" onSubmit={join}>
            <label htmlFor="l-code" className="small muted">{t('common.haveCode')}</label>
            <input
              id="l-code"
              className="input code-input"
              placeholder="K7Q2MZ"
              maxLength={10}
              autoCapitalize="characters"
              autoComplete="off"
              value={code}
              onChange={(e) => { setCode(e.target.value); setError(null); }}
            />
            <button className="btn" type="submit">{t('common.join')}</button>
          </form>
          {error && <p className="error">{error}</p>}
        </section>

        {videoOk && <section className="l-demo" aria-label={t('land.videoAria')}>
          <video
            className="l-video"
            src="/media/meld-demo-16x9.mp4"
            poster="/media/hero-poster.jpg"
            autoPlay
            muted
            loop
            playsInline
            preload="metadata"
            onError={() => setVideoOk(false)}
            aria-label={t('land.videoAria')}
          />
        </section>}

        <section id="features" className="l-section">
          <div className="eyebrow">{t('land.whyEyebrow')}</div>
          <h2 className="l-h2">{t('land.whyTitle')}</h2>
          <div className="l-grid">
            {FEATURES.map((f) => (
              <article key={f.n} className="panel l-card">
                <div className="l-icon" aria-hidden="true">{f.icon}</div>
                <h3>{t(`land.f${f.n}t`)}</h3>
                <p className="muted">{t(`land.f${f.n}b`)}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="how" className="l-section">
          <div className="eyebrow">{t('land.howEyebrow')}</div>
          <h2 className="l-h2">{t('land.howTitle')}</h2>
          <ol className="l-steps">
            {STEPS.map((n) => (
              <li key={n} className="panel">
                <span className="l-step-n mono">{String(n).padStart(2, '0')}</span>
                <h3>{t(`land.s${n}t`)}</h3>
                <p className="muted">{t(`land.s${n}b`)}</p>
              </li>
            ))}
          </ol>
        </section>

        <section id="integrations" className="l-section">
          <div className="eyebrow">{t('land.intEyebrow')}</div>
          <h2 className="l-h2">{t('land.intTitle')}</h2>
          <div className="l-grid l-grid-3">
            {INTEGRATIONS.map((i, n) => (
              <div key={n} className="panel l-int">
                <strong>{i.name()}</strong>
                <span className={`small muted${i.mono ? ' mono' : ''}`}>{i.body()}</span>
              </div>
            ))}
          </div>
          <p className="small muted" style={{ marginTop: 14 }}>
            {tr('land.intDocs', {
              link: <a href={`${GITHUB}/blob/main/docs/INTEGRATIONS.md`} target="_blank" rel="noreferrer">{t('land.intDocsLink')}</a>,
            })}
          </p>
        </section>

        <section className="l-section">
          <div className="panel l-trust">
            <div>
              <div className="eyebrow">{t('land.trustEyebrow')}</div>
              <h2 className="l-h2" style={{ marginTop: 8 }}>{t('land.trustTitle')}</h2>
              <p className="muted">{t('land.trustBody')}</p>
            </div>
            <ul className="l-facts">
              <li><strong>18</strong><span className="small muted">{t('land.factLangs')}</span></li>
              <li><strong>0</strong><span className="small muted">{t('land.factSignups')}</span></li>
              <li><strong>2</strong><span className="small muted">{t('land.factApps')}</span></li>
            </ul>
          </div>
        </section>

        <section id="faq" className="l-section">
          <div className="eyebrow">{t('land.faqEyebrow')}</div>
          <h2 className="l-h2">{t('land.faqTitle')}</h2>
          <div className="l-faq">
            {faq().map((f) => (
              <details key={f.q} className="panel">
                <summary>{f.q}</summary>
                <p className="muted">{f.a}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="l-section l-final">
          <Mark size={64} />
          <h2 className="l-h2">{t('land.finalTitle')}</h2>
          <div className="l-cta" style={{ justifyContent: 'center' }}>
            <a className="btn primary" href="/app" onClick={go('/app')}>{t('land.finalStart')}</a>
            <a className="btn" href={ANDROID} target="_blank" rel="noreferrer">{t('land.finalAndroid')}</a>
          </div>
        </section>
      </main>

      <footer className="l-footer small muted">
        <span>{t('land.footer', { year: new Date().getFullYear() })}</span>
        <nav aria-label="Footer">
          <a href="/privacy" onClick={go('/privacy')}>{t('common.privacy')}</a>
          <a href={`${GITHUB}/blob/main/SECURITY.md`} target="_blank" rel="noreferrer">{t('land.security')}</a>
          <a href={`${GITHUB}/issues/new/choose`} target="_blank" rel="noreferrer">{t('land.report')}</a>
          <a href={GITHUB} target="_blank" rel="noreferrer">GitHub</a>
          <LanguagePicker />
        </nav>
      </footer>
    </div>
  );
}
