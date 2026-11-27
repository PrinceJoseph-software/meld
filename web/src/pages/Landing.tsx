import { useState } from 'react';
import { Mark } from '../components/Mark';
import { navigate } from '../router';
import { sanitizeCode } from '../code';

const GITHUB = 'https://github.com/PrinceJoseph-software/meld';
const ANDROID = `${GITHUB}/releases/latest`;

const FEATURES: { title: string; body: string; icon: string }[] = [
  { icon: '◎', title: 'Zero sign-up', body: 'No accounts, emails or passwords. Make a poll, share the link, and everyone votes from any phone or laptop.' },
  { icon: '⇅', title: 'Works offline', body: 'Vote on a train or in a basement. Meld saves everything on your device and syncs the moment you reconnect.' },
  { icon: '①', title: 'Ranked choice', body: 'Let people order their favourites. Instant runoff finds the option most of the group can live with.' },
  { icon: '◷', title: 'Find a time', body: 'Offer time slots, check them against your calendar, and send the winning time to Google or Apple Calendar.' },
  { icon: '◐', title: 'Avatars & anonymous mode', body: 'Design a geometric avatar, or vote behind an anonymous mask when honesty matters more than names.' },
  { icon: '▶', title: 'Built for the room', body: 'A live push arena, deadlines with countdowns, Present mode for screen sharing, and a result card to post anywhere.' },
];

const STEPS = [
  { n: '01', title: 'Ask', body: 'Type a question and a few options: a quick poll, a ranked vote, or time slots. Add a deadline if you want one.' },
  { n: '02', title: 'Share', body: 'Send the link or show the QR code. Friends vote in their browser; Android users can use the app.' },
  { n: '03', title: 'Decide', body: 'Watch votes arrive live. Crown the winner in one tap, or let the deadline close it for you.' },
];

const INTEGRATIONS = [
  { name: 'Slack', body: '/meld Lunch? | Tacos | Ramen' },
  { name: 'Discord', body: '/meld question: Movie night?' },
  { name: 'Webhooks', body: 'Signed JSON when a poll is decided' },
  { name: 'Embeds', body: 'A live, votable poll on any website' },
  { name: 'Calendar feeds', body: 'Decided times appear automatically' },
  { name: 'Weekly polls', body: 'A fresh poll every week, one link' },
];

/** Shown on the page and published as FAQPage structured data (keep them identical). */
export const FAQ: { q: string; a: string }[] = [
  { q: 'Do I need an account to use Meld?', a: 'No. Nobody signs up: not you, not the people voting. Make a poll, share the link or QR code, and anyone can vote from a phone or laptop browser.' },
  { q: 'Is Meld free?', a: 'Yes. Meld is free and open source under the MIT license, with no ads, no tracking and no premium tier.' },
  { q: 'Does Meld work offline?', a: 'Yes. Votes and polls are saved on your device first and sync automatically when you reconnect, on both the Android app and the installable web app.' },
  { q: 'How does ranked-choice voting work in Meld?', a: 'Each person orders the options. Meld runs an instant runoff: the option with the fewest first choices is removed and its votes move to the next choice, until one option has a majority.' },
  { q: 'Can Meld find a time that works for everyone?', a: 'Yes. Create a time-slot poll, let people mark the times they can make, and send the winning slot to Google Calendar or Apple Calendar in one tap.' },
  { q: 'Can I use Meld in Slack or Discord?', a: 'Yes. Type /meld with a question and options to start a poll in a Slack or Discord channel. The result is posted back when the poll closes. Webhooks and website embeds are available too.' },
  { q: 'Which languages does Meld support?', a: 'The Android app is available in 18 languages, including English, Spanish, French, German, Portuguese, Arabic, Hindi, Japanese, Korean and Chinese.' },
];

export function Landing() {
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [videoOk, setVideoOk] = useState(true);
  const join = (e: React.FormEvent) => {
    e.preventDefault();
    const c = sanitizeCode(code);
    if (!c) return setError('Codes are 4–10 letters and numbers.');
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
        <nav className="l-links" aria-label="Sections">
          <a href="#features">Features</a>
          <a href="#how">How it works</a>
          <a href="#integrations">Integrations</a>
          <a href="#faq">FAQ</a>
          <a href={GITHUB} target="_blank" rel="noreferrer">GitHub</a>
        </nav>
        <a className="btn primary small" href="/app" onClick={go('/app')}>Open Meld</a>
      </header>

      <main>
        <section className="l-hero">
          <div className="eyebrow">Group polls · Time slots · No sign-up</div>
          <h1 className="l-title">Decide together.<br /><span className="accent">Even offline.</span></h1>
          <p className="l-lede">
            Meld turns “where should we eat?” into a decision in under a minute. Make a poll, share a link,
            and watch your group vote live, with ranked choice, time slots and a result worth sharing.
          </p>
          <div className="l-cta">
            <a className="btn primary" href="/app" onClick={go('/app')}>Start a poll, it's free</a>
            <a className="btn" href={ANDROID} target="_blank" rel="noreferrer">Get the Android app</a>
          </div>
          <form className="l-join" onSubmit={join}>
            <label htmlFor="l-code" className="small muted">Have a code?</label>
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
            <button className="btn" type="submit">Join</button>
          </form>
          {error && <p className="error">{error}</p>}
        </section>

        {videoOk && <section className="l-demo" aria-label="Meld in action">
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
            aria-label="Two people using Meld: creating a ranked poll, voting live, going offline and crowning the winner"
          />
        </section>}

        <section id="features" className="l-section">
          <div className="eyebrow">Why Meld</div>
          <h2 className="l-h2">Everything a group decision needs. Nothing it doesn't.</h2>
          <div className="l-grid">
            {FEATURES.map((f) => (
              <article key={f.title} className="panel l-card">
                <div className="l-icon" aria-hidden="true">{f.icon}</div>
                <h3>{f.title}</h3>
                <p className="muted">{f.body}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="how" className="l-section">
          <div className="eyebrow">How it works</div>
          <h2 className="l-h2">Ask. Share. Decide.</h2>
          <ol className="l-steps">
            {STEPS.map((s) => (
              <li key={s.n} className="panel">
                <span className="l-step-n mono">{s.n}</span>
                <h3>{s.title}</h3>
                <p className="muted">{s.body}</p>
              </li>
            ))}
          </ol>
        </section>

        <section id="integrations" className="l-section">
          <div className="eyebrow">Fits your tools</div>
          <h2 className="l-h2">Where your group already talks.</h2>
          <div className="l-grid l-grid-3">
            {INTEGRATIONS.map((i) => (
              <div key={i.name} className="panel l-int">
                <strong>{i.name}</strong>
                <span className="small muted mono">{i.body}</span>
              </div>
            ))}
          </div>
          <p className="small muted" style={{ marginTop: 14 }}>
            Setup guides are in the <a href={`${GITHUB}/blob/main/docs/INTEGRATIONS.md`} target="_blank" rel="noreferrer">integrations docs</a>.
          </p>
        </section>

        <section className="l-section">
          <div className="panel l-trust">
            <div>
              <div className="eyebrow">Private by default</div>
              <h2 className="l-h2" style={{ marginTop: 8 }}>No ads. No trackers. Open source.</h2>
              <p className="muted">
                Meld has no accounts, ads or analytics. Rooms can only be opened with their code. The whole app is
                MIT-licensed on GitHub, and you can read exactly what it does.
              </p>
            </div>
            <ul className="l-facts">
              <li><strong>18</strong><span className="small muted">languages</span></li>
              <li><strong>0</strong><span className="small muted">sign-ups</span></li>
              <li><strong>2</strong><span className="small muted">apps: Android + web</span></li>
            </ul>
          </div>
        </section>

        <section id="faq" className="l-section">
          <div className="eyebrow">Questions</div>
          <h2 className="l-h2">Frequently asked</h2>
          <div className="l-faq">
            {FAQ.map((f) => (
              <details key={f.q} className="panel">
                <summary>{f.q}</summary>
                <p className="muted">{f.a}</p>
              </details>
            ))}
          </div>
        </section>

        <section className="l-section l-final">
          <Mark size={64} />
          <h2 className="l-h2">Your group's next decision is one link away.</h2>
          <div className="l-cta" style={{ justifyContent: 'center' }}>
            <a className="btn primary" href="/app" onClick={go('/app')}>Start a poll</a>
            <a className="btn" href={ANDROID} target="_blank" rel="noreferrer">Android app</a>
          </div>
        </section>
      </main>

      <footer className="l-footer small muted">
        <span>© {new Date().getFullYear()} Meld · MIT License</span>
        <nav aria-label="Footer">
          <a href={`${GITHUB}/blob/main/PRIVACY_POLICY.md`} target="_blank" rel="noreferrer">Privacy</a>
          <a href={`${GITHUB}/blob/main/SECURITY.md`} target="_blank" rel="noreferrer">Security</a>
          <a href={`${GITHUB}/issues/new/choose`} target="_blank" rel="noreferrer">Report an issue</a>
          <a href={GITHUB} target="_blank" rel="noreferrer">GitHub</a>
        </nav>
      </footer>
    </div>
  );
}
