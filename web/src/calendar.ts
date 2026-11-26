// Calendar links for decided time slots: one-tap add (Google / .ics for Apple & Outlook) and
// subscribable feeds (served by web/api/ics.ts) that update automatically.

const stamp = (ms: number) => new Date(ms).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
const esc = (s: string) => s.replace(/\\/g, '\\\\').replace(/;/g, '\;').replace(/,/g, '\\,').replace(/\n/g, '\\n');

export function googleCalendarUrl(title: string, start: number, end: number, details: string) {
  const p = new URLSearchParams({ action: 'TEMPLATE', text: title, dates: `${stamp(start)}/${stamp(end)}`, details });
  return `https://calendar.google.com/calendar/render?${p}`;
}

export function downloadIcs(code: string, title: string, start: number, end: number, details: string) {
  const ics = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//Meld//Decisions//EN', 'BEGIN:VEVENT', `UID:${code}@meld`,
    `DTSTAMP:${stamp(Date.now())}`, `DTSTART:${stamp(start)}`, `DTEND:${stamp(end)}`, `SUMMARY:${esc(title)}`,
    `DESCRIPTION:${esc(details)}`, 'END:VEVENT', 'END:VCALENDAR'].join('\r\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([ics], { type: 'text/calendar' }));
  a.download = `meld-${code}.ics`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
}

/** Feed URLs: subscribe once and decided slots (every week, for a series) appear by themselves. */
export function feedUrls(code: string, seriesId?: string | null) {
  const path = seriesId ? `/cal/series/${seriesId}.ics` : `/cal/${code}.ics`;
  const https = `${location.origin}${path}`;
  const webcal = https.replace(/^https?:/, 'webcal:');
  return {
    https,
    apple: webcal,
    google: `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(webcal)}`,
  };
}
