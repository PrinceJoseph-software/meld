package io.github.princejoseph.meld

import io.github.princejoseph.meld.data.ConflictDetector
import io.github.princejoseph.meld.data.ConflictSeverity
import io.github.princejoseph.meld.data.ConsensusInsightCalculator
import io.github.princejoseph.meld.data.FreeSlotCalculator
import io.github.princejoseph.meld.data.IcsCalendarParser
import io.github.princejoseph.meld.data.InsightStrength
import io.github.princejoseph.meld.data.OptionUiModel
import io.github.princejoseph.meld.data.ParticipantCommitment
import io.github.princejoseph.meld.data.RoomMode
import io.github.princejoseph.meld.data.VoterChip
import io.github.princejoseph.meld.data.avatarStyleIndexForUid
import io.github.princejoseph.meld.data.badgeColorIndexForUid
import io.github.princejoseph.meld.data.initialsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
    @Test
    fun `initials formatting produces crisp 2-letter monograms`() {
        assertEquals("GU", initialsFor(""))
        assertEquals("AL", initialsFor("Alex"))
        assertEquals("AR", initialsFor("Alex Rivera"))
        assertEquals("JC", initialsFor("  jules   chen "))
    }

    @Test
    fun `badge color and avatar style index are deterministic and in range`() {
        val idx1 = badgeColorIndexForUid("user-123", 6)
        val idx2 = badgeColorIndexForUid("user-123", 6)
        assertEquals(idx1, idx2)
        assertTrue(idx1 in 0 until 6)

        val style1 = avatarStyleIndexForUid("user-123", 6)
        val style2 = avatarStyleIndexForUid("user-123", 6)
        assertEquals(style1, style2)
        assertTrue(style1 in 0 until 6)
    }

    @Test
    fun `ics parser extracts valid events and ignores cancelled ones`() {
        val sampleIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            DTSTART:20261126T170000
            DTEND:20261126T183000
            SUMMARY:Design Review
            END:VEVENT
            BEGIN:VEVENT
            DTSTART:20261126T190000
            DTEND:20261126T200000
            STATUS:CANCELLED
            SUMMARY:Cancelled Dinner
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val busy = IcsCalendarParser.parse(sampleIcs)
        assertEquals(1, busy.size)
        assertEquals("Design Review", busy.first().summary)

        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val slots = FreeSlotCalculator.suggestFreeBlocks(
            busyIntervals = busy,
            nowMs = baseCal.timeInMillis,
            daysAhead = 2,
            maxSuggestions = 4
        )
        assertTrue(slots.isNotEmpty())
        val conflicts = slots.any { s ->
            s.startMs < busy.first().endMs && s.endMs > busy.first().startMs
        }
        assertEquals(false, conflicts)
    }

    @Test
    fun `consensus insight calculator detects unanimous, tied, and overlapping time windows`() {
        val v1 = VoterChip("u1", "Alex", "AL", 0, 0)
        val v2 = VoterChip("u2", "Samira", "SA", 1, 1)
        val v3 = VoterChip("u3", "Jules", "JU", 2, 2)

        val opt1 = OptionUiModel("o1", "Thu, 7:00 PM", 0, 3, 75, 0.75f, true, false, true, false, true, false, listOf(v1, v2, v3))
        val opt2 = OptionUiModel("o2", "Fri, 12:30 PM", 1, 1, 25, 0.25f, false, false, false, false, false, false, listOf(v1))

        val unanimous = ConsensusInsightCalculator.calculate(
            options = listOf(opt1, opt2),
            mode = RoomMode.TIME_SLOTS,
            totalUniqueVoters = 3,
            isLocked = false,
            winnerOptionId = null
        )
        assertEquals(InsightStrength.UNANIMOUS, unanimous?.strength)
        assertEquals("Thu, 7:00 PM", unanimous?.suggestedLabel)
        assertEquals(100, unanimous?.overlapPercentage)

        // Overlapping windows: Thu 5:00 PM (v1) + Thu 5:30 PM (v2, v3) -> overlap 5:30 PM - 6:00 PM covers all 3 voters
        val overlapA = OptionUiModel("oa", "Thu, 5:00 PM", 0, 1, 33, 0.33f, true, false, false, false, false, false, listOf(v1))
        val overlapB = OptionUiModel("ob", "Thu, 5:30 PM", 1, 2, 67, 0.67f, false, false, true, false, false, false, listOf(v2, v3))
        val overlapInsight = ConsensusInsightCalculator.calculate(
            options = listOf(overlapA, overlapB),
            mode = RoomMode.TIME_SLOTS,
            totalUniqueVoters = 3,
            isLocked = false,
            winnerOptionId = null
        )
        assertEquals(true, overlapInsight?.isOverlapWindow)
        assertEquals(3, overlapInsight?.overlapVoterCount)
        assertEquals(InsightStrength.UNANIMOUS, overlapInsight?.strength)
        assertTrue(overlapInsight?.suggestedLabel?.contains("5:30 PM") == true)

        // Tied discrete slots
        val tieA = OptionUiModel("ta", "Sat, 4:00 PM", 0, 1, 50, 0.5f, false, false, false, true, false, false, listOf(v1))
        val tieB = OptionUiModel("tb", "Sun, 2:00 PM", 1, 1, 50, 0.5f, false, false, false, true, false, false, listOf(v2))
        val tiedInsight = ConsensusInsightCalculator.calculate(
            options = listOf(tieA, tieB),
            mode = RoomMode.TIME_SLOTS,
            totalUniqueVoters = 2,
            isLocked = false,
            winnerOptionId = null
        )
        assertEquals(InsightStrength.TIED, tiedInsight?.strength)
        assertEquals("Sun, 2:00 PM", tiedInsight?.runnerUpLabel)
    }

    @Test
    fun `conflict detector flags overlapping commitments when two or more high priority participants conflict`() {
        val refNow = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val slotWindow = ConsensusInsightCalculator.parseSlotWindowMs("Thu, Nov 26 · 7:00 PM", refNow)!!
        val v1 = VoterChip("u1", "Alex Rivera", "AR", 0, 0, isHighPriority = true)
        val v2 = VoterChip("u2", "Samira K.", "SK", 1, 1, isHighPriority = true)

        val opt1 = OptionUiModel("o1", "Thu, Nov 26 · 7:00 PM", 0, 2, 100, 1f, true, false, true, false, true, false, listOf(v1, v2))
        val opt2 = OptionUiModel("o2", "Fri, Nov 27 · 12:30 PM", 1, 0, 0, 0f, false, false, false, false, false, false, emptyList())

        val commitments = mapOf(
            "u1" to listOf(ParticipantCommitment("u1", "Alex Rivera", slotWindow.first, slotWindow.second, "Design Review")),
            "u2" to listOf(ParticipantCommitment("u2", "Samira K.", slotWindow.first + 10L * 60_000L, slotWindow.second, "Client Sync"))
        )

        val result = ConflictDetector.analyze(
            options = listOf(opt1, opt2),
            activeVoters = listOf(v1, v2),
            mode = RoomMode.TIME_SLOTS,
            highPriorityUids = setOf("u1", "u2"),
            commitmentsByUid = commitments,
            isLocked = false,
            referenceNowMs = refNow
        )

        assertEquals(ConflictSeverity.WARNING, result.alert?.severity)
        assertEquals("Thu, Nov 26 · 7:00 PM", result.alert?.conflictedOptionLabel)
        assertEquals(2, result.alert?.highPriorityParticipantNames?.size)
        assertEquals("Fri, Nov 27 · 12:30 PM", result.alert?.suggestedAlternativeLabel)
        assertTrue(result.conflictedOptionHints.containsKey("o1"))

        // If fewer than 2 high-priority participants, no alert should trigger
        val singlePriorityResult = ConflictDetector.analyze(
            options = listOf(opt1, opt2),
            activeVoters = listOf(v1, v2),
            mode = RoomMode.TIME_SLOTS,
            highPriorityUids = setOf("u1"),
            commitmentsByUid = commitments,
            isLocked = false,
            referenceNowMs = refNow
        )
        assertEquals(null, singlePriorityResult.alert)
    }

    @Test
    fun `resolveCalendarEventWindowMs parses explicit dates and advances past weekdays to next occurrence`() {
        val refThuMorning = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val explicitWindow = ConsensusInsightCalculator.resolveCalendarEventWindowMs(
            label = "Thu, Nov 26 · 7:00 PM",
            referenceNowMs = refThuMorning
        )!!
        assertEquals(60L * 60_000L, explicitWindow.second - explicitWindow.first)
        val explicitCal = Calendar.getInstance().apply { timeInMillis = explicitWindow.first }
        assertEquals(Calendar.NOVEMBER, explicitCal.get(Calendar.MONTH))
        assertEquals(26, explicitCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, explicitCal.get(Calendar.HOUR_OF_DAY))

        // Tuesday relative to Thursday Nov 26 -> shifts forward to upcoming Tuesday Dec 1
        val weekdayOnlyWindow = ConsensusInsightCalculator.resolveCalendarEventWindowMs(
            label = "Tue, 7:00 PM",
            referenceNowMs = refThuMorning
        )!!
        assertTrue(weekdayOnlyWindow.first > refThuMorning)
        val weekdayCal = Calendar.getInstance().apply { timeInMillis = weekdayOnlyWindow.first }
        assertEquals(Calendar.TUESDAY, weekdayCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(19, weekdayCal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `avatar spec packs and unpacks all five customization layers and supports legacy indices`() {
        val spec = io.github.princejoseph.meld.data.AvatarSpec(
            chassis = 9,
            eyes = 5,
            accessory = 6,
            pattern = 4,
            frame = 3
        )
        val packed = spec.toPacked()
        assertEquals(spec, io.github.princejoseph.meld.data.AvatarSpec.fromPacked(packed))

        // Legacy 0..5 indices remain valid without a database migration
        val legacy2 = io.github.princejoseph.meld.data.AvatarSpec.fromPacked(2)
        assertEquals(2, legacy2.chassis)
        assertEquals(1, legacy2.pattern)
        assertEquals(0, legacy2.accessory)
    }

    @Test
    fun `input and room code sanitizers strip control characters and reject invalid deep link codes`() {
        assertEquals("Lunch Spot", io.github.princejoseph.meld.data.sanitizeMeldInput("  Lunch\u0000\n  Spot  ", 28))
        assertEquals("ABC12", io.github.princejoseph.meld.data.sanitizeMeldInput("ABC12345", 5))

        assertEquals("MELD42", io.github.princejoseph.meld.data.sanitizeRoomCode("#meld42"))
        assertEquals("K9X2", io.github.princejoseph.meld.data.sanitizeRoomCode("k9x2"))
        assertEquals(null, io.github.princejoseph.meld.data.sanitizeRoomCode("../../etc/passwd"))
        assertEquals(null, io.github.princejoseph.meld.data.sanitizeRoomCode("<script>alert(1)</script>"))
        assertEquals(null, io.github.princejoseph.meld.data.sanitizeRoomCode("ab"))
    }

    @Test
    fun `anonymous spam guard hashes deterministically, masks IPs, and enforces duplicate and rate limits`() {
        val guard = io.github.princejoseph.meld.data.AnonymousSpamGuard
        assertEquals("192.168.x.42", guard.maskIpForDisplay("192.168.1.42"))
        assertEquals("Local Device", guard.maskIpForDisplay("offline-local"))

        val h1 = guard.computeNetworkHash("room99", "192.168.1.42", "salt-a")
        val h2 = guard.computeNetworkHash("ROOM99", "192.168.1.42", "salt-a")
        assertEquals(h1, h2)
        assertEquals(24, h1.length)

        val now = 1_000_000L
        assertEquals(
            io.github.princejoseph.meld.data.AnonymousVoteGate.Allowed,
            guard.evaluateAnonymousVote(null, "uid-1", now)
        )

        var lock = guard.nextLockRecord("ROOM99", h1, "192.168.x.42", "uid-1", null, now)
        val duplicate = guard.evaluateAnonymousVote(lock, "uid-2", now + 1_000L)
        assertTrue(duplicate is io.github.princejoseph.meld.data.AnonymousVoteGate.BlockedDuplicateNetwork)

        // Rapid actions trigger rate-limit cooldown
        repeat(5) {
            lock = guard.nextLockRecord("ROOM99", h1, "192.168.x.42", "uid-1", lock, now + 1_000L)
        }
        val rateLimited = guard.evaluateAnonymousVote(lock, "uid-1", now + 2_000L)
        assertTrue(rateLimited is io.github.princejoseph.meld.data.AnonymousVoteGate.BlockedRateLimit)
    }
}

