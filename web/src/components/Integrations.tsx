import { useEffect, useState } from 'react';
import { feedUrls } from '../calendar';
import {
  getSeries, getWebhookConfig, newWebhookSecret, saveWebhookConfig, setSeriesActive,
  type Room, type Series,
} from '../model';

/** Owner-only panel: decision webhook, website embed, calendar feed, weekly repeat. */
export function Integrations({ room }: { room: Room }) {
  const [open, setOpen] = useState(false);
  const [url, setUrl] = useState('');
  const [secret, setSecret] = useState('');
  const [saved, setSaved] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [series, setSeries] = useState<Series | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (loaded) return;
    getWebhookConfig(room).then((cfg) => {
      setUrl(cfg?.webhookUrl ?? '');
      setSecret(cfg?.webhookSecret || newWebhookSecret());
      setLoaded(true);
    });
    if (room.seriesId) getSeries(room.seriesId).then(setSeries).catch(() => {});
  }, [open, room, loaded]);

  const embed = `<iframe src="${location.origin}/embed/${room.code}" width="100%" height="560" style="border:0;border-radius:16px" title="${room.question.replace(/"/g, '&quot;')}" loading="lazy"></iframe>`;
  const feeds = feedUrls(room.code, room.seriesId);

  const save = async () => {
    setError(null);
    if (url && !/^https:\/\/.+/.test(url)) return setError('Webhook URL must start with https://');
    try {
      await saveWebhookConfig(room, url.trim() || null, secret);
      setSaved(url ? 'Saved. Meld will POST the result here when the decision is reached.' : 'Webhook removed.');
    } catch {
      setError("Couldn't save. Are you online?");
    }
  };

  return (
    <section className="panel">
      <button className="row between btn ghost block" style={{ padding: 0, minHeight: 32, border: 0 }} aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="eyebrow">Integrations</span>
        <span className="small muted">{open ? 'Hide' : 'Webhook · Embed · Calendar'}</span>
      </button>
      {open && (
        <div className="stack" style={{ marginTop: 12 }}>
          {!loaded ? <p className="small muted">Loading settings…</p> : <div>
            <div style={{ fontWeight: 600 }}>Decision webhook</div>
            <p className="small muted" style={{ margin: '4px 0 8px' }}>
              When the room closes, Meld sends a signed JSON POST (<span className="mono">decision.reached</span>) to your system.
              Verify it with <span className="mono">HMAC-SHA256(secret, timestamp + "." + body)</span>. See docs/INTEGRATIONS.md.
            </p>
            <input className="input" placeholder="https://your-system.example.com/meld-webhook" value={url} onChange={(e) => setUrl(e.target.value)} aria-label="Webhook URL" />
            <div className="row" style={{ marginTop: 8 }}>
              <input className="input grow mono small" readOnly value={secret} aria-label="Signing secret" onFocus={(e) => e.currentTarget.select()} />
              <button className="btn small" onClick={() => setSecret(newWebhookSecret())}>New secret</button>
            </div>
            <button className="btn small primary" style={{ marginTop: 8 }} onClick={save}>Save webhook</button>
            {saved && <p className="small" style={{ color: 'var(--emerald)' }}>{saved}</p>}
            {error && <p className="error">{error}</p>}
          </div>}

          <div>
            <div style={{ fontWeight: 600 }}>Embed on a website</div>
            <p className="small muted" style={{ margin: '4px 0 8px' }}>Paste this where you want the live poll to appear. Visitors can vote right there.</p>
            <textarea className="input mono small" rows={3} readOnly value={embed} onFocus={(e) => e.currentTarget.select()} aria-label="Embed code" />
          </div>

          {room.mode === 'TIME_SLOTS' && (
            <div>
              <div style={{ fontWeight: 600 }}>Calendar feed</div>
              <p className="small muted" style={{ margin: '4px 0 8px' }}>
                Subscribe once: the decided time {room.seriesId ? 'for every week of this poll ' : ''}appears in your calendar automatically.
              </p>
              <div className="chips">
                <a className="btn small" href={feeds.google} target="_blank" rel="noreferrer">Google Calendar</a>
                <a className="btn small" href={feeds.apple}>Apple Calendar</a>
              </div>
            </div>
          )}

          {room.seriesId && series && (
            <div className="row between">
              <div>
                <div style={{ fontWeight: 600 }}>Repeats weekly</div>
                <div className="small muted">
                  {series.active ? `Next poll opens ${new Date(series.nextRunAt).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' })}` : 'Paused'}
                  {' · '}share <span className="mono">{location.origin}/s/{room.seriesId}</span> to always reach this week's poll
                </div>
              </div>
              <button className="btn small" onClick={() => { setSeriesActive(room.seriesId!, !series.active); setSeries({ ...series, active: !series.active }); }}>
                {series.active ? 'Pause' : 'Resume'}
              </button>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
