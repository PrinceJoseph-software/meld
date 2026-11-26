package io.github.princejoseph.meld.ui.screens

import androidx.compose.material.icons.outlined.Image
import io.github.princejoseph.meld.ui.components.PollCardImageExporter
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.princejoseph.meld.BuildConfig
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.data.ConflictAlert
import io.github.princejoseph.meld.data.ConsensusInsightCalculator
import io.github.princejoseph.meld.data.DecisionInsight
import io.github.princejoseph.meld.data.InsightStrength
import io.github.princejoseph.meld.data.RoomDetailsUiModel
import io.github.princejoseph.meld.data.RoomMode
import io.github.princejoseph.meld.data.SyncStatus
import io.github.princejoseph.meld.data.VoterChip
import io.github.princejoseph.meld.data.VoterIdentity
import io.github.princejoseph.meld.ui.components.AnimatedCrownIcon
import io.github.princejoseph.meld.ui.components.AvatarStudioSheet
import io.github.princejoseph.meld.ui.components.DecisionPushArena
import io.github.princejoseph.meld.ui.components.GeometricAvatar
import io.github.princejoseph.meld.ui.components.GeometricBurstOverlay
import io.github.princejoseph.meld.ui.components.LiveHardwareLed
import io.github.princejoseph.meld.ui.components.OptionCard
import io.github.princejoseph.meld.ui.components.OverlappingAvatarStack
import io.github.princejoseph.meld.ui.components.PollDeadlineBanner
import io.github.princejoseph.meld.ui.components.SharePopoverSheet
import io.github.princejoseph.meld.ui.components.TimeSlotDistributionChart
import io.github.princejoseph.meld.ui.components.VoiceCommentsSection
import io.github.princejoseph.meld.ui.components.draftingDotGrid
import io.github.princejoseph.meld.ui.components.performTactileConfirm
import io.github.princejoseph.meld.ui.components.performTactileTick
import io.github.princejoseph.meld.ui.theme.InstrumentSerifFamily
import io.github.princejoseph.meld.ui.theme.JetBrainsMonoFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.MonoTabularTextStyle
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily
import io.github.princejoseph.meld.ui.theme.VoterBadgeColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun DecisionRoomScreen(
    room: RoomDetailsUiModel,
    identity: VoterIdentity,
    syncStatus: SyncStatus,
    simulatedOffline: Boolean,
    isSimulatingFriends: Boolean,
    celebrationBurstCount: Int,
    onBack: () -> Unit,
    onOpenRoom: (String) -> Unit = {},
    onUpdateVoterProfile: (name: String, avatarStyleIndex: Int, badgeColorIndex: Int) -> Unit,
    onToggleVote: (String) -> Unit,
    onSwipeVote: (String, Boolean) -> Unit,
    onTogglePriorityParticipant: (String) -> Unit,
    onAddOption: (String) -> Unit,
    onCrownWinner: (String?) -> Unit,
    onCreateRunoff: () -> Unit,
    onReopenRoom: () -> Unit,
    onResetVotes: () -> Unit,
    onDeadlineExpired: () -> Unit,
    onToggleSimulatedOffline: () -> Unit,
    onSimulateFriends: () -> Unit,
    onBuildResultSummary: (RoomDetailsUiModel) -> String,
    onAddVoiceComment: (optionId: String?, transcript: String, durationSeconds: Int, audioFilePath: String?) -> Unit = { _, _, _, _ -> },
    onDeleteVoiceComment: (commentId: String) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current

    BackHandler(onBack = onBack)

    var showSharePopover by remember { mutableStateOf(false) }
    var sharePopoverOpenWithQr by remember { mutableStateOf(false) }
    var showAvatarStudio by remember { mutableStateOf(false) }
    var linkCopied by remember { mutableStateOf(false) }
    var summaryCopied by remember { mutableStateOf(false) }
    var nudgePulse by remember { mutableIntStateOf(0) }

    // Live 1-second ticker for decision timer countdown
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(room.deadlineEpochMs) {
        if (room.deadlineEpochMs != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    LaunchedEffect(linkCopied) {
        if (linkCopied) {
            delay(2000)
            linkCopied = false
        }
    }

    LaunchedEffect(summaryCopied) {
        if (summaryCopied) {
            delay(2000)
            summaryCopied = false
        }
    }

    val timerRemainingMs = room.deadlineEpochMs?.let { (it - nowMs).coerceAtLeast(0L) }
    val effectiveLocked = room.isLocked || (timerRemainingMs != null && timerRemainingMs == 0L)
    val winningOption = remember(room.options) {
        room.options.firstOrNull { it.isWinner }
            ?: room.options.firstOrNull { it.voteCount > 0 }
            ?: room.options.firstOrNull()
    }
    val showExportToCalendar = effectiveLocked && room.mode == RoomMode.TIME_SLOTS && winningOption != null

    LaunchedEffect(room.id, timerRemainingMs == 0L, room.isLocked) {
        if (timerRemainingMs == 0L && !room.isLocked) {
            view.performTactileConfirm()
            onDeadlineExpired()
        }
    }

    LaunchedEffect(room.conflictAlert?.conflictedOptionId) {
        if (room.conflictAlert != null && !effectiveLocked) {
            view.performTactileTick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .draftingDotGrid(colors.dotGrid)
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (room.nextRoomCode != null || room.isRanked || room.repeatWeekly) {
                    item(key = "room_mode_notice") {
                        RoomModeNotice(room = room, onOpenRoom = onOpenRoom)
                    }
                }
                item(key = "voter_pill_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                view.performTactileTick()
                                onBack()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.hairline, CircleShape)
                                .testTag("room_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back_to_create),
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        VoterIdentityPill(
                            identity = identity,
                            reactionTrigger = room.totalVotes,
                            onOpenStudio = { showAvatarStudio = true },
                            isAnonymous = room.isAnonymous,
                            onOpenAnonStudio = onOpenSettings,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(key = "room_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.hairline, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LiveHardwareLed(syncStatus = syncStatus)
                                val statusLabel = when (syncStatus) {
                                    SyncStatus.SYNCING -> stringResource(
                                        R.string.sync_status_syncing,
                                        room.queuedVoteCount.coerceAtLeast(1)
                                    )
                                    SyncStatus.OFFLINE -> if (room.queuedVoteCount > 0) {
                                        stringResource(R.string.sync_status_offline_queued, room.queuedVoteCount)
                                    } else {
                                        stringResource(R.string.sync_status_offline)
                                    }
                                    SyncStatus.LIVE -> stringResource(R.string.sync_status_live)
                                }
                                Text(
                                    text = statusLabel,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.7.sp,
                                    color = colors.inkSecondary
                                )
                            }

                            if (timerRemainingMs != null) {
                                val timerText = if (timerRemainingMs == 0L) {
                                    stringResource(R.string.badge_timer_ended)
                                } else {
                                    stringResource(R.string.closes_in_label, formatCountdown(timerRemainingMs))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.surfaceElevated)
                                        .border(1.dp, colors.hairline, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = timerText,
                                        style = MonoTabularTextStyle.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (timerRemainingMs == 0L) colors.vermilion else colors.inkPrimary
                                        )
                                    )
                                }
                            }

                            if (room.isAnonymous) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.surfaceElevated)
                                        .border(1.dp, colors.hairline, RoundedCornerShape(6.dp))
                                        .clickable {
                                            view.performTactileTick()
                                            onOpenSettings()
                                        }
                                        .padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.VisibilityOff,
                                        contentDescription = stringResource(R.string.anonymous_room_hint),
                                        tint = colors.inkSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "ANON",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp,
                                        color = colors.inkSecondary
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = effectiveLocked) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.emerald.copy(alpha = 0.14f))
                                    .border(1.dp, colors.emerald.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AnimatedCrownIcon(tint = colors.emerald, modifier = Modifier.size(14.dp))
                                Text(
                                    text = stringResource(R.string.badge_decision_locked),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.8.sp,
                                    color = colors.emerald
                                )
                            }
                        }

                        Text(
                            text = room.question,
                            fontFamily = InstrumentSerifFamily,
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            color = colors.inkPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val ageLabel = formatAgeLabel(room.createdAtEpochMs)
                            val daysLeft = TimeUnit.MILLISECONDS
                                .toDays((room.expiresAtEpochMs - nowMs).coerceAtLeast(0L))
                                .coerceAtLeast(1L)
                            Text(
                                text = "$ageLabel • Expires in ${daysLeft}d • #${room.id}",
                                style = MonoTabularTextStyle.copy(
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                            )

                            OverlappingAvatarStack(
                                voters = room.activeVoters,
                                reactionTrigger = room.totalVotes
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShareLinkMorphButton(
                                copied = linkCopied,
                                onClick = {
                                    view.performTactileConfirm()
                                    clipboard.setText(AnnotatedString(room.shareUrl))
                                    linkCopied = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("header_share_link_button")
                            )

                            IconButton(
                                onClick = {
                                    view.performTactileTick()
                                    sharePopoverOpenWithQr = false
                                    showSharePopover = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                                    .testTag("header_qr_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.QrCode2,
                                    contentDescription = stringResource(R.string.cd_show_qr),
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                if (room.deadlineEpochMs != null && timerRemainingMs != null) {
                    item(key = "poll_deadline_banner") {
                        PollDeadlineBanner(
                            createdAtEpochMs = room.createdAtEpochMs,
                            deadlineEpochMs = room.deadlineEpochMs,
                            remainingMs = timerRemainingMs,
                            isLocked = effectiveLocked,
                            countdownText = formatCountdown(timerRemainingMs)
                        )
                    }
                }

                item(key = "decision_push_arena") {
                    DecisionPushArena(
                        options = room.options,
                        totalVotes = room.totalVotes,
                        isLocked = effectiveLocked,
                        nudgePulse = nudgePulse,
                        onNudge = { nudgePulse += 1 }
                    )
                }

                if (room.mode == RoomMode.TIME_SLOTS && room.options.isNotEmpty()) {
                    item(key = "time_slot_distribution_chart") {
                        TimeSlotDistributionChart(
                            options = room.options
                        )
                    }
                }

                room.decisionInsight?.let { insight ->
                    item(key = "decision_insight_badge") {
                        DecisionInsightBadge(
                            insight = insight,
                            mode = room.mode,
                            isCreator = room.isCreator,
                            isLocked = effectiveLocked,
                            onLockSuggested = { optionId ->
                                view.performTactileConfirm()
                                onCrownWinner(optionId)
                            }
                        )
                    }
                }

                item(key = "conflict_detector_alert") {
                    AnimatedVisibility(
                        visible = room.conflictAlert != null && !effectiveLocked,
                        enter = expandVertically(spring(stiffness = 420f, dampingRatio = 0.78f)) + fadeIn(tween(180)),
                        exit = shrinkVertically() + fadeOut(tween(140))
                    ) {
                        room.conflictAlert?.let { alert ->
                            ConflictDetectorAlertCard(
                                alert = alert,
                                onSelectAlternative = { altOptionId ->
                                    view.performTactileConfirm()
                                    onToggleVote(altOptionId)
                                }
                            )
                        }
                    }
                }

                if (room.mode == RoomMode.TIME_SLOTS && !effectiveLocked && !room.isAnonymous && room.activeVoters.isNotEmpty()) {
                    item(key = "priority_participants_strip") {
                        PriorityParticipantsStrip(
                            voters = room.activeVoters,
                            onTogglePriority = { uid ->
                                view.performTactileTick()
                                onTogglePriorityParticipant(uid)
                            }
                        )
                    }
                }

                if (room.mode == RoomMode.TIME_SLOTS && !effectiveLocked) {
                    item(key = "swipe_vote_hint") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = stringResource(R.string.swipe_vote_hint),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.7.sp,
                                color = colors.inkSecondary.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                items(
                    items = room.options,
                    key = { it.id }
                ) { option ->
                    OptionCard(
                        option = option,
                        isRoomLocked = effectiveLocked,
                        onVoteClick = { onToggleVote(option.id) },
                        enableSwipeVote = room.mode == RoomMode.TIME_SLOTS && !effectiveLocked,
                        onSwipeVote = { voteYes -> onSwipeVote(option.id, voteYes) },
                        nudgePulse = nudgePulse,
                        modifier = Modifier.animateItem(
                            placementSpec = spring(stiffness = 450f, dampingRatio = 0.78f)
                        )
                    )
                }

                if (!effectiveLocked && (room.allowFriendsToAdd || room.isCreator)) {
                    item(key = "inline_add_option") {
                        InlineAddOptionRow(
                            onAddOption = { label ->
                                view.performTactileTick()
                                onAddOption(label)
                            }
                        )
                    }
                }

                item(key = "voice_comments_section") {
                    VoiceCommentsSection(
                        comments = room.voiceComments,
                        options = room.options,
                        isRoomLocked = effectiveLocked,
                        onAddComment = onAddVoiceComment,
                        onDeleteComment = onDeleteVoiceComment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                if (room.isCreator) {
                    item(key = "creator_toolbar") {
                        CreatorToolbarCard(
                            isLocked = effectiveLocked,
                            hasVotes = room.totalVotes > 0,
                            hasTiedLeaders = room.hasTiedLeaders,
                            summaryCopied = summaryCopied,
                            onCrownWinner = {
                                view.performTactileConfirm()
                                onCrownWinner(null)
                            },
                            onCreateRunoff = {
                                view.performTactileConfirm()
                                onCreateRunoff()
                            },
                            onCopySummary = {
                                view.performTactileConfirm()
                                clipboard.setText(AnnotatedString(onBuildResultSummary(room)))
                                summaryCopied = true
                            },
                            onReopenRoom = {
                                view.performTactileTick()
                                onReopenRoom()
                            },
                            onResetVotes = {
                                view.performTactileTick()
                                onResetVotes()
                            }
                        )
                    }
                }

                // Developer-only tools: fake voters / offline toggle must never reach release users.
                if (BuildConfig.DEBUG && !effectiveLocked) {
                    item(key = "utility_sim_bar") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(colors.surfaceInset)
                                    .border(1.dp, colors.hairline, RoundedCornerShape(24.dp))
                                    .clickable(enabled = !isSimulatingFriends) {
                                        view.performTactileTick()
                                        onSimulateFriends()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("simulate_friends_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = colors.vermilion,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = stringResource(
                                        if (isSimulatingFriends) R.string.simulating_friends
                                        else R.string.action_simulate_friends
                                    ),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        if (simulatedOffline) Color(0xFFD97706).copy(alpha = 0.14f)
                                        else colors.surfaceInset
                                    )
                                    .border(
                                        1.dp,
                                        if (simulatedOffline) Color(0xFFD97706) else colors.hairline,
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable {
                                        view.performTactileTick()
                                        onToggleSimulatedOffline()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("toggle_offline_sim_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (simulatedOffline) Icons.Outlined.CloudQueue else Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    tint = if (simulatedOffline) Color(0xFFD97706) else colors.inkSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = stringResource(
                                        if (simulatedOffline) R.string.action_toggle_online_sim
                                        else R.string.action_toggle_offline_sim
                                    ),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (simulatedOffline) Color(0xFFD97706) else colors.inkSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Sticky Mobile Bottom Thumb-Bar
            Surface(
                color = colors.canvas,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(thickness = 1.dp, color = colors.hairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (effectiveLocked) {
                            // After the decision: one tap to share a polished result image.
                            IconButton(
                                onClick = {
                                    view.performTactileConfirm()
                                    PollCardImageExporter.exportAndShare(context, room)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                                    .testTag("bottom_share_card_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = stringResource(R.string.action_share_result_card),
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            if (showExportToCalendar) {
                                Button(
                                    onClick = {
                                        view.performTactileConfirm()
                                        launchCalendarInsertIntent(
                                            context = context,
                                            roomId = room.id,
                                            title = room.question,
                                            slotLabel = winningOption.label,
                                            explicitWindow = winningOption.slotStartMs?.let { winningOption.slotWindowMs() }
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("bottom_export_calendar_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.emerald,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.EventAvailable,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.action_export_to_calendar),
                                            fontFamily = PlusJakartaSansFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        view.performTactileConfirm()
                                        clipboard.setText(AnnotatedString(onBuildResultSummary(room)))
                                        summaryCopied = true
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceElevated)
                                        .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                                        .testTag("bottom_copy_summary_icon_button")
                                ) {
                                    Icon(
                                        imageVector = if (summaryCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(
                                            if (summaryCopied) R.string.action_summary_copied
                                            else R.string.action_copy_summary
                                        ),
                                        tint = if (summaryCopied) colors.emerald else colors.inkPrimary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = {
                                        view.performTactileConfirm()
                                        clipboard.setText(AnnotatedString(onBuildResultSummary(room)))
                                        summaryCopied = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("bottom_copy_summary_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.emerald,
                                        contentColor = Color.White
                                    )
                                ) {
                                    AnimatedContent(targetState = summaryCopied, label = "bottom_sum_copy") { copied ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = stringResource(
                                                    if (copied) R.string.action_summary_copied
                                                    else R.string.action_copy_summary
                                                ),
                                                fontFamily = PlusJakartaSansFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    view.performTactileConfirm()
                                    clipboard.setText(AnnotatedString(room.shareUrl))
                                    linkCopied = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("bottom_share_link_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.inkPrimary,
                                    contentColor = colors.canvas
                                )
                            ) {
                                AnimatedContent(targetState = linkCopied, label = "bottom_link_copy") { copied ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(
                                                if (copied) R.string.action_link_copied
                                                else R.string.action_share_link
                                            ),
                                            fontFamily = PlusJakartaSansFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                view.performTactileTick()
                                sharePopoverOpenWithQr = false
                                showSharePopover = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                                .testTag("bottom_qr_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.QrCode2,
                                contentDescription = stringResource(R.string.cd_show_qr),
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        GeometricBurstOverlay(
            burstTrigger = celebrationBurstCount + nudgePulse,
            isWinnerBurst = effectiveLocked
        )
    }

    if (showSharePopover) {
        SharePopoverSheet(
            room = room,
            initialShowQr = sharePopoverOpenWithQr,
            onDismiss = { showSharePopover = false }
        )
    }

    if (showAvatarStudio) {
        AvatarStudioSheet(
            identity = identity,
            onSave = { name, packedStyle, badgeColorIdx ->
                onUpdateVoterProfile(name, packedStyle, badgeColorIdx)
            },
            onDismiss = { showAvatarStudio = false }
        )
    }
}

@Composable
private fun VoterIdentityPill(
    identity: VoterIdentity,
    reactionTrigger: Int,
    onOpenStudio: () -> Unit,
    isAnonymous: Boolean = false,
    onOpenAnonStudio: () -> Unit = onOpenStudio,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current

    val avatarStyle = if (isAnonymous) identity.anonymousAvatarStyleIndex else identity.avatarStyleIndex
    val colorIdx = if (isAnonymous) identity.anonymousBadgeColorIndex else identity.badgeColorIndex
    val labelText = if (isAnonymous) {
        "Disguise: ${identity.effectiveAnonymousName}"
    } else if (identity.displayName.isBlank()) {
        "${stringResource(R.string.voter_pill_prefix)} ${stringResource(R.string.voter_guest_prompt)}"
    } else {
        "${stringResource(R.string.voter_pill_prefix)} ${identity.effectiveName}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(24.dp))
            .clickable {
                view.performTactileTick()
                if (isAnonymous) onOpenAnonStudio() else onOpenStudio()
            }
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            .testTag("voter_identity_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GeometricAvatar(
            avatarStyleIndex = avatarStyle,
            colorIndex = colorIdx,
            size = 28.dp,
            borderColor = colors.surfaceElevated,
            reactionTrigger = reactionTrigger
        )
        Text(
            text = labelText,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.inkPrimary,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.vermilion.copy(alpha = 0.10f))
                .border(1.dp, colors.vermilion.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("open_avatar_studio_button")
        ) {
            Text(
                text = if (isAnonymous) "BADGE" else stringResource(R.string.avatar_open_studio),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = colors.vermilion
            )
        }
    }
}

@Composable
private fun DecisionInsightBadge(
    insight: DecisionInsight,
    mode: RoomMode,
    isCreator: Boolean,
    isLocked: Boolean,
    onLockSuggested: (String) -> Unit
) {
    val colors = MeldTheme.colors
    val isStrongOrLocked = insight.strength == InsightStrength.STRONG_CONSENSUS ||
        insight.strength == InsightStrength.UNANIMOUS ||
        insight.strength == InsightStrength.LOCKED
    val isTied = insight.strength == InsightStrength.TIED

    val accentColor = when {
        isStrongOrLocked -> colors.emerald
        insight.strength == InsightStrength.AWAITING_VOTES -> colors.inkSecondary
        else -> colors.vermilion
    }
    val borderColor = when {
        isStrongOrLocked -> colors.emerald.copy(alpha = 0.40f)
        insight.strength == InsightStrength.AWAITING_VOTES -> colors.hairline
        else -> colors.vermilion.copy(alpha = 0.38f)
    }
    val badgeBg = when {
        isStrongOrLocked -> colors.emerald.copy(alpha = 0.08f)
        insight.strength == InsightStrength.AWAITING_VOTES -> colors.surfaceElevated
        else -> colors.vermilion.copy(alpha = 0.06f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(badgeBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .animateContentSize(spring(stiffness = 420f, dampingRatio = 0.78f))
            .testTag("decision_insight_badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left architectural hairline accent bar
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Crisp geometric consensus target glyph (no generic AI sparkles or emojis)
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
                        val rOuter = size.minDimension / 2f
                        drawCircle(
                            color = accentColor.copy(alpha = 0.35f),
                            radius = rOuter,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx())
                        )
                        drawCircle(
                            color = accentColor,
                            radius = rOuter * 0.45f
                        )
                    }

                    Text(
                        text = stringResource(R.string.decision_insight_label),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = accentColor
                    )

                    val subTag = when {
                        insight.strength == InsightStrength.UNANIMOUS -> stringResource(R.string.insight_unanimous_tag)
                        insight.totalUniqueVoters > 0 -> stringResource(
                            R.string.insight_overlap_ratio,
                            insight.overlapVoterCount,
                            insight.totalUniqueVoters,
                            insight.overlapPercentage
                        )
                        mode == RoomMode.TIME_SLOTS -> stringResource(R.string.consensus_window_tag)
                        else -> stringResource(R.string.consensus_pick_tag)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceElevated.copy(alpha = 0.85f))
                            .border(1.dp, colors.hairline, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = subTag,
                            style = MonoTabularTextStyle.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkSecondary
                            )
                        )
                    }
                }

                when (insight.strength) {
                    InsightStrength.AWAITING_VOTES -> {
                        Text(
                            text = stringResource(R.string.insight_awaiting_votes),
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                    }
                    InsightStrength.TIED -> {
                        Text(
                            text = "${insight.suggestedLabel} · ${stringResource(R.string.insight_tied_summary, insight.runnerUpLabel.orEmpty())}",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = colors.inkPrimary
                        )
                    }
                    else -> {
                        val prefix = if (mode == RoomMode.TIME_SLOTS) {
                            stringResource(R.string.suggested_meeting_time_prefix)
                        } else {
                            stringResource(R.string.suggested_pick_prefix)
                        }
                        Text(
                            text = "$prefix: ${insight.suggestedLabel}",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.inkPrimary
                        )
                    }
                }
            }

            if (isCreator && !isLocked && !isTied &&
                insight.strength != InsightStrength.AWAITING_VOTES &&
                insight.suggestedOptionId != null
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor)
                        .clickable { onLockSuggested(insight.suggestedOptionId) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("insight_lock_suggested_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_lock_suggested),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictDetectorAlertCard(
    alert: ConflictAlert,
    onSelectAlternative: (String) -> Unit
) {
    val colors = MeldTheme.colors
    val amberAccent = Color(0xFFD97706)
    val namesLabel = alert.highPriorityParticipantNames.joinToString(", ")
    val message = if (alert.severity == io.github.princejoseph.meld.data.ConflictSeverity.DEADLOCK) {
        stringResource(R.string.conflict_alert_deadlock, namesLabel)
    } else {
        stringResource(
            R.string.conflict_alert_overlap,
            alert.highPriorityParticipantNames.size,
            namesLabel,
            alert.conflictedOptionLabel
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(amberAccent.copy(alpha = 0.08f))
            .border(1.dp, amberAccent.copy(alpha = 0.36f), RoundedCornerShape(12.dp))
            .testTag("conflict_detector_alert"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(amberAccent)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Geometric conflict diamond glyph
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(12.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2f, 0.8f)
                            lineTo(size.width - 0.8f, size.height / 2f)
                            lineTo(size.width / 2f, size.height - 0.8f)
                            lineTo(0.8f, size.height / 2f)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = amberAccent,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4.dp.toPx())
                        )
                    }
                    Text(
                        text = stringResource(R.string.conflict_detector_label),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = amberAccent
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceElevated.copy(alpha = 0.85f))
                            .border(1.dp, colors.hairline, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = alert.commitmentSummary,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 9.sp,
                            color = colors.inkSecondary
                        )
                    }
                }

                Text(
                    text = message,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.inkPrimary
                )
            }

            if (alert.suggestedAlternativeOptionId != null && alert.suggestedAlternativeLabel != null) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, amberAccent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .clickable { onSelectAlternative(alert.suggestedAlternativeOptionId) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("conflict_pick_alternative_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_switch_conflict_free, alert.suggestedAlternativeLabel),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = amberAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityParticipantsStrip(
    voters: List<VoterChip>,
    onTogglePriority: (String) -> Unit
) {
    val colors = MeldTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.priority_participants_label),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = colors.inkSecondary
            )
            Text(
                text = stringResource(R.string.priority_participants_hint),
                fontFamily = PlusJakartaSansFamily,
                fontSize = 11.sp,
                color = colors.inkSecondary.copy(alpha = 0.8f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            voters.forEach { voter ->
                val isPriority = voter.isHighPriority
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            if (isPriority) colors.vermilion.copy(alpha = 0.10f)
                            else colors.surfaceElevated
                        )
                        .border(
                            1.dp,
                            if (isPriority) colors.vermilion.copy(alpha = 0.55f) else colors.hairline,
                            RoundedCornerShape(24.dp)
                        )
                        .clickable { onTogglePriority(voter.voterUid) }
                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                        .testTag("priority_participant_${voter.voterUid}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    GeometricAvatar(
                        avatarStyleIndex = voter.avatarStyleIndex,
                        colorIndex = voter.badgeColorIndex,
                        size = 24.dp,
                        borderColor = colors.surfaceElevated
                    )
                    Text(
                        text = voter.voterName,
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = if (isPriority) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (isPriority) colors.inkPrimary else colors.inkSecondary
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isPriority) colors.vermilion else colors.hairline)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareLinkMorphButton(
    copied: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (copied) colors.emerald else colors.hairline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = colors.surfaceElevated,
            contentColor = if (copied) colors.emerald else colors.inkPrimary
        )
    ) {
        AnimatedContent(targetState = copied, label = "share_morph") { isCopied ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(
                        if (isCopied) R.string.action_link_copied else R.string.action_share_link
                    ),
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun InlineAddOptionRow(
    onAddOption: (String) -> Unit
) {
    val colors = MeldTheme.colors
    var newOptionText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceInset)
            .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = colors.inkSecondary,
            modifier = Modifier.size(18.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            if (newOptionText.isEmpty()) {
                Text(
                    text = stringResource(R.string.placeholder_friend_add_option),
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    color = colors.inkSecondary.copy(alpha = 0.65f)
                )
            }
            BasicTextField(
                value = newOptionText,
                onValueChange = { newOptionText = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = colors.inkPrimary
                ),
                cursorBrush = SolidColor(colors.vermilion),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (newOptionText.isNotBlank()) {
                            onAddOption(newOptionText)
                            newOptionText = ""
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inline_add_option_input")
            )
        }

        if (newOptionText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.inkPrimary)
                    .clickable {
                        onAddOption(newOptionText)
                        newOptionText = ""
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("inline_add_option_submit"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.action_add_inline),
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.canvas
                )
            }
        }
    }
}

@Composable
private fun CreatorToolbarCard(
    isLocked: Boolean,
    hasVotes: Boolean,
    hasTiedLeaders: Boolean,
    summaryCopied: Boolean,
    onCrownWinner: () -> Unit,
    onCreateRunoff: () -> Unit,
    onCopySummary: () -> Unit,
    onReopenRoom: () -> Unit,
    onResetVotes: () -> Unit
) {
    val colors = MeldTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.creator_toolbar_label),
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            letterSpacing = 0.9.sp,
            color = colors.inkSecondary
        )

        if (!isLocked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCrownWinner,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("crown_winner_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.emerald,
                        contentColor = Color.White
                    )
                ) {
                    AnimatedCrownIcon(tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_crown_winner),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                if (hasVotes) {
                    OutlinedButton(
                        onClick = onResetVotes,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("reset_votes_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.hairline)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_reset_votes),
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = colors.inkSecondary
                        )
                    }
                }
            }

            // 1-Tap Tie-Breaker Runoff when top options are deadlocked
            AnimatedVisibility(visible = hasTiedLeaders) {
                OutlinedButton(
                    onClick = onCreateRunoff,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("runoff_tiebreaker_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.vermilion.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.vermilion.copy(alpha = 0.08f),
                        contentColor = colors.vermilion
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_runoff_tiebreaker),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCopySummary,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("creator_copy_summary_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.inkPrimary,
                        contentColor = colors.canvas
                    )
                ) {
                    Icon(
                        imageVector = if (summaryCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (summaryCopied) R.string.action_summary_copied else R.string.action_copy_summary
                        ),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                OutlinedButton(
                    onClick = onReopenRoom,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("reopen_room_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.hairline)
                ) {
                    Text(
                        text = stringResource(R.string.action_reopen_room),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = colors.inkPrimary
                    )
                }
            }
        }
    }
}

/** Weekly-series hand-off ("this week's poll is open") and the ranked-choice explainer. */
@Composable
private fun RoomModeNotice(room: RoomDetailsUiModel, onOpenRoom: (String) -> Unit) {
    val colors = MeldTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val next = room.nextRoomCode
        if (next != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.vermilion.copy(alpha = 0.12f))
                    .border(1.dp, colors.vermilion.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .clickable { onOpenRoom(next) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag("next_poll_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.next_poll_open),
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = colors.inkPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.next_poll_open_action, next),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = colors.vermilion
                )
            }
        }
        val lines = buildList {
            if (room.isRanked) {
                add(
                    if (room.isLocked && room.rankedRounds > 1) {
                        stringResource(R.string.ranked_rounds, room.rankedRounds)
                    } else {
                        stringResource(R.string.ranked_hint)
                    }
                )
                if (room.isLocked && room.rankedTie) add(stringResource(R.string.ranked_tie_note))
            }
            if (room.repeatWeekly) add(stringResource(R.string.weekly_badge))
        }
        if (lines.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.inkSecondary
                    )
                }
            }
        }
    }
}

private fun launchCalendarInsertIntent(
    context: android.content.Context,
    roomId: String,
    title: String,
    slotLabel: String,
    explicitWindow: Pair<Long, Long>? = null
) {
    val window = explicitWindow ?: ConsensusInsightCalculator.resolveCalendarEventWindowMs(slotLabel)
    val intent = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, title)
        putExtra(
            CalendarContract.Events.DESCRIPTION,
            context.getString(R.string.calendar_event_description, roomId, slotLabel)
        )
        putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        if (window != null) {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, window.first)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, window.second)
        }
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.calendar_no_app_error),
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun formatCountdown(remainingMs: Long): String {
    val totalSecs = remainingMs / 1000L
    val hours = totalSecs / 3600L
    val mins = (totalSecs % 3600L) / 60L
    val secs = totalSecs % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm %02ds", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

private fun formatAgeLabel(createdAtMs: Long): String {
    val diffMs = (System.currentTimeMillis() - createdAtMs).coerceAtLeast(0L)
    val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        mins < 1 -> "Created just now"
        mins < 60 -> "Created ${mins}m ago"
        hours < 24 -> "Created ${hours}h ago"
        else -> "Created ${days}d ago"
    }
}
