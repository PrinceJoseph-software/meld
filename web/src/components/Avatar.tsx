import { BADGE_COLORS, initials } from '../identity';

export function Avatar({ name, colorIndex, large }: { name: string; colorIndex: number; large?: boolean }) {
  const color = BADGE_COLORS[((colorIndex % BADGE_COLORS.length) + BADGE_COLORS.length) % BADGE_COLORS.length];
  return (
    <span className={`avatar${large ? ' lg' : ''}`} style={{ background: color }} title={name} aria-hidden="true">
      {initials(name)}
    </span>
  );
}
