// Port of the Android GeometricAvatar (ui/components/GeometricAvatar.kt) and AvatarSpec
// packing (data/MeldModels.kt). The packed int is stored in ballots/comments as
// `avatarStyleIndex`, so an avatar designed on one platform renders identically on the other.

export const CHASSIS_COUNT = 10;
export const EYES_COUNT = 6;
export const ACCESSORY_COUNT = 7;
export const PATTERN_COUNT = 5;
export const FRAME_COUNT = 4;
const PACKED_FLAG = 1 << 20;

export const CHASSIS_NAMES = ['Visor', 'Aperture', 'Split', 'Totem', 'Diamond', 'Signal', 'Pixel', 'Orbital', 'Shield', 'Chrono'];
export const EYES_NAMES = ['Dots', 'Wink', 'Happy', 'Star', 'Scanner', 'Focused'];
export const ACCESSORY_NAMES = ['None', 'Antenna', 'Headphones', 'Crown', 'Halo', 'Specs', 'Spark'];
export const PATTERN_NAMES = ['Solid Matte', 'Diagonal Stripes', 'Micro Dot Grid', 'Concentric Rings', 'Quarter Split'];
export const FRAME_NAMES = ['Clean', 'Bezel', 'Gear Ring', 'Arc'];
export const PALETTE_NAMES = ['Vermilion', 'Cobalt', 'Emerald', 'Ochre', 'Steel Slate', 'Royal Violet', 'Sunset Coral', 'Cyber Cyan'];

export interface AvatarSpec { chassis: number; eyes: number; accessory: number; pattern: number; frame: number }

const clamp = (v: number, max: number) => Math.min(Math.max(v, 0), max - 1);

export function toPacked(s: AvatarSpec): number {
  return PACKED_FLAG | clamp(s.chassis, CHASSIS_COUNT) | (clamp(s.eyes, EYES_COUNT) << 4) |
    (clamp(s.accessory, ACCESSORY_COUNT) << 7) | (clamp(s.pattern, PATTERN_COUNT) << 10) |
    (clamp(s.frame, FRAME_COUNT) << 13);
}

export function fromPacked(raw: number): AvatarSpec {
  if ((raw & PACKED_FLAG) !== 0) {
    return {
      chassis: clamp(raw & 0xf, CHASSIS_COUNT),
      eyes: clamp((raw >>> 4) & 0x7, EYES_COUNT),
      accessory: clamp((raw >>> 7) & 0x7, ACCESSORY_COUNT),
      pattern: clamp((raw >>> 10) & 0x7, PATTERN_COUNT),
      frame: clamp((raw >>> 13) & 0x3, FRAME_COUNT),
    };
  }
  const safe = (((raw | 0) % 6) + 6) % 6; // legacy indices from older app versions
  return { chassis: safe, eyes: safe % EYES_COUNT, accessory: 0, pattern: safe === 2 ? 1 : 0, frame: 0 };
}

export const DEFAULT_MASK_SPEC: AvatarSpec = { chassis: 4, eyes: 4, accessory: 5, pattern: 3, frame: 1 };

/** Java String.hashCode, so default avatars match what Android picks for the same uid. */
export function javaHash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

export function randomSpec(): AvatarSpec {
  const r = (n: number) => Math.floor(Math.random() * n);
  return { chassis: r(CHASSIS_COUNT), eyes: r(EYES_COUNT), accessory: r(ACCESSORY_COUNT), pattern: r(PATTERN_COUNT), frame: r(FRAME_COUNT) };
}

// ---- SVG geometry (viewBox 0 0 100 100; w = h = 100, centre 50,50) -------------------------

const W = 100;
const C = 50;
const FG = '#FFFFFF';
const INK = '#121214';

type Attrs = Record<string, string | number>;
export interface Shape { tag: 'circle' | 'rect' | 'line' | 'path' | 'ellipse'; attrs: Attrs }

const fill = (color: string, alpha = 1): Attrs => ({ fill: color, 'fill-opacity': alpha });
const stroke = (color: string, width: number, alpha = 1, cap: 'round' | 'butt' = 'butt'): Attrs =>
  ({ fill: 'none', stroke: color, 'stroke-width': width, 'stroke-opacity': alpha, 'stroke-linecap': cap });

const circle = (cx: number, cy: number, r: number, a: Attrs): Shape => ({ tag: 'circle', attrs: { cx, cy, r, ...a } });
const line = (x1: number, y1: number, x2: number, y2: number, a: Attrs): Shape => ({ tag: 'line', attrs: { x1, y1, x2, y2, ...a } });
const rect = (x: number, y: number, w: number, h: number, rx: number, a: Attrs, extra: Attrs = {}): Shape =>
  ({ tag: 'rect', attrs: { x, y, width: w, height: h, rx, ry: rx, ...a, ...extra } });
const path = (d: string, a: Attrs, extra: Attrs = {}): Shape => ({ tag: 'path', attrs: { d, ...a, ...extra } });

/** Compose drawArc semantics: oval bounds + start/sweep in degrees, clockwise from 3 o'clock. */
function arc(x: number, y: number, w: number, h: number, start: number, sweep: number, useCenter: boolean): string {
  const rx = w / 2, ry = h / 2, cx = x + rx, cy = y + ry;
  const pt = (deg: number) => {
    const t = (deg * Math.PI) / 180;
    return [cx + rx * Math.cos(t), cy + ry * Math.sin(t)].map((v) => v.toFixed(2)).join(' ');
  };
  const large = Math.abs(sweep) > 180 ? 1 : 0;
  const a = `A ${rx} ${ry} 0 ${large} 1 ${pt(start + sweep)}`;
  return useCenter ? `M ${cx} ${cy} L ${pt(start)} ${a} Z` : `M ${pt(start)} ${a}`;
}

function patternLayer(p: number): Shape[] {
  switch (p) {
    case 1: return [-1, 0, 1, 2].map((i) => line(i * 34, 100, i * 34 + 100, 0, stroke(INK, 8, 0.22)));
    case 2: {
      const out: Shape[] = [];
      for (let y = 1; y <= 3; y++) for (let x = 1; x <= 3; x++) out.push(circle(x * 25, y * 25, 3.2, fill(FG, 0.24)));
      return out;
    }
    case 3: return [circle(C, C, 36, stroke(FG, 6, 0.22))];
    case 4: return [path('M 0 100 L 100 0 L 100 100 Z', fill(INK, 0.24))];
    default: return [];
  }
}

function chassisLayer(c: number): Shape[] {
  switch (c) {
    case 0: return [
      rect(C - 32, C - 14 - 4, 64, 28, 14, fill(INK)),
      line(C - 11, C + 20, C + 11, C + 20, stroke(FG, 6, 0.85, 'round')),
    ];
    case 1: return [circle(C, C - 2, 29, stroke(FG, 7.5, 0.32)), circle(C, C - 2, 18, fill(INK, 0.85))];
    case 2: return [
      rect(C - 32, C - 20, 64, 44, 12, fill(INK, 0.28)),
      path(arc(C - 16, C + 6, 32, 18, 20, 140, false), stroke(FG, 6.5, 1, 'round')),
    ];
    case 3: return [rect(C - 16, C + 14, 32, 11, 5.5, fill(INK))];
    case 4: return [
      rect(C - 26, C - 26, 52, 52, 8, fill(FG, 0.22), { transform: `rotate(45 ${C} ${C})` }),
      circle(C, C + 18, 5.5, fill(INK)),
    ];
    case 5: return [circle(C, C - 22, 5.5, fill(INK)), path(arc(C - 18, C + 5, 36, 22, 15, 150, false), stroke(FG, 7, 1, 'round'))];
    case 6: return [
      rect(C - 29, C - 18 + 1, 58, 36, 0, fill(INK, 0.8)),
      line(C - 58 * 0.35, C + 12, C + 58 * 0.35, C + 12, stroke(FG, 4.5, 0.4)),
    ];
    case 7: return [
      { tag: 'ellipse', attrs: { cx: C, cy: C, rx: 38, ry: 14, transform: `rotate(-24 ${C} ${C})`, ...stroke(FG, 5.5, 0.36) } },
      circle(C, C, 24, fill(INK, 0.45)),
    ];
    case 8: return [path(arc(C - 34, C - 34, 68, 68, 210, 120, true), fill(INK, 0.35)), circle(C, C + 18, 5.5, fill(FG))];
    default: return [circle(C, C, 30, stroke(INK, 6, 0.55)), line(C, C + 18, C, C + 27, stroke(FG, 5.5, 0.8, 'round'))];
  }
}

function eyesLayer(e: number): Shape[] {
  const y = C - 4, l = C - 14, r = C + 14;
  switch (e) {
    case 1: return [circle(l, y, 7.5, fill(FG)), line(r - 7, y, r + 7, y, stroke(FG, 7.5, 1, 'round'))];
    case 2: return [l, r].map((x) => path(arc(x - 7, y - 5, 14, 12, 195, 150, false), stroke(FG, 6.5, 1, 'round')));
    case 3: return [l, r].map((x) => path(`M ${x} ${y - 7.8} L ${x + 7.8} ${y} L ${x} ${y + 7.8} L ${x - 7.8} ${y} Z`, fill(FG)));
    case 4: return [rect(C - 22, y - 4.5, 44, 9, 4.5, fill(FG)), circle(C + 6, y, 3.2, fill(INK))];
    case 5: return [
      line(l - 6, y - 4, l + 6, y + 2, stroke(FG, 7, 1, 'round')),
      line(r + 6, y - 4, r - 6, y + 2, stroke(FG, 7, 1, 'round')),
    ];
    default: return [circle(l, y, 7.2, fill(FG)), circle(r, y, 7.2, fill(FG))];
  }
}

function accessoryLayer(a: number): Shape[] {
  switch (a) {
    case 1: return [line(C, 24, C, 8, stroke(FG, 5.5, 1, 'round')), circle(C, 8, 7, fill(INK)), circle(C, 8, 3.8, fill(FG))];
    case 2: return [
      path(arc(C - 38, 12, 76, 56, 195, 150, false), stroke(INK, 6.5, 1, 'round')),
      rect(8, C - 12, 11, 24, 5, fill(FG)),
      rect(81, C - 12, 11, 24, 5, fill(FG)),
    ];
    case 3: return [path(`M ${C - 18} 24 L ${C - 18} 12 L ${C - 7} 18 L ${C} 9 L ${C + 7} 18 L ${C + 18} 12 L ${C + 18} 24 Z`, fill(FG))];
    case 4: return [{ tag: 'ellipse', attrs: { cx: C, cy: 14.5, rx: 22, ry: 5.5, ...stroke(FG, 5.5, 0.9) } }];
    case 5: {
      const y = C - 4;
      return [circle(C - 14, y, 12, stroke(INK, 5)), circle(C + 14, y, 12, stroke(INK, 5)), line(C - 2, y, C + 2, y, stroke(INK, 5))];
    }
    case 6: return [path(`M ${C} 7 L ${C + 6.3} 16 L ${C} 25 L ${C - 6.3} 16 Z`, fill(FG))];
    default: return [];
  }
}

function frameLayer(f: number): Shape[] {
  switch (f) {
    case 1: return [circle(C, C, 43, stroke(FG, 4, 0.55))];
    case 2: return [circle(C, C, 44, { ...stroke(INK, 5, 0.55), 'stroke-dasharray': '9 6' })];
    case 3: return [path(arc(6, 6, 88, 88, -55, 95, false), stroke(FG, 5.5, 0.75, 'round'))];
    default: return [];
  }
}

export function avatarShapes(spec: AvatarSpec): Shape[] {
  return [
    ...patternLayer(spec.pattern),
    ...chassisLayer(spec.chassis),
    ...eyesLayer(spec.eyes),
    ...accessoryLayer(spec.accessory),
    ...frameLayer(spec.frame),
  ];
}

export const AVATAR_VIEWBOX = `0 0 ${W} ${W}`;
