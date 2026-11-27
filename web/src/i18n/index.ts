// Tiny dependency-free i18n for the web app (the Android app uses strings.xml in the same 18
// languages). English is bundled; other languages load on demand and are precached for offline.
import { createElement, Fragment, useSyncExternalStore, type ReactNode } from 'react';
import en from './locales/en.json';

export type Key = keyof typeof en | `${string}_${Intl.LDMLPluralRule}`;
type Dict = Record<string, string>;

export const LANGS: { code: string; name: string; rtl?: boolean }[] = [
  { code: 'en', name: 'English' },
  { code: 'ar', name: 'العربية', rtl: true },
  { code: 'de', name: 'Deutsch' },
  { code: 'es', name: 'Español' },
  { code: 'fr', name: 'Français' },
  { code: 'hi', name: 'हिन्दी' },
  { code: 'id', name: 'Bahasa Indonesia' },
  { code: 'it', name: 'Italiano' },
  { code: 'ja', name: '日本語' },
  { code: 'ko', name: '한국어' },
  { code: 'nl', name: 'Nederlands' },
  { code: 'pl', name: 'Polski' },
  { code: 'pt', name: 'Português' },
  { code: 'ru', name: 'Русский' },
  { code: 'sv', name: 'Svenska' },
  { code: 'tr', name: 'Türkçe' },
  { code: 'vi', name: 'Tiếng Việt' },
  { code: 'zh', name: '中文' },
];

const STORAGE_KEY = 'meld.lang';
const loaders = import.meta.glob<{ default: Dict }>(['./locales/*.json', '!./locales/en.json']);

let lang = 'en';
let dict: Dict = en;
let plural = new Intl.PluralRules('en');
const listeners = new Set<() => void>();

const supported = (code: string | null | undefined) =>
  code && LANGS.some((l) => l.code === code) ? code : null;

/** Saved choice, else the first browser language we support (pt-BR → pt, zh-Hant → zh), else English. */
export function detectLang(): string {
  try {
    const saved = supported(localStorage.getItem(STORAGE_KEY));
    if (saved) return saved;
  } catch { /* storage blocked */ }
  const prefs = typeof navigator !== 'undefined' ? navigator.languages ?? [navigator.language] : [];
  for (const p of prefs) {
    const base = supported(p?.toLowerCase().split('-')[0]);
    if (base) return base;
  }
  return 'en';
}

function apply(code: string, d: Dict) {
  lang = code;
  dict = d;
  plural = new Intl.PluralRules(code);
  if (typeof document !== 'undefined') {
    document.documentElement.lang = code;
    document.documentElement.dir = LANGS.find((l) => l.code === code)?.rtl ? 'rtl' : 'ltr';
  }
  listeners.forEach((l) => l());
}

async function load(code: string): Promise<Dict> {
  if (code === 'en') return en;
  const loader = loaders[`./locales/${code}.json`];
  return loader ? (await loader()).default : en;
}

/** Call once before the first render. Never throws: falls back to English. */
export async function initI18n(): Promise<void> {
  const code = detectLang();
  try {
    apply(code, await load(code));
  } catch {
    apply('en', en);
  }
}

export async function setLang(code: string): Promise<void> {
  const next = supported(code) ?? 'en';
  try { localStorage.setItem(STORAGE_KEY, next); } catch { /* storage blocked */ }
  apply(next, await load(next));
}

export const getLang = () => lang;

/** Re-renders the caller when the language changes. */
export function useLang(): string {
  return useSyncExternalStore(
    (cb) => { listeners.add(cb); return () => listeners.delete(cb); },
    () => lang,
    () => lang,
  );
}

type Vars = Record<string, string | number>;

function lookup(key: string, vars?: Vars): string {
  if (vars && typeof vars.count === 'number') {
    const form = `${key}_${plural.select(vars.count)}`;
    return dict[form] ?? dict[`${key}_other`] ?? (en as Dict)[form] ?? (en as Dict)[`${key}_other`] ?? key;
  }
  return dict[key] ?? (en as Dict)[key] ?? key;
}

/** Translate `key`, filling `{name}` placeholders. Pass `count` to pick the plural form. */
export function t(key: string, vars?: Vars): string {
  const s = lookup(key, vars);
  return vars ? s.replace(/\{(\w+)\}/g, (m, k) => (k in vars ? String(vars[k]) : m)) : s;
}

/** Like t(), but placeholders can be React elements (e.g. bold names, links). */
export function tr(key: string, vars: Record<string, ReactNode>): ReactNode {
  const parts = lookup(key, vars as Vars).split(/\{(\w+)\}/g);
  return createElement(
    Fragment,
    null,
    ...parts.map((p, i) => createElement(Fragment, { key: i }, i % 2 ? (vars[p] ?? `{${p}}`) : p)),
  );
}

/** "A, B and C" in the current language. */
export function list(items: string[]): string {
  try {
    return new Intl.ListFormat(lang, { type: 'conjunction' }).format(items);
  } catch {
    return items.join(', ');
  }
}
