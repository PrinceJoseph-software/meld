package io.github.princejoseph.meld.data

import android.content.Context
import io.github.princejoseph.meld.notifications.DeadlineWorkScheduler
import io.github.princejoseph.meld.notifications.MeldNotificationManager
import io.github.princejoseph.meld.ui.theme.VoterBadgeColors
import io.github.princejoseph.meld.widget.MeldWidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.UUID
import kotlin.math.roundToInt

class MeldRepository(
    private val dao: MeldDao,
    private val identityStore: IdentityStore,
    connectivityObserver: ConnectivityObserver? = null,
    private val appContext: Context? = null,
    private val remoteSync: RemoteSyncGateway = appContext?.let { RemoteSyncGatewayFactory.create(it) } ?: NoOpRemoteSyncGateway
) {
    private val secureRandom = SecureRandom()
    private val syncMutex = Mutex()

    val identityFlow: Flow<VoterIdentity> = identityStore.identityFlow
    val darkThemeOverrideFlow: Flow<Boolean?> = identityStore.darkThemeOverrideFlow

    private val _simulatedOffline = MutableStateFlow(false)
    val simulatedOffline: StateFlow<Boolean> = _simulatedOffline.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _highPriorityByRoom = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val _commitmentsByUid = MutableStateFlow<Map<String, List<ParticipantCommitment>>>(emptyMap())
    private val _declinedByRoom = MutableStateFlow<Map<String, Map<String, Set<String>>>>(emptyMap())
    private val _anonymousLockAlertByRoom = MutableStateFlow<Map<String, String?>>(emptyMap())

    val isOnlineFlow: Flow<Boolean> = if (connectivityObserver != null) {
        combine(connectivityObserver.isSystemOnline, _simulatedOffline) { sysOnline, simOffline ->
            sysOnline && !simOffline
        }
    } else {
        combine(_simulatedOffline) { (simOffline) -> !simOffline }
    }

    val syncStatusFlow: Flow<SyncStatus> = combine(isOnlineFlow, _isSyncing) { online, syncing ->
        when {
            !online -> SyncStatus.OFFLINE
            syncing -> SyncStatus.SYNCING
            else -> SyncStatus.LIVE
        }
    }

    val allPendingCountFlow: Flow<Int> = combine(dao.observeAllPendingVotes()) { (list) ->
        list.size
    }

    val recentRoomsFlow: Flow<List<RecentRoomSummary>> = dao.observeRecentRooms().combine(
        identityStore.identityFlow
    ) { list, _ ->
        val now = System.currentTimeMillis()
        list.map { entry ->
            val isExpired = entry.room.deadlineEpochMs != null && now >= entry.room.deadlineEpochMs
            val effectiveLocked = entry.room.isLocked || isExpired
            val winnerLabel = if (entry.room.winnerOptionId != null) {
                entry.options.firstOrNull { it.id == entry.room.winnerOptionId }?.label
            } else {
                null
            }
            RecentRoomSummary(
                id = entry.room.id,
                question = entry.room.question,
                mode = runCatching { RoomMode.valueOf(entry.room.mode) }.getOrDefault(RoomMode.QUICK_POLL),
                totalVotes = entry.votes.size,
                isLocked = effectiveLocked,
                createdAtEpochMs = entry.room.createdAtEpochMs,
                winnerLabel = winnerLabel,
                optionCount = entry.options.size,
                isExpired = isExpired
            )
        }
    }

    suspend fun initIdentity(): VoterIdentity = identityStore.ensureIdentity()

    suspend fun setDarkTheme(isDark: Boolean) = identityStore.setDarkTheme(isDark)

    fun setSimulatedOffline(offline: Boolean) {
        _simulatedOffline.value = offline
    }

    suspend fun roomExists(roomId: String): Boolean {
        val safeCode = sanitizeRoomCode(roomId) ?: return false
        return dao.getRoomOnce(safeCode) != null
    }

    suspend fun updateVoterProfile(
        newName: String,
        avatarStyleIndex: Int,
        badgeColorIndex: Int
    ) {
        val identity = identityStore.ensureIdentity()
        val cleaned = sanitizeMeldInput(newName, maxLength = 28)
        val packedAvatar = AvatarSpec.fromPacked(avatarStyleIndex).toPacked()
        identityStore.setDisplayName(cleaned)
        identityStore.setAvatarStyleAndColor(packedAvatar, badgeColorIndex)
        val effective = cleaned.ifEmpty { "Guest" }
        dao.updateVoterProfileAcrossVotes(
            voterUid = identity.uid,
            newName = effective,
            colorIndex = badgeColorIndex.coerceIn(0, VoterBadgeColors.lastIndex),
            avatarIndex = packedAvatar
        )
        appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
    }

    fun togglePriorityParticipant(roomId: String, voterUid: String) {
        _highPriorityByRoom.update { current ->
            val existing = current[roomId].orEmpty()
            val next = if (existing.contains(voterUid)) existing - voterUid else existing + voterUid
            current + (roomId to next)
        }
    }

    fun setParticipantCommitments(
        uid: String,
        participantName: String,
        busyIntervals: List<BusyInterval>
    ) {
        if (uid.isBlank()) return
        val mapped = busyIntervals.map { interval ->
            ParticipantCommitment(
                participantUid = uid,
                participantName = participantName,
                startMs = interval.startMs,
                endMs = interval.endMs,
                title = interval.summary.ifBlank { "Calendar commitment" }
            )
        }
        _commitmentsByUid.update { it + (uid to mapped) }
    }

    suspend fun deleteRoom(roomId: String) {
        appContext?.let { DeadlineWorkScheduler.cancelForRoom(it, roomId) }
        dao.clearPendingVotesForRoom(roomId)
        dao.deleteRoom(roomId)
        _highPriorityByRoom.update { it - roomId }
        _declinedByRoom.update { it - roomId }
        _anonymousLockAlertByRoom.update { it - roomId }
        appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
    }

    fun clearAnonymousLockAlert(roomId: String) {
        _anonymousLockAlertByRoom.update { it - roomId }
    }

    fun observeRoom(roomId: String): Flow<RoomDetailsUiModel?> {
        val extras = combine(
            _highPriorityByRoom,
            _commitmentsByUid,
            _declinedByRoom,
            _anonymousLockAlertByRoom
        ) { p, c, d, a ->
            RoomObserveExtras(p, c, d, a)
        }
        return combine(
            dao.observeRoom(roomId),
            dao.observePendingVotesForRoom(roomId),
            dao.observeAnonymousLocksForRoom(roomId),
            identityStore.identityFlow,
            identityStore.creatorRoomIdsFlow,
            dao.observeVoiceCommentsForRoom(roomId),
            extras
        ) { args ->
            val roomData = args[0] as RoomWithOptionsAndVotes? ?: return@combine null
            @Suppress("UNCHECKED_CAST")
            val pendingVotes = args[1] as List<PendingVoteEntity>
            @Suppress("UNCHECKED_CAST")
            val anonymousLocks = args[2] as List<AnonymousLockEntity>
            val identity = args[3] as VoterIdentity
            @Suppress("UNCHECKED_CAST")
            val creatorIds = args[4] as Set<String>
            @Suppress("UNCHECKED_CAST")
            val voiceComments = args[5] as List<VoiceCommentEntity>
            val extra = args[6] as RoomObserveExtras

            val isCreator = creatorIds.contains(roomId)
            val defaultPriority = if (isCreator && roomData.room.mode == RoomMode.TIME_SLOTS.name) {
                setOf(identity.uid)
            } else {
                emptySet()
            }
            val roomPriorityUids = extra.priorityMap[roomId] ?: defaultPriority
            mapRoomToUiModel(
                data = roomData,
                pendingVotes = pendingVotes,
                identity = identity,
                isCreator = isCreator,
                highPriorityUids = roomPriorityUids,
                commitmentsByUid = extra.commitments,
                declinedByOption = extra.declinedMap[roomId].orEmpty(),
                anonymousLocks = anonymousLocks,
                anonymousLockAlert = extra.alertMap[roomId],
                voiceComments = voiceComments
            )
        }
    }

    suspend fun createRoom(
        question: String,
        mode: RoomMode,
        optionLabels: List<String>,
        allowMultiple: Boolean,
        allowFriendsToAdd: Boolean,
        isAnonymous: Boolean = false,
        deadlineMinutes: Int? = null
    ): String {
        val creatorIdentity = identityStore.ensureIdentity()
        val roomId = generateRoomCode()
        val creatorToken = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val fourteenDaysMs = 14L * 24L * 60L * 60L * 1000L
        val deadlineMs = deadlineMinutes?.let { now + it * 60L * 1000L }

        val room = RoomEntity(
            id = roomId,
            question = sanitizeMeldInput(question, maxLength = 200),
            mode = mode.name,
            allowMultiple = allowMultiple,
            allowFriendsToAdd = allowFriendsToAdd,
            isAnonymous = isAnonymous,
            deadlineEpochMs = deadlineMs,
            creatorToken = creatorToken,
            winnerOptionId = null,
            isLocked = false,
            createdAtEpochMs = now,
            expiresAtEpochMs = now + fourteenDaysMs
        )

        val options = optionLabels
            .map { sanitizeMeldInput(it, maxLength = 140) }
            .filter { it.isNotEmpty() }
            .take(16)
            .mapIndexed { idx, label ->
                OptionEntity(
                    id = "${roomId}_opt_${UUID.randomUUID().toString().take(8)}",
                    roomId = roomId,
                    label = label,
                    sortOrder = idx
                )
            }

        dao.createRoomWithOptions(room, options)
        identityStore.saveCreatorToken(roomId, creatorToken)
        remoteSync.pushRoom(room, options)
        if (mode == RoomMode.TIME_SLOTS) {
            _highPriorityByRoom.update { it + (roomId to setOf(creatorIdentity.uid)) }
        }
        if (deadlineMs != null) {
            appContext?.let {
                DeadlineWorkScheduler.scheduleForRoom(it, roomId, now, deadlineMs)
            }
        }
        appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
        return roomId
    }

    suspend fun createRunoffRoom(sourceRoomId: String): String? {
        val room = dao.getRoomOnce(sourceRoomId) ?: return null
        val options = dao.getOptionsOnce(sourceRoomId)
        val votes = dao.getVotesOnce(sourceRoomId)
        val counts = votes.groupingBy { it.optionId }.eachCount()
        val maxVotes = options.maxOfOrNull { counts[it.id] ?: 0 } ?: 0
        val tiedLeaders = if (maxVotes > 0) {
            options.filter { (counts[it.id] ?: 0) == maxVotes }
        } else {
            options.take(2)
        }
        val runoffOptions = if (tiedLeaders.size >= 2) {
            tiedLeaders.map { it.label }
        } else {
            options.sortedByDescending { counts[it.id] ?: 0 }.take(2).map { it.label }
        }
        if (runoffOptions.size < 2) return null

        val cleanQuestion = room.question.removePrefix("Runoff: ").trim()
        return createRoom(
            question = "Runoff: $cleanQuestion",
            mode = runCatching { RoomMode.valueOf(room.mode) }.getOrDefault(RoomMode.QUICK_POLL),
            optionLabels = runoffOptions,
            allowMultiple = false,
            allowFriendsToAdd = false,
            isAnonymous = room.isAnonymous,
            deadlineMinutes = 15
        )
    }

    suspend fun toggleVote(roomId: String, optionId: String, isCurrentlyOnline: Boolean) {
        val room = dao.getRoomOnce(roomId) ?: return
        val timerExpired = room.deadlineEpochMs != null && System.currentTimeMillis() >= room.deadlineEpochMs
        if (room.isLocked || timerExpired) return

        val identity = identityStore.ensureIdentity()
        if (room.isAnonymous && !verifyAndRecordAnonymousNetworkLock(room.id, identity.uid)) {
            return
        }

        val committedVotes = dao.getVotesOnce(roomId)
        val pendingVotes = dao.getAllPendingVotesOnce().filter { it.roomId == roomId }
        val effectiveVotes = applyPendingVotes(committedVotes, pendingVotes)

        val alreadyVotedThisOption = effectiveVotes.any {
            it.optionId == optionId && it.voterUid == identity.uid
        }

        if (!alreadyVotedThisOption) {
            setDeclined(roomId, optionId, identity.uid, declined = false)
        }

        applyVoteMutation(
            room = room,
            optionId = optionId,
            identity = identity,
            isRemoval = alreadyVotedThisOption,
            isCurrentlyOnline = isCurrentlyOnline
        )
        appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
    }

    suspend fun castDirectionalVote(
        roomId: String,
        optionId: String,
        voteYes: Boolean,
        isCurrentlyOnline: Boolean
    ) {
        val room = dao.getRoomOnce(roomId) ?: return
        val timerExpired = room.deadlineEpochMs != null && System.currentTimeMillis() >= room.deadlineEpochMs
        if (room.isLocked || timerExpired) return

        val identity = identityStore.ensureIdentity()
        if (room.isAnonymous && !verifyAndRecordAnonymousNetworkLock(room.id, identity.uid)) {
            return
        }

        val committedVotes = dao.getVotesOnce(roomId)
        val pendingVotes = dao.getAllPendingVotesOnce().filter { it.roomId == roomId }
        val effectiveVotes = applyPendingVotes(committedVotes, pendingVotes)

        val alreadyVotedThisOption = effectiveVotes.any {
            it.optionId == optionId && it.voterUid == identity.uid
        }

        if (voteYes) {
            setDeclined(roomId, optionId, identity.uid, declined = false)
            if (alreadyVotedThisOption) return
            applyVoteMutation(
                room = room,
                optionId = optionId,
                identity = identity,
                isRemoval = false,
                isCurrentlyOnline = isCurrentlyOnline
            )
            appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
        } else {
            setDeclined(roomId, optionId, identity.uid, declined = true)
            if (!alreadyVotedThisOption) return
            applyVoteMutation(
                room = room,
                optionId = optionId,
                identity = identity,
                isRemoval = true,
                isCurrentlyOnline = isCurrentlyOnline
            )
            appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
        }
    }

    private suspend fun verifyAndRecordAnonymousNetworkLock(roomId: String, voterUid: String): Boolean {
        val now = System.currentTimeMillis()
        val snapshot = appContext?.let { AnonymousSpamGuard.captureCurrentNetwork(it) }
            ?: NetworkLockSnapshot("offline-local", "Local Device", "net")
        val salt = identityStore.getOrCreateNetworkSalt()
        val networkHash = AnonymousSpamGuard.computeNetworkHash(roomId, snapshot.rawIpNormalized, salt)
        val lockKey = "$roomId:$networkHash"
        val existing = dao.getAnonymousLock(lockKey)

        return when (val decision = AnonymousSpamGuard.evaluateAnonymousVote(existing, voterUid, now)) {
            is AnonymousVoteGate.Allowed -> {
                val updated = AnonymousSpamGuard.nextLockRecord(
                    roomId = roomId,
                    networkHash = networkHash,
                    maskedIpLabel = snapshot.maskedIpLabel,
                    voterUid = voterUid,
                    existingLock = existing,
                    nowMs = now
                )
                dao.upsertAnonymousLock(updated)
                _anonymousLockAlertByRoom.update { it - roomId }
                true
            }
            is AnonymousVoteGate.BlockedDuplicateNetwork -> {
                _anonymousLockAlertByRoom.update {
                    it + (roomId to "Ballot locked to IP ${decision.maskedIpLabel} — one anonymous voter per network.")
                }
                false
            }
            is AnonymousVoteGate.BlockedRateLimit -> {
                _anonymousLockAlertByRoom.update {
                    it + (roomId to "Anti-spam cooldown (${decision.retrySeconds}s) active for IP ${decision.maskedIpLabel}.")
                }
                false
            }
        }
    }

    private fun setDeclined(roomId: String, optionId: String, uid: String, declined: Boolean) {
        _declinedByRoom.update { rooms ->
            val byOption = rooms[roomId].orEmpty().toMutableMap()
            val set = byOption[optionId].orEmpty()
            val nextSet = if (declined) set + uid else set - uid
            if (nextSet.isEmpty()) byOption.remove(optionId) else byOption[optionId] = nextSet
            rooms + (roomId to byOption)
        }
    }

    private suspend fun applyVoteMutation(
        room: RoomEntity,
        optionId: String,
        identity: VoterIdentity,
        isRemoval: Boolean,
        isCurrentlyOnline: Boolean
    ) {
        val voterName = if (room.isAnonymous) identity.effectiveAnonymousName else identity.effectiveName
        val badgeColor = if (room.isAnonymous) identity.anonymousBadgeColorIndex else identity.badgeColorIndex
        val avatarStyle = if (room.isAnonymous) identity.anonymousAvatarStyleIndex else identity.avatarStyleIndex

        if (!isCurrentlyOnline) {
            dao.insertPendingVote(
                PendingVoteEntity(
                    id = UUID.randomUUID().toString(),
                    roomId = room.id,
                    optionId = optionId,
                    voterUid = identity.uid,
                    voterName = voterName,
                    voterBadgeColorIndex = badgeColor,
                    voterAvatarStyleIndex = avatarStyle,
                    isRemoval = isRemoval,
                    allowMultiple = room.allowMultiple,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
            return
        }

        val vote = VoteEntity(
            id = "${room.id}:$optionId:${identity.uid}",
            roomId = room.id,
            optionId = optionId,
            voterUid = identity.uid,
            voterName = voterName,
            voterBadgeColorIndex = badgeColor,
            voterAvatarStyleIndex = avatarStyle,
            votedAtEpochMs = System.currentTimeMillis()
        )

        commitVote(
            roomId = room.id,
            optionId = optionId,
            voterUid = identity.uid,
            allowMultiple = room.allowMultiple,
            isRemoval = isRemoval,
            vote = vote
        )
    }

    suspend fun flushPendingVotes() {
        syncMutex.withLock {
            val pending = dao.getAllPendingVotesOnce()
            if (pending.isEmpty()) return

            _isSyncing.value = true
            try {
                delay(450L)
                for (entry in pending) {
                    val room = dao.getRoomOnce(entry.roomId)
                    val expired = room?.deadlineEpochMs != null && System.currentTimeMillis() >= room.deadlineEpochMs
                    if (room == null || room.isLocked || expired) {
                        dao.deletePendingVoteById(entry.id)
                        continue
                    }

                    val vote = VoteEntity(
                        id = "${entry.roomId}:${entry.optionId}:${entry.voterUid}",
                        roomId = entry.roomId,
                        optionId = entry.optionId,
                        voterUid = entry.voterUid,
                        voterName = entry.voterName,
                        voterBadgeColorIndex = entry.voterBadgeColorIndex,
                        voterAvatarStyleIndex = entry.voterAvatarStyleIndex,
                        votedAtEpochMs = entry.createdAtEpochMs
                    )
                    commitVote(
                        roomId = entry.roomId,
                        optionId = entry.optionId,
                        voterUid = entry.voterUid,
                        allowMultiple = entry.allowMultiple,
                        isRemoval = entry.isRemoval,
                        vote = vote
                    )
                    dao.deletePendingVoteById(entry.id)
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun commitVote(
        roomId: String,
        optionId: String,
        voterUid: String,
        allowMultiple: Boolean,
        isRemoval: Boolean,
        vote: VoteEntity
    ) {
        if (allowMultiple) {
            if (isRemoval) {
                dao.deleteVote(roomId, optionId, voterUid)
                remoteSync.removeVote(roomId, optionId, voterUid)
            } else {
                dao.insertVote(vote)
                remoteSync.pushVote(vote, allowMultiple = true)
            }
        } else {
            dao.toggleSingleChoiceVote(
                roomId = roomId,
                optionId = optionId,
                voterUid = voterUid,
                alreadySelected = isRemoval,
                newVote = vote
            )
            if (isRemoval) {
                remoteSync.removeVote(roomId, optionId, voterUid)
            } else {
                remoteSync.pushVote(vote, allowMultiple = false)
            }
        }
    }

    suspend fun addOptionToRoom(roomId: String, label: String) {
        val cleaned = sanitizeMeldInput(label, maxLength = 140)
        if (cleaned.isEmpty()) return
        val room = dao.getRoomOnce(roomId) ?: return
        val timerExpired = room.deadlineEpochMs != null && System.currentTimeMillis() >= room.deadlineEpochMs
        if (room.isLocked || timerExpired) return
        val isCreator = identityStore.hasValidCreatorToken(roomId, room.creatorToken)
        if (!room.allowFriendsToAdd && !isCreator) return

        val existing = dao.getOptionsOnce(roomId)
        if (existing.size >= 20) return
        val nextSort = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.insertOption(
            OptionEntity(
                id = "${roomId}_opt_${UUID.randomUUID().toString().take(8)}",
                roomId = roomId,
                label = cleaned,
                sortOrder = nextSort
            )
        )
    }

    suspend fun crownWinner(roomId: String, explicitOptionId: String? = null) {
        val room = dao.getRoomOnce(roomId) ?: return
        if (!identityStore.hasValidCreatorToken(roomId, room.creatorToken)) return

        val options = dao.getOptionsOnce(roomId)
        if (options.isEmpty()) return
        val votes = dao.getVotesOnce(roomId)

        val winnerId = explicitOptionId ?: run {
            val counts = votes.groupingBy { it.optionId }.eachCount()
            options.sortedWith(
                compareByDescending<OptionEntity> { counts[it.id] ?: 0 }
                    .thenBy { it.sortOrder }
            ).first().id
        }

        dao.clearPendingVotesForRoom(roomId)
        dao.updateRoomLock(roomId = roomId, isLocked = true, winnerOptionId = winnerId)
        appContext?.let {
            DeadlineWorkScheduler.cancelForRoom(it, roomId)
            val winnerLabel = options.firstOrNull { opt -> opt.id == winnerId }?.label
            MeldNotificationManager.showDecisionReached(
                context = it,
                roomId = roomId,
                question = room.question,
                winnerLabel = winnerLabel,
                voteCount = votes.size
            )
            MeldWidgetUpdater.refreshAllAsync(it)
        }
    }

    suspend fun finalizeExpiredRoomIfNeeded(
        roomId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val room = dao.getRoomOnce(roomId) ?: return false
        val deadline = room.deadlineEpochMs ?: return false
        if (room.isLocked || nowMs < deadline) return false

        val options = dao.getOptionsOnce(roomId)
        val committedVotes = dao.getVotesOnce(roomId)
        val pendingVotes = dao.getAllPendingVotesOnce().filter { it.roomId == roomId }
        val effectiveVotes = applyPendingVotes(committedVotes, pendingVotes)
        val counts = effectiveVotes.groupingBy { it.optionId }.eachCount()
        val totalVotes = effectiveVotes.size

        val winnerId = if (totalVotes > 0 && options.isNotEmpty()) {
            options.sortedWith(
                compareByDescending<OptionEntity> { counts[it.id] ?: 0 }
                    .thenBy { it.sortOrder }
            ).first().id
        } else {
            null
        }

        dao.clearPendingVotesForRoom(roomId)
        dao.updateRoomLock(roomId = roomId, isLocked = true, winnerOptionId = winnerId)
        appContext?.let {
            DeadlineWorkScheduler.cancelForRoom(it, roomId)
            val winnerLabel = options.firstOrNull { opt -> opt.id == winnerId }?.label
            MeldNotificationManager.showDecisionReached(
                context = it,
                roomId = roomId,
                question = room.question,
                winnerLabel = winnerLabel,
                voteCount = effectiveVotes.size
            )
            MeldWidgetUpdater.refreshAllAsync(it)
        }
        return true
    }

    suspend fun reopenRoom(roomId: String) {
        val room = dao.getRoomOnce(roomId) ?: return
        if (!identityStore.hasValidCreatorToken(roomId, room.creatorToken)) return
        val now = System.currentTimeMillis()
        val nextDeadline = room.deadlineEpochMs?.takeIf { it > now }
        dao.updateRoomDeadlineAndLock(
            roomId = roomId,
            isLocked = false,
            winnerOptionId = null,
            deadlineEpochMs = nextDeadline
        )
        if (nextDeadline != null) {
            appContext?.let { DeadlineWorkScheduler.scheduleForRoom(it, roomId, room.createdAtEpochMs, nextDeadline) }
        }
        appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
    }

    suspend fun resetVotes(roomId: String) {
        val room = dao.getRoomOnce(roomId) ?: return
        if (!identityStore.hasValidCreatorToken(roomId, room.creatorToken)) return
        dao.clearPendingVotesForRoom(roomId)
        dao.clearRoomVotes(roomId)
        dao.clearAnonymousLocksForRoom(roomId)
        _declinedByRoom.update { it - roomId }
        _anonymousLockAlertByRoom.update { it - roomId }
        if (room.isLocked) {
            dao.updateRoomLock(roomId = roomId, isLocked = false, winnerOptionId = null)
        }
        appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
    }

    suspend fun simulateThreeFriendsVoting(roomId: String) {
        val room = dao.getRoomOnce(roomId) ?: return
        if (room.isLocked) return
        val options = dao.getOptionsOnce(roomId)
        if (options.isEmpty()) return

        val existingVotes = dao.getVotesOnce(roomId)
        val counts = existingVotes.groupingBy { it.optionId }.eachCount()
        val sortedByAsc = options.sortedBy { counts[it.id] ?: 0 }
        val challengerOption = sortedByAsc.first()
        val secondaryOption = options.lastOrNull() ?: challengerOption

        val friends = listOf(
            FriendSpec("sim_alex_rivera", "Alex Rivera", colorIdx = 1, avatarIdx = AvatarSpec(0, 0, 2, 1, 1).toPacked()),
            FriendSpec("sim_samira_k", "Samira K.", colorIdx = 2, avatarIdx = AvatarSpec(2, 2, 4, 2, 0).toPacked()),
            FriendSpec("sim_jules_chen", "Jules Chen", colorIdx = 6, avatarIdx = AvatarSpec(6, 3, 1, 3, 2).toPacked())
        )

        if (room.mode == RoomMode.TIME_SLOTS.name) {
            val slotWindow = ConsensusInsightCalculator.parseSlotWindowMs(challengerOption.label)
            if (slotWindow != null) {
                val (slotStart, slotEnd) = slotWindow
                _commitmentsByUid.update { current ->
                    current + (
                        "sim_alex_rivera" to listOf(
                            ParticipantCommitment(
                                participantUid = "sim_alex_rivera",
                                participantName = "Alex Rivera",
                                startMs = slotStart,
                                endMs = slotEnd,
                                title = "Design Review"
                            )
                        )
                    ) + (
                        "sim_samira_k" to listOf(
                            ParticipantCommitment(
                                participantUid = "sim_samira_k",
                                participantName = "Samira K.",
                                startMs = slotStart + 15L * 60L * 1000L,
                                endMs = slotEnd + 15L * 60L * 1000L,
                                title = "Client Sync"
                            )
                        )
                    )
                }
            }
            val currentPriority = _highPriorityByRoom.value[roomId].orEmpty()
            _highPriorityByRoom.update {
                it + (roomId to (currentPriority + setOf("sim_alex_rivera", "sim_samira_k")))
            }
            if (options.size >= 2 && secondaryOption.id != challengerOption.id) {
                setDeclined(roomId, secondaryOption.id, "sim_alex_rivera", declined = true)
            }
        }

        friends.forEachIndexed { idx, friend ->
            val targetOption = when {
                idx == 0 -> challengerOption
                idx == 1 -> challengerOption
                room.allowMultiple && options.size > 1 -> secondaryOption
                else -> if (options.size > 1 && idx == 2) challengerOption else secondaryOption
            }

            val vote = VoteEntity(
                id = "$roomId:${targetOption.id}:${friend.uid}",
                roomId = roomId,
                optionId = targetOption.id,
                voterUid = friend.uid,
                voterName = friend.name,
                voterBadgeColorIndex = friend.colorIdx % VoterBadgeColors.size,
                voterAvatarStyleIndex = friend.avatarIdx,
                votedAtEpochMs = System.currentTimeMillis()
            )

            if (room.allowMultiple) {
                dao.insertVote(vote)
            } else {
                dao.toggleSingleChoiceVote(
                    roomId = roomId,
                    optionId = targetOption.id,
                    voterUid = friend.uid,
                    alreadySelected = false,
                    newVote = vote
                )
            }
            if (idx < friends.lastIndex) {
                delay(420L)
            }
        }
        appContext?.let { MeldWidgetUpdater.refreshAllAsync(it) }
    }

    fun buildResultSummary(room: RoomDetailsUiModel): String {
        val winner = room.options.firstOrNull { it.isWinner } ?: room.options.firstOrNull()
        val lines = buildString {
            appendLine("Meld Decision: ${room.question}")
            if (room.isLocked && winner != null) {
                appendLine("Winner: ${winner.label} (${winner.voteCount} votes, ${winner.percentage}%)")
            }
            appendLine()
            room.options.forEachIndexed { idx, opt ->
                val marker = if (opt.isWinner) "★" else "${idx + 1}."
                appendLine("$marker ${opt.label} — ${opt.voteCount} (${opt.percentage}%)")
            }
            appendLine()
            append(room.shareUrl)
        }
        return lines
    }

    private fun applyPendingVotes(
        committedVotes: List<VoteEntity>,
        pendingVotes: List<PendingVoteEntity>
    ): List<VoteEntity> {
        if (pendingVotes.isEmpty()) return committedVotes
        val working = committedVotes.toMutableList()
        for (pending in pendingVotes) {
            if (pending.allowMultiple) {
                if (pending.isRemoval) {
                    working.removeAll { it.optionId == pending.optionId && it.voterUid == pending.voterUid }
                } else {
                    working.removeAll { it.optionId == pending.optionId && it.voterUid == pending.voterUid }
                    working.add(
                        VoteEntity(
                            id = "${pending.roomId}:${pending.optionId}:${pending.voterUid}",
                            roomId = pending.roomId,
                            optionId = pending.optionId,
                            voterUid = pending.voterUid,
                            voterName = pending.voterName,
                            voterBadgeColorIndex = pending.voterBadgeColorIndex,
                            voterAvatarStyleIndex = pending.voterAvatarStyleIndex,
                            votedAtEpochMs = pending.createdAtEpochMs
                        )
                    )
                }
            } else {
                working.removeAll { it.voterUid == pending.voterUid }
                if (!pending.isRemoval) {
                    working.add(
                        VoteEntity(
                            id = "${pending.roomId}:${pending.optionId}:${pending.voterUid}",
                            roomId = pending.roomId,
                            optionId = pending.optionId,
                            voterUid = pending.voterUid,
                            voterName = pending.voterName,
                            voterBadgeColorIndex = pending.voterBadgeColorIndex,
                            voterAvatarStyleIndex = pending.voterAvatarStyleIndex,
                            votedAtEpochMs = pending.createdAtEpochMs
                        )
                    )
                }
            }
        }
        return working
    }

    private fun mapRoomToUiModel(
        data: RoomWithOptionsAndVotes,
        pendingVotes: List<PendingVoteEntity>,
        identity: VoterIdentity,
        isCreator: Boolean,
        highPriorityUids: Set<String> = emptySet(),
        commitmentsByUid: Map<String, List<ParticipantCommitment>> = emptyMap(),
        declinedByOption: Map<String, Set<String>> = emptyMap(),
        anonymousLocks: List<AnonymousLockEntity> = emptyList(),
        anonymousLockAlert: String? = null,
        voiceComments: List<VoiceCommentEntity> = emptyList()
    ): RoomDetailsUiModel {
        val now = System.currentTimeMillis()
        val isExpiredByTimer = data.room.deadlineEpochMs != null && now >= data.room.deadlineEpochMs
        val effectiveLocked = data.room.isLocked || isExpiredByTimer

        val effectiveVotes = applyPendingVotes(data.votes, pendingVotes)
        val pendingOptionIds = pendingVotes.map { it.optionId }.toSet()
        val votesByOption = effectiveVotes.groupBy { it.optionId }
        val totalVotes = effectiveVotes.size

        val uniqueVoterCount = effectiveVotes.distinctBy { it.voterUid }.size
        val activeVoters = effectiveVotes
            .distinctBy { it.voterUid }
            .sortedByDescending { it.votedAtEpochMs }
            .mapIndexed { idx, vote ->
                val isPriority = highPriorityUids.contains(vote.voterUid)
                val isMe = vote.voterUid == identity.uid
                if (data.room.isAnonymous) {
                    val anonName = if (isMe) identity.effectiveAnonymousName else vote.voterName.ifEmpty { "Voter ${idx + 1}" }
                    val anonColor = if (isMe) identity.anonymousBadgeColorIndex else vote.voterBadgeColorIndex
                    val anonStyle = if (isMe) identity.anonymousAvatarStyleIndex else vote.voterAvatarStyleIndex
                    VoterChip(
                        voterUid = vote.voterUid,
                        voterName = anonName,
                        initials = initialsFor(anonName),
                        badgeColorIndex = anonColor,
                        avatarStyleIndex = anonStyle,
                        isHighPriority = isPriority
                    )
                } else {
                    VoterChip(
                        voterUid = vote.voterUid,
                        voterName = vote.voterName,
                        initials = initialsFor(vote.voterName),
                        badgeColorIndex = vote.voterBadgeColorIndex,
                        avatarStyleIndex = vote.voterAvatarStyleIndex,
                        isHighPriority = isPriority
                    )
                }
            }

        val sortedOptions = data.options.sortedWith(
            compareByDescending<OptionEntity> {
                if (data.room.isLocked && data.room.winnerOptionId == it.id) Int.MAX_VALUE
                else votesByOption[it.id]?.size ?: 0
            }.thenByDescending { votesByOption[it.id]?.size ?: 0 }
                .thenBy { it.sortOrder }
        )

        val maxVotes = sortedOptions.maxOfOrNull { votesByOption[it.id]?.size ?: 0 } ?: 0
        val leaderCount = if (maxVotes > 0) {
            sortedOptions.count { (votesByOption[it.id]?.size ?: 0) == maxVotes }
        } else {
            0
        }
        val hasTiedLeaders = !effectiveLocked && maxVotes > 0 && leaderCount > 1

        val baseUiOptions = sortedOptions.mapIndexed { index, opt ->
            val optVotes = votesByOption[opt.id].orEmpty()
            val count = optVotes.size
            val pct = if (totalVotes > 0) ((count * 100f) / totalVotes).roundToInt() else 0
            val fraction = if (totalVotes > 0) count.toFloat() / totalVotes.toFloat() else 0f
            val isVotedByMe = optVotes.any { it.voterUid == identity.uid }
            val isWinner = if (data.room.isLocked) {
                data.room.winnerOptionId == opt.id
            } else {
                isExpiredByTimer && maxVotes > 0 && index == 0
            }
            val isTied = !effectiveLocked && maxVotes > 0 && count == maxVotes && leaderCount > 1
            val isLeading = !effectiveLocked && maxVotes > 0 && index == 0 && count == maxVotes && leaderCount == 1
            val isUnanimous = uniqueVoterCount >= 2 && count == uniqueVoterCount

            val declinedUids = declinedByOption[opt.id].orEmpty()
            OptionUiModel(
                id = opt.id,
                label = opt.label,
                sortOrder = opt.sortOrder,
                voteCount = count,
                percentage = pct,
                fraction = fraction,
                isVotedByMe = isVotedByMe,
                isPendingSync = pendingOptionIds.contains(opt.id),
                isLeading = isLeading,
                isTiedLeader = isTied,
                isUnanimous = isUnanimous,
                isWinner = isWinner,
                isDeclinedByMe = declinedUids.contains(identity.uid),
                declinedCount = declinedUids.size,
                voters = if (data.room.isAnonymous) {
                    optVotes.mapIndexed { vIdx, v ->
                        val isMe = v.voterUid == identity.uid
                        val anonName = if (isMe) identity.effectiveAnonymousName else v.voterName.ifEmpty { "Voter ${vIdx + 1}" }
                        val anonColor = if (isMe) identity.anonymousBadgeColorIndex else v.voterBadgeColorIndex
                        val anonStyle = if (isMe) identity.anonymousAvatarStyleIndex else v.voterAvatarStyleIndex
                        VoterChip(
                            voterUid = v.voterUid,
                            voterName = anonName,
                            initials = initialsFor(anonName),
                            badgeColorIndex = anonColor,
                            avatarStyleIndex = anonStyle,
                            isHighPriority = highPriorityUids.contains(v.voterUid)
                        )
                    }
                } else {
                    optVotes.map { v ->
                        VoterChip(
                            voterUid = v.voterUid,
                            voterName = v.voterName,
                            initials = initialsFor(v.voterName),
                            badgeColorIndex = v.voterBadgeColorIndex,
                            avatarStyleIndex = v.voterAvatarStyleIndex,
                            isHighPriority = highPriorityUids.contains(v.voterUid)
                        )
                    }
                }
            )
        }

        val roomMode = runCatching { RoomMode.valueOf(data.room.mode) }.getOrDefault(RoomMode.QUICK_POLL)
        val conflictResult = ConflictDetector.analyze(
            options = baseUiOptions,
            activeVoters = activeVoters,
            mode = roomMode,
            highPriorityUids = highPriorityUids,
            commitmentsByUid = commitmentsByUid,
            isLocked = effectiveLocked,
            referenceNowMs = now
        )
        val uiOptions = baseUiOptions.map { opt ->
            val hint = conflictResult.conflictedOptionHints[opt.id]
            if (hint != null) opt.copy(hasPriorityConflict = true, conflictHint = hint) else opt
        }

        val insight = ConsensusInsightCalculator.calculate(
            options = uiOptions,
            mode = roomMode,
            totalUniqueVoters = uniqueVoterCount,
            isLocked = effectiveLocked,
            winnerOptionId = data.room.winnerOptionId
        )

        val maskedIp = if (data.room.isAnonymous) {
            anonymousLocks.firstOrNull()?.maskedIpLabel
                ?: appContext?.let { AnonymousSpamGuard.captureCurrentNetwork(it).maskedIpLabel }
        } else {
            null
        }

        val uiVoiceComments = voiceComments.map { comment ->
            val optLabel = data.options.firstOrNull { it.id == comment.optionId }?.label
            val isMine = comment.voterUid == identity.uid
            val displayName = if (data.room.isAnonymous) {
                if (isMine) identity.effectiveAnonymousName else comment.voterName
            } else {
                comment.voterName
            }
            val color = if (data.room.isAnonymous) {
                if (isMine) identity.anonymousBadgeColorIndex else comment.voterBadgeColorIndex
            } else {
                comment.voterBadgeColorIndex
            }
            val style = if (data.room.isAnonymous) {
                if (isMine) identity.anonymousAvatarStyleIndex else comment.voterAvatarStyleIndex
            } else {
                comment.voterAvatarStyleIndex
            }
            VoiceCommentUiModel(
                id = comment.id,
                roomId = comment.roomId,
                optionId = comment.optionId,
                optionLabel = optLabel,
                voterUid = comment.voterUid,
                voterName = displayName,
                voterBadgeColorIndex = color,
                voterAvatarStyleIndex = style,
                transcript = comment.transcript,
                durationSeconds = comment.durationSeconds,
                createdAtEpochMs = comment.createdAtEpochMs,
                isMyComment = isMine
            )
        }

        return RoomDetailsUiModel(
            id = data.room.id,
            question = data.room.question,
            mode = roomMode,
            allowMultiple = data.room.allowMultiple,
            allowFriendsToAdd = data.room.allowFriendsToAdd,
            isAnonymous = data.room.isAnonymous,
            deadlineEpochMs = data.room.deadlineEpochMs,
            isCreator = isCreator,
            isLocked = effectiveLocked,
            isExpiredByTimer = isExpiredByTimer,
            winnerOptionId = data.room.winnerOptionId,
            createdAtEpochMs = data.room.createdAtEpochMs,
            expiresAtEpochMs = data.room.expiresAtEpochMs,
            options = uiOptions,
            totalVotes = totalVotes,
            activeVoters = activeVoters,
            queuedVoteCount = pendingVotes.size,
            hasTiedLeaders = hasTiedLeaders,
            decisionInsight = insight,
            highPriorityUids = highPriorityUids,
            conflictAlert = conflictResult.alert,
            maskedNetworkIp = maskedIp,
            isIpVoteLocked = data.room.isAnonymous && anonymousLocks.isNotEmpty(),
            anonymousLockAlert = anonymousLockAlert,
            voiceComments = uiVoiceComments
        )
    }

    suspend fun addVoiceComment(
        roomId: String,
        optionId: String?,
        transcript: String,
        durationSeconds: Int
    ) {
        val cleanTranscript = sanitizeMeldInput(transcript, maxLength = 300)
        if (cleanTranscript.isEmpty()) return
        val identity = identityStore.ensureIdentity()
        val room = dao.getRoomOnce(roomId) ?: return
        val timerExpired = room.deadlineEpochMs != null && System.currentTimeMillis() >= room.deadlineEpochMs
        if (room.isLocked || timerExpired) return
        if (dao.getVoiceCommentCountOnce(roomId) >= 50) return

        val validOptionId = optionId?.takeIf { targetId ->
            dao.getOptionsOnce(roomId).any { it.id == targetId }
        }

        val name = if (room.isAnonymous) identity.effectiveAnonymousName else identity.effectiveName
        val color = if (room.isAnonymous) identity.anonymousBadgeColorIndex else identity.badgeColorIndex
        val style = if (room.isAnonymous) identity.anonymousAvatarStyleIndex else identity.avatarStyleIndex

        val comment = VoiceCommentEntity(
            id = UUID.randomUUID().toString(),
            roomId = roomId,
            optionId = validOptionId,
            voterUid = identity.uid,
            voterName = name,
            voterBadgeColorIndex = color,
            voterAvatarStyleIndex = style,
            transcript = cleanTranscript,
            durationSeconds = durationSeconds.coerceIn(1, 60),
            createdAtEpochMs = System.currentTimeMillis()
        )
        dao.insertVoiceComment(comment)
        remoteSync.pushVoiceComment(comment)
    }

    suspend fun deleteVoiceComment(commentId: String) {
        val existing = dao.getVoiceCommentByIdOnce(commentId) ?: return
        val identity = identityStore.ensureIdentity()
        if (existing.voterUid != identity.uid) return
        dao.deleteVoiceComment(commentId)
        remoteSync.deleteVoiceComment(existing.roomId, commentId)
    }

    private fun generateRoomCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(6) {
            repeat(6) {
                append(alphabet[secureRandom.nextInt(alphabet.length)])
            }
        }
    }

    private data class FriendSpec(
        val uid: String,
        val name: String,
        val colorIdx: Int,
        val avatarIdx: Int
    )

    private data class RoomObserveExtras(
        val priorityMap: Map<String, Set<String>>,
        val commitments: Map<String, List<ParticipantCommitment>>,
        val declinedMap: Map<String, Map<String, Set<String>>>,
        val alertMap: Map<String, String?>
    )
}
