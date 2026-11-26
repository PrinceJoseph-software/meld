// Records the Meld promo: two real browsers (Alice = creator, dark; Bob = voter, light)
// against the local Firebase emulators. Writes alice.webm, bob.webm and timeline.json.
const { chromium } = require('playwright');
const fs = require('fs');
const OUT = __dirname + '/out';
fs.mkdirSync(OUT, { recursive: true });
const DOMAIN = process.env.DEMO_DOMAIN || 'meld.local';
const BASE = `http://${DOMAIN}`;

// Visible tap ripples, so viewers can see where the "finger" goes.
const TAP_FX = () => {
  addEventListener('pointerdown', (e) => {
    const d = document.createElement('div');
    d.style.cssText = `position:fixed;left:${e.clientX - 22}px;top:${e.clientY - 22}px;width:44px;height:44px;border-radius:50%;
      background:rgba(255,87,46,.35);border:2px solid rgba(255,87,46,.9);pointer-events:none;z-index:99999;
      transform:scale(.4);opacity:1;transition:transform .45s ease-out,opacity .45s ease-out`;
    document.documentElement.appendChild(d);
    requestAnimationFrame(() => { d.style.transform = 'scale(1.4)'; d.style.opacity = '0'; });
    setTimeout(() => d.remove(), 600);
  }, true);
};

(async () => {
  const browser = await chromium.launch({
    args: [`--host-resolver-rules=MAP ${DOMAIN} 127.0.0.1`, `--unsafely-treat-insecure-origin-as-secure=${BASE}`],
  });
  const mk = async (scheme) => {
    const ctx = await browser.newContext({
      viewport: { width: 780, height: 1688 }, deviceScaleFactor: 1, hasTouch: false, colorScheme: scheme,
      recordVideo: { dir: OUT, size: { width: 780, height: 1688 } }, acceptDownloads: true,
    });
    await ctx.addInitScript(TAP_FX);
    // Render the phone layout (390 CSS px wide) at 2x so the recording is sharp.
    await ctx.addInitScript(() => {
      const apply = () => { document.documentElement.style.zoom = '2'; };
      if (document.documentElement) apply(); else addEventListener('DOMContentLoaded', apply);
    });
    // Recording-only shim: a plain-http demo hostname is not a "secure context".
    await ctx.addInitScript(() => {
      if (!crypto.randomUUID) {
        crypto.randomUUID = () => ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, (c) =>
          (c ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (c / 4)))).toString(16));
      }
    });
    return ctx;
  };
  const aCtx = await mk('dark');
  const bCtx = await mk('light');
  const a = await aCtx.newPage();
  const b = await bCtx.newPage();
  const t0 = Date.now();
  const timeline = [];
  const scene = (caption) => { timeline.push({ t: (Date.now() - t0) / 1000, caption }); console.log(((Date.now() - t0) / 1000).toFixed(1), caption); };
  const pause = (ms) => new Promise((r) => setTimeout(r, ms));
  const type = (loc, text) => loc.pressSequentially(text, { delay: 55 });

  // Both phones on the home screen
  scene('No account. No sign-up.');
  await Promise.all([a.goto(BASE + '/'), b.goto(BASE + '/')]);
  await pause(2200);

  // 1. Avatar Studio
  scene('Design your avatar in seconds');
  await a.getByRole('button', { name: 'Open Avatar Studio' }).click();
  await pause(500);
  await type(a.getByLabel('Display name'), 'Alice');
  await pause(300);
  for (const name of ['Orbital', 'Diamond', 'Visor']) { await a.getByRole('button', { name: new RegExp(name) }).click(); await pause(450); }
  await a.getByRole('tab', { name: 'Eyes' }).click(); await pause(300);
  await a.getByRole('button', { name: /Happy/ }).click(); await pause(400);
  await a.getByRole('tab', { name: 'Gear' }).click(); await pause(300);
  await a.getByRole('button', { name: /Crown/ }).click(); await pause(400);
  await a.getByRole('tab', { name: 'Palette' }).click(); await pause(300);
  await a.getByRole('button', { name: 'Cobalt palette' }).click(); await pause(700);
  await a.getByRole('button', { name: 'Done' }).click();
  // Bob quietly sets up too
  await b.getByRole('button', { name: 'Open Avatar Studio' }).click();
  await b.getByLabel('Display name').fill('Bob');
  await b.getByRole('button', { name: /Shield/ }).click();
  await b.getByRole('tab', { name: 'Palette' }).click();
  await b.getByRole('button', { name: 'Emerald palette' }).click();
  await b.getByRole('button', { name: 'Done' }).click();
  await pause(600);

  // 2. Create a ranked poll
  scene('Ask your group anything');
  await type(a.getByLabel('Question'), 'Team offsite city?');
  await type(a.getByLabel('Option 1', { exact: true }), 'Lisbon');
  await type(a.getByLabel('Option 2', { exact: true }), 'Oslo');
  await a.getByRole('button', { name: '+ Add option' }).click();
  await type(a.getByLabel('Option 3', { exact: true }), 'Rome');
  await a.mouse.wheel(0, 500); await pause(500);
  scene('Ranked choice, deadlines, weekly repeats');
  await a.getByText('Ranked choice').click(); await pause(500);
  await a.getByRole('button', { name: '1 hour' }).click(); await pause(600);
  await a.getByRole('button', { name: 'Create room' }).click();
  await a.waitForURL(/\/r\/[A-Z0-9]{6}$/);
  const url = a.url();
  await pause(900);

  // 3. Share
  scene('Share a link or QR — no app needed');
  await a.getByRole('button', { name: /Share/ }).first().click();
  await pause(2200);
  await a.getByRole('button', { name: /Share/ }).first().click();
  await pause(300);

  // 4. Live voting
  scene('Everyone votes — results update live');
  await b.goto(url);
  await b.getByRole('heading', { name: 'Team offsite city?' }).waitFor();
  await pause(900);
  await a.locator('button.option', { hasText: 'Lisbon' }).click(); await pause(900);
  await b.locator('button.option', { hasText: 'Rome' }).click(); await pause(600);
  await b.locator('button.option', { hasText: 'Oslo' }).click(); await pause(1600);
  await a.mouse.wheel(0, 260); await pause(1400);

  // 5. Offline
  scene('Offline? Keep voting. It syncs when you’re back.');
  await bCtx.setOffline(true);
  await pause(900);
  await b.locator('button.option', { hasText: 'Rome' }).click(); await pause(500);
  await b.locator('button.option', { hasText: 'Oslo' }).click(); await pause(500);
  await b.locator('button.option', { hasText: 'Lisbon' }).click(); await pause(1800);
  await bCtx.setOffline(false);
  await pause(2600);

  // 6. Decide
  scene('Crown the winner in one tap');
  await a.mouse.wheel(0, 700); await pause(600);
  await a.getByRole('button', { name: /Close & crown winner/ }).click();
  await pause(2600);
  await b.mouse.wheel(0, 900); await pause(1200);

  // 7. Result card
  scene('Share a beautiful result card');
  const [dl] = await Promise.all([a.waitForEvent('download'), a.getByRole('button', { name: 'Share result card' }).click()]);
  await dl.saveAs(`${OUT}/result_card.png`);
  await pause(1500);
  scene('END');

  const aVid = await a.video().path();
  const bVid = await b.video().path();
  await aCtx.close(); await bCtx.close(); await browser.close();
  fs.renameSync(aVid, `${OUT}/alice.webm`);
  fs.renameSync(bVid, `${OUT}/bob.webm`);
  fs.writeFileSync(`${OUT}/timeline.json`, JSON.stringify(timeline, null, 1));
  console.log('done');
})().catch((e) => { console.error(e); process.exit(1); });
