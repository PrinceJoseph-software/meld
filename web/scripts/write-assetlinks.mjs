// Generates public/.well-known/assetlinks.json so https://<this site>/r/CODE links open the
// Android app when it is installed (Android App Links verification).
import { mkdirSync, writeFileSync } from 'node:fs';

// Accepts fingerprints however they were pasted: with or without a "SHA256:" label, colons,
// spaces or line breaks. Several can be given, separated by commas or semicolons.
// Each must be 32 bytes (64 hex digits); anything else is rejected loudly.
export function normalizeFingerprints(raw) {
  const out = [];
  for (const part of String(raw || '').split(/[,;]/)) {
    const hex = part.replace(/^\s*sha-?256\s*:?/i, '').replace(/[^0-9a-f]/gi, '').toUpperCase();
    if (!hex) continue;
    if (hex.length !== 64) {
      console.warn(`assetlinks.json: ignoring an invalid ANDROID_CERT_SHA256 entry (${hex.length} hex digits, expected 64)`);
      continue;
    }
    const pretty = hex.match(/../g).join(':');
    if (!out.includes(pretty)) out.push(pretty);
  }
  return out;
}

const fingerprints = normalizeFingerprints(process.env.ANDROID_CERT_SHA256);

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
