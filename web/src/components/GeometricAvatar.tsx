import { useEffect, useRef } from 'react';
import { AVATAR_VIEWBOX, avatarShapes, fromPacked } from '../avatar';
import { BADGE_COLORS } from '../identity';

interface Props {
  avatarStyleIndex: number;
  colorIndex: number;
  size?: number;
  /** Increment to play the push reaction (pop, lunge, hop, tilt). */
  reactionTrigger?: number;
  /** -1..1: how hard this avatar leans (e.g. toward the option it's pushing). */
  pushBias?: number;
  title?: string;
  className?: string;
}

const reducedMotion = () =>
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Same geometry and motion as the Android GeometricAvatar. */
export function GeometricAvatar({ avatarStyleIndex, colorIndex, size = 28, reactionTrigger = 0, pushBias = 0, title, className }: Props) {
  const ref = useRef<HTMLSpanElement>(null);
  const spec = fromPacked(avatarStyleIndex);
  const bg = BADGE_COLORS[((colorIndex % BADGE_COLORS.length) + BADGE_COLORS.length) % BADGE_COLORS.length];
  const rest = `rotate(${pushBias * 9}deg)`;

  useEffect(() => {
    if (reducedMotion()) return;
    ref.current?.animate(
      [{ transform: `scale(0.65) ${rest}` }, { transform: `scale(1.08) ${rest}`, offset: 0.6 }, { transform: `scale(1) ${rest}` }],
      { duration: 360, easing: 'cubic-bezier(.34,1.56,.64,1)' },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!reactionTrigger || reducedMotion()) return;
    const dir = pushBias === 0 ? 1 : Math.sign(pushBias);
    ref.current?.animate(
      [
        { transform: `translate(0,0) scale(1) ${rest}` },
        { transform: `translate(${dir * 7}px,-4px) scale(1.18) rotate(${dir * 19}deg)`, offset: 0.22 },
        { transform: `translate(${dir * -2}px,1px) scale(0.97) rotate(${dir * -4}deg)`, offset: 0.55 },
        { transform: `translate(0,0) scale(1) ${rest}` },
      ],
      { duration: 520, easing: 'ease-out' },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reactionTrigger]);

  return (
    <span
      ref={ref}
      className={`geo-avatar${className ? ` ${className}` : ''}`}
      style={{ width: size, height: size, background: bg, transform: rest }}
      title={title}
      aria-hidden={title ? undefined : true}
      role={title ? 'img' : undefined}
      aria-label={title}
    >
      <svg viewBox={AVATAR_VIEWBOX} width={size} height={size} focusable="false">
        {avatarShapes(spec).map((s, i) => {
          const Tag = s.tag;
          return <Tag key={i} {...(s.attrs as Record<string, string | number>)} />;
        })}
      </svg>
    </span>
  );
}

export function AvatarStack({ voters, max = 5, size = 28, reactionTrigger = 0 }: {
  voters: { key: string; name: string; avatarStyleIndex: number; colorIndex: number }[];
  max?: number;
  size?: number;
  reactionTrigger?: number;
}) {
  if (voters.length === 0) return null;
  const visible = voters.slice(0, max);
  const overflow = voters.length - visible.length;
  return (
    <span className="avatar-stack">
      {visible.map((v) => (
        <GeometricAvatar key={v.key} avatarStyleIndex={v.avatarStyleIndex} colorIndex={v.colorIndex} size={size} title={v.name} reactionTrigger={reactionTrigger} />
      ))}
      {overflow > 0 && <span className="geo-avatar overflow" style={{ width: size, height: size }}>+{overflow}</span>}
    </span>
  );
}
