/** Room codes: 4–10 letters/digits, case-insensitive, optional leading '#'. Dependency-free. */
export function sanitizeCode(raw: string): string | null {
  const c = raw.trim().replace(/^#/, '').toUpperCase();
  return /^[A-Z0-9]{4,10}$/.test(c) ? c : null;
}
