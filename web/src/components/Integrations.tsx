import { useEffect, useState } from 'react';
import { feedUrls } from '../calendar';
import {
  getSeries, getWebhookConfig, newWebhookSecret, saveWebhookConfig, setSeriesActive,
  type Room, type Series,
} from '../model';
import { getLang, t, tr } from '../i18n';

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
    if (url && !/^https:\/\/.+/.test(url)) return setError(t('int.httpsOnly'));
    try {
      await saveWebhookConfig(room, url.trim() || null, secret);
      setSaved(url ? t('int.saved') : t('int.removed'));
    } catch {
      setError(t('int.saveFail'));
    }
  };

  return (
    <section className="panel">
      <button className="row between btn ghost block" style={{ padding: 0, minHeight: 32, border: 0 }} aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="eyebrow">{t('int.title')}</span>
        <span className="small muted">{open ? t('int.hide') : t('int.summary')}</span>
      </button>
      {open && (
        <div className="stack" style={{ marginTop: 12 }}>
          {!loaded ? <p className="small muted">{t('int.loading')}</p> : <div>
            <div style={{ fontWeight: 600 }}>{t('int.webhook')}</div>
            <p className="small muted" style={{ margin: '4px 0 8px' }}>
              {tr('int.webhookHelp', {
                event: <span className="mono">decision.reached</span>,
                formula: <span className="mono">HMAC-SHA256(secret, timestamp + "." + body)</span>,
              })}
            </p>
            <input className="input" placeholder="https://your-system.example.com/meld-webhook" value={url} onChange={(e) => setUrl(e.target.value)} aria-label={t('int.webhookUrl')} />
            <div className="row" style={{ marginTop: 8 }}>
              <input className="input grow mono small" readOnly value={secret} aria-label={t('int.secret')} onFocus={(e) => e.currentTarget.select()} />
              <button className="btn small" onClick={() => setSecret(newWebhookSecret())}>{t('int.newSecret')}</button>
            </div>
            <button className="btn small primary" style={{ marginTop: 8 }} onClick={save}>{t('int.saveWebhook')}</button>
            {saved && <p className="small" style={{ color: 'var(--emerald)' }}>{saved}</p>}
            {error && <p className="error">{error}</p>}
          </div>}

          <div>
            <div style={{ fontWeight: 600 }}>{t('int.embed')}</div>
            <p className="small muted" style={{ margin: '4px 0 8px' }}>{t('int.embedHelp')}</p>
            <textarea className="input mono small" rows={3} readOnly value={embed} onFocus={(e) => e.currentTarget.select()} aria-label={t('int.embedCode')} />
          </div>

          {room.mode === 'TIME_SLOTS' && (
            <div>
              <div style={{ fontWeight: 600 }}>{t('int.feed')}</div>
              <p className="small muted" style={{ margin: '4px 0 8px' }}>
                {room.seriesId ? t('int.feedHelpSeries') : t('int.feedHelp')}
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
                <div style={{ fontWeight: 600 }}>{t('int.weekly')}</div>
                <div className="small muted">
                  {series.active ? t('int.nextOpens', { date: new Date(series.nextRunAt).toLocaleDateString(getLang(), { weekday: 'long', day: 'numeric', month: 'short' }) }) : t('int.paused')}
                  {' · '}{tr('int.shareSeries', { link: <span className="mono">{location.origin}/s/{room.seriesId}</span> })}
                </div>
              </div>
              <button className="btn small" onClick={() => { setSeriesActive(room.seriesId!, !series.active); setSeries({ ...series, active: !series.active }); }}>
                {series.active ? t('int.pause') : t('int.resume')}
              </button>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
