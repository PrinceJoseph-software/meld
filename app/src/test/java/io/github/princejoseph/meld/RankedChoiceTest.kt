package io.github.princejoseph.meld

import io.github.princejoseph.meld.data.RankedChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as web/test/tally.test.ts so both platforms provably agree. */
class RankedChoiceTest {
    private val opts = listOf("A", "B", "C", "D").mapIndexed { i, id -> id to i }

    @Test fun `majority in round one wins immediately`() {
        val r = RankedChoice.instantRunoff(opts, listOf(listOf("A"), listOf("A"), listOf("B")))
        assertEquals("A", r.winnerId)
        assertEquals(1, r.rounds.size)
    }

    @Test fun `eliminated choices transfer to next preferences`() {
        val r = RankedChoice.instantRunoff(
            opts, listOf(listOf("A"), listOf("A"), listOf("B"), listOf("B"), listOf("C", "B"))
        )
        assertEquals("B", r.winnerId)
        assertEquals(3, r.finalCounts["B"])
    }

    @Test fun `exhausted ballots drop out of the majority threshold`() {
        val r = RankedChoice.instantRunoff(opts, listOf(listOf("A"), listOf("A"), listOf("B"), listOf("C"), listOf("D")))
        assertEquals("A", r.winnerId)
    }

    @Test fun `a dead heat is resolved deterministically and flagged`() {
        val r = RankedChoice.instantRunoff(opts, listOf(listOf("A", "B"), listOf("B", "A")))
        assertTrue(r.tie)
        assertEquals("A", r.winnerId)
    }

    @Test fun `ignores unknown and duplicate ids`() {
        val r = RankedChoice.instantRunoff(opts, listOf(listOf("Z", "A", "A"), listOf("A")))
        assertEquals("A", r.winnerId)
    }
}
