package io.github.princejoseph.meld.data

import kotlin.math.abs

enum class RoomMode {
    QUICK_POLL,
    TIME_SLOTS
}

enum class SyncStatus {
    LIVE,
    OFFLINE,
    SYNCING
}

data class AvatarSpec(
    val chassis: Int = 0,
    val eyes: Int = 0,
    val accessory: Int = 0,
    val pattern: Int = 0,
    val frame: Int = 0
) {
    fun toPacked(): Int {
        val c = chassis.coerceIn(0, CHASSIS_COUNT - 1)
        val e = eyes.coerceIn(0, EYES_COUNT - 1)
        val a = accessory.coerceIn(0, ACCESSORY_COUNT - 1)
        val p = pattern.coerceIn(0, PATTERN_COUNT - 1)
        val f = frame.coerceIn(0, FRAME_COUNT - 1)
        return PACKED_FLAG or c or (e shl 4) or (a shl 7) or (p shl 10) or (f shl 13)
    }

    companion object {
        const val CHASSIS_COUNT = 10
        const val EYES_COUNT = 6
        const val ACCESSORY_COUNT = 7
        const val PATTERN_COUNT = 5
        const val FRAME_COUNT = 4
        private const val PACKED_FLAG = 1 shl 20

        fun fromPacked(raw: Int): AvatarSpec {
            if ((raw and PACKED_FLAG) != 0) {
                return AvatarSpec(
                    chassis = (raw and 0xF).coerceIn(0, CHASSIS_COUNT - 1),
                    eyes = ((raw ushr 4) and 0x7).coerceIn(0, EYES_COUNT - 1),
                    accessory = ((raw ushr 7) and 0x7).coerceIn(0, ACCESSORY_COUNT - 1),
                    pattern = ((raw ushr 10) and 0x7).coerceIn(0, PATTERN_COUNT - 1),
                    frame = ((raw ushr 13) and 0x3).coerceIn(0, FRAME_COUNT - 1)
                )
            }
            val safe = ((raw % 6) + 6) % 6
            return AvatarSpec(
                chassis = safe,
                eyes = safe % EYES_COUNT,
                accessory = 0,
                pattern = if (safe == 2) 1 else 0,
                frame = 0
            )
        }
    }
}

data class VoterIdentity(
    val uid: String,
    val displayName: String,
    val badgeColorIndex: Int,
    val avatarStyleIndex: Int = 0,
    val anonymousAvatarStyleIndex: Int = 0,
    val anonymousBadgeColorIndex: Int = 4,
    val anonymousMaskName: String = "Incognito"
) {
    val effectiveName: String
        get() = displayName.trim().ifEmpty { "Guest" }

    val effectiveAnonymousName: String
        get() = anonymousMaskName.trim().ifEmpty { "Incognito" }

    val initials: String
        get() = initialsFor(effectiveName)

    val anonymousInitials: String
        get() = initialsFor(effectiveAnonymousName)

    val avatarSpec: AvatarSpec
        get() = AvatarSpec.fromPacked(avatarStyleIndex)

    val anonymousAvatarSpec: AvatarSpec
        get() = AvatarSpec.fromPacked(anonymousAvatarStyleIndex)
}

data class VoiceCommentUiModel(
    val id: String,
    val roomId: String,
    val optionId: String? = null,
    val optionLabel: String? = null,
    val voterUid: String,
    val voterName: String,
    val voterBadgeColorIndex: Int,
    val voterAvatarStyleIndex: Int,
    val transcript: String,
    val durationSeconds: Int = 3,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val isMyComment: Boolean = false,
    val audioFilePath: String? = null
)

data class VoterChip(
    val voterUid: String,
    val voterName: String,
    val initials: String,
    val badgeColorIndex: Int,
    val avatarStyleIndex: Int = 0,
    val isHighPriority: Boolean = false
)

data class OptionUiModel(
    val id: String,
    val label: String,
    val sortOrder: Int,
    val voteCount: Int,
    val percentage: Int,
    val fraction: Float,
    val isVotedByMe: Boolean,
    val isPendingSync: Boolean,
    val isLeading: Boolean,
    val isTiedLeader: Boolean = false,
    val isUnanimous: Boolean = false,
    val isWinner: Boolean,
    val voters: List<VoterChip>,
    val hasPriorityConflict: Boolean = false,
    val conflictHint: String? = null,
    val isDeclinedByMe: Boolean = false,
    val declinedCount: Int = 0,
    val slotStartMs: Long? = null,
    val slotEndMs: Long? = null,
    /** Ranked rooms: this device's position for the option (1 = first choice), else null. */
    val myRank: Int? = null
) {
    /**
     * The option's time window. Uses the stored timestamps when present (locale-independent);
     * falls back to parsing the label only for rooms created before schema v7.
     */
    fun slotWindowMs(referenceNowMs: Long = System.currentTimeMillis()): Pair<Long, Long>? {
        val start = slotStartMs ?: return ConsensusInsightCalculator.parseSlotWindowMs(label, referenceNowMs)
        return start to (slotEndMs ?: (start + 60L * 60L * 1000L))
    }
}

data class RoomDetailsUiModel(
    val id: String,
    val question: String,
    val mode: RoomMode,
    val allowMultiple: Boolean,
    val allowFriendsToAdd: Boolean,
    val isAnonymous: Boolean = false,
    val deadlineEpochMs: Long? = null,
    val isCreator: Boolean,
    val isLocked: Boolean,
    val isExpiredByTimer: Boolean = false,
    val winnerOptionId: String?,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val options: List<OptionUiModel>,
    val totalVotes: Int,
    val activeVoters: List<VoterChip>,
    val queuedVoteCount: Int = 0,
    val hasTiedLeaders: Boolean = false,
    val decisionInsight: DecisionInsight? = null,
    val highPriorityUids: Set<String> = emptySet(),
    val conflictAlert: ConflictAlert? = null,
    val maskedNetworkIp: String? = null,
    val isIpVoteLocked: Boolean = false,
    val anonymousLockAlert: String? = null,
    val isRanked: Boolean = false,
    val rankedRounds: Int = 0,
    val rankedTie: Boolean = false,
    val repeatWeekly: Boolean = false,
    val seriesId: String? = null,
    val nextRoomCode: String? = null,
    val voiceComments: List<VoiceCommentUiModel> = emptyList()
) {
    val shareUrl: String
        get() = io.github.princejoseph.meld.BuildConfig.WEB_BASE_URL
            .takeIf { it.isNotBlank() }
            ?.let { "$it/r/$id" }
            ?: "meld://r/$id"

    val deepLinkUri: String
        get() = "meld://r/$id"
}

enum class InsightStrength {
    AWAITING_VOTES,
    TIED,
    LEADING,
    STRONG_CONSENSUS,
    UNANIMOUS,
    LOCKED
}

data class DecisionInsight(
    val suggestedLabel: String,
    val suggestedOptionId: String?,
    val overlapVoterCount: Int,
    val totalUniqueVoters: Int,
    val overlapPercentage: Int,
    val strength: InsightStrength,
    val runnerUpLabel: String? = null,
    val isOverlapWindow: Boolean = false
)

object ConsensusInsightCalculator {

    private val timeFormats = listOf(
        "EEE, MMM d · h:mm a",
        "EEE, h:mm a",
        "EEE h:mm a",
        "MMM d · h:mm a",
        "MMM d, h:mm a"
    )

    fun calculate(
        options: List<OptionUiModel>,
        mode: RoomMode,
        totalUniqueVoters: Int,
        isLocked: Boolean,
        winnerOptionId: String?
    ): DecisionInsight? {
        if (options.isEmpty()) return null

        if (isLocked) {
            val winner = options.firstOrNull { it.id == winnerOptionId }
                ?: options.maxByOrNull { it.voters.distinctBy { v -> v.voterUid }.size }
                ?: options.first()
            val overlap = winner.voters.distinctBy { it.voterUid }.size
            val pct = if (totalUniqueVoters > 0) ((overlap * 100f) / totalUniqueVoters).toInt().coerceIn(0, 100) else 0
            return DecisionInsight(
                suggestedLabel = winner.label,
                suggestedOptionId = winner.id,
                overlapVoterCount = overlap,
                totalUniqueVoters = totalUniqueVoters,
                overlapPercentage = pct,
                strength = InsightStrength.LOCKED
            )
        }

        if (totalUniqueVoters == 0) {
            return DecisionInsight(
                suggestedLabel = options.first().label,
                suggestedOptionId = options.first().id,
                overlapVoterCount = 0,
                totalUniqueVoters = 0,
                overlapPercentage = 0,
                strength = InsightStrength.AWAITING_VOTES
            )
        }

        // For Time Slots, check if any parsed intervals genuinely overlap in time to find a denser consensus sub-window
        val discreteBest = options
            .map { opt -> opt to opt.voters.distinctBy { it.voterUid }.map { it.voterUid }.toSet() }
            .sortedWith(
                compareByDescending<Pair<OptionUiModel, Set<String>>> { it.second.size }
                    .thenBy { it.first.sortOrder }
            )

        val topDiscreteCount = discreteBest.first().second.size

        if (mode == RoomMode.TIME_SLOTS && options.size >= 2) {
            val parsedSlots = options.mapNotNull { opt ->
                opt.slotWindowMs()?.let { (startMs, endMs) ->
                    Triple(opt, startMs, endMs)
                }
            }
            var bestOverlapLabel: String? = null
            var bestOverlapOptionId: String? = null
            var bestOverlapCount = topDiscreteCount

            for (i in parsedSlots.indices) {
                for (j in i + 1 until parsedSlots.size) {
                    val (optA, startA, endA) = parsedSlots[i]
                    val (optB, startB, endB) = parsedSlots[j]
                    val overlapStart = maxOf(startA, startB)
                    val overlapEnd = minOf(endA, endB)
                    if (overlapStart < overlapEnd) {
                        val combinedVoters = (optA.voters + optB.voters)
                            .map { it.voterUid }
                            .toSet()
                        if (combinedVoters.size > bestOverlapCount) {
                            bestOverlapCount = combinedVoters.size
                            bestOverlapLabel = formatOverlapRange(overlapStart, overlapEnd)
                            bestOverlapOptionId = optA.id
                        }
                    }
                }
            }

            if (bestOverlapLabel != null && bestOverlapCount > topDiscreteCount) {
                val pct = ((bestOverlapCount * 100f) / totalUniqueVoters).toInt().coerceIn(0, 100)
                val strength = when {
                    totalUniqueVoters >= 2 && bestOverlapCount == totalUniqueVoters -> InsightStrength.UNANIMOUS
                    pct >= 67 || bestOverlapCount >= 2 -> InsightStrength.STRONG_CONSENSUS
                    else -> InsightStrength.LEADING
                }
                return DecisionInsight(
                    suggestedLabel = bestOverlapLabel,
                    suggestedOptionId = bestOverlapOptionId,
                    overlapVoterCount = bestOverlapCount,
                    totalUniqueVoters = totalUniqueVoters,
                    overlapPercentage = pct,
                    strength = strength,
                    isOverlapWindow = true
                )
            }
        }

        val tiedLeaders = discreteBest.filter { it.second.size == topDiscreteCount && topDiscreteCount > 0 }
        val pct = ((topDiscreteCount * 100f) / totalUniqueVoters).toInt().coerceIn(0, 100)

        if (tiedLeaders.size > 1) {
            return DecisionInsight(
                suggestedLabel = tiedLeaders[0].first.label,
                suggestedOptionId = tiedLeaders[0].first.id,
                overlapVoterCount = topDiscreteCount,
                totalUniqueVoters = totalUniqueVoters,
                overlapPercentage = pct,
                strength = InsightStrength.TIED,
                runnerUpLabel = tiedLeaders[1].first.label
            )
        }

        val leader = discreteBest.first().first
        val strength = when {
            totalUniqueVoters >= 2 && topDiscreteCount == totalUniqueVoters -> InsightStrength.UNANIMOUS
            pct >= 67 || (totalUniqueVoters >= 2 && topDiscreteCount >= 2) -> InsightStrength.STRONG_CONSENSUS
            else -> InsightStrength.LEADING
        }

        return DecisionInsight(
            suggestedLabel = leader.label,
            suggestedOptionId = leader.id,
            overlapVoterCount = topDiscreteCount,
            totalUniqueVoters = totalUniqueVoters,
            overlapPercentage = pct,
            strength = strength
        )
    }

    fun parseSlotWindowMs(
        label: String,
        referenceNowMs: Long = System.currentTimeMillis()
    ): Pair<Long, Long>? {
        val cleaned = label.trim().replace(" · ", ", ").replace("·", " ")
        val refCal = java.util.Calendar.getInstance().apply { timeInMillis = referenceNowMs }
        val normalizedPatterns = listOf(
            "EEE, MMM d, h:mm a" to true,
            "EEE, MMM d h:mm a" to true,
            "MMM d, h:mm a" to true,
            "MMM d h:mm a" to true,
            "EEE, h:mm a" to false,
            "EEE h:mm a" to false
        )
        val locales = listOf(java.util.Locale.US, java.util.Locale.getDefault()).distinct()
        for ((pattern, hasMonth) in normalizedPatterns) {
            val parsed = locales.firstNotNullOfOrNull { locale ->
                runCatching {
                    val sdf = java.text.SimpleDateFormat(pattern, locale)
                    sdf.isLenient = true
                    sdf.parse(cleaned)
                }.getOrNull()
            }
            if (parsed != null) {
                val parsedCal = java.util.Calendar.getInstance().apply { time = parsed }
                val normalized = java.util.Calendar.getInstance().apply {
                    timeInMillis = referenceNowMs
                    if (hasMonth) {
                        set(java.util.Calendar.YEAR, refCal.get(java.util.Calendar.YEAR))
                        set(java.util.Calendar.MONTH, parsedCal.get(java.util.Calendar.MONTH))
                        set(java.util.Calendar.DAY_OF_MONTH, parsedCal.get(java.util.Calendar.DAY_OF_MONTH))
                    } else {
                        set(java.util.Calendar.DAY_OF_WEEK, parsedCal.get(java.util.Calendar.DAY_OF_WEEK))
                    }
                    set(java.util.Calendar.HOUR_OF_DAY, parsedCal.get(java.util.Calendar.HOUR_OF_DAY))
                    set(java.util.Calendar.MINUTE, parsedCal.get(java.util.Calendar.MINUTE))
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val start = normalized.timeInMillis
                return start to (start + 60L * 60L * 1000L)
            }
        }
        return null
    }

    fun resolveCalendarEventWindowMs(
        label: String,
        referenceNowMs: Long = System.currentTimeMillis()
    ): Pair<Long, Long>? {
        val window = parseSlotWindowMs(label, referenceNowMs) ?: return null
        val hasExplicitMonth = Regex(
            "\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(label)
        return if (!hasExplicitMonth && window.first < referenceNowMs - 60L * 60_000L) {
            val weekMs = 7L * 24L * 60L * 60L * 1000L
            (window.first + weekMs) to (window.second + weekMs)
        } else {
            window
        }
    }

    private fun formatOverlapRange(startMs: Long, endMs: Long): String {
        val dayTime = java.text.SimpleDateFormat("EEE, h:mm a", java.util.Locale.US)
        val endTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US)
        return "${dayTime.format(java.util.Date(startMs))} – ${endTime.format(java.util.Date(endMs))}"
    }
}

/** An option to create. Time-slot options carry their window; the label is for display only. */
data class NewOptionSpec(
    val label: String,
    val slotStartMs: Long? = null,
    val slotEndMs: Long? = null
)

data class ParticipantCommitment(
    val participantUid: String,
    val participantName: String,
    val startMs: Long,
    val endMs: Long,
    val title: String
)

enum class ConflictSeverity {
    WARNING,
    DEADLOCK
}

data class ConflictAlert(
    val conflictedOptionId: String,
    val conflictedOptionLabel: String,
    val highPriorityParticipantNames: List<String>,
    val commitmentSummary: String,
    val suggestedAlternativeLabel: String? = null,
    val suggestedAlternativeOptionId: String? = null,
    val severity: ConflictSeverity = ConflictSeverity.WARNING
)

data class ConflictAnalysisResult(
    val conflictedOptionHints: Map<String, String>,
    val alert: ConflictAlert?
)

object ConflictDetector {

    fun analyze(
        options: List<OptionUiModel>,
        activeVoters: List<VoterChip>,
        mode: RoomMode,
        highPriorityUids: Set<String>,
        commitmentsByUid: Map<String, List<ParticipantCommitment>>,
        isLocked: Boolean,
        referenceNowMs: Long = System.currentTimeMillis()
    ): ConflictAnalysisResult {
        if (isLocked || mode != RoomMode.TIME_SLOTS || highPriorityUids.size < 2 || options.isEmpty()) {
            return ConflictAnalysisResult(emptyMap(), null)
        }

        val nameByUid = activeVoters.associate { it.voterUid to it.voterName }.toMutableMap()
        commitmentsByUid.values.flatten().forEach { commitment ->
            nameByUid.putIfAbsent(commitment.participantUid, commitment.participantName)
        }

        val selectedSlots = options.filter { it.voteCount > 0 }.ifEmpty { options }
        val conflictDetailsByOptionId = mutableMapOf<String, List<ParticipantCommitment>>()
        val optionHints = mutableMapOf<String, String>()

        for (opt in options) {
            val window = opt.slotWindowMs(referenceNowMs) ?: continue
            val (slotStart, slotEnd) = window

            val overlappingPerUid = mutableMapOf<String, ParticipantCommitment>()
            for (uid in highPriorityUids) {
                val match = commitmentsByUid[uid].orEmpty().firstOrNull { c ->
                    c.startMs < slotEnd && c.endMs > slotStart
                }
                if (match != null) {
                    overlappingPerUid[uid] = match
                }
            }

            if (overlappingPerUid.size >= 2) {
                val list = overlappingPerUid.values.toList()
                conflictDetailsByOptionId[opt.id] = list
                optionHints[opt.id] = "${list.size} priority conflicts"
            }
        }

        // Check if any selected (or leading) slot has 2+ high-priority participants with overlapping calendar commitments
        val primaryConflictedOption = selectedSlots
            .sortedWith(compareByDescending<OptionUiModel> { it.voteCount }.thenBy { it.sortOrder })
            .firstOrNull { conflictDetailsByOptionId.containsKey(it.id) }

        if (primaryConflictedOption != null) {
            val conflicts = conflictDetailsByOptionId.getValue(primaryConflictedOption.id)
            val participantNames = conflicts.map { nameByUid[it.participantUid] ?: it.participantName }.distinct()
            val titles = conflicts.map { it.title }.filter { it.isNotBlank() }.distinct().take(2)
            val summary = if (titles.isNotEmpty()) {
                titles.joinToString(" & ")
            } else {
                "Overlapping commitments"
            }
            val cleanAlternative = options.firstOrNull {
                it.id != primaryConflictedOption.id && !conflictDetailsByOptionId.containsKey(it.id)
            }

            return ConflictAnalysisResult(
                conflictedOptionHints = optionHints,
                alert = ConflictAlert(
                    conflictedOptionId = primaryConflictedOption.id,
                    conflictedOptionLabel = primaryConflictedOption.label,
                    highPriorityParticipantNames = participantNames,
                    commitmentSummary = summary,
                    suggestedAlternativeLabel = cleanAlternative?.label,
                    suggestedAlternativeOptionId = cleanAlternative?.id,
                    severity = ConflictSeverity.WARNING
                )
            )
        }

        // Secondary proactive check: 2+ high-priority participants voted on completely disjoint time slots with zero overlap
        val votedSlots = options.filter { it.voteCount > 0 }
        if (votedSlots.size >= 2) {
            val votingPriorityUids = votedSlots
                .flatMap { it.voters }
                .map { it.voterUid }
                .filter { highPriorityUids.contains(it) }
                .distinct()

            if (votingPriorityUids.size >= 2) {
                val maxPriorityOnAnySlot = votedSlots.maxOfOrNull { opt ->
                    opt.voters.count { highPriorityUids.contains(it.voterUid) }
                } ?: 0

                if (maxPriorityOnAnySlot < 2) {
                    val splitNames = votingPriorityUids.map { nameByUid[it] ?: "Participant" }
                    val topSlot = votedSlots.maxByOrNull { it.voteCount } ?: votedSlots.first()
                    return ConflictAnalysisResult(
                        conflictedOptionHints = optionHints,
                        alert = ConflictAlert(
                            conflictedOptionId = topSlot.id,
                            conflictedOptionLabel = topSlot.label,
                            highPriorityParticipantNames = splitNames,
                            commitmentSummary = "Split priority availability across slots",
                            severity = ConflictSeverity.DEADLOCK
                        )
                    )
                }
            }
        }

        return ConflictAnalysisResult(optionHints, null)
    }
}

data class RecentRoomSummary(
    val id: String,
    val question: String,
    val mode: RoomMode,
    val totalVotes: Int,
    val isLocked: Boolean,
    val createdAtEpochMs: Long,
    val winnerLabel: String? = null,
    val optionCount: Int = 0,
    val isExpired: Boolean = false
)

fun initialsFor(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return "GU"
    val parts = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        parts[0].length >= 2 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first().uppercaseChar()}•"
    }
}

fun badgeColorIndexForUid(uid: String, paletteSize: Int): Int {
    if (paletteSize <= 0) return 0
    return abs(uid.hashCode()) % paletteSize
}

fun avatarStyleIndexForUid(uid: String, styleCount: Int = AvatarSpec.CHASSIS_COUNT): Int {
    if (styleCount <= 0) return 0
    return abs(uid.reversed().hashCode()) % styleCount
}

private val CONTROL_CHARS_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
private val ROOM_CODE_REGEX = Regex("^[A-Z0-9]{4,10}$")

fun sanitizeMeldInput(raw: String, maxLength: Int): String {
    return raw.replace(CONTROL_CHARS_REGEX, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(maxLength)
}

fun sanitizeRoomCode(raw: String?): String? {
    val candidate = raw?.trim()?.removePrefix("#")?.uppercase() ?: return null
    return candidate.takeIf { ROOM_CODE_REGEX.matches(it) }
}

