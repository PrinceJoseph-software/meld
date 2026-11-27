import type { ReactNode } from 'react';
import { Mark } from '../components/Mark';
import { LanguagePicker } from '../components/LanguagePicker';
import { navigate } from '../router';
import { t } from '../i18n';
// Same text as PRIVACY_POLICY.md in the repo root (CI checks they match).
import policy from '../content/privacy.md?raw';

/** Tiny Markdown renderer for the policy: headings, paragraphs, lists, **bold**, `code`, links. */
function inline(text: string): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\)/g;
  let last = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index));
    const k = out.length;
    if (m[1]) out.push(<strong key={k}>{inline(m[1])}</strong>);
    else if (m[2]) out.push(<code key={k} className="mono">{m[2]}</code>);
    else {
      const href = m[4];
      const external = /^https?:/.test(href);
      out.push(<a key={k} href={href} {...(external ? { target: '_blank', rel: 'noreferrer' } : {})}>{m[3]}</a>);
    }
    last = re.lastIndex;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

function render(md: string): ReactNode[] {
  const blocks: ReactNode[] = [];
  let list: string[] = [];
  const flush = () => {
    if (list.length) blocks.push(<ul key={blocks.length}>{list.map((li, i) => <li key={i}>{inline(li)}</li>)}</ul>);
    list = [];
  };
  for (const raw of md.split('\n')) {
    const line = raw.trim();
    if (line.startsWith('- ')) { list.push(line.slice(2)); continue; }
    flush();
    if (!line) continue;
    const h = line.match(/^(#{1,3}) (.*)$/);
    if (h) {
      const level = h[1].length;
      const Tag = (level === 1 ? 'h1' : level === 2 ? 'h2' : 'h3') as 'h1' | 'h2' | 'h3';
      blocks.push(<Tag key={blocks.length} className={level === 1 ? 'title' : undefined}>{inline(h[2])}</Tag>);
    } else {
      blocks.push(<p key={blocks.length}>{inline(line)}</p>);
    }
  }
  flush();
  return blocks;
}

export function Privacy() {
  return (
    <div className="landing">
      <header className="l-nav">
        <a className="brand" href="/" onClick={(e) => { e.preventDefault(); navigate('/'); }}>
          <Mark size={34} />
          Meld
        </a>
        <a className="btn primary small" style={{ marginInlineStart: 'auto' }} href="/app" onClick={(e) => { e.preventDefault(); navigate('/app'); }}>
          {t('land.open')}
        </a>
      </header>
      <main>
        <article className="panel prose">{render(policy)}</article>
      </main>
      <footer className="l-footer small muted">
        <span>{t('land.footer', { year: new Date().getFullYear() })}</span>
        <LanguagePicker />
      </footer>
    </div>
  );
}
