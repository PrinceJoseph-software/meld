import { useEffect, useState } from 'react';

const CAP = 'M34,46 L74,46 A8,8 0 0 1 82,54 L82,54 A8,8 0 0 1 74,62 L34,62 A8,8 0 0 1 26,54 L26,54 A8,8 0 0 1 34,46 Z';
const CAP_OUT = 'M34,43 L74,43 A11,11 0 0 1 85,54 L85,54 A11,11 0 0 1 74,65 L34,65 A11,11 0 0 1 23,54 L23,54 A11,11 0 0 1 34,43 Z';
const DIAMOND = 'M54,47.5 L60.5,54 L54,60.5 L47.5,54 Z';

/** The Meld mark: same geometry as the Android splash, launcher icon and in-app logo. */
export function Mark({ size = 32, animate = false }: { size?: number; animate?: boolean }) {
  return (
    <svg className={`mark${animate ? ' mark-roll' : ''}`} viewBox="15 15 78 78" width={size} height={size} aria-hidden="true">
      <g className="cap-a"><path d={CAP} fill="var(--mark-a)" transform="rotate(35 54 54)" /></g>
      <g className="cap-b" transform="rotate(-35 54 54)">
        <path d={CAP_OUT} fill="var(--canvas)" />
        <path d={CAP} fill="var(--accent)" />
      </g>
      <path className="diamond" d={DIAMOND} fill="var(--canvas)" stroke="var(--mark-a)" strokeWidth="2.5" />
    </svg>
  );
}

/**
 * Launch choreography (port of LaunchCircleJoiningOverlay): the two capsules roll in from
 * opposite sides and lock into the mark, then the overlay fades. Non-blocking (<1s), shown
 * once per session, skipped with reduced motion.
 */
export function LaunchReveal() {
  const [show, setShow] = useState(() => {
    // Only on the home screen: someone opening a shared poll link goes straight to the poll.
    if (location.pathname !== '/') return false;
    try {
      if (sessionStorage.getItem('meld.launched')) return false;
      sessionStorage.setItem('meld.launched', '1');
    } catch { /* storage blocked: still fine to show */ }
    return !matchMedia('(prefers-reduced-motion: reduce)').matches;
  });
  useEffect(() => {
    if (!show) return;
    const t = setTimeout(() => setShow(false), 1100);
    return () => clearTimeout(t);
  }, [show]);
  if (!show) return null;
  return (
    <div className="launch" aria-hidden="true">
      <Mark size={112} animate />
    </div>
  );
}
