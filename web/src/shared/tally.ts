// Vote tallying shared by the web app and the server (web/api). The Android app has an
// identical Kotlin port (data/RankedChoice.kt); tests on both sides use the same cases.

export interface TallyOption { id: string; sortOrder: number }

export interface IrvRound {
  /** First-choice counts among options still in the race. */
  counts: Record<string, number>;
  /** Ballots that still rank at least one remaining option. */
  active: number;
  eliminated: string | null;
}

export interface IrvResult {
  winnerId: string | null;
  rounds: IrvRound[];
  /** True when the final two were exactly level and the tie-break decided it. */
  tie: boolean;
}

/** Borda score: higher ranks earn more points. Used only to break ties deterministically. */
function borda(ballots: string[][], n: number): Map<string, number> {
  const score = new Map<string, number>();
  for (const b of ballots) b.forEach((id, rank) => score.set(id, (score.get(id) ?? 0) + (n - rank)));
  return score;
}

/**
 * Instant-runoff voting. Each round counts every ballot for its highest-ranked option still
 * in the race; an option with a majority of active ballots wins, otherwise the weakest is
 * eliminated and its ballots transfer. Ties: lower Borda score is eliminated first, then the
 * option listed later.
 */
export function instantRunoff(options: TallyOption[], ballots: string[][]): IrvResult {
  const ids = new Set(options.map((o) => o.id));
  const clean = ballots.map((b) => [...new Set(b)].filter((id) => ids.has(id))).filter((b) => b.length > 0);
  const order = new Map(options.map((o) => [o.id, o.sortOrder]));
  const bordaScore = borda(clean, options.length);
  const remaining = new Set(options.map((o) => o.id));
  const rounds: IrvRound[] = [];
  if (clean.length === 0) return { winnerId: null, rounds, tie: false };

  // weaker = should be eliminated first
  const weaker = (a: string, b: string) =>
    (bordaScore.get(a) ?? 0) - (bordaScore.get(b) ?? 0) || (order.get(b) ?? 0) - (order.get(a) ?? 0);

  while (remaining.size > 0) {
    const counts: Record<string, number> = {};
    remaining.forEach((id) => (counts[id] = 0));
    let active = 0;
    for (const b of clean) {
      const top = b.find((id) => remaining.has(id));
      if (top) { counts[top] += 1; active += 1; }
    }
    const sorted = [...remaining].sort((a, b) => counts[b] - counts[a] || -weaker(a, b));
    const leader = sorted[0];
    if (active === 0) {
      rounds.push({ counts, active, eliminated: null });
      return { winnerId: null, rounds, tie: false };
    }
    if (counts[leader] * 2 > active || remaining.size === 1) {
      rounds.push({ counts, active, eliminated: null });
      return { winnerId: leader, rounds, tie: false };
    }
    if (remaining.size === 2 && counts[sorted[0]] === counts[sorted[1]]) {
      rounds.push({ counts, active, eliminated: null });
      return { winnerId: leader, rounds, tie: true };
    }
    const min = Math.min(...[...remaining].map((id) => counts[id]));
    const loser = [...remaining].filter((id) => counts[id] === min).sort(weaker)[0];
    rounds.push({ counts, active, eliminated: loser });
    remaining.delete(loser);
  }
  return { winnerId: null, rounds, tie: false };
}

export interface Results {
  /** Per-option count shown in the UI (final-round counts for ranked rooms). */
  counts: Record<string, number>;
  total: number;
  leaderId: string | null;
  tiedLeaders: string[];
  irv: IrvResult | null;
}

export function computeResults(isRanked: boolean, options: TallyOption[], ballots: string[][]): Results {
  if (isRanked) {
    const irv = instantRunoff(options, ballots);
    const last = irv.rounds[irv.rounds.length - 1];
    const counts: Record<string, number> = {};
    options.forEach((o) => (counts[o.id] = last?.counts[o.id] ?? 0));
    return { counts, total: last?.active ?? 0, leaderId: irv.winnerId, tiedLeaders: irv.tie && irv.winnerId ? Object.keys(last.counts) : [], irv };
  }
  const counts: Record<string, number> = {};
  options.forEach((o) => (counts[o.id] = 0));
  ballots.forEach((b) => b.forEach((id) => { if (id in counts) counts[id] += 1; }));
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  const max = Math.max(0, ...Object.values(counts));
  const tied = max > 0 ? options.filter((o) => counts[o.id] === max).sort((a, b) => a.sortOrder - b.sortOrder) : [];
  return { counts, total, leaderId: tied[0]?.id ?? null, tiedLeaders: tied.length > 1 ? tied.map((o) => o.id) : [], irv: null };
}
