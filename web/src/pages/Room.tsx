import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { TopBar } from '../components/Logo';
import { AvatarStack, GeometricAvatar } from '../components/GeometricAvatar';
import { Burst, DeadlineBanner, PushArena, type ArenaOption } from '../components/Arena';
import { PresentStage } from '../components/PresentStage';
import { Qr } from '../components/Qr';
import { ensureSignedIn } from '../firebase';
import { effectiveLook, getIdentity, rememberRoom } from '../identity';
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
  const [presenting, setPresenting] = useState(false);
  const [newOption, setNewOption] = useState('');
  const [burst, setBurst] = useState(0);
  const [myPulse, setMyPulse] = useState(0);
  const wasOpen = useRef(open);
  useEffect(() => {
    if (wasOpen.current && !open) setBurst((b) => b + 1); // decision reached
    wasOpen.current = open;
  }, [open]);

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

  const vote = (optionId: string, direction?: 'yes' | 'no') => {
    if (!open) return;
    const next = new Set(mine);
    const want = direction ? direction === 'yes' : !next.has(optionId);
    if (!room.allowMultiple && want) next.clear();
    if (want) next.add(optionId); else next.delete(optionId);
    if (want && !mine.has(optionId)) {
      setBurst((b) => b + 1);
      setMyPulse((p) => p + 1);
    }
    setMyBallot(room, [...next]);
  };

  const arenaOptions: ArenaOption[] = sorted.map((o) => ({
    id: o.id,
    label: o.label,
    count: counts.get(o.id) ?? 0,
    voters: ballots.filter((b) => b.optionIds.includes(o.id)).map((b) => ({
      key: b.authUid, name: b.voterName, avatarStyleIndex: b.avatarStyleIndex ?? 0, colorIndex: b.badgeColorIndex,
    })),
  }));
  const myLook = effectiveLook(getIdentity(), room.isAnonymous);

  return (
    <>
      <TopBar
        right={
          <div className="row" style={{ gap: 6 }}>
            <button className="btn small ghost" onClick={() => setPresenting(true)} aria-label="Present full screen">Present</button>
            <button className="btn small" onClick={() => setShareOpen((v) => !v)} aria-expanded={shareOpen}>
              Share <span className="mono">{room.code}</span>
            </button>
          </div>
        }
      />
      <Burst trigger={burst} />
      {presenting && (
        <PresentStage
          question={room.question}
          code={room.code}
          url={shareUrl}
          options={arenaOptions}
          total={total}
          winnerId={winnerId}
          onClose={() => setPresenting(false)}
        />
      )}

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
          {open && ' · swipe → to vote, ← to drop'}
        </p>
        <div className="row" style={{ marginTop: 12, gap: 10 }}>
          <GeometricAvatar avatarStyleIndex={myLook.avatarStyleIndex} colorIndex={myLook.badgeColorIndex} size={36} reactionTrigger={myPulse} pushBias={myBallot ? 1 : 0} />
          <span className="small">
            You're voting as <strong>{myLook.name}</strong>{room.isAnonymous && <span className="muted"> (mask)</span>}
          </span>
        </div>
        {open && room.deadlineEpochMs != null && (
          <DeadlineBanner createdAt={room.createdAtEpochMs} deadline={room.deadlineEpochMs} now={now} />
        )}
      </section>

      <PushArena options={arenaOptions} total={total} locked={!open} winnerId={winnerId} />

      <section className="panel stack" aria-label="Options">
        {sorted.map((o) => {
          const count = counts.get(o.id) ?? 0;
          const arena = arenaOptions.find((x) => x.id === o.id)!;
          return (
            <OptionCard
              key={o.id}
              label={o.label}
              count={count}
              pct={total > 0 ? Math.round((count / total) * 100) : 0}
              voters={arena.voters}
              mine={mine.has(o.id)}
              winner={winnerId === o.id}
              open={open}
              onVote={(dir) => vote(o.id, dir)}
            />
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
              <GeometricAvatar avatarStyleIndex={c.avatarStyleIndex ?? 0} colorIndex={c.badgeColorIndex} size={40} title={c.voterName} />
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

/** Option card: tap to toggle, or swipe right to vote / left to withdraw (like the app). */
function OptionCard({ label, count, pct, voters, mine, winner, open, onVote }: {
  label: string; count: number; pct: number; voters: ArenaOption['voters']; mine: boolean; winner: boolean; open: boolean;
  onVote: (dir?: 'yes' | 'no') => void;
}) {
  const [dx, setDx] = useState(0);
  const start = useRef<{ x: number; y: number; id: number } | null>(null);
  const dragged = useRef(false);
  const THRESHOLD = 80;

  const onDown = (e: PointerEvent<HTMLButtonElement>) => {
    if (!open) return;
    start.current = { x: e.clientX, y: e.clientY, id: e.pointerId };
    dragged.current = false;
  };
  const onMove = (e: PointerEvent<HTMLButtonElement>) => {
    const s = start.current;
    if (!s || s.id !== e.pointerId) return;
    const x = e.clientX - s.x;
    if (!dragged.current && Math.abs(x) > 8 && Math.abs(x) > Math.abs(e.clientY - s.y)) {
      dragged.current = true;
      e.currentTarget.setPointerCapture(e.pointerId);
    }
    if (dragged.current) setDx(Math.max(-140, Math.min(140, x)));
  };
  const onUp = () => {
    if (dragged.current && Math.abs(dx) >= THRESHOLD) onVote(dx > 0 ? 'yes' : 'no');
    start.current = null;
    setDx(0);
  };

  return (
    <button
      className={`option${mine ? ' mine' : ''}${winner ? ' winner' : ''}${dx ? ' dragging' : ''}`}
      disabled={!open}
      aria-pressed={mine}
      style={{ transform: dx ? `translateX(${dx}px) rotate(${dx / 40}deg)` : undefined, touchAction: 'pan-y' }}
      onPointerDown={onDown}
      onPointerMove={onMove}
      onPointerUp={onUp}
      onPointerCancel={onUp}
      onClick={(e) => {
        if (dragged.current) { e.preventDefault(); dragged.current = false; return; }
        onVote();
      }}
    >
      <span className="fill" style={{ width: `${pct}%` }} />
      <span className="swipe-hint yes" style={{ opacity: Math.max(0, dx) / THRESHOLD }}>VOTE ›</span>
      <span className="swipe-hint no" style={{ opacity: Math.max(0, -dx) / THRESHOLD }}>‹ DROP</span>
      <span className="content" style={{ opacity: dx ? 0.35 + 0.65 * (1 - Math.min(1, Math.abs(dx) / 140)) : 1 }}>
        <span className="check" aria-hidden="true">{mine ? '✓' : ''}</span>
        <span className="grow">
          <span style={{ fontWeight: 600, display: 'block' }}>{winner ? '★ ' : ''}{label}</span>
          {voters.length > 0 && <span style={{ display: 'block', marginTop: 6 }}><AvatarStack voters={voters} size={26} /></span>}
        </span>
        <span className="count">{count}<span className="muted small"> · {pct}%</span></span>
      </span>
    </button>
  );
}
