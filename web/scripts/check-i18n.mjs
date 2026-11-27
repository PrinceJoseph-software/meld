// Checks every web translation against en.json: same keys, same {placeholders}, valid plural forms.
import { readdirSync, readFileSync } from 'node:fs';

const dir = new URL('../src/i18n/locales/', import.meta.url);
const en = JSON.parse(readFileSync(new URL('en.json', dir)));
const FORMS = ['zero', 'one', 'two', 'few', 'many', 'other'];
const base = (k) => k.replace(/_(zero|one|two|few|many|other)$/, '');
const vars = (s) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',');
const enBases = new Set(Object.keys(en).map(base));
let problems = 0;

for (const file of readdirSync(dir).filter((f) => f.endsWith('.json') && f !== 'en.json')) {
  const code = file.replace('.json', '');
  const d = JSON.parse(readFileSync(new URL(file, dir)));
  // Plural forms that matter in practice. "many" in es/fr/it/pt only covers millions and
  // correctly falls back to "other"; Slavic languages and Arabic need their extra forms.
  const cats = new Intl.PluralRules(code).resolvedOptions().pluralCategories;
  const extra = { ru: ['few', 'many'], pl: ['few', 'many'], ar: ['zero', 'two', 'few', 'many'] }[code] ?? [];
  const need = new Set(cats.filter((c) => c === 'one' || extra.includes(c)));
  const issues = [];
  for (const k of Object.keys(en)) {
    const isPlural = /_(one|other)$/.test(k);
    if (isPlural) {
      if (k.endsWith('_other') && !d[k]) issues.push(`missing ${k}`);
      for (const f of need) if (!d[`${base(k)}_${f}`] && !(f === 'other')) issues.push(`missing plural ${base(k)}_${f}`);
      continue;
    }
    if (!(k in d)) issues.push(`missing ${k}`);
    else if (vars(d[k]) !== vars(en[k])) issues.push(`placeholders differ in ${k}: "${d[k]}"`);
  }
  for (const k of Object.keys(d)) {
    if (!enBases.has(base(k))) issues.push(`unknown key ${k}`);
    else if (k !== base(k) && !FORMS.includes(k.slice(base(k).length + 1))) issues.push(`bad plural ${k}`);
    else if (k !== base(k)) {
      const ref = en[`${base(k)}_other`];
      if (vars(d[k]).replace('count', '') !== vars(ref).replace('count', '')) issues.push(`placeholders differ in ${k}`);
    }
  }
  const unique = [...new Set(issues)];
  problems += unique.length;
  console.log(`${code}: ${unique.length ? unique.length + ' issue(s)\n  ' + unique.slice(0, 15).join('\n  ') : 'ok'}`);
}
process.exit(problems ? 1 : 0);
