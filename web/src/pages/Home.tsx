import { useState } from 'react';
import { TopBar } from '../components/Logo';
import { GeometricAvatar } from '../components/GeometricAvatar';
import { AvatarStudio } from '../components/AvatarStudio';
import { forgetRoom, getIdentity, recentRooms } from '../identity';
import { createRoom, sanitizeCode, type RoomMode } from '../model';
import { navigate } from '../router';
import { t } from '../i18n';
import { LanguagePicker } from '../components/LanguagePicker';

interface DraftOption { key: string; label: string; slotStartMs: number | null; local: string }

const DEADLINES: { key: string; minutes: number | null }[] = [
  { key: 'home.deadlineNone', minutes: null },
  { key: 'home.deadline15m', minutes: 15 },
  { key: 'home.deadline1h', minutes: 60 },
  { key: 'home.deadline24h', minutes: 1440 },
];

const slotFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
});

function blankOption(): DraftOption {
  return { key: crypto.randomUUID(), label: '', slotStartMs: null, local: '' };
}

export function Home() {
  const [identity, setIdentity] = useState(getIdentity());
  const [studioOpen, setStudioOpen] = useState(false);
  const [code, setCode] = useState('');
  const [joinError, setJoinError] = useState<string | null>(null);

  const [mode, setMode] = useState<RoomMode>('QUICK_POLL');
  const [question, setQuestion] = useState('');
  const [options, setOptions] = useState<DraftOption[]>([blankOption(), blankOption()]);
  const [allowMultiple, setAllowMultiple] = useState(false);
  const [allowFriendsToAdd, setAllowFriendsToAdd] = useState(false);
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [isRanked, setIsRanked] = useState(false);
  const [repeatWeekly, setRepeatWeekly] = useState(false);
  const [deadline, setDeadline] = useState<number | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [recent, setRecent] = useState(recentRooms());

  const join = () => {
    const c = sanitizeCode(code);
    if (!c) {
      setJoinError(t('common.codeInvalid'));
      return;
    }
    navigate(`/r/${c}`);
  };

  const filled = options.filter((o) => o.label.trim());
  const create = async () => {
    if (!question.trim()) return setCreateError(t('home.errQuestion'));
    if (filled.length < 2) return setCreateError(t('home.errOptions'));
    setCreating(true);
    setCreateError(null);
    try {
      const room = await createRoom({
        question,
        mode,
        options: filled.map((o) => ({ label: o.label, slotStartMs: o.slotStartMs })),
        allowMultiple,
        allowFriendsToAdd,
        isAnonymous,
        isRanked,
        repeatWeekly,
        deadlineMinutes: deadline,
      });
      navigate(`/r/${room}`);
    } catch {
      setCreateError(navigator.onLine
        ? t('home.errCreate')
        : t('home.errFirstOnline'));
      setCreating(false);
    }
  };

  const updateOption = (key: string, patch: Partial<DraftOption>) =>
    setOptions((list) => list.map((o) => (o.key === key ? { ...o, ...patch } : o)));

  return (
    <>
      <TopBar
        right={
          <button
            className="btn ghost small"
            onClick={() => setStudioOpen(true)}
            aria-label={t('home.openStudio')}
          >
            <GeometricAvatar avatarStyleIndex={identity.avatarStyle} colorIndex={identity.badgeColorIndex} size={28} />
            <span className="truncate" style={{ maxWidth: 120 }}>{identity.displayName || t('home.setName')}</span>
          </button>
        }
      />

      <section className="panel">
        <div className="eyebrow">{t('home.eyebrow')}</div>
        <h1 className="title">{t('home.title')}</h1>
        <p className="muted" style={{ margin: 0 }}>
          {t('home.lede')}
        </p>
      </section>

      <section className="panel">
        <label className="small muted" htmlFor="join-code">{t('common.haveCode')}</label>
        <div className="row" style={{ marginTop: 8 }}>
          <input
            id="join-code"
            className="input code-input grow"
            inputMode="text"
            autoCapitalize="characters"
            autoComplete="off"
            placeholder="K7Q2MZ"
            maxLength={10}
            value={code}
            onChange={(e) => { setCode(e.target.value); setJoinError(null); }}
            onKeyDown={(e) => e.key === 'Enter' && join()}
          />
          <button className="btn primary" onClick={join}>{t('common.join')}</button>
        </div>
        {joinError && <p className="error">{joinError}</p>}
      </section>

      <section className="panel stack">
        <div className="eyebrow">{t('home.newRoom')}</div>
        <div className="segmented" role="group" aria-label={t('home.roomType')}>
          <button aria-pressed={mode === 'QUICK_POLL'} onClick={() => setMode('QUICK_POLL')}>{t('mode.quickPoll')}</button>
          <button aria-pressed={mode === 'TIME_SLOTS'} onClick={() => setMode('TIME_SLOTS')}>{t('mode.timeSlots')}</button>
        </div>
        <input
          className="input"
          placeholder={mode === 'QUICK_POLL' ? t('home.qPoll') : t('home.qSlots')}
          maxLength={200}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          aria-label={t('home.question')}
        />
        {options.map((o, idx) => (
          <div className="row" key={o.key}>
            {mode === 'TIME_SLOTS' ? (
              <input
                className="input grow"
                type="datetime-local"
                value={o.local}
                aria-label={t('home.slotN', { n: idx + 1 })}
                onChange={(e) => {
                  const ms = e.target.value ? new Date(e.target.value).getTime() : NaN;
                  updateOption(o.key, Number.isFinite(ms)
                    ? { local: e.target.value, slotStartMs: ms, label: slotFormat.format(ms) }
                    : { local: '', slotStartMs: null, label: '' });
                }}
              />
            ) : (
              <input
                className="input grow"
                placeholder={t('home.optionN', { n: idx + 1 })}
                maxLength={140}
                value={o.label}
                aria-label={t('home.optionN', { n: idx + 1 })}
                onChange={(e) => updateOption(o.key, { label: e.target.value, slotStartMs: null })}
              />
            )}
            {options.length > 2 && (
              <button
                className="btn ghost small"
                aria-label={t('home.removeOptionN', { n: idx + 1 })}
                onClick={() => setOptions((list) => list.filter((x) => x.key !== o.key))}
              >
                ✕
              </button>
            )}
          </div>
        ))}
        {options.length < 12 && (
          <button className="btn ghost small" onClick={() => setOptions((l) => [...l, blankOption()])}>
            {t('home.addOption')}
          </button>
        )}

        <div>
          <div className="small muted" style={{ marginBottom: 8 }}>{t('home.deadline')}</div>
          <div className="chips">
            {DEADLINES.map((d) => (
              <button key={d.key} className="chip" aria-pressed={deadline === d.minutes} onClick={() => setDeadline(d.minutes)}>
                {t(d.key)}
              </button>
            ))}
          </div>
        </div>

        <label className="toggle"><span>{t('home.ranked')} <span className="small muted">{t('home.rankedHint')}</span></span>
          <input type="checkbox" checked={isRanked} onChange={(e) => { setIsRanked(e.target.checked); if (e.target.checked) setAllowMultiple(false); }} /></label>
        {!isRanked && (
          <label className="toggle"><span>{t('home.multiple')}</span>
            <input type="checkbox" checked={allowMultiple} onChange={(e) => setAllowMultiple(e.target.checked)} /></label>
        )}
        <label className="toggle"><span>{t('home.weekly')} <span className="small muted">{t('home.weeklyHint')}</span></span>
          <input type="checkbox" checked={repeatWeekly} onChange={(e) => setRepeatWeekly(e.target.checked)} /></label>
        <label className="toggle"><span>{t('home.friendsAdd')}</span>
          <input type="checkbox" checked={allowFriendsToAdd} onChange={(e) => setAllowFriendsToAdd(e.target.checked)} /></label>
        <label className="toggle"><span>{t('home.anonymous')}</span>
          <input type="checkbox" checked={isAnonymous} onChange={(e) => setIsAnonymous(e.target.checked)} /></label>

        {createError && <p className="error">{createError}</p>}
        <button className="btn primary block" disabled={creating} onClick={create}>
          {creating ? t('home.creating') : t('home.create')}
        </button>
      </section>

      {recent.length > 0 && (
        <section className="panel">
          <div className="eyebrow">{t('home.recent')}</div>
          <div style={{ marginTop: 6 }}>
            {recent.map((r) => (
              <div className="list-item" key={r.code}>
                <a
                  className="grow"
                  href={`/r/${r.code}`}
                  style={{ textDecoration: 'none', minWidth: 0 }}
                  onClick={(e) => { e.preventDefault(); navigate(`/r/${r.code}`); }}
                >
                  <div className="truncate" style={{ fontWeight: 600 }}>{r.question}</div>
                  <div className="small muted mono">{r.code}</div>
                </a>
                <button
                  className="btn ghost small"
                  aria-label={t('home.removeRecent', { code: r.code })}
                  onClick={() => { forgetRoom(r.code); setRecent(recentRooms()); }}
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="small muted row" style={{ justifyContent: 'center', marginTop: 20, gap: 12, flexWrap: 'wrap' }}>
        <span>{t('home.footer')} <a href="https://github.com/PrinceJoseph-software/meld/blob/main/PRIVACY_POLICY.md">{t('common.privacy')}</a></span>
        <LanguagePicker />
      </div>

      <AvatarStudio open={studioOpen} onClose={() => setStudioOpen(false)} onSaved={setIdentity} />
    </>
  );
}
