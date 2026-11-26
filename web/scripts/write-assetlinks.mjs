// Generates public/.well-known/assetlinks.json so https://<this site>/r/CODE links open the
// Android app when it is installed (Android App Links verification).
import { mkdirSync, writeFileSync } from 'node:fs';

const fingerprints = (process.env.ANDROID_CERT_SHA256 || '')
  .split(',')
  .map((s) => s.trim().toUpperCase())
  .filter(Boolean);

const statements = fingerprints.length
  ? [
      {
        relation: ['delegate_permission/common.handle_all_urls'],
        target: {
          namespace: 'android_app',
          package_name: 'io.github.princejoseph.meld',
          sha256_cert_fingerprints: fingerprints,
        },
      },
    ]
  : [];

mkdirSync('public/.well-known', { recursive: true });
writeFileSync('public/.well-known/assetlinks.json', JSON.stringify(statements, null, 2) + '\n');
console.log(`assetlinks.json: ${fingerprints.length} fingerprint(s)`);
