import { useEffect, useState } from 'react';
import { GeometricAvatar } from './GeometricAvatar';

export interface ArenaVoter { key: string; name: string; avatarStyleIndex: number; colorIndex: number }
export interface ArenaOption { id: string; label: string; count: number; voters: ArenaVoter[] }

/**
 * Port of DecisionPushArena: voters' avatars lean and push the decision toward consensus,
 * or battle head-to-head across a fulcrum when two options are contested. Tap to "Push"
 * (every avatar lunges); avatars also react whenever a new vote lands.
 */
export function PushArena({ options, total, locked, winnerId }: {
  options: ArenaOption[]; total: number; locked: boolean; winnerId: string | null;
}) {
  const [nudge, setNudge] = useState(0);
  const [lastTotal, setLastTotal] = useState(total);
  useEffect(() => {
    if (total !== lastTotal) {
      setLastTotal(total);
      setNudge((n) => n + 1);
    }
  }, [total, lastTotal]);

  const leader = options[0];
  if (!leader) return null;
  const unanimous = total > 0 && leader.count === total && leader.voters.length >= 2;
  const challenger = options[1] && options[1].count > 0 && !locked && !unanimous ? options[1] : null;
  const pct = total > 0 ? Math.round((leader.count / total) * 100) : 0;

  const level = locked ? 'DECIDED' : unanimous || pct >= 67 ? 'SURGE' : total >= 2 ? 'RALLY' : 'SPARK';
  const tone = locked || unanimous ? 'emerald' : total >= 2 ? 'accent' : 'muted';

  return (
    <section
      className={`panel arena arena-${tone}`}
      role="button"
      tabIndex={total > 0 ? 0 : -1}
      aria-label="Decision momentum. Tap to push."
      onClick={() => total > 0 && setNudge((n) => n + 1)}
      onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && total > 0 && setNudge((n) => n + 1)}
    >
      <div className="row between">
        <div className="row" style={{ gap: 8 }}>
          <span className="small mono muted" style={{ letterSpacing: '0.08em' }}>DECISION MOMENTUM</span>
          <span className={`level level-${tone}`}>{level}</span>
        </div>
        {total > 0 && <span className="small mono accent">Push ›</span>}
      </div>

      {total === 0 ? (
        <p className="small muted" style={{ margin: '10px 0 0' }}>Vote to put your avatar on the push track</p>
      ) : challenger ? (
        <>
          <div className="tug">
            <Squad voters={leader.voters} bias={1} nudge={nudge} />
            <div className="track">
              {[0.25, 0.5, 0.75].map((t) => <span key={t} className="tick" style={{ left: `${t * 100}%` }} />)}
              <span className="puck" style={{ left: `${(leader.count / (leader.count + challenger.count)) * 100}%` }} />
            </div>
            <Squad voters={challenger.voters} bias={-1} nudge={nudge} />
          </div>
          <p className="small muted arena-caption">{leader.label} ({leader.count}) vs {challenger.label} ({challenger.count})</p>
        </>
      ) : (
        <>
          <div className="push">
            <div className="push-track">
              <div className="push-block" style={{ width: `${locked || unanimous ? 100 : Math.max(20, pct)}%` }}>
                <Squad voters={leader.voters} bias={1} nudge={nudge} />
                {!locked && <span className="chevrons" aria-hidden="true">›››</span>}
              </div>
              <span className="finish" />
            </div>
          </div>
          <p className="small muted arena-caption">
            {locked && winnerId ? `Pushed across the line: ${leader.label}` : `${leader.label} leading the push • ${pct}%`}
          </p>
        </>
      )}
    </section>
  );
}

function Squad({ voters, bias, nudge }: { voters: ArenaVoter[]; bias: number; nudge: number }) {
  const shown = voters.slice(0, 4);
  return (
    <span className="squad">
      {shown.map((v) => (
        <GeometricAvatar key={v.key} avatarStyleIndex={v.avatarStyleIndex} colorIndex={v.colorIndex} size={30} pushBias={bias} reactionTrigger={nudge} title={v.name} />
      ))}
      {voters.length > shown.length && <span className="small mono muted">+{voters.length - shown.length}</span>}
    </span>
  );
}

/** Port of PollDeadlineBanner: live countdown with a draining bar. */
export function DeadlineBanner({ createdAt, deadline, now }: { createdAt: number; deadline: number; now: number }) {
  const remaining = Math.max(0, deadline - now);
  const span = Math.max(1, deadline - createdAt);
  const urgent = remaining < 60_000;
  const s = Math.floor(remaining / 1000);
  const text = s >= 3600
    ? `${Math.floor(s / 3600)}h ${String(Math.floor((s % 3600) / 60)).padStart(2, '0')}m ${String(s % 60).padStart(2, '0')}s`
    : `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  return (
    <div className={`deadline${urgent ? ' urgent' : ''}`} role="timer" aria-live="off">
      <div className="row between small">
        <span className="mono">{remaining > 0 ? 'Closes in' : 'Deadline reached'}</span>
        <span className="mono count">{text}</span>
      </div>
      <div className="deadline-bar"><span style={{ width: `${(remaining / span) * 100}%` }} /></div>
    </div>
  );
}

/** Celebration burst: geometric confetti in the brand colours (skipped with reduced motion). */
export function Burst({ trigger }: { trigger: number }) {
  const [pieces, setPieces] = useState<{ id: number; x: number; y: number; r: number; c: string; s: number }[]>([]);
  useEffect(() => {
    if (!trigger || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const colors = ['var(--accent)', 'var(--emerald)', 'var(--ink)', '#D97706'];
    const next = Array.from({ length: 18 }, (_, i) => ({
      id: trigger * 100 + i,
      x: (Math.random() - 0.5) * 260,
      y: -80 - Math.random() * 160,
      r: Math.random() * 360,
      c: colors[i % colors.length],
      s: 6 + Math.random() * 6,
    }));
    setPieces(next);
    const t = setTimeout(() => setPieces([]), 900);
    return () => clearTimeout(t);
  }, [trigger]);
  if (pieces.length === 0) return null;
  return (
    <div className="burst" aria-hidden="true">
      {pieces.map((p) => (
        <span
          key={p.id}
          style={{
            width: p.s, height: p.s, background: p.c, borderRadius: p.id % 3 === 0 ? '50%' : 2,
            ['--x' as string]: `${p.x}px`, ['--y' as string]: `${p.y}px`, ['--r' as string]: `${p.r}deg`,
          }}
        />
      ))}
    </div>
  );
}
