import { useEffect, useRef } from 'react';
import { GeometricAvatar } from './GeometricAvatar';
import { Qr } from './Qr';
import type { ArenaOption } from './Arena';

/**
 * Port of LivePresentStageModal: a distraction-free full-screen stage for screen sharing
 * (Meet/Zoom/Discord or a TV): avatar race lanes, live counts, and a QR for scan-to-vote.
 */
export function PresentStage({ question, code, url, options, total, winnerId, onClose }: {
  question: string; code: string; url: string; options: ArenaOption[]; total: number; winnerId: string | null; onClose: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  // Keep the latest onClose without re-running the effect: the room re-renders every second
  // (countdown), and re-running would exit fullscreen, which browsers won't re-enter without a tap.
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    const el = ref.current;
    el?.requestFullscreen?.().catch(() => { /* not allowed: overlay still covers the page */ });
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && closeRef.current();
    const onFs = () => !document.fullscreenElement && closeRef.current();
    window.addEventListener('keydown', onKey);
    document.addEventListener('fullscreenchange', onFs);
    return () => {
      window.removeEventListener('keydown', onKey);
      document.removeEventListener('fullscreenchange', onFs);
      if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    };
  }, []);

  return (
    <div ref={ref} className="stage" role="dialog" aria-label="Present mode">
      <div className="stage-head">
        <div className="grow">
          <div className="eyebrow">Live · {total} vote{total === 1 ? '' : 's'}</div>
          <h1 className="stage-title">{question}</h1>
        </div>
        <div className="stage-qr">
          <Qr value={url} label={`QR code for room ${code}`} />
          <div className="mono" style={{ fontSize: 22, letterSpacing: '0.2em', textAlign: 'center', marginTop: 8 }}>{code}</div>
        </div>
      </div>
      <div className="lanes">
        {options.map((o) => {
          const pct = total > 0 ? o.count / total : 0;
          return (
            <div key={o.id} className={`lane${winnerId === o.id ? ' won' : ''}`}>
              <div className="row between"><span className="lane-label">{winnerId === o.id ? '★ ' : ''}{o.label}</span><span className="mono count">{o.count}</span></div>
              <div className="lane-track">
                <div className="lane-runner" style={{ left: `calc(${pct * 100}% - ${pct * 44}px)` }}>
                  {o.voters[0]
                    ? <GeometricAvatar avatarStyleIndex={o.voters[0].avatarStyleIndex} colorIndex={o.voters[0].colorIndex} size={44} pushBias={1} reactionTrigger={o.count} />
                    : <span className="lane-empty" />}
                </div>
              </div>
            </div>
          );
        })}
      </div>
      <button className="btn small stage-close" onClick={onClose}>Exit present mode</button>
    </div>
  );
}
