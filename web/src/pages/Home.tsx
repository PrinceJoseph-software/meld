import { useState } from 'react';
import { TopBar } from '../components/Logo';
import { Avatar } from '../components/Avatar';
import { BADGE_COLORS, forgetRoom, getIdentity, recentRooms, saveIdentity } from '../identity';
import { createRoom, sanitizeCode, type RoomMode } from '../model';
import { navigate } from '../router';

interface DraftOption { key: string; label: string; slotStartMs: number | null; local: string }

const DEADLINES: { label: string; minutes: number | null }[] = [
  { label: 'No deadline', minutes: null },
  { label: '15 min', minutes: 15 },
  { label: '1 hour', minutes: 60 },
  { label: '24 hours', minutes: 1440 },
];

const slotFormat = new Intl.DateTimeFormat(undefined, {
  weekday: 'short', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit',
});

function blankOption(): DraftOption {
  return { key: crypto.randomUUID(), label: '', slotStartMs: null, local: '' };
}

export function Home() {
  const [identity, setIdentity] = useState(getIdentity());
  const [code, setCode] = useState('');
  const [joinError, setJoinError] = useState<string | null>(null);

  const [mode, setMode] = useState<RoomMode>('QUICK_POLL');
  const [question, setQuestion] = useState('');
  const [options, setOptions] = useState<DraftOption[]>([blankOption(), blankOption()]);
  const [allowMultiple, setAllowMultiple] = useState(false);
  const [allowFriendsToAdd, setAllowFriendsToAdd] = useState(false);
  const [isAnonymous, setIsAnonymous] = useState(false);
  const [deadline, setDeadline] = useState<number | null>(null);
  const [createError, setCreateError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [recent, setRecent] = useState(recentRooms());

  const join = () => {
    const c = sanitizeCode(code);
    if (!c) {
      setJoinError('Codes are 4–10 letters and numbers, like K7Q2MZ.');
      return;
    }
    navigate(`/r/${c}`);
  };

  const filled = options.filter((o) => o.label.trim());
  const create = async () => {
    if (!question.trim()) return setCreateError('Add a question first.');
    if (filled.length < 2) return setCreateError('Add at least 2 options.');
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
        deadlineMinutes: deadline,
      });
      navigate(`/r/${room}`);
    } catch {
      setCreateError("Couldn't create the room. Check your connection and try again.");
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
            onClick={() => (document.getElementById('profile') as HTMLDialogElement).showModal()}
            aria-label="Edit your name"
          >
            <Avatar name={identity.displayName || 'Guest'} colorIndex={identity.badgeColorIndex} />
            <span className="truncate" style={{ maxWidth: 120 }}>{identity.displayName || 'Set name'}</span>
          </button>
        }
      />

      <section className="panel">
        <div className="eyebrow">Zero-login polls</div>
        <h1 className="title">Decide together in seconds</h1>
        <p className="muted" style={{ margin: 0 }}>
          Make a poll or find a time, share the link, and everyone votes from any phone or
          laptop — even offline.
        </p>
      </section>

      <section className="panel">
        <label className="small muted" htmlFor="join-code">Have a code?</label>
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
          <button className="btn primary" onClick={join}>Join</button>
        </div>
        {joinError && <p className="error">{joinError}</p>}
      </section>

      <section className="panel stack">
        <div className="eyebrow">New room</div>
        <div className="segmented" role="group" aria-label="Room type">
          <button aria-pressed={mode === 'QUICK_POLL'} onClick={() => setMode('QUICK_POLL')}>Quick poll</button>
          <button aria-pressed={mode === 'TIME_SLOTS'} onClick={() => setMode('TIME_SLOTS')}>Time slots</button>
        </div>
        <input
          className="input"
          placeholder={mode === 'QUICK_POLL' ? 'Where should we eat tonight?' : 'When can everyone meet?'}
          maxLength={200}
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          aria-label="Question"
        />
        {options.map((o, idx) => (
          <div className="row" key={o.key}>
            {mode === 'TIME_SLOTS' ? (
              <input
                className="input grow"
                type="datetime-local"
                value={o.local}
                aria-label={`Time slot ${idx + 1}`}
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
                placeholder={`Option ${idx + 1}`}
                maxLength={140}
                value={o.label}
                aria-label={`Option ${idx + 1}`}
                onChange={(e) => updateOption(o.key, { label: e.target.value, slotStartMs: null })}
              />
            )}
            {options.length > 2 && (
              <button
                className="btn ghost small"
                aria-label={`Remove option ${idx + 1}`}
                onClick={() => setOptions((list) => list.filter((x) => x.key !== o.key))}
              >
                ✕
              </button>
            )}
          </div>
        ))}
        {options.length < 12 && (
          <button className="btn ghost small" onClick={() => setOptions((l) => [...l, blankOption()])}>
            + Add option
          </button>
        )}

        <div>
          <div className="small muted" style={{ marginBottom: 8 }}>Deadline</div>
          <div className="chips">
            {DEADLINES.map((d) => (
              <button key={d.label} className="chip" aria-pressed={deadline === d.minutes} onClick={() => setDeadline(d.minutes)}>
                {d.label}
              </button>
            ))}
          </div>
        </div>

        <label className="toggle"><span>Allow multiple choices</span>
          <input type="checkbox" checked={allowMultiple} onChange={(e) => setAllowMultiple(e.target.checked)} /></label>
        <label className="toggle"><span>Friends can add options</span>
          <input type="checkbox" checked={allowFriendsToAdd} onChange={(e) => setAllowFriendsToAdd(e.target.checked)} /></label>
        <label className="toggle"><span>Anonymous (show masks, not names)</span>
          <input type="checkbox" checked={isAnonymous} onChange={(e) => setIsAnonymous(e.target.checked)} /></label>

        {createError && <p className="error">{createError}</p>}
        <button className="btn primary block" disabled={creating} onClick={create}>
          {creating ? 'Creating…' : 'Create room'}
        </button>
      </section>

      {recent.length > 0 && (
        <section className="panel">
          <div className="eyebrow">Recent rooms</div>
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
                  aria-label={`Remove ${r.code} from recent rooms`}
                  onClick={() => { forgetRoom(r.code); setRecent(recentRooms()); }}
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        </section>
      )}

      <p className="small muted" style={{ textAlign: 'center', marginTop: 20 }}>
        No account, no ads. <a href="https://github.com/PrinceJoseph-software/meld/blob/main/PRIVACY_POLICY.md">Privacy</a>
      </p>

      <dialog id="profile">
        <form
          method="dialog"
          className="stack"
          onSubmit={(e) => {
            const data = new FormData(e.currentTarget);
            saveIdentity({
              displayName: String(data.get('name') || ''),
              maskName: String(data.get('mask') || ''),
              badgeColorIndex: Number(data.get('color') || 0),
            });
            setIdentity(getIdentity());
          }}
        >
          <div className="eyebrow">Your identity</div>
          <label className="small muted" htmlFor="pname">Display name</label>
          <input id="pname" name="name" className="input" maxLength={28} defaultValue={identity.displayName} placeholder="Alex" />
          <label className="small muted" htmlFor="pmask">Anonymous mask name</label>
          <input id="pmask" name="mask" className="input" maxLength={24} defaultValue={identity.maskName} />
          <div className="small muted">Colour</div>
          <div className="chips" role="radiogroup" aria-label="Badge colour">
            {BADGE_COLORS.map((c, i) => (
              <label key={c} style={{ cursor: 'pointer' }}>
                <input type="radio" name="color" value={i} defaultChecked={identity.badgeColorIndex === i} style={{ position: 'absolute', opacity: 0 }} />
                <span className="avatar" style={{ background: c, margin: 0, width: 32, height: 32 }} aria-label={`Colour ${i + 1}`} />
              </label>
            ))}
          </div>
          <button className="btn primary block" type="submit">Save</button>
        </form>
      </dialog>
    </>
  );
}
