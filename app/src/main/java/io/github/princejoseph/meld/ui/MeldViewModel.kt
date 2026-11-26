package io.github.princejoseph.meld.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.princejoseph.meld.data.ConnectivityObserver
import io.github.princejoseph.meld.data.DeviceCalendarReader
import io.github.princejoseph.meld.data.FreeSlotCalculator
import io.github.princejoseph.meld.data.IcsCalendarParser
import io.github.princejoseph.meld.data.IdentityStore
import io.github.princejoseph.meld.data.MeldDatabase
import io.github.princejoseph.meld.data.MeldRepository
import io.github.princejoseph.meld.data.RecentRoomSummary
import io.github.princejoseph.meld.data.RoomDetailsUiModel
import io.github.princejoseph.meld.data.RoomMode
import io.github.princejoseph.meld.data.SuggestedTimeSlot
import io.github.princejoseph.meld.data.SyncStatus
import io.github.princejoseph.meld.data.VoterIdentity
import io.github.princejoseph.meld.notifications.MeldNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class DraftOption(
    val id: String = UUID.randomUUID().toString(),
    val text: String = ""
)

data class CreateRoomUiState(
    val mode: RoomMode = RoomMode.QUICK_POLL,
    val question: String = "",
    val options: List<DraftOption> = listOf(DraftOption(), DraftOption(), DraftOption()),
    val allowMultiple: Boolean = false,
    val allowFriendsToAdd: Boolean = false,
    val isAnonymous: Boolean = false,
    val deadlineMinutes: Int? = null,
    val newlyAddedOptionId: String? = null,
    val validationMessage: String? = null,
    val joinCodeError: String? = null,
    val isCreating: Boolean = false
)

enum class CalendarSyncSource {
    NONE,
    DEVICE_CALENDAR,
    ICS_FILE
}

data class CalendarSyncUiState(
    val isLoading: Boolean = false,
    val source: CalendarSyncSource = CalendarSyncSource.NONE,
    val suggestedSlots: List<SuggestedTimeSlot> = emptyList(),
    val selectedSlotIds: Set<String> = emptySet(),
    val statusMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MeldViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MeldRepository
    private val identityStore = IdentityStore(application)

    init {
        MeldNotificationManager.ensureChannels(application)
        val db = MeldDatabase.getInstance(application)
        val connectivityObserver = ConnectivityObserver(application)
        repository = MeldRepository(db.meldDao(), identityStore, connectivityObserver, application)
        viewModelScope.launch {
            val id = repository.initIdentity()
            if (DeviceCalendarReader.hasCalendarPermission(application)) {
                val now = System.currentTimeMillis()
                val sevenDaysLater = now + 7L * 24L * 60L * 60L * 1000L
                val busy = withContext(Dispatchers.IO) {
                    DeviceCalendarReader.readBusyIntervals(application, now, sevenDaysLater)
                }
                if (busy.isNotEmpty()) {
                    repository.setParticipantCommitments(id.uid, id.effectiveName, busy)
                }
            }
        }

        // Background sync watcher: flush pending outbox votes automatically on reconnect
        viewModelScope.launch {
            combine(
                repository.isOnlineFlow,
                repository.allPendingCountFlow
            ) { online, pendingCount ->
                online to pendingCount
            }.collectLatest { (online, pendingCount) ->
                if (online && pendingCount > 0) {
                    repository.flushPendingVotes()
                }
            }
        }

        // Auto-close any unlocked poll whose deadline has expired
        viewModelScope.launch {
            repository.recentRoomsFlow.collectLatest { rooms ->
                rooms.filter { !it.isLocked && it.isExpired }.forEach { expired ->
                    repository.finalizeExpiredRoomIfNeeded(expired.id)
                }
            }
        }
    }

    val identity: StateFlow<VoterIdentity> = repository.identityFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = VoterIdentity(uid = "", displayName = "", badgeColorIndex = 0, avatarStyleIndex = 0)
        )

    val darkThemeOverride: StateFlow<Boolean?> = repository.darkThemeOverrideFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val selectedLanguage: StateFlow<String?> = identityStore.selectedLanguageFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val hapticsEnabled: StateFlow<Boolean> = identityStore.hapticsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val hasCompletedOnboarding: StateFlow<Boolean?> = identityStore.hasCompletedOnboardingFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun completeOnboarding() {
        viewModelScope.launch {
            identityStore.setOnboardingCompleted(true)
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            identityStore.setLanguage(lang)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            identityStore.setHapticsEnabled(enabled)
        }
    }

    fun updateAnonymousBadge(avatarStyleIndex: Int, badgeColorIndex: Int, maskName: String) {
        viewModelScope.launch {
            identityStore.setAnonymousBadge(avatarStyleIndex, badgeColorIndex, maskName)
            _logoPulseCount.update { it + 1 }
        }
    }

    fun addVoiceComment(
        optionId: String?,
        transcript: String,
        durationSeconds: Int,
        audioFilePath: String? = null
    ) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.addVoiceComment(roomId, optionId, transcript, durationSeconds, audioFilePath)
            _logoPulseCount.update { it + 1 }
        }
    }

    fun deleteVoiceComment(commentId: String) {
        viewModelScope.launch {
            repository.deleteVoiceComment(commentId)
        }
    }

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatusFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncStatus.LIVE
        )

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val simulatedOffline: StateFlow<Boolean> = repository.simulatedOffline

    val recentRooms: StateFlow<List<RecentRoomSummary>> = repository.recentRoomsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _createState = MutableStateFlow(CreateRoomUiState())
    val createState: StateFlow<CreateRoomUiState> = _createState.asStateFlow()

    private val _calendarSyncState = MutableStateFlow(CalendarSyncUiState())
    val calendarSyncState: StateFlow<CalendarSyncUiState> = _calendarSyncState.asStateFlow()

    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

    val activeRoom: StateFlow<RoomDetailsUiModel?> = _activeRoomId
        .flatMapLatest { roomId ->
            if (roomId == null) flowOf(null) else repository.observeRoom(roomId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _logoPulseCount = MutableStateFlow(0)
    val logoPulseCount: StateFlow<Int> = _logoPulseCount.asStateFlow()

    private val _celebrationBurstCount = MutableStateFlow(0)
    val celebrationBurstCount: StateFlow<Int> = _celebrationBurstCount.asStateFlow()

    private val _isSimulatingFriends = MutableStateFlow(false)
    val isSimulatingFriends: StateFlow<Boolean> = _isSimulatingFriends.asStateFlow()

    fun toggleTheme(currentlyDark: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(!currentlyDark)
        }
    }

    fun toggleSimulatedOffline() {
        repository.setSimulatedOffline(!simulatedOffline.value)
    }

    fun triggerLogoPulse() {
        _logoPulseCount.update { it + 1 }
    }

    fun setMode(mode: RoomMode) {
        _createState.update { state ->
            state.copy(mode = mode, validationMessage = null)
        }
    }

    fun applyQuickTemplate(mode: RoomMode, question: String, options: List<String>) {
        _createState.update {
            it.copy(
                mode = mode,
                question = question,
                options = options.map { label -> DraftOption(text = label) },
                validationMessage = null
            )
        }
        _logoPulseCount.update { it + 1 }
    }

    fun updateQuestion(text: String) {
        _createState.update { it.copy(question = text, validationMessage = null) }
    }

    fun updateOption(id: String, text: String) {
        _createState.update { state ->
            state.copy(
                options = state.options.map { opt ->
                    if (opt.id == id) opt.copy(text = text) else opt
                },
                validationMessage = null
            )
        }
    }

    fun addOptionAfter(afterId: String? = null) {
        val newOption = DraftOption()
        _createState.update { state ->
            val current = state.options.toMutableList()
            if (current.size >= 12) return@update state
            val idx = if (afterId == null) -1 else current.indexOfFirst { it.id == afterId }
            if (idx >= 0) {
                current.add(idx + 1, newOption)
            } else {
                current.add(newOption)
            }
            state.copy(
                options = current,
                newlyAddedOptionId = newOption.id,
                validationMessage = null
            )
        }
    }

    fun consumeNewlyAddedOptionFocus() {
        _createState.update { it.copy(newlyAddedOptionId = null) }
    }

    fun removeOption(id: String) {
        _createState.update { state ->
            if (state.options.size <= 2) return@update state
            state.copy(options = state.options.filterNot { it.id == id })
        }
    }

    fun moveOption(fromIndex: Int, toIndex: Int) {
        _createState.update { state ->
            val list = state.options.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            state.copy(options = list)
        }
    }

    fun setAllowMultiple(enabled: Boolean) {
        _createState.update { it.copy(allowMultiple = enabled) }
    }

    fun setAllowFriendsToAdd(enabled: Boolean) {
        _createState.update { it.copy(allowFriendsToAdd = enabled) }
    }

    fun setIsAnonymous(enabled: Boolean) {
        _createState.update { it.copy(isAnonymous = enabled) }
    }

    fun setDeadlineMinutes(minutes: Int?) {
        _createState.update { it.copy(deadlineMinutes = minutes) }
    }

    // Calendar Sync Actions
    fun syncFromDeviceCalendar() {
        _calendarSyncState.update { it.copy(isLoading = true, statusMessage = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val fourDaysLater = now + 4L * 24L * 60L * 60L * 1000L
            val sevenDaysLater = now + 7L * 24L * 60L * 60L * 1000L
            val (busyForConflicts, suggestions) = withContext(Dispatchers.IO) {
                val busy = DeviceCalendarReader.readBusyIntervals(
                    context = getApplication(),
                    startWindowMs = now,
                    endWindowMs = sevenDaysLater
                )
                val nearbyBusy = busy.filter { it.startMs < fourDaysLater }
                busy to FreeSlotCalculator.suggestFreeBlocks(busyIntervals = nearbyBusy, nowMs = now)
            }
            val currentIdentity = repository.initIdentity()
            repository.setParticipantCommitments(currentIdentity.uid, currentIdentity.effectiveName, busyForConflicts)
            _calendarSyncState.value = CalendarSyncUiState(
                isLoading = false,
                source = CalendarSyncSource.DEVICE_CALENDAR,
                suggestedSlots = suggestions,
                selectedSlotIds = suggestions.take(3).map { it.id }.toSet(),
                statusMessage = null
            )
        }
    }

    fun syncFromIcsUri(uri: Uri, errorText: String) {
        _calendarSyncState.update { it.copy(isLoading = true, statusMessage = null) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val raw = withContext(Dispatchers.IO) {
                DeviceCalendarReader.readIcsUri(getApplication(), uri)
            }
            if (raw.isNullOrBlank()) {
                _calendarSyncState.update {
                    it.copy(isLoading = false, statusMessage = errorText)
                }
                return@launch
            }
            val (busy, suggestions) = withContext(Dispatchers.Default) {
                val parsedBusy = IcsCalendarParser.parse(raw)
                parsedBusy to FreeSlotCalculator.suggestFreeBlocks(busyIntervals = parsedBusy, nowMs = now)
            }
            val currentIdentity = repository.initIdentity()
            repository.setParticipantCommitments(currentIdentity.uid, currentIdentity.effectiveName, busy)
            _calendarSyncState.value = CalendarSyncUiState(
                isLoading = false,
                source = CalendarSyncSource.ICS_FILE,
                suggestedSlots = suggestions,
                selectedSlotIds = suggestions.take(3).map { it.id }.toSet(),
                statusMessage = null
            )
        }
    }

    fun toggleSuggestedSlot(slotId: String) {
        _calendarSyncState.update { state ->
            val next = state.selectedSlotIds.toMutableSet()
            if (next.contains(slotId)) next.remove(slotId) else next.add(slotId)
            state.copy(selectedSlotIds = next)
        }
    }

    fun setCalendarStatusMessage(message: String?) {
        _calendarSyncState.update { it.copy(statusMessage = message) }
    }

    fun applySelectedCalendarSlots() {
        val syncState = _calendarSyncState.value
        val pickedLabels = syncState.suggestedSlots
            .filter { syncState.selectedSlotIds.contains(it.id) }
            .map { it.label }
        if (pickedLabels.isEmpty()) return

        _createState.update { state ->
            val existingNonEmpty = state.options.filter { it.text.isNotBlank() }
            val merged = (existingNonEmpty + pickedLabels.map { DraftOption(text = it) })
                .distinctBy { it.text }
                .take(12)
            val padded = if (merged.size < 2) {
                merged + List(2 - merged.size) { DraftOption() }
            } else {
                merged
            }
            state.copy(
                mode = RoomMode.TIME_SLOTS,
                options = padded,
                validationMessage = null
            )
        }
        _logoPulseCount.update { it + 1 }
    }

    fun createRoom(
        emptyQuestionError: String,
        minOptionsError: String
    ) {
        val snapshot = _createState.value
        if (snapshot.isCreating) return

        val question = snapshot.question.trim()
        if (question.isEmpty()) {
            _createState.update { it.copy(validationMessage = emptyQuestionError) }
            return
        }

        val filledOptions = snapshot.options.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (filledOptions.size < 2) {
            _createState.update { it.copy(validationMessage = minOptionsError) }
            return
        }

        _createState.update { it.copy(isCreating = true, validationMessage = null) }
        viewModelScope.launch {
            val roomId = repository.createRoom(
                question = question,
                mode = snapshot.mode,
                optionLabels = filledOptions,
                allowMultiple = snapshot.allowMultiple,
                allowFriendsToAdd = snapshot.allowFriendsToAdd,
                isAnonymous = snapshot.isAnonymous,
                deadlineMinutes = snapshot.deadlineMinutes
            )
            _activeRoomId.value = roomId
            _logoPulseCount.update { it + 1 }
            _createState.value = CreateRoomUiState(mode = snapshot.mode)
        }
    }

    fun joinRoomByCode(rawCode: String, notFoundMessage: (String) -> String) {
        val cleaned = rawCode.trim().removePrefix("#").uppercase()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            if (repository.roomExists(cleaned)) {
                _createState.update { it.copy(joinCodeError = null) }
                _activeRoomId.value = cleaned
            } else {
                _createState.update { it.copy(joinCodeError = notFoundMessage(cleaned)) }
            }
        }
    }

    fun clearJoinCodeError() {
        _createState.update { it.copy(joinCodeError = null) }
    }

    fun openRoom(roomId: String) {
        _activeRoomId.value = roomId.uppercase()
    }

    fun closeRoom() {
        _activeRoomId.value = null
    }

    fun updateVoterProfile(name: String, avatarStyleIndex: Int, badgeColorIndex: Int) {
        viewModelScope.launch {
            repository.updateVoterProfile(name, avatarStyleIndex, badgeColorIndex)
            _logoPulseCount.update { it + 1 }
        }
    }

    fun toggleVote(optionId: String) {
        val roomId = _activeRoomId.value ?: return
        val currentlyOnline = isOnline.value
        viewModelScope.launch {
            repository.toggleVote(roomId, optionId, currentlyOnline)
            _logoPulseCount.update { it + 1 }
            _celebrationBurstCount.update { it + 1 }
        }
    }

    fun castSwipeVote(optionId: String, voteYes: Boolean) {
        val roomId = _activeRoomId.value ?: return
        val currentlyOnline = isOnline.value
        viewModelScope.launch {
            repository.castDirectionalVote(roomId, optionId, voteYes, currentlyOnline)
            _logoPulseCount.update { it + 1 }
            if (voteYes) {
                _celebrationBurstCount.update { it + 1 }
            }
        }
    }

    fun deleteHistoryRoom(roomId: String) {
        viewModelScope.launch {
            repository.deleteRoom(roomId)
            if (_activeRoomId.value == roomId) {
                _activeRoomId.value = null
            }
        }
    }

    fun togglePriorityParticipant(voterUid: String) {
        val roomId = _activeRoomId.value ?: return
        repository.togglePriorityParticipant(roomId, voterUid)
    }

    fun addOptionToActiveRoom(label: String) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.addOptionToRoom(roomId, label)
        }
    }

    fun crownWinner(explicitOptionId: String? = null) {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.crownWinner(roomId, explicitOptionId)
            _logoPulseCount.update { it + 1 }
            _celebrationBurstCount.update { it + 1 }
        }
    }

    fun createRunoffFromTied() {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            val runoffId = repository.createRunoffRoom(roomId) ?: return@launch
            _activeRoomId.value = runoffId
            _logoPulseCount.update { it + 1 }
        }
    }

    fun reopenRoom() {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.reopenRoom(roomId)
        }
    }

    fun finalizeExpiredRoom() {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            val closedNow = repository.finalizeExpiredRoomIfNeeded(roomId)
            if (closedNow) {
                _logoPulseCount.update { it + 1 }
                _celebrationBurstCount.update { it + 1 }
            }
        }
    }

    fun resetVotes() {
        val roomId = _activeRoomId.value ?: return
        viewModelScope.launch {
            repository.resetVotes(roomId)
        }
    }

    fun simulateThreeFriends() {
        val roomId = _activeRoomId.value ?: return
        if (_isSimulatingFriends.value) return
        viewModelScope.launch {
            _isSimulatingFriends.value = true
            try {
                repository.simulateThreeFriendsVoting(roomId)
                _logoPulseCount.update { it + 1 }
                _celebrationBurstCount.update { it + 1 }
            } finally {
                _isSimulatingFriends.value = false
            }
        }
    }

    fun clearAnonymousLockAlert() {
        val roomId = _activeRoomId.value ?: return
        repository.clearAnonymousLockAlert(roomId)
    }

    fun buildResultSummary(room: RoomDetailsUiModel): String {
        return repository.buildResultSummary(room)
    }
}
