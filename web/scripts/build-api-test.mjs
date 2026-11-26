// Bundles web/api/*.ts into .api-test/ so the Node test runner can import the handlers.
import { build } from 'esbuild';
import { readdirSync } from 'node:fs';
const entries = [
  ...readdirSync('api').filter((f) => f.endsWith('.ts')).map((f) => `api/${f}`),
  ...readdirSync('api/cron').filter((f) => f.endsWith('.ts')).map((f) => `api/cron/${f}`),
];
await build({
  entryPoints: entries,
  outdir: '.api-test',
  outbase: 'api',
  bundle: true,
  platform: 'node',
  format: 'esm',
  target: 'node20',
  packages: 'external',
  outExtension: { '.js': '.mjs' },
  logLevel: 'warning',
});
console.log(`bundled ${entries.length} API handlers`);
