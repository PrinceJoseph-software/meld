import { useEffect, useMemo, useRef, useState, type PointerEvent } from 'react';
import { TopBar } from '../components/Logo';
import { AvatarStack, GeometricAvatar } from '../components/GeometricAvatar';
import { Burst, DeadlineBanner, PushArena, type ArenaOption } from '../components/Arena';
import { PresentStage } from '../components/PresentStage';
import { Integrations } from '../components/Integrations';
import { renderCard, shareOrDownloadCard } from '../card';
import { downloadIcs, feedUrls, googleCalendarUrl } from '../calendar';
import { computeResults } from '../shared/tally';
import { Qr } from '../components/Qr';
import { ensureSignedIn } from '../firebase';
import { effectiveLook, getIdentity, rememberRoom } from '../identity';
import {
  addComment,
  addOption,
  claimRoom,
  closeRoom,
  deleteComment,
  fetchAudio,
  isVotingOpen,
  MAX_AUDIO_BASE64,
  pingDecision,
  reopenRoom,
  resetVotes,
  setMyBallot,
  watchRoom,
  type Room,
  type RoomState,
} from '../model';
import { navigate, useNow } from '../router';
import { list, t, tr } from '../i18n';

function formatRemaining(ms: number) {
  const s = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  return h > 0 ? `${h}h ${String(m).padStart(2, '0')}m` : `${m}:${String(sec).padStart(2, '0')}`;
}

export function RoomPage({ code, embedded = false }: { code: string | null; embedded?: boolean }) {
  const [state, setState] = useState<RoomState | null>(null);
  const [missing, setMissing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [authUid, setAuthUid] = useState<string | null>(null);
  const [claimMsg, setClaimMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!code) return;
    ensureSignedIn().then(setAuthUid).catch(() => {});
    // Claim link from Slack/Discord: /r/CODE?claim=TOKEN makes this device the owner.
    const token = new URLSearchParams(location.search).get('claim');
    if (token) {
      history.replaceState(null, '', location.pathname);
      claimRoom(code, token)
        .then((ok) => setClaimMsg(ok ? t('room.claimOk') : t('room.claimUsed')))
        .catch(() => setClaimMsg(t('room.claimFail')));
    }
    return watchRoom(
      code,
      (s) => {
        setState(s);
        if (!embedded) rememberRoom(code, s.room.question);
      },
      () => setMissing(true),
      (e) => setError(e.message),
    );
  }, [code, embedded]);

  if (!code || missing) {
    return (
      <>
        {!embedded && <TopBar />}
        <section className="panel">
          <div className="eyebrow">{t('room.notFoundEyebrow')}</div>
          <h1 className="title">{t('room.notFoundTitle')}</h1>
          <p className="muted">{t('room.notFoundBody')}</p>
          {!embedded && <button className="btn primary" onClick={() => navigate('/app')}>{t('room.backHome')}</button>}
        </section>
      </>
    );
  }
  if (!state) {
    return (
      <>
        {!embedded && <TopBar />}
        <section className="panel" aria-busy="true">
          <div className="eyebrow mono">{code}</div>
          <p className="muted">{error ? `${t('room.loadFail')} ${navigator.onLine ? '' : t('room.loadFailOffline')}` : t('room.loading')}</p>
        </section>
      </>
    );
  }
  return (
    <>
      {claimMsg && <div className="banner" role="status">{claimMsg}</div>}
      <RoomView state={state} authUid={authUid} embedded={embedded} />
    </>
  );
}

function RoomView({ state, authUid, embedded }: { state: RoomState; authUid: string | null; embedded: boolean }) {
  const { room, options, ballots, comments } = state;
  const now = useNow(1000, room.deadlineEpochMs != null && !room.isLocked);
  const open = isVotingOpen(room, now);
  const ranked = Boolean(room.isRanked);
  const isOwner = authUid != null && room.ownerUid === authUid;
  const myBallot = ballots.find((b) => b.authUid === authUid);
  const myRanking = myBallot?.optionIds ?? [];
  const mine = new Set(myRanking);
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

  // Room looks closed and no one has announced it yet: ask the server to (it re-checks).
  useEffect(() => {
    if (!open && !room.notifiedAt && !state.fromCache) pingDecision(room.code);
  }, [open, room.notifiedAt, room.code, state.fromCache]);

  const results = useMemo(
    () => computeResults(ranked, options, ballots.map((b) => b.optionIds)),
    [ranked, options, ballots],
  );
  const counts = results.counts;
  const total = results.total;
  // Closed room: the owner's pick, else the computed result (instant-runoff for ranked).
  const winnerId = room.winnerOptionId ?? (!open ? results.leaderId : null);
  const leaders = results.tiedLeaders.length > 1 ? results.tiedLeaders : results.leaderId ? [results.leaderId] : [];
  const labelOf = (id: string) => options.find((o) => o.id === id)?.label ?? '?';

  const sorted = [...options].sort((a, b) => (counts[b.id] ?? 0) - (counts[a.id] ?? 0) || a.sortOrder - b.sortOrder);
  const shareUrl = `${location.origin}/r/${room.code}`;

  const vote = (optionId: string, direction?: 'yes' | 'no') => {
    if (!open) return;
    const has = mine.has(optionId);
    const want = direction ? direction === 'yes' : !has;
    let next: string[];
    if (ranked) {
      // Ranking order = order you pick them in; picking again removes it.
      next = want ? (has ? myRanking : [...myRanking, optionId]) : myRanking.filter((id) => id !== optionId);
    } else {
      const set = new Set(myRanking);
      if (!room.allowMultiple && want) set.clear();
      if (want) set.add(optionId); else set.delete(optionId);
      next = [...set];
    }
    if (want && !has) {
      setBurst((b) => b + 1);
      setMyPulse((p) => p + 1);
    }
    setMyBallot(room, next);
  };

  const votersFor = (id: string) => ballots
    .filter((b) => (ranked ? b.optionIds[0] === id : b.optionIds.includes(id)))
    .map((b) => ({ key: b.authUid, name: b.voterName, avatarStyleIndex: b.avatarStyleIndex ?? 0, colorIndex: b.badgeColorIndex }));
  const arenaOptions: ArenaOption[] = sorted.map((o) => ({ id: o.id, label: o.label, count: counts[o.id] ?? 0, voters: votersFor(o.id) }));
  const myLook = effectiveLook(getIdentity(), room.isAnonymous);
  const howToVote = ranked ? t('room.howRanked') : room.allowMultiple ? t('room.howMulti') : t('room.howOne');

  return (
    <>
      {!embedded && (
        <TopBar
          right={
            <div className="row" style={{ gap: 6 }}>
              <button className="btn small ghost" onClick={() => setPresenting(true)} aria-label={t('room.presentAria')}>{t('room.present')}</button>
              <button className="btn small" onClick={() => setShareOpen((v) => !v)} aria-expanded={shareOpen}>
                {t('room.share')} <span className="mono">{room.code}</span>
              </button>
            </div>
          }
        />
      )}
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

      {room.nextRoomCode && (
        <a className="banner row between" href={`/r/${room.nextRoomCode}`} onClick={(e) => { if (!embedded) { e.preventDefault(); navigate(`/r/${room.nextRoomCode}`); } }} style={{ textDecoration: 'none' }}>
          <span><strong>{t('room.nextOpen')}</strong> {t('room.nextOld')}</span>
          <span className="accent mono">{room.nextRoomCode} ›</span>
        </a>
      )}

      {shareOpen && <SharePanel room={room} url={shareUrl} />}

      <section className="panel">
        <div className="row between">
          <span className="eyebrow">
            {room.mode === 'TIME_SLOTS' ? t('mode.timeSlots') : t('mode.quickPoll')}{ranked ? ` · ${t('tag.ranked')}` : ''}{room.isAnonymous ? ` · ${t('tag.anonymous')}` : ''}{room.seriesId ? ` · ${t('tag.weekly')}` : ''}
          </span>
          <Status room={room} open={open} now={now} synced={!state.fromCache && !state.hasPendingWrites} />
        </div>
        <h1 className="title">{room.question}</h1>
        <p className="small muted" style={{ margin: 0 }}>
          {t('room.voters', { count: ballots.length })} · {howToVote}
          {open && ` · ${ranked ? t('room.hintRanked') : t('room.hintSwipe')}`}
        </p>
        <div className="row" style={{ marginTop: 12, gap: 10 }}>
          <GeometricAvatar avatarStyleIndex={myLook.avatarStyleIndex} colorIndex={myLook.badgeColorIndex} size={36} reactionTrigger={myPulse} pushBias={myBallot ? 1 : 0} />
          <span className="small">
            {tr('room.votingAs', { name: <strong>{myLook.name}</strong> })}{room.isAnonymous && <span className="muted"> {t('room.mask')}</span>}
          </span>
        </div>
        {open && room.deadlineEpochMs != null && (
          <DeadlineBanner createdAt={room.createdAtEpochMs} deadline={room.deadlineEpochMs} now={now} />
        )}
      </section>

      {!embedded && <PushArena options={arenaOptions} total={total} locked={!open} winnerId={winnerId} />}

      <section className="panel stack" aria-label={t('room.options')}>
        {ranked && myRanking.length > 0 && (
          <div className="ranking row between small">
            <span>{t('room.yourRanking')} {myRanking.map((id, i) => <span key={id} className="mono">{i > 0 ? ' › ' : ''}{i + 1}. {labelOf(id)}</span>)}</span>
            {open && <button className="btn small ghost" onClick={() => setMyBallot(room, [])}>{t('common.clear')}</button>}
          </div>
        )}
        {sorted.map((o) => {
          const count = counts[o.id] ?? 0;
          const rank = ranked ? myRanking.indexOf(o.id) : -1;
          return (
            <OptionCard
              key={o.id}
              label={o.label}
              count={count}
              pct={total > 0 ? Math.round((count / total) * 100) : 0}
              voters={votersFor(o.id)}
              mine={mine.has(o.id)}
              rank={rank >= 0 ? rank + 1 : null}
              winner={winnerId === o.id}
              open={open}
              onVote={(dir) => vote(o.id, dir)}
            />
          );
        })}

        {ranked && results.irv && results.irv.rounds.length > 1 && (
          <details className="rounds">
            <summary className="small">{t('room.rounds', { count: results.irv.rounds.length })}</summary>
            <ol className="small muted">
              {results.irv.rounds.map((r, i) => (
                <li key={i}>
                  {Object.entries(r.counts).sort((x, y) => y[1] - x[1]).map(([id, n]) => `${labelOf(id)} ${n}`).join(' · ')}
                  {r.eliminated ? ` → ${t('room.eliminated', { option: labelOf(r.eliminated) })}` : results.irv!.winnerId ? ` → ${t('room.majority', { option: labelOf(results.irv!.winnerId) })}` : ''}
                </li>
              ))}
            </ol>
          </details>
        )}

        {open && !embedded && (room.allowFriendsToAdd || isOwner) && options.length < 20 && (
          <form
            className="row"
            onSubmit={(e) => {
              e.preventDefault();
              if (!newOption.trim()) return;
              addOption(room, newOption, Math.max(-1, ...options.map((o) => o.sortOrder)) + 1);
              setNewOption('');
            }}
          >
            <input className="input grow" placeholder={t('room.suggest')} maxLength={140} value={newOption} onChange={(e) => setNewOption(e.target.value)} aria-label={t('room.newOption')} />
            <button className="btn" type="submit">{t('common.add')}</button>
          </form>
        )}
      </section>

      {!open && !embedded && (
        <Outcome room={room} options={options} counts={counts} total={total} ballots={ballots} winnerId={winnerId} tie={results.tiedLeaders.length > 1 || Boolean(results.irv?.tie)} rounds={results.irv?.rounds.length ?? 0} />
      )}

      {embedded && (
        <p className="small muted" style={{ textAlign: 'center' }}>
          <a href={shareUrl} target="_blank" rel="noreferrer">{t('room.openInMeld')}</a> · {t('room.codeLabel')} <span className="mono">{room.code}</span>
        </p>
      )}

      {isOwner && !embedded && (
        <section className="panel">
          <div className="eyebrow">{t('room.ownerEyebrow')}</div>
          <div className="chips" style={{ marginTop: 10 }}>
            {open ? (
              <button className="btn small primary" disabled={total === 0} onClick={() => { closeRoom(room, results.leaderId); }}>
                {ranked ? t('room.closeWinner') : t('room.closeLeader')}
              </button>
            ) : (
              <button className="btn small" onClick={() => reopenRoom(room)}>{t('room.reopen')}</button>
            )}
            <button
              className="btn small ghost"
              disabled={ballots.length === 0}
              onClick={() => confirm(t('room.resetConfirm')) && resetVotes(room)}
            >
              {t('room.reset')}
            </button>
          </div>
          {open && leaders.length > 1 && <p className="small muted">{t('room.tie', { list: list(leaders.map(labelOf)) })}</p>}
        </section>
      )}
      {isOwner && !embedded && <Integrations room={room} />}

      {!embedded && <Opinions room={room} comments={comments} options={options} open={open} isOwner={isOwner} authUid={authUid} />}
    </>
  );
}

/** After the decision: add the winning slot to a calendar, and export a shareable image card. */
function Outcome({ room, options, counts, total, ballots, winnerId, tie, rounds }: {
  room: Room; options: RoomState['options']; counts: Record<string, number>; total: number;
  ballots: RoomState['ballots']; winnerId: string | null; tie: boolean; rounds: number;
}) {
  const [busy, setBusy] = useState(false);
  const winner = options.find((o) => o.id === winnerId) ?? null;
  const url = `${location.origin}/r/${room.code}`;
  const details = `${t('outcome.decidedIn', { winner: winner?.label ?? '' })}\n${url}`;
  const slot = winner?.slotStartMs ? { start: winner.slotStartMs, end: winner.slotEndMs ?? winner.slotStartMs + 3_600_000 } : null;
  const feeds = feedUrls(room.code, room.seriesId);

  const exportCard = async () => {
    setBusy(true);
    try {
      const pct = (id: string) => (total > 0 ? Math.round(((counts[id] ?? 0) / total) * 100) : 0);
      const blob = await renderCard({
        code: room.code,
        url,
        question: room.question,
        modeLabel: `${room.mode === 'TIME_SLOTS' ? t('mode.timeSlots') : t('mode.quickPoll')}${room.isRanked ? ` · ${t('tag.ranked')}` : ''}`,
        decided: true,
        winner: winner ? { label: winner.label, votes: counts[winner.id] ?? 0, pct: pct(winner.id) } : null,
        rows: [...options].sort((a, b) => (counts[b.id] ?? 0) - (counts[a.id] ?? 0)).map((o) => ({ label: o.label, votes: counts[o.id] ?? 0, pct: pct(o.id), isWinner: o.id === winnerId })),
        voters: ballots.filter((b) => winner && (room.isRanked ? b.optionIds[0] === winner.id : b.optionIds.includes(winner.id))).map((b) => ({ avatarStyleIndex: b.avatarStyleIndex ?? 0, colorIndex: b.badgeColorIndex })),
        totalVoters: ballots.length,
        note: room.isRanked && rounds > 1 ? t('outcome.afterRounds', { count: rounds }) : tie ? t('outcome.tieBreak') : undefined,
        dark: matchMedia('(prefers-color-scheme: dark)').matches,
      });
      await shareOrDownloadCard(blob, room.code);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="panel stack">
      <div className="eyebrow" style={{ color: 'var(--emerald)' }}>{t('outcome.decision')}</div>
      <div style={{ fontWeight: 700, fontSize: 22 }}>{winner ? winner.label : t('outcome.noVotes')}</div>
      {slot && (
        <div className="chips">
          <a className="btn small" href={googleCalendarUrl(room.question, slot.start, slot.end, details)} target="_blank" rel="noreferrer">{t('outcome.googleCal')}</a>
          <button className="btn small" onClick={() => downloadIcs(room.code, room.question, slot.start, slot.end, details)}>{t('outcome.ics')}</button>
          <a className="btn small ghost" href={feeds.apple} title={t('outcome.subscribeTitle')}>{t('outcome.subscribe')}</a>
        </div>
      )}
      <button className="btn primary" disabled={busy} onClick={exportCard}>{busy ? t('outcome.making') : t('outcome.shareCard')}</button>
    </section>
  );
}

function Status({ room, open, now, synced }: { room: Room; open: boolean; now: number; synced: boolean }) {
  if (!open) return <span className="badge">{t('status.closed')}</span>;
  if (room.deadlineEpochMs) {
    return <span className="badge warn"><span className="dot" />{t('status.left', { time: formatRemaining(room.deadlineEpochMs - now) })}</span>;
  }
  return synced
    ? <span className="badge live"><span className="dot" />{t('status.live')}</span>
    : <span className="badge warn"><span className="dot" />{t('status.syncing')}</span>;
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
        <Qr value={url} label={t('share.qr', { code: room.code })} />
        <div className="grow stack" style={{ minWidth: 200 }}>
          <div className="eyebrow">{t('share.invite')}</div>
          <div className="mono" style={{ fontSize: 28, letterSpacing: '0.2em' }}>{room.code}</div>
          <div className="small muted" style={{ wordBreak: 'break-all' }}>{url}</div>
          <div className="chips">
            <button className="btn small primary" onClick={copy}>{copied ? t('share.copied') : t('share.copy')}</button>
            {canShare && (
              <button className="btn small" onClick={() => navigator.share({ title: 'Meld', text: room.question, url }).catch(() => {})}>
                {t('share.share')}
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
          setRecError(t('op.tooLong'));
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
      setRecError(t('op.noMic'));
    }
  };
  const stopRecording = () => {
    recorder.current?.stop();
    setRecording(false);
  };

  if (!open && comments.length === 0) return null;

  return (
    <section className="panel">
      <div className="eyebrow">{t('op.title')}</div>
      <div style={{ marginTop: 6 }}>
        {comments.length === 0 && <p className="small muted">{t('op.empty')}</p>}
        {comments.map((c) => {
          const optionLabel = options.find((o) => o.id === c.optionId)?.label;
          const mineComment = c.authorAuthUid === authUid || c.voterUid === me.uid;
          return (
            <div className="comment" key={c.id}>
              <GeometricAvatar avatarStyleIndex={c.avatarStyleIndex ?? 0} colorIndex={c.badgeColorIndex} size={40} title={c.voterName} />
              <div className="grow">
                <div className="small"><strong>{c.voterName}</strong>{optionLabel && <span className="muted"> · {t('op.on', { option: optionLabel })}</span>}</div>
                <div style={{ marginTop: 2 }}>{c.transcript}</div>
                {c.hasAudio && <AudioPlayer room={room} id={c.id} />}
              </div>
              {(mineComment || isOwner) && (
                <button className="btn ghost small" aria-label={t('op.delete')} onClick={() => deleteComment(room, c.id)}>✕</button>
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
          <select className="input" value={target} onChange={(e) => setTarget(e.target.value)} aria-label={t('op.target')}>
            <option value="">{t('op.aboutRoom')}</option>
            {options.map((o) => <option key={o.id} value={o.id}>{t('op.aboutOption', { option: o.label })}</option>)}
          </select>
          <div className="row">
            <input className="input grow" placeholder={t('op.placeholder')} maxLength={300} value={text} onChange={(e) => setText(e.target.value)} aria-label={t('op.aria')} />
            <button className="btn" type="submit" disabled={!text.trim()}>{t('common.post')}</button>
          </div>
          {'MediaRecorder' in window && (
            <button type="button" className={`btn small${recording ? ' primary' : ''}`} onClick={recording ? stopRecording : startRecording}>
              {recording ? t('op.stop') : t('op.record')}
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
      {failed ? t('op.audioOffline') : t('op.play')}
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
function OptionCard({ label, count, pct, voters, mine, rank = null, winner, open, onVote }: {
  label: string; count: number; pct: number; voters: ArenaOption['voters']; mine: boolean; rank?: number | null; winner: boolean; open: boolean;
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
      aria-label={rank ? t('option.choiceN', { label, n: rank }) : undefined}
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
      <span className="swipe-hint yes" style={{ opacity: Math.max(0, dx) / THRESHOLD }}>{t('option.vote')}</span>
      <span className="swipe-hint no" style={{ opacity: Math.max(0, -dx) / THRESHOLD }}>{t('option.drop')}</span>
      <span className="content" style={{ opacity: dx ? 0.35 + 0.65 * (1 - Math.min(1, Math.abs(dx) / 140)) : 1 }}>
        <span className={`check${rank ? ' ranked' : ''}`} aria-hidden="true">{rank ?? (mine ? '✓' : '')}</span>
        <span className="grow">
          <span style={{ fontWeight: 600, display: 'block' }}>{winner ? '★ ' : ''}{label}</span>
          {voters.length > 0 && <span style={{ display: 'block', marginTop: 6 }}><AvatarStack voters={voters} size={26} /></span>}
        </span>
        <span className="count">{count}<span className="muted small"> · {pct}%</span></span>
      </span>
    </button>
  );
}
