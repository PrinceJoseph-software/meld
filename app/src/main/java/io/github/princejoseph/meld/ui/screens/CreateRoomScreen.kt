package io.github.princejoseph.meld.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.data.RecentRoomSummary
import io.github.princejoseph.meld.data.RoomMode
import io.github.princejoseph.meld.ui.CalendarSyncUiState
import io.github.princejoseph.meld.ui.CreateRoomUiState
import io.github.princejoseph.meld.ui.DraftOption
import io.github.princejoseph.meld.ui.components.CalendarSyncSheet
import io.github.princejoseph.meld.ui.components.MeldSpringSwitch
import io.github.princejoseph.meld.ui.components.draftingDotGrid
import io.github.princejoseph.meld.ui.components.performTactileConfirm
import io.github.princejoseph.meld.ui.components.performTactileTick
import io.github.princejoseph.meld.ui.theme.InstrumentSerifFamily
import io.github.princejoseph.meld.ui.theme.JetBrainsMonoFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.MonoTabularTextStyle
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    state: CreateRoomUiState,
    calendarSyncState: CalendarSyncUiState,
    recentRooms: List<RecentRoomSummary>,
    onModeChange: (RoomMode) -> Unit,
    onApplyQuickTemplate: (RoomMode, String, List<String>) -> Unit,
    onQuestionChange: (String) -> Unit,
    onQuestionVoiceChange: (String?, Int) -> Unit = { _, _ -> },
    onOptionChange: (String, String) -> Unit,
    onOptionTimePicked: (optionId: String, label: String, startMs: Long) -> Unit =
        { id, label, _ -> onOptionChange(id, label) },
    onAddOptionAfter: (String?) -> Unit,
    onConsumeOptionFocus: () -> Unit,
    onRemoveOption: (String) -> Unit,
    onMoveOption: (Int, Int) -> Unit,
    onAllowMultipleChange: (Boolean) -> Unit,
    onAllowFriendsChange: (Boolean) -> Unit,
    onAnonymousChange: (Boolean) -> Unit,
    onDeadlineMinutesChange: (Int?) -> Unit,
    onSyncDeviceCalendar: () -> Unit,
    onImportIcsUri: (Uri, String) -> Unit,
    onCalendarPermissionDenied: (String) -> Unit,
    onToggleCalendarSlot: (String) -> Unit,
    onApplyCalendarSlots: () -> Unit,
    onCreateRoom: (emptyQuestionErr: String, minOptionsErr: String) -> Unit,
    onJoinByCode: (code: String, notFoundFormat: (String) -> String) -> Unit,
    onClearJoinCodeError: () -> Unit,
    onOpenRecentRoom: (String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val scrollState = rememberScrollState()

    // Deadline alerts need POST_NOTIFICATIONS on Android 13+. Ask in context, the first time
    // the user actually sets a deadline; a denial just means no alert (the poll still closes).
    val deadlineContext = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* no-op: notifications are best-effort */ }
    val setDeadlineMinutes: (Int?) -> Unit = { minutes ->
        if (minutes != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                deadlineContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onDeadlineMinutesChange(minutes)
    }

    val emptyQuestionError = stringResource(R.string.error_empty_question)
    val minOptionsError = stringResource(R.string.error_min_options)

    var showCalendarSyncSheet by remember { mutableStateOf(false) }
    var timePickerTargetOptionId by remember { mutableStateOf<String?>(null) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }
    var showCustomDeadlineDialog by remember { mutableStateOf(false) }
    var joinCodeText by remember { mutableStateOf("") }

    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    LaunchedEffect(state.newlyAddedOptionId) {
        val targetId = state.newlyAddedOptionId ?: return@LaunchedEffect
        focusRequesters[targetId]?.requestFocus()
        onConsumeOptionFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .draftingDotGrid(colors.dotGrid)
            .imePadding()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Centered Creation Card — renders immediately on frame 1 (zero loading delay)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
                    .animateContentSize(spring(stiffness = 420f, dampingRatio = 0.80f))
                    .padding(20.dp)
            ) {
                SegmentedModeSwitcher(
                    selectedMode = state.mode,
                    onModeSelected = {
                        view.performTactileTick()
                        onModeChange(it)
                    }
                )

                Spacer(modifier = Modifier.height(22.dp))

                val context = LocalContext.current
                var isRecordingQuestion by remember { mutableStateOf(false) }
                var questionRecSeconds by remember { mutableIntStateOf(0) }
                var questionRecPath by remember { mutableStateOf<String?>(null) }
                var questionRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
                var questionPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
                var isPlayingQuestionVoice by remember { mutableStateOf(false) }

                DisposableEffect(Unit) {
                    onDispose {
                        runCatching { questionRecorder?.stop() }
                        runCatching { questionRecorder?.release() }
                        runCatching { questionPlayer?.stop() }
                        runCatching { questionPlayer?.release() }
                    }
                }

                fun startQuestionRecording() {
                    runCatching { questionPlayer?.stop() }
                    runCatching { questionPlayer?.release() }
                    questionPlayer = null
                    isPlayingQuestionVoice = false

                    val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
                    val outFile = File(dir, "q_${UUID.randomUUID()}.m4a")
                    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        MediaRecorder(context)
                    } else {
                        @Suppress("DEPRECATION")
                        MediaRecorder()
                    }
                    val ok = runCatching {
                        rec.setAudioSource(MediaRecorder.AudioSource.MIC)
                        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        rec.setAudioEncodingBitRate(32_000)
                        rec.setAudioSamplingRate(16_000)
                        rec.setOutputFile(outFile.absolutePath)
                        rec.prepare()
                        rec.start()
                    }.isSuccess
                    if (ok) {
                        questionRecorder = rec
                        questionRecPath = outFile.absolutePath
                        questionRecSeconds = 0
                        isRecordingQuestion = true
                    } else {
                        runCatching { rec.release() }
                        runCatching { outFile.delete() }
                        isRecordingQuestion = false
                    }
                }

                fun stopQuestionRecording() {
                    val rec = questionRecorder
                    val path = questionRecPath
                    val dur = questionRecSeconds.coerceIn(1, 60)
                    questionRecorder = null
                    questionRecPath = null
                    isRecordingQuestion = false

                    val stopped = runCatching { rec?.stop() }.isSuccess
                    runCatching { rec?.release() }
                    if (stopped && path != null && File(path).exists() && File(path).length() > 0L) {
                        state.questionVoicePath?.let { old -> runCatching { File(old).delete() } }
                        view.performTactileConfirm()
                        onQuestionVoiceChange(path, dur)
                    } else if (path != null) {
                        runCatching { File(path).delete() }
                    }
                }

                LaunchedEffect(isRecordingQuestion) {
                    if (isRecordingQuestion) {
                        while (isRecordingQuestion && questionRecSeconds < 60) {
                            delay(1000L)
                            questionRecSeconds += 1
                        }
                        if (isRecordingQuestion && questionRecSeconds >= 60) {
                            stopQuestionRecording()
                        }
                    }
                }

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) startQuestionRecording()
                }

                val questionPlaceholder = stringResource(
                    if (state.mode == RoomMode.QUICK_POLL) R.string.placeholder_question_poll
                    else R.string.placeholder_question_time
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.question.isEmpty()) {
                            Text(
                                text = questionPlaceholder,
                                fontFamily = InstrumentSerifFamily,
                                fontSize = 28.sp,
                                lineHeight = 33.sp,
                                color = colors.inkSecondary.copy(alpha = 0.55f)
                            )
                        }
                        BasicTextField(
                            value = state.question,
                            onValueChange = onQuestionChange,
                            textStyle = TextStyle(
                                fontFamily = InstrumentSerifFamily,
                                fontSize = 28.sp,
                                lineHeight = 33.sp,
                                color = colors.inkPrimary
                            ),
                            cursorBrush = SolidColor(colors.vermilion),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("question_input")
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            view.performTactileTick()
                            if (isRecordingQuestion) {
                                stopQuestionRecording()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    startQuestionRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingQuestion) colors.vermilion else colors.vermilion.copy(alpha = 0.1f))
                            .testTag("voice_dictate_button")
                    ) {
                        Icon(
                            imageVector = if (isRecordingQuestion) Icons.Outlined.Stop else Icons.Outlined.Mic,
                            contentDescription = if (isRecordingQuestion) "Stop Recording Question" else "Record Voice Question",
                            tint = if (isRecordingQuestion) Color.White else colors.vermilion,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Live Recording or Saved Voice Question Preview (MediaPlayer)
                AnimatedVisibility(visible = isRecordingQuestion || !state.questionVoicePath.isNullOrBlank()) {
                    val voicePath = state.questionVoicePath
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.canvas)
                            .border(1.dp, colors.vermilion.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isRecordingQuestion) {
                            Text(
                                text = "Recording voice question • 0:${questionRecSeconds.toString().padStart(2, '0')}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.vermilion
                            )
                            TextButton(
                                onClick = { stopQuestionRecording() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold, color = colors.vermilion)
                            }
                        } else if (!voicePath.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isPlayingQuestionVoice) {
                                            runCatching { questionPlayer?.stop() }
                                            runCatching { questionPlayer?.release() }
                                            questionPlayer = null
                                            isPlayingQuestionVoice = false
                                        } else {
                                            val player = MediaPlayer()
                                            val started = runCatching {
                                                player.setDataSource(voicePath)
                                                player.prepare()
                                                player.setOnCompletionListener {
                                                    isPlayingQuestionVoice = false
                                                    runCatching { player.release() }
                                                    if (questionPlayer === player) questionPlayer = null
                                                }
                                                player.start()
                                            }.isSuccess
                                            if (started) {
                                                questionPlayer = player
                                                isPlayingQuestionVoice = true
                                            } else {
                                                runCatching { player.release() }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(colors.vermilion)
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingQuestionVoice) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                                        contentDescription = if (isPlayingQuestionVoice) "Stop" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "Voice Question (0:${state.questionVoiceDurationSec.toString().padStart(2, '0')})",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkPrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    runCatching { questionPlayer?.stop() }
                                    runCatching { questionPlayer?.release() }
                                    questionPlayer = null
                                    isPlayingQuestionVoice = false
                                    runCatching { File(voicePath).delete() }
                                    onQuestionVoiceChange(null, 0)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Remove voice question",
                                    tint = colors.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Calendar Sync button in Time Slots mode
                AnimatedVisibility(
                    visible = state.mode == RoomMode.TIME_SLOTS,
                    enter = expandVertically(spring(stiffness = 420f, dampingRatio = 0.78f)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        OutlinedButton(
                            onClick = {
                                view.performTactileTick()
                                showCalendarSyncSheet = true
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("calendar_sync_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.vermilion.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colors.vermilion.copy(alpha = 0.07f),
                                contentColor = colors.vermilion
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_calendar_sync),
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(12.dp))

                val rowHeightPx = with(LocalDensity.current) { 54.dp.toPx() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.options.forEachIndexed { index, option ->
                        val requester = focusRequesters.getOrPut(option.id) { FocusRequester() }
                        val placeholder = when (state.mode) {
                            RoomMode.QUICK_POLL -> stringResource(R.string.placeholder_option_poll, index + 1)
                            RoomMode.TIME_SLOTS -> when (index) {
                                0 -> stringResource(R.string.placeholder_option_time_1)
                                1 -> stringResource(R.string.placeholder_option_time_2)
                                2 -> stringResource(R.string.placeholder_option_time_3)
                                else -> stringResource(R.string.placeholder_option_time_n, index + 1)
                            }
                        }

                        DraftOptionRow(
                            option = option,
                            index = index,
                            totalCount = state.options.size,
                            placeholder = placeholder,
                            showTimePickerIcon = state.mode == RoomMode.TIME_SLOTS,
                            focusRequester = requester,
                            rowHeightPx = rowHeightPx,
                            onValueChange = { onOptionChange(option.id, it) },
                            onImeAction = {
                                view.performTactileTick()
                                if (index == state.options.lastIndex) {
                                    onAddOptionAfter(option.id)
                                } else {
                                    val nextId = state.options[index + 1].id
                                    focusRequesters[nextId]?.requestFocus()
                                }
                            },
                            onPickTimeClick = {
                                view.performTactileTick()
                                timePickerTargetOptionId = option.id
                            },
                            onRemoveClick = {
                                view.performTactileTick()
                                onRemoveOption(option.id)
                            },
                            onMoveBySteps = { stepDelta ->
                                val targetIndex = (index + stepDelta).coerceIn(0, state.options.lastIndex)
                                if (targetIndex != index) {
                                    view.performTactileTick()
                                    onMoveOption(index, targetIndex)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            view.performTactileTick()
                            onAddOptionAfter(state.options.lastOrNull()?.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("add_option_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = colors.inkSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_add_option),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.inkSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                Spacer(modifier = Modifier.height(6.dp))

                // Compact Settings Strip with Tactile Switches + Decision Timer
                SettingToggleRow(
                    label = stringResource(R.string.setting_multiple_picks),
                    checked = state.allowMultiple,
                    onCheckedChange = onAllowMultipleChange,
                    testTag = "setting_multiple_switch"
                )
                SettingToggleRow(
                    label = stringResource(R.string.setting_friends_add),
                    checked = state.allowFriendsToAdd,
                    onCheckedChange = onAllowFriendsChange,
                    testTag = "setting_friends_add_switch"
                )
                SettingToggleRow(
                    label = stringResource(R.string.setting_anonymous_votes),
                    checked = state.isAnonymous,
                    onCheckedChange = onAnonymousChange,
                    testTag = "setting_anonymous_switch"
                )

                // Optional Poll Deadline Strip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Timer,
                                contentDescription = null,
                                tint = if (state.deadlineMinutes != null) colors.vermilion else colors.inkSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.setting_deadline_label),
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = colors.inkPrimary
                            )
                        }

                        AnimatedVisibility(
                            visible = state.deadlineMinutes != null,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            val mins = state.deadlineMinutes ?: 0
                            val targetMs = System.currentTimeMillis() + mins * 60_000L
                            val fmtPattern = if (mins >= 24 * 60) "MMM d, h:mm a" else "h:mm a"
                            val closeTimeText = remember(mins) {
                                SimpleDateFormat(fmtPattern, Locale.getDefault()).format(java.util.Date(targetMs))
                            }
                            Text(
                                text = stringResource(R.string.deadline_preview, closeTimeText),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = colors.vermilion
                            )
                        }
                    }

                    val presetValues = setOf(null, 1, 5, 15, 60, 1440)
                    val isCustomSelected = state.deadlineMinutes != null && !presetValues.contains(state.deadlineMinutes)
                    val choices = listOf(
                        null to stringResource(R.string.deadline_none),
                        1 to stringResource(R.string.deadline_1m),
                        5 to stringResource(R.string.deadline_5m),
                        15 to stringResource(R.string.deadline_15m),
                        60 to stringResource(R.string.deadline_1h),
                        1440 to stringResource(R.string.deadline_24h)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        choices.forEach { (mins, label) ->
                            val selected = state.deadlineMinutes == mins
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) colors.inkPrimary else colors.surfaceInset)
                                    .border(
                                        1.dp,
                                        if (selected) colors.inkPrimary else colors.hairline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        view.performTactileTick()
                                        setDeadlineMinutes(mins)
                                    }
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                                    .testTag("deadline_chip_${mins ?: "off"}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = if (selected) colors.canvas else colors.inkSecondary
                                )
                            }
                        }

                        val customLabel = if (isCustomSelected) {
                            "${state.deadlineMinutes}m"
                        } else {
                            stringResource(R.string.deadline_custom)
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isCustomSelected) colors.vermilion else colors.surfaceInset)
                                .border(
                                    1.dp,
                                    if (isCustomSelected) colors.vermilion else colors.hairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    view.performTactileTick()
                                    showCustomDeadlineDialog = true
                                }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                                .testTag("deadline_chip_custom"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customLabel,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (isCustomSelected) Color.White else colors.inkSecondary
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = state.validationMessage != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Text(
                        text = state.validationMessage.orEmpty(),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = colors.vermilion,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Full-width 52dp Tactile Primary CTA
                val ctaInteraction = remember { MutableInteractionSource() }
                val ctaPressed by ctaInteraction.collectIsPressedAsState()
                val ctaScale by animateFloatAsState(
                    targetValue = if (ctaPressed) 0.985f else 1f,
                    animationSpec = spring(stiffness = 500f, dampingRatio = 0.72f),
                    label = "cta_scale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .graphicsLayer {
                            scaleX = ctaScale
                            scaleY = ctaScale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.vermilion)
                        .clickable(
                            interactionSource = ctaInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = {
                                view.performTactileConfirm()
                                onCreateRoom(emptyQuestionError, minOptionsError)
                            }
                        )
                        .testTag("create_room_cta"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cta_create_room),
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Discreet Quick-Start Preset Chips when question is blank
            AnimatedVisibility(
                visible = state.question.isBlank(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.quick_start_label),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.9.sp,
                        color = colors.inkSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickPresetChip("Dinner spot") {
                            view.performTactileTick()
                            onApplyQuickTemplate(
                                RoomMode.QUICK_POLL,
                                "Where are we eating tonight?",
                                listOf("Wood-fired Ramen", "Tacos & Mezcal", "Sourdough Pizza")
                            )
                        }
                        QuickPresetChip("Sync window") {
                            view.performTactileTick()
                            onApplyQuickTemplate(
                                RoomMode.TIME_SLOTS,
                                "When works for our sync?",
                                listOf("Thu · 10:30 AM", "Thu · 3:00 PM", "Fri · 11:00 AM")
                            )
                        }
                        QuickPresetChip("Movie night") {
                            view.performTactileTick()
                            onApplyQuickTemplate(
                                RoomMode.QUICK_POLL,
                                "Which film tonight?",
                                listOf("Past Lives", "Dune: Part Two", "Spirited Away")
                            )
                        }
                    }
                }
            }

            // Join by #Code + Recent Rooms Strip
            val notFoundTemplate = stringResource(R.string.error_room_not_found, "%s")
            Spacer(modifier = Modifier.height(18.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (joinCodeText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.join_code_placeholder),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = colors.inkSecondary.copy(alpha = 0.65f)
                            )
                        }
                        BasicTextField(
                            value = joinCodeText,
                            onValueChange = {
                                joinCodeText = it.uppercase().take(8)
                                if (state.joinCodeError != null) onClearJoinCodeError()
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.inkPrimary
                            ),
                            cursorBrush = SolidColor(colors.vermilion),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    view.performTactileTick()
                                    onJoinByCode(joinCodeText) { code ->
                                        String.format(notFoundTemplate, code)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("join_code_input")
                        )
                    }
                    if (joinCodeText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.inkPrimary)
                                .clickable {
                                    view.performTactileTick()
                                    onJoinByCode(joinCodeText) { code ->
                                        String.format(notFoundTemplate, code)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("join_code_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.action_join),
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.canvas
                            )
                        }
                    }
                }

                if (state.joinCodeError != null) {
                    Text(
                        text = state.joinCodeError,
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 12.sp,
                        color = colors.vermilion,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (recentRooms.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.recent_rooms_label),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 0.9.sp,
                            color = colors.inkSecondary
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    view.performTactileTick()
                                    onOpenHistory()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("recent_view_all_history_button")
                        ) {
                            Text(
                                text = "${stringResource(R.string.history_view_all)} (${recentRooms.size})",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = colors.vermilion
                            )
                        }
                    }

                    recentRooms.take(4).forEach { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                                .clickable {
                                    view.performTactileTick()
                                    onOpenRecentRoom(recent.id)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("recent_room_${recent.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = recent.question,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "#${recent.id} · ${stringResource(R.string.room_votes_count, recent.totalVotes)}",
                                    style = MonoTabularTextStyle.copy(
                                        fontSize = 11.sp,
                                        color = colors.inkSecondary
                                    )
                                )
                            }
                            if (recent.isLocked) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.emerald.copy(alpha = 0.14f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.room_locked_tag),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = colors.emerald
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = colors.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showCalendarSyncSheet) {
        CalendarSyncSheet(
            syncState = calendarSyncState,
            onSyncDeviceCalendar = onSyncDeviceCalendar,
            onImportIcsUri = onImportIcsUri,
            onPermissionDenied = onCalendarPermissionDenied,
            onToggleSlot = onToggleCalendarSlot,
            onApplySelected = onApplyCalendarSlots,
            onDismiss = { showCalendarSyncSheet = false }
        )
    }

    if (timePickerTargetOptionId != null && !showTimeDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { timePickerTargetOptionId = null },
            confirmButton = {
                TextButton(onClick = {
                    view.performTactileTick()
                    pendingDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    showTimeDialog = true
                }) {
                    Text("Next", color = colors.vermilion)
                }
            },
            dismissButton = {
                TextButton(onClick = { timePickerTargetOptionId = null }) {
                    Text(stringResource(R.string.action_close), color = colors.inkSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (timePickerTargetOptionId != null && showTimeDialog) {
        val timeState = rememberTimePickerState(initialHour = 19, initialMinute = 0, is24Hour = false)
        AlertDialog(
            onDismissRequest = {
                showTimeDialog = false
                timePickerTargetOptionId = null
            },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.action_pick_time),
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                TimePicker(state = timeState)
            },
            confirmButton = {
                TextButton(onClick = {
                    view.performTactileConfirm()
                    val targetId = timePickerTargetOptionId
                    val dateMillis = pendingDateMillis ?: System.currentTimeMillis()
                    if (targetId != null) {
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = dateMillis
                        }
                        val localCal = Calendar.getInstance().apply {
                            set(
                                utcCal.get(Calendar.YEAR),
                                utcCal.get(Calendar.MONTH),
                                utcCal.get(Calendar.DAY_OF_MONTH),
                                timeState.hour,
                                timeState.minute,
                                0
                            )
                            set(Calendar.MILLISECOND, 0)
                        }
                        val formatted = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
                            .format(localCal.time)
                        onOptionTimePicked(targetId, formatted, localCal.timeInMillis)
                    }
                    showTimeDialog = false
                    timePickerTargetOptionId = null
                }) {
                    Text("Apply", color = colors.vermilion)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimeDialog = false
                    timePickerTargetOptionId = null
                }) {
                    Text(stringResource(R.string.action_close), color = colors.inkSecondary)
                }
            }
        )
    }

    if (showCustomDeadlineDialog) {
        var minutesText by remember {
            mutableStateOf((state.deadlineMinutes ?: 30).coerceIn(1, 10080).toString())
        }
        AlertDialog(
            onDismissRequest = { showCustomDeadlineDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.custom_deadline_title),
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.custom_deadline_minutes_hint),
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceInset)
                            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        BasicTextField(
                            value = minutesText,
                            onValueChange = { raw ->
                                minutesText = raw.filter { it.isDigit() }.take(5)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = colors.inkPrimary
                            ),
                            cursorBrush = SolidColor(colors.vermilion),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_deadline_minutes_input")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2 to "2m", 10 to "10m", 30 to "30m", 180 to "3h").forEach { (preset, label) ->
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceInset)
                                    .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                                    .clickable {
                                        view.performTactileTick()
                                        minutesText = preset.toString()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = minutesText.toIntOrNull()?.coerceIn(1, 10080)
                        if (parsed != null) {
                            view.performTactileConfirm()
                            setDeadlineMinutes(parsed)
                        }
                        showCustomDeadlineDialog = false
                    },
                    modifier = Modifier.testTag("confirm_custom_deadline_button")
                ) {
                    Text(stringResource(R.string.action_set_deadline), color = colors.vermilion)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDeadlineDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = colors.inkSecondary)
                }
            }
        )
    }
}

@Composable
private fun QuickPresetChip(
    label: String,
    onClick: () -> Unit
) {
    val colors = MeldTheme.colors
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = colors.inkPrimary
        )
    }
}

@Composable
private fun SegmentedModeSwitcher(
    selectedMode: RoomMode,
    onModeSelected: (RoomMode) -> Unit
) {
    val colors = MeldTheme.colors
    val shape = RoundedCornerShape(10.dp)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceInset)
            .border(1.dp, colors.hairline, shape)
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedMode == RoomMode.QUICK_POLL) 0.dp else segmentWidth,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.78f),
            label = "mode_pill_offset"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceElevated)
                .border(0.75.dp, colors.hairline, RoundedCornerShape(8.dp))
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            ModeTab(
                label = stringResource(R.string.mode_quick_poll),
                selected = selectedMode == RoomMode.QUICK_POLL,
                onClick = { onModeSelected(RoomMode.QUICK_POLL) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_tab_poll")
            )
            ModeTab(
                label = stringResource(R.string.mode_time_slots),
                selected = selectedMode == RoomMode.TIME_SLOTS,
                onClick = { onModeSelected(RoomMode.TIME_SLOTS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("mode_tab_time")
            )
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.inkPrimary else colors.inkSecondary,
        animationSpec = tween(150),
        label = "mode_text_color"
    )

    Box(
        modifier = modifier
            .height(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = textColor
        )
    }
}

@Composable
private fun DraftOptionRow(
    option: DraftOption,
    index: Int,
    totalCount: Int,
    placeholder: String,
    showTimePickerIcon: Boolean,
    focusRequester: FocusRequester,
    rowHeightPx: Float,
    onValueChange: (String) -> Unit,
    onImeAction: () -> Unit,
    onPickTimeClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onMoveBySteps: (Int) -> Unit
) {
    val colors = MeldTheme.colors
    var dragOffsetY by remember(option.id) { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceInset)
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .padding(start = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 48.dp)
                .pointerInput(option.id, index, totalCount) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { dragOffsetY = 0f },
                        onDragCancel = { dragOffsetY = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetY += dragAmount.y
                            if (dragOffsetY > rowHeightPx * 0.7f) {
                                onMoveBySteps(1)
                                dragOffsetY -= rowHeightPx
                            } else if (dragOffsetY < -rowHeightPx * 0.7f) {
                                onMoveBySteps(-1)
                                dragOffsetY += rowHeightPx
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.DragIndicator,
                contentDescription = stringResource(R.string.cd_drag_option),
                tint = colors.inkSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (option.text.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp,
                    color = colors.inkSecondary.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = option.text,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.vermilion),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(onNext = { onImeAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("option_input_$index")
            )
        }

        if (showTimePickerIcon) {
            IconButton(
                onClick = onPickTimeClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("pick_time_button_$index")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = stringResource(R.string.action_pick_time),
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (totalCount > 2) {
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("remove_option_button_$index")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cd_remove_option),
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    view.performTactileTick()
                    onCheckedChange(!checked)
                }
            )
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = colors.inkPrimary
        )
        MeldSpringSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
