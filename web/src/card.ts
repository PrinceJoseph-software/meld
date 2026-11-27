// Poll result card (PNG, 1080×1350 — Instagram/WhatsApp-friendly portrait). Mirrors the
// Android PollCardImageExporter, drawn with the app's fonts, palette, dot texture and mark.
import QRCode from 'qrcode';
import { AVATAR_VIEWBOX, avatarShapes, fromPacked } from './avatar';
import { BADGE_COLORS } from './identity';
import { getLang, t } from './i18n';

export interface CardData {
  code: string;
  url: string;
  question: string;
  modeLabel: string;
  decided: boolean;
  winner: { label: string; votes: number; pct: number } | null;
  rows: { label: string; votes: number; pct: number; isWinner: boolean }[];
  voters: { avatarStyleIndex: number; colorIndex: number }[];
  totalVoters: number;
  note?: string;
  dark: boolean;
}

const W = 1080;
const H = 1350;
const CAP = 'M34,46 L74,46 A8,8 0 0 1 82,54 L82,54 A8,8 0 0 1 74,62 L34,62 A8,8 0 0 1 26,54 L26,54 A8,8 0 0 1 34,46 Z';
const CAP_OUT = 'M34,43 L74,43 A11,11 0 0 1 85,54 L85,54 A11,11 0 0 1 74,65 L34,65 A11,11 0 0 1 23,54 L23,54 A11,11 0 0 1 34,43 Z';
const DIAMOND = 'M54,47.5 L60.5,54 L54,60.5 L47.5,54 Z';

async function loadFonts() {
  const faces = [
    new FontFace('MeldSerif', 'url(/fonts/instrument_serif.ttf)'),
    new FontFace('MeldSans', 'url(/fonts/plus_jakarta_sans.ttf)', { weight: '200 800' }),
    new FontFace('MeldMono', 'url(/fonts/jetbrains_mono.ttf)', { weight: '100 800' }),
  ];
  await Promise.all(faces.map(async (f) => {
    try { document.fonts.add(await f.load()); } catch { /* falls back to system fonts */ }
  }));
}

function avatarImage(styleIndex: number, colorIndex: number, size: number): Promise<HTMLImageElement> {
  const bg = BADGE_COLORS[((colorIndex % 8) + 8) % 8];
  const inner = avatarShapes(fromPacked(styleIndex)).map((s) =>
    `<${s.tag} ${Object.entries(s.attrs).map(([k, v]) => `${k}="${v}"`).join(' ')}/>`).join('');
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${AVATAR_VIEWBOX}" width="${size}" height="${size}">` +
    `<clipPath id="c"><circle cx="50" cy="50" r="50"/></clipPath><g clip-path="url(#c)"><rect width="100" height="100" fill="${bg}"/>${inner}</g></svg>`;
  return loadImage(`data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`);
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

function wrap(ctx: CanvasRenderingContext2D, text: string, maxWidth: number, maxLines: number): string[] {
  const words = text.split(/\s+/);
  const lines: string[] = [];
  let line = '';
  for (const w of words) {
    const test = line ? `${line} ${w}` : w;
    if (ctx.measureText(test).width > maxWidth && line) {
      lines.push(line);
      line = w;
    } else {
      line = test;
    }
  }
  if (line) lines.push(line);
  if (lines.length > maxLines) {
    const kept = lines.slice(0, maxLines);
    let last = kept[maxLines - 1];
    while (ctx.measureText(`${last}…`).width > maxWidth && last.length) last = last.slice(0, -1);
    kept[maxLines - 1] = `${last}…`;
    return kept;
  }
  return lines;
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

export async function renderCard(d: CardData): Promise<Blob> {
  await loadFonts();
  const c = { canvas: d.dark ? '#0B0B0D' : '#F6F5F1', surface: d.dark ? '#141417' : '#FFFFFF', inset: d.dark ? '#1E1E22' : '#EFECE6',
    ink: d.dark ? '#F4F4F6' : '#121214', ink2: d.dark ? '#8E8E98' : '#5C5C66', hair: d.dark ? 'rgba(255,255,255,0.09)' : '#E2DFD7',
    accent: d.dark ? '#FF572E' : '#FF451A', emerald: d.dark ? '#14B8A6' : '#0D9488', dot: d.dark ? 'rgba(255,255,255,0.16)' : 'rgba(92,92,102,0.22)' };
  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d')!;

  // Background + dot texture
  ctx.fillStyle = c.canvas;
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = c.dot;
  for (let y = 18; y < H; y += 36) for (let x = 18; x < W; x += 36) { ctx.beginPath(); ctx.arc(x, y, 1.6, 0, Math.PI * 2); ctx.fill(); }

  const pad = 72;
  // Mark + wordmark
  ctx.save();
  ctx.translate(pad - 14, 64);
  ctx.scale(0.9, 0.9);
  ctx.translate(-15, -15);
  const rot = (deg: number, draw: () => void) => { ctx.save(); ctx.translate(54, 54); ctx.rotate((deg * Math.PI) / 180); ctx.translate(-54, -54); draw(); ctx.restore(); };
  rot(35, () => { ctx.fillStyle = c.ink; ctx.fill(new Path2D(CAP)); });
  rot(-35, () => { ctx.fillStyle = c.canvas; ctx.fill(new Path2D(CAP_OUT)); ctx.fillStyle = c.accent; ctx.fill(new Path2D(CAP)); });
  ctx.fillStyle = c.canvas; ctx.fill(new Path2D(DIAMOND));
  ctx.strokeStyle = c.ink; ctx.lineWidth = 2.5; ctx.stroke(new Path2D(DIAMOND));
  ctx.restore();
  ctx.fillStyle = c.ink;
  ctx.font = '600 40px MeldSans, sans-serif';
  ctx.textBaseline = 'middle';
  ctx.fillText('Meld', pad + 66, 100);
  ctx.font = '600 26px MeldMono, monospace';
  ctx.fillStyle = c.ink2;
  ctx.textAlign = 'right';
  ctx.fillText(d.code.split('').join(' '), W - pad, 100);
  ctx.textAlign = 'left';

  // Layout pass: size the panel to its content, then centre panel + footer on the card.
  ctx.font = '400 74px MeldSerif, serif';
  const qLines = wrap(ctx, d.question, W - 2 * pad - 24, 3);
  const rowCount = Math.min(d.rows.length, 7);
  const contentH = 72 + 34 + qLines.length * 78 + 18 + (d.winner ? 168 + 34 : 0) + rowCount * 82 + 24;
  const panelH = Math.min(920, Math.max(560, contentH));
  const blockH = panelH + 40 + 180;
  const panelY = Math.max(168, Math.round(168 + (H - 168 - 60 - blockH) / 2));
  ctx.fillStyle = c.surface;
  roundRect(ctx, pad - 24, panelY, W - 2 * (pad - 24), panelH, 36);
  ctx.fill();
  ctx.strokeStyle = c.hair; ctx.lineWidth = 2; ctx.stroke();

  let y = panelY + 72;
  ctx.font = '700 22px MeldMono, monospace';
  ctx.fillStyle = d.decided ? c.emerald : c.accent;
  ctx.fillText(`${t(d.decided ? 'card.decided' : 'card.live')} · ${d.modeLabel.toLocaleUpperCase(getLang())}`, pad + 12, y);
  y += 34;
  ctx.font = '400 74px MeldSerif, serif';
  ctx.fillStyle = c.ink;
  ctx.textBaseline = 'top';
  for (const line of qLines) { ctx.fillText(line, pad + 12, y); y += 78; }
  y += 18;

  // Winner block
  if (d.winner) {
    const bh = 168;
    ctx.fillStyle = d.decided ? (d.dark ? 'rgba(20,184,166,0.14)' : 'rgba(13,148,136,0.10)') : (d.dark ? 'rgba(255,87,46,0.14)' : 'rgba(255,69,26,0.08)');
    roundRect(ctx, pad + 12, y, W - 2 * pad - 24, bh, 24);
    ctx.fill();
    ctx.fillStyle = d.decided ? c.emerald : c.accent;
    ctx.font = '700 22px MeldMono, monospace';
    ctx.fillText(t(d.decided ? 'card.winner' : 'card.leading'), pad + 40, y + 28);
    ctx.fillStyle = c.ink;
    ctx.font = '700 50px MeldSans, sans-serif';
    ctx.fillText(wrap(ctx, d.winner.label, W - 2 * pad - 340, 1)[0], pad + 40, y + 66);
    ctx.font = '600 22px MeldSans, sans-serif';
    ctx.fillStyle = c.ink2;
    ctx.fillText(`${t('card.votes', { count: d.winner.votes })} · ${d.winner.pct}%${d.note ? ` · ${d.note}` : ''}`, pad + 40, y + 128);
    // voter avatars on the winner
    const shown = d.voters.slice(0, 5);
    const imgs = await Promise.all(shown.map((v) => avatarImage(v.avatarStyleIndex, v.colorIndex, 96).catch(() => null)));
    let ax = W - pad - 40 - 64;
    for (const img of imgs.reverse()) {
      if (!img) continue;
      ctx.save();
      ctx.beginPath(); ctx.arc(ax + 32, y + bh / 2, 35, 0, Math.PI * 2); ctx.fillStyle = c.surface; ctx.fill();
      ctx.drawImage(img, ax, y + bh / 2 - 32, 64, 64);
      ctx.restore();
      ax -= 44;
    }
    y += bh + 34;
  }

  // Result bars
  const rows = d.rows.slice(0, Math.max(1, Math.min(rowCount, Math.floor((panelY + panelH - 24 - y) / 82))));
  for (const r of rows) {
    const bw = W - 2 * pad - 24;
    ctx.fillStyle = c.inset;
    roundRect(ctx, pad + 12, y, bw, 64, 16); ctx.fill();
    if (r.pct > 0) {
      ctx.fillStyle = r.isWinner ? (d.dark ? 'rgba(20,184,166,0.30)' : 'rgba(13,148,136,0.20)') : (d.dark ? 'rgba(255,87,46,0.22)' : 'rgba(255,69,26,0.14)');
      roundRect(ctx, pad + 12, y, Math.max(32, (bw * r.pct) / 100), 64, 16); ctx.fill();
    }
    ctx.fillStyle = c.ink;
    ctx.font = '600 28px MeldSans, sans-serif';
    ctx.textBaseline = 'middle';
    ctx.fillText(wrap(ctx, r.label, bw - 220, 1)[0], pad + 36, y + 32);
    ctx.font = '700 26px MeldMono, monospace';
    ctx.textAlign = 'right';
    ctx.fillText(`${r.votes} · ${r.pct}%`, pad + 12 + bw - 24, y + 32);
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    y += 82;
  }

  // Footer: QR + call to action
  const qr = await loadImage(await QRCode.toDataURL(d.url, { margin: 1, width: 180, color: { dark: '#121214', light: '#FFFFFF' } }));
  const fy = panelY + panelH + 40;
  ctx.fillStyle = '#FFFFFF';
  roundRect(ctx, pad - 8, fy, 180, 180, 20); ctx.fill();
  ctx.drawImage(qr, pad + 2, fy + 10, 160, 160);
  ctx.fillStyle = c.ink;
  ctx.font = '700 34px MeldSans, sans-serif';
  ctx.fillText(t(d.decided ? 'card.seeResults' : 'card.scanToVote'), pad + 210, fy + 36);
  ctx.font = '500 24px MeldMono, monospace';
  ctx.fillStyle = c.accent;
  ctx.fillText(d.url.replace(/^https?:\/\//, ''), pad + 210, fy + 92);
  ctx.font = '500 22px MeldSans, sans-serif';
  ctx.fillStyle = c.ink2;
  ctx.fillText(`${t('card.voters', { count: d.totalVoters })} · ${new Date().toLocaleDateString(getLang(), { day: 'numeric', month: 'short', year: 'numeric' })}`, pad + 210, fy + 136);

  return new Promise((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('render failed'))), 'image/png'));
}

/** Share via the OS share sheet when possible (mobile), otherwise download the PNG. */
export async function shareOrDownloadCard(blob: Blob, code: string) {
  const file = new File([blob], `meld-${code}.png`, { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file], title: t('card.shareTitle') });
      return;
    } catch { /* cancelled: fall through to download */ }
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = file.name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
}
