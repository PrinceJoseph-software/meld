// Local stand-in for Vercel: serves web/dist (SPA fallback, vercel.json-style rewrites) and the
// API handlers bundled into .api-test/ (npm run build:api). For local testing with emulators:
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 node scripts/local-server.mjs
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join } from 'node:path';

const PORT = Number(process.env.PORT || 4173);
const DIST = new URL('../dist/', import.meta.url).pathname;
const API = new URL('../.api-test/', import.meta.url).pathname;
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.ttf': 'font/ttf', '.json': 'application/json', '.webmanifest': 'application/manifest+json', '.svg': 'image/svg+xml' };

createServer(async (req, res) => {
  let url = new URL(req.url, `http://localhost:${PORT}`);
  const cal = url.pathname.match(/^\/cal\/(series\/)?([A-Za-z0-9]+)\.ics$/);
  if (cal) url = new URL(`/api/ics?${cal[1] ? 'series' : 'code'}=${cal[2]}`, url);
  if (url.pathname.startsWith('/api/')) {
    try {
      const mod = await import(join(API, `${url.pathname.slice(5)}.mjs`));
      const handler = mod[req.method];
      if (!handler) { res.writeHead(405).end(); return; }
      const chunks = [];
      for await (const c of req) chunks.push(c);
      const body = chunks.length ? Buffer.concat(chunks) : undefined;
      const request = new Request(url, { method: req.method, headers: req.headers, body: req.method === 'GET' ? undefined : body });
      const response = await handler(request);
      res.writeHead(response.status, Object.fromEntries(response.headers));
      res.end(Buffer.from(await response.arrayBuffer()));
    } catch (e) {
      console.error(e);
      res.writeHead(500).end(String(e));
    }
    return;
  }
  let file = join(DIST, decodeURIComponent(url.pathname));
  try {
    if ((await stat(file)).isDirectory()) file = join(file, 'index.html');
  } catch {
    file = join(DIST, 'index.html');
  }
  const headers = { 'content-type': types[extname(file)] || 'application/octet-stream' };
  if (!url.pathname.startsWith('/embed/')) headers['x-frame-options'] = 'DENY';
  res.writeHead(200, headers).end(await readFile(file));
}).listen(PORT, () => console.log(`Meld local server on http://localhost:${PORT}`));
