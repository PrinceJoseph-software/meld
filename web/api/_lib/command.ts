// Parses the chat command used by both bots:
//   /meld Where should we eat? | Tacos | Ramen | Pizza --ranked --deadline 2h
// Options are separated by "|" (or new lines). Flags:
//   --ranked  ranked-choice      --multi   pick several      --anon  anonymous
//   --weekly  repeat every week  --deadline 30m|2h|1d (default 24h)  --open (no deadline)

export interface ParsedCommand {
  question: string;
  options: string[];
  isRanked: boolean;
  allowMultiple: boolean;
  isAnonymous: boolean;
  repeatWeekly: boolean;
  deadlineMinutes: number | null;
}

export const USAGE =
  'Usage: `/meld Question? | Option 1 | Option 2 [| more…] [--ranked] [--multi] [--anon] [--weekly] [--deadline 2h|30m|1d] [--open]`';

function parseDuration(v: string | undefined): number | null {
  const m = v?.trim().match(/^(\d{1,4})\s*(m|min|h|hr|d|day)s?$/i);
  if (!m) return null;
  const n = Number(m[1]);
  const unit = m[2].toLowerCase()[0];
  const minutes = unit === 'm' ? n : unit === 'h' ? n * 60 : n * 1440;
  return Math.min(Math.max(minutes, 1), 60 * 24 * 30);
}

export function parseCommand(text: string): ParsedCommand | { error: string } {
  let rest = ` ${text} `;
  const flag = (name: string) => {
    const re = new RegExp(`\\s--${name}(?=\\s)`, 'i');
    const hit = re.test(rest);
    rest = rest.replace(re, ' ');
    return hit;
  };
  let deadlineMinutes: number | null = 24 * 60;
  const d = rest.match(/\s--deadline\s+(\S+)/i);
  if (d) {
    const parsed = parseDuration(d[1]);
    if (parsed === null) return { error: `Couldn't read deadline "${d[1]}". Try 30m, 2h or 1d.` };
    deadlineMinutes = parsed;
    rest = rest.replace(d[0], ' ');
  }
  const isRanked = flag('ranked');
  const allowMultiple = flag('multi');
  const isAnonymous = flag('anon');
  const repeatWeekly = flag('weekly');
  if (flag('open')) deadlineMinutes = null;

  const parts = rest.split(/\||\n/).map((s) => s.trim()).filter(Boolean);
  if (parts.length < 3) return { error: `Give a question and at least two options.\n${USAGE}` };
  const [question, ...options] = parts;
  if (options.length > 16) return { error: 'Up to 16 options, please.' };
  return {
    question,
    options: [...new Set(options)],
    isRanked,
    allowMultiple: allowMultiple && !isRanked,
    isAnonymous,
    repeatWeekly,
    deadlineMinutes,
  };
}
