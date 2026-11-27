// Renders promo graphics (title, captions, frames, end card) as PNGs with the app's fonts.
const { chromium } = require('playwright');
const fs = require('fs');
const OUT = __dirname + '/out/assets';
fs.mkdirSync(OUT, { recursive: true });
const FONTS = require('path').join(__dirname, '../../web/public/fonts');
const f64 = (n) => fs.readFileSync(`${FONTS}/${n}`).toString('base64');
const fontCss = `
@font-face{font-family:MeldSerif;src:url(data:font/ttf;base64,${f64('instrument_serif.ttf')})}
@font-face{font-family:MeldSans;src:url(data:font/ttf;base64,${f64('plus_jakarta_sans.ttf')});font-weight:200 800}
@font-face{font-family:MeldMono;src:url(data:font/ttf;base64,${f64('jetbrains_mono.ttf')});font-weight:100 800}
*{margin:0;box-sizing:border-box} body{font-family:MeldSans;color:#F4F4F6}`;
const CAP = 'M34,46 L74,46 A8,8 0 0 1 82,54 L82,54 A8,8 0 0 1 74,62 L34,62 A8,8 0 0 1 26,54 L26,54 A8,8 0 0 1 34,46 Z';
const CAP_OUT = 'M34,43 L74,43 A11,11 0 0 1 85,54 L85,54 A11,11 0 0 1 74,65 L34,65 A11,11 0 0 1 23,54 L23,54 A11,11 0 0 1 34,43 Z';
const DIA = 'M54,47.5 L60.5,54 L54,60.5 L47.5,54 Z';
const mark = (s) => `<svg width="${s}" height="${s}" viewBox="15 15 78 78"><path d="${CAP}" fill="#F4F4F6" transform="rotate(35 54 54)"/>
<g transform="rotate(-35 54 54)"><path d="${CAP_OUT}" fill="#0B0B0D"/><path d="${CAP}" fill="#FF572E"/></g>
<path d="${DIA}" fill="#0B0B0D" stroke="#F4F4F6" stroke-width="2.5"/></svg>`;
const dots = (w, h) => `background:#0B0B0D radial-gradient(circle, rgba(255,255,255,.16) 1.6px, transparent 2px) center/36px 36px;width:${w}px;height:${h}px`;

const FORMATS = {
  land: { w: 1920, h: 1080, phoneH: 800, capSize: 60, capTop: 52 },
  vert: { w: 1080, h: 1920, phoneH: 1080, capSize: 66, capTop: 150 },
};
const timeline = JSON.parse(fs.readFileSync(__dirname + '/out/timeline.json'));

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const shot = async (name, w, h, html, transparent = false) => {
    await page.setViewportSize({ width: w, height: h });
    await page.setContent(`<html><head><style>${fontCss}</style></head><body${transparent ? ' style="background:transparent"' : ''}>${html}</body></html>`);
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: `${OUT}/${name}.png`, omitBackground: transparent });
  };

  for (const [k, f] of Object.entries(FORMATS)) {
    const pw = Math.round(f.phoneH * 390 / 844);
    const gap = k === 'land' ? 120 : 40;
    const x1 = Math.round(f.w / 2 - gap / 2 - pw);
    const x2 = Math.round(f.w / 2 + gap / 2);
    const y = k === 'land' ? 200 : 420;
    fs.writeFileSync(`${OUT}/layout_${k}.json`, JSON.stringify({ pw, ph: f.phoneH, x1, x2, y }));

    await shot(`bg_${k}`, f.w, f.h, `<div style="${dots(f.w, f.h)}"></div>`);
    // phone rounded-corner mask (white = visible)
    await shot(`mask_${k}`, pw, f.phoneH, `<div style="width:${pw}px;height:${f.phoneH}px;background:#000"><div style="width:100%;height:100%;border-radius:${Math.round(pw * 0.09)}px;background:#fff"></div></div>`);
    // frames + labels overlay
    const label = (x, who, what, color) => `<div style="position:absolute;left:${x}px;top:${y + f.phoneH + 18}px;width:${pw}px;text-align:center;font:600 ${k === 'land' ? 22 : 28}px MeldSans">
      <span style="display:inline-block;width:12px;height:12px;border-radius:50%;background:${color};margin-right:10px"></span>${who} <span style="color:#8E8E98;font-weight:500">· ${what}</span></div>`;
    const ring = (x) => `<div style="position:absolute;left:${x - 3}px;top:${y - 3}px;width:${pw + 6}px;height:${f.phoneH + 6}px;border-radius:${Math.round(pw * 0.09) + 3}px;border:3px solid rgba(255,255,255,.14);box-shadow:0 30px 80px rgba(0,0,0,.6)"></div>`;
    const brand = k === 'land'
      ? `<div style="position:absolute;left:56px;bottom:44px;display:flex;align-items:center;gap:14px;font:600 30px MeldSans">${mark(44)}Meld</div>`
      : `<div style="position:absolute;left:0;right:0;bottom:90px;display:flex;justify-content:center;align-items:center;gap:16px;font:600 40px MeldSans">${mark(60)}Meld</div>`;
    await shot(`frame_${k}`, f.w, f.h, `<div style="position:relative;width:${f.w}px;height:${f.h}px">${ring(x1)}${ring(x2)}
      ${label(x1, 'Alice', 'creates the poll', '#2563EB')}${label(x2, 'Bob', 'votes from a link', '#0D9488')}${brand}</div>`, true);

    // captions
    for (const [i, s] of timeline.entries()) {
      if (s.caption === 'END') continue;
      await shot(`cap_${k}_${i}`, f.w, Math.round(f.capTop + f.capSize * (k === 'vert' ? 2.6 : 1.6)), `<div style="position:absolute;top:${f.capTop}px;left:60px;right:60px;text-align:center;
        font:400 ${f.capSize}px/1.1 MeldSerif;color:#F4F4F6;text-shadow:0 2px 24px rgba(0,0,0,.6)">${s.caption}</div>`, true);
    }

    // title card
    await shot(`title_${k}`, f.w, f.h, `<div style="${dots(f.w, f.h)};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:${k === 'land' ? 26 : 40}px">
      ${mark(k === 'land' ? 170 : 240)}
      <div style="font:400 ${k === 'land' ? 120 : 150}px/1 MeldSerif">Meld</div>
      <div style="font:500 ${k === 'land' ? 40 : 52}px/1.25 MeldSans;color:#C9C9D1;text-align:center">Decide together. <span style="color:#FF572E">Even offline.</span></div>
      <div style="font:700 ${k === 'land' ? 20 : 26}px MeldMono;letter-spacing:.2em;color:#8E8E98;margin-top:10px">POLLS · TIME SLOTS · NO SIGN-UP</div></div>`);

    // result card scene
    const cardH = k === 'land' ? 860 : 1300;
    await shot(`card_${k}`, f.w, f.h, `<div style="${dots(f.w, f.h)};display:flex;flex-direction:${k === 'land' ? 'row' : 'column'};align-items:center;justify-content:center;gap:${k === 'land' ? 80 : 50}px">
      <div style="max-width:${k === 'land' ? 620 : 900}px;text-align:${k === 'land' ? 'left' : 'center'}">
        <div style="font:700 22px MeldMono;letter-spacing:.2em;color:#14B8A6">DECIDED</div>
        <div style="font:400 ${k === 'land' ? 84 : 90}px/1.05 MeldSerif;margin-top:16px">One tap.<br/>A result worth sharing.</div>
        <div style="font:500 ${k === 'land' ? 30 : 36}px/1.4 MeldSans;color:#C9C9D1;margin-top:22px">Post it to the group chat, Stories or Slack.</div></div>
      <img src="data:image/png;base64,${fs.readFileSync(__dirname + '/out/result_card.png').toString('base64')}" style="height:${cardH}px;border-radius:28px;box-shadow:0 40px 120px rgba(0,0,0,.7);border:2px solid rgba(255,255,255,.12)"/></div>`);

    // end card
    const chips = ['Android app', 'Web app', 'Works offline', 'Ranked choice', 'Weekly polls', 'Slack', 'Discord', 'Webhooks', 'Calendar sync', '18 languages'];
    await shot(`end_${k}`, f.w, f.h, `<div style="${dots(f.w, f.h)};display:flex;flex-direction:column;align-items:center;justify-content:center;gap:34px;padding:0 70px">
      <div style="display:flex;align-items:center;gap:22px;font:400 ${k === 'land' ? 110 : 130}px/1 MeldSerif">${mark(k === 'land' ? 110 : 140)}Meld</div>
      <div style="font:500 ${k === 'land' ? 38 : 46}px/1.3 MeldSans;color:#C9C9D1;text-align:center">Group decisions without the group-chat chaos.</div>
      <div style="display:flex;flex-wrap:wrap;justify-content:center;gap:14px;max-width:${k === 'land' ? 1300 : 940}px">
        ${chips.map((c) => `<span style="font:600 ${k === 'land' ? 24 : 30}px MeldSans;padding:12px 22px;border-radius:999px;background:#141417;border:1.5px solid rgba(255,255,255,.12)">${c}</span>`).join('')}</div>
      <div style="font:700 ${k === 'land' ? 26 : 32}px MeldMono;letter-spacing:.14em;color:#FF572E;margin-top:12px">FREE · NO ACCOUNT NEEDED</div></div>`);
  }
  await browser.close();
  console.log('assets done');
})();
