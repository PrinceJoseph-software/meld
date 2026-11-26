import { useEffect, useMemo, useRef, useState } from 'react';
import { TopBar } from '../components/Logo';
import { Avatar } from '../components/Avatar';
import { Qr } from '../components/Qr';
import { ensureSignedIn } from '../firebase';
import { getIdentity, rememberRoom } from '../identity';
import {
  addComment,
  addOption,
  closeRoom,
  deleteComment,
  fetchAudio,
  isVotingOpen,
  MAX_AUDIO_BASE64,
  reopenRoom,
  resetVotes,
  setMyBallot,
  watchRoom,
  type Room,
  type RoomState,
} from '../model';
import { navigate, useNow } from '../router';

function formatRemaining(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}:${String(sec).padStart(2, '0')}`;
}

export function RoomPage({ code }: { code: string | null }) {
  const [state, setState] = useState<RoomState | null>(null);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [authUid, setAuthUid] = useState<string | null>(null);

  useEffect(() => {
    if (!code) return;
    ensureSignedIn().then(setAuthUid).catch(() => {});
    return watchRoom(
      code,
      (s) => {
        setState(s);
        rememberRoom(code, s.room.question);
      },
      () => setMissing(true),
      (e) => setError(e.message),
    );
  }, [code]);

  if (!code || missing) {
    return (
      <>
        <TopBar />
        <section className="panel">
          <div className="eyebrow">Room not found</div>
          <h1 className="title">That code doesn't exist</h1>
          <p className="muted">Check the code with whoever shared it.</p>
          <button className="btn primary" onClick={() => navigate('/')}>Back home</button>
        </section>
      </>
    );
  }
  if (!state) {
    return (
      <>
        <TopBar />
        <section className="panel" aria-busy="true">
          <div className="eyebrow mono">{code}</div>
          <p className="muted">{error ? `Couldn't load this room. ${navigator.onLine ? '' : "You're offline and it isn't saved on this device yet."}` : 'Loading room…'}</p>
        </section>
      </>
    );
  }
  return <RoomView state={state} authUid={authUid} />;
}

function RoomView({ state, authUid }: { state: RoomState; authUid: string | null }) {
  const { room, options, ballots, comments } = state;
  const now = useNow(1000, room.deadlineEpochMs != null && !room.isLocked);
  const open = isVotingOpen(room, now);
  const isOwner = authUid != null && room.ownerUid === authUid;
  const myBallot = ballots.find((b) => b.authUid === authUid);
  const mine = new Set(myBallot?.optionIds ?? []);
  const [shareOpen, setShareOpen] = useState(false);
  const [newOption, setNewOption] = useState('');

  const counts = useMemo(() => {
    const c = new Map<string, number>();
    ballots.forEach((b) => b.optionIds.forEach((id) => c.set(id, (c.get(id) ?? 0) + 1)));
    return c;
  }, [ballots]);
  const total = [...counts.values()].reduce((a, b) => a + b, 0);
  const max = Math.max(0, ...counts.values());
  const leaders = options.filter((o) => max > 0 && (counts.get(o.id) ?? 0) === max);
  // A passed deadline closes the room for everyone; the top option wins (first on ties).
  const winnerId = room.winnerOptionId ?? (!open && leaders[0] ? leaders[0].id : null);

  const sorted = [...options].sort((a, b) => (counts.get(b.id) ?? 0) - (counts.get(a.id) ?? 0) || a.sortOrder - b.sortOrder);
  const shareUrl = `${location.origin}/r/${room.code}`;

  const vote = (optionId: string) => {
    if (!open) return;
    const next = new Set(mine);
    if (room.allowMultiple) {
      if (next.has(optionId)) next.delete(optionId); else next.add(optionId);
    } else if (next.has(optionId)) {
      next.clear();
    } else {
      next.clear();
      next.add(optionId);
    }
    setMyBallot(room, [...next]);
  };

  return (
    <>
      <TopBar
        right={
          <button className="btn small" onClick={() => setShareOpen((v) => !v)} aria-expanded={shareOpen}>
            Share <span className="mono">{room.code}</span>
          </button>
        }
      />

      {shareOpen && <SharePanel room={room} url={shareUrl} />}

      <section className="panel">
        <div className="row between">
          <span className="eyebrow">{room.mode === 'TIME_SLOTS' ? 'Time slots' : 'Quick poll'}{room.isAnonymous ? ' · anonymous' : ''}</span>
          <Status room={room} open={open} now={now} synced={!state.fromCache && !state.hasPendingWrites} />
        </div>
        <h1 className="title">{room.question}</h1>
        <p className="small muted" style={{ margin: 0 }}>
          {total} vote{total === 1 ? '' : 's'} · {ballots.length} voter{ballots.length === 1 ? '' : 's'}
          {room.allowMultiple ? ' · pick any' : ' · pick one'}
        </p>
      </section>

      <section className="panel stack" aria-label="Options">
        {sorted.map((o) => {
          const count = counts.get(o.id) ?? 0;
          const pct = total > 0 ? Math.round((count / total) * 100) : 0;
          const voters = ballots.filter((b) => b.optionIds.includes(o.id));
          const isWinner = winnerId === o.id;
          return (
            <button
              key={o.id}
              className={`option${mine.has(o.id) ? ' mine' : ''}${isWinner ? ' winner' : ''}`}
              disabled={!open}
              aria-pressed={mine.has(o.id)}
              onClick={() => vote(o.id)}
            >
              <span className="fill" style={{ width: `${pct}%` }} />
              <span className="content">
                <span className="check" aria-hidden="true">{mine.has(o.id) ? '✓' : ''}</span>
                <span className="grow">
                  <span style={{ fontWeight: 600, display: 'block' }}>{isWinner ? '★ ' : ''}{o.label}</span>
                  {voters.length > 0 && (
                    <span className="avatars" style={{ marginTop: 6 }}>
                      {voters.slice(0, 6).map((v) => <Avatar key={v.authUid} name={v.voterName} colorIndex={v.badgeColorIndex} />)}
                    </span>
                  )}
                </span>
                <span className="count">{count}<span className="muted small"> · {pct}%</span></span>
              </span>
            </button>
          );
        })}

        {open && (room.allowFriendsToAdd || isOwner) && options.length < 20 && (
          <form
            className="row"
            onSubmit={(e) => {
              e.preventDefault();
              if (!newOption.trim()) return;
              addOption(room, newOption, Math.max(-1, ...options.map((o) => o.sortOrder)) + 1);
              setNewOption('');
            }}
          >
            <input className="input grow" placeholder="Suggest another option" maxLength={140} value={newOption} onChange={(e) => setNewOption(e.target.value)} aria-label="New option" />
            <button className="btn" type="submit">Add</button>
          </form>
        )}
      </section>

      {isOwner && (
        <section className="panel">
          <div className="eyebrow">You created this room</div>
          <div className="chips" style={{ marginTop: 10 }}>
            {open ? (
              <button className="btn small primary" disabled={total === 0} onClick={() => closeRoom(room, leaders[0]?.id ?? null)}>
                Close &amp; crown leader
              </button>
            ) : (
              <button className="btn small" onClick={() => reopenRoom(room)}>Reopen voting</button>
            )}
            <button
              className="btn small ghost"
              disabled={ballots.length === 0}
              onClick={() => confirm('Clear every vote in this room?') && resetVotes(room)}
            >
              Reset votes
            </button>
          </div>
          {open && leaders.length > 1 && <p className="small muted">It's a tie between {leaders.map((l) => l.label).join(' and ')}.</p>}
        </section>
      )}

      <Opinions room={room} comments={comments} options={options} open={open} isOwner={isOwner} authUid={authUid} />
    </>
  );
}

function Status({ room, open, now, synced }: { room: Room; open: boolean; now: number; synced: boolean }) {
  if (!open) return <span className="badge">Closed</span>;
  if (room.deadlineEpochMs) {
    return <span className="badge warn"><span className="dot" />{formatRemaining(room.deadlineEpochMs - now)} left</span>;
  }
  return synced
    ? <span className="badge live"><span className="dot" />Live</span>
    : <span className="badge warn"><span className="dot" />Syncing</span>;
}

function SharePanel({ room, url }: { room: Room; url: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch { /* clipboard blocked: the URL is visible to copy manually */ }
  };
  const canShare = typeof navigator.share === 'function';
  return (
    <section className="panel">
      <div className="row" style={{ alignItems: 'flex-start', flexWrap: 'wrap', gap: 16 }}>
        <Qr value={url} label={`QR code for room ${room.code}`} />
        <div className="grow stack" style={{ minWidth: 200 }}>
          <div className="eyebrow">Invite people</div>
          <div className="mono" style={{ fontSize: 28, letterSpacing: '0.2em' }}>{room.code}</div>
          <div className="small muted" style={{ wordBreak: 'break-all' }}>{url}</div>
          <div className="chips">
            <button className="btn small primary" onClick={copy}>{copied ? 'Copied' : 'Copy link'}</button>
            {canShare && (
              <button className="btn small" onClick={() => navigator.share({ title: 'Meld', text: room.question, url }).catch(() => {})}>
                Share…
              </button>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}

function Opinions({ room, comments, options, open, isOwner, authUid }: {
  room: Room; comments: RoomState['comments']; options: RoomState['options']; open: boolean; isOwner: boolean; authUid: string | null;
}) {
  const [text, setText] = useState('');
  const [target, setTarget] = useState<string>('');
  const [recording, setRecording] = useState(false);
  const [recError, setRecError] = useState<string | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const startedAt = useRef(0);
  const me = getIdentity();

  const startRecording = async () => {
    setRecError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mimeType = ['audio/mp4', 'audio/webm;codecs=opus', 'audio/webm'].find((t) => MediaRecorder.isTypeSupported(t)) ?? '';
      const rec = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: 32_000 });
      const chunks: Blob[] = [];
      rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
      rec.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(chunks, { type: rec.mimeType || 'audio/webm' });
        const seconds = (Date.now() - startedAt.current) / 1000;
        const base64 = await blobToBase64(blob);
        if (base64.length > MAX_AUDIO_BASE64) {
          setRecError('That note is too long to send. Keep it under a minute.');
          return;
        }
        addComment(room, text, target || null, { base64, mimeType: blob.type.split(';')[0], seconds });
        setText('');
      };
      recorder.current = rec;
      startedAt.current = Date.now();
      rec.start();
      setRecording(true);
      setTimeout(() => rec.state === 'recording' && rec.stop(), 60_000);
    } catch {
      setRecError("Microphone isn't available. You can type your opinion instead.");
    }
  };
  const stopRecording = () => {
    recorder.current?.stop();
    setRecording(false);
  };

  if (!open && comments.length === 0) return null;

  return (
    <section className="panel">
      <div className="eyebrow">Opinions</div>
      <div style={{ marginTop: 6 }}>
        {comments.length === 0 && <p className="small muted">No opinions yet. Say why you voted.</p>}
        {comments.map((c) => {
          const optionLabel = options.find((o) => o.id === c.optionId)?.label;
          const mineComment = c.authorAuthUid === authUid || c.voterUid === me.uid;
          return (
            <div className="comment" key={c.id}>
              <Avatar name={c.voterName} colorIndex={c.badgeColorIndex} large />
              <div className="grow">
                <div className="small"><strong>{c.voterName}</strong>{optionLabel && <span className="muted"> · on {optionLabel}</span>}</div>
                <div style={{ marginTop: 2 }}>{c.transcript}</div>
                {c.hasAudio && <AudioPlayer room={room} id={c.id} />}
              </div>
              {(mineComment || isOwner) && (
                <button className="btn ghost small" aria-label="Delete opinion" onClick={() => deleteComment(room, c.id)}>✕</button>
              )}
            </div>
          );
        })}
      </div>
      {open && (
        <form
          className="stack"
          style={{ marginTop: 10 }}
          onSubmit={(e) => {
            e.preventDefault();
            if (!text.trim()) return;
            addComment(room, text, target || null);
            setText('');
          }}
        >
          <select className="input" value={target} onChange={(e) => setTarget(e.target.value)} aria-label="Opinion about">
            <option value="">About the whole room</option>
            {options.map((o) => <option key={o.id} value={o.id}>About: {o.label}</option>)}
          </select>
          <div className="row">
            <input className="input grow" placeholder="Why did you vote that way?" maxLength={300} value={text} onChange={(e) => setText(e.target.value)} aria-label="Your opinion" />
            <button className="btn" type="submit" disabled={!text.trim()}>Post</button>
          </div>
          {'MediaRecorder' in window && (
            <button type="button" className={`btn small${recording ? ' primary' : ''}`} onClick={recording ? stopRecording : startRecording}>
              {recording ? '■ Stop & send voice note' : '● Record voice note'}
            </button>
          )}
          {recError && <p className="error">{recError}</p>}
        </form>
      )}
    </section>
  );
}

function AudioPlayer({ room, id }: { room: Room; id: string }) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  if (src) return <audio controls src={src} style={{ width: '100%', marginTop: 8 }} />;
  return (
    <button
      className="btn small"
      style={{ marginTop: 8 }}
      onClick={() => fetchAudio(room, id).then((s) => (s ? setSrc(s) : setFailed(true))).catch(() => setFailed(true))}
    >
      {failed ? 'Audio unavailable offline' : '▶ Play voice note'}
    </button>
  );
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(',')[1] ?? '');
    reader.onerror = reject;
    reader.readAsDataURL(blob);
  });
}
