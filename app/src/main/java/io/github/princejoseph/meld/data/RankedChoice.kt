package io.github.princejoseph.meld.data

/**
 * Instant-runoff (ranked-choice) tally. Kotlin port of web/src/shared/tally.ts: both
 * platforms must agree on the winner, so the algorithm and tie-breaks are identical.
 *
 * Each round counts every ballot for its highest-ranked option still in the race. An option
 * with a majority of active ballots wins; otherwise the weakest is eliminated and its ballots
 * transfer. Ties: lower Borda score is eliminated first, then the option listed later.
 */
object RankedChoice {
    data class Round(val counts: Map<String, Int>, val active: Int, val eliminated: String?)
    data class Result(val winnerId: String?, val rounds: List<Round>, val tie: Boolean) {
        val finalCounts: Map<String, Int> get() = rounds.lastOrNull()?.counts.orEmpty()
        val finalActive: Int get() = rounds.lastOrNull()?.active ?: 0
    }

    /** [options] = (id, sortOrder); each ballot lists option ids best-first. */
    fun instantRunoff(options: List<Pair<String, Int>>, ballots: List<List<String>>): Result {
        val ids = options.map { it.first }.toSet()
        val clean = ballots.map { b -> b.distinct().filter { it in ids } }.filter { it.isNotEmpty() }
        val order = options.toMap()
        val n = options.size
        val borda = mutableMapOf<String, Int>()
        clean.forEach { b -> b.forEachIndexed { rank, id -> borda[id] = (borda[id] ?: 0) + (n - rank) } }
        val remaining = options.map { it.first }.toMutableSet()
        val rounds = mutableListOf<Round>()
        if (clean.isEmpty()) return Result(null, rounds, false)

        // negative => a is weaker (eliminated first)
        val weaker = Comparator<String> { a, b ->
            val byBorda = (borda[a] ?: 0) - (borda[b] ?: 0)
            if (byBorda != 0) byBorda else (order[b] ?: 0) - (order[a] ?: 0)
        }

        while (remaining.isNotEmpty()) {
            val counts = remaining.associateWith { 0 }.toMutableMap()
            var active = 0
            for (b in clean) {
                val top = b.firstOrNull { it in remaining } ?: continue
                counts[top] = counts.getValue(top) + 1
                active += 1
            }
            val sorted = remaining.sortedWith(
                compareByDescending<String> { counts.getValue(it) }.thenComparator { a, b -> -weaker.compare(a, b) }
            )
            val leader = sorted.first()
            if (active == 0) {
                rounds += Round(counts, active, null)
                return Result(null, rounds, false)
            }
            if (counts.getValue(leader) * 2 > active || remaining.size == 1) {
                rounds += Round(counts, active, null)
                return Result(leader, rounds, false)
            }
            if (remaining.size == 2 && counts.getValue(sorted[0]) == counts.getValue(sorted[1])) {
                rounds += Round(counts, active, null)
                return Result(leader, rounds, true)
            }
            val min = remaining.minOf { counts.getValue(it) }
            val loser = remaining.filter { counts.getValue(it) == min }.sortedWith(weaker).first()
            rounds += Round(counts, active, loser)
            remaining -= loser
        }
        return Result(null, rounds, false)
    }
}
