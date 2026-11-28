package io.github.princejoseph.meld

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.princejoseph.meld.ui.MeldViewModel
import io.github.princejoseph.meld.ui.components.AvatarStudioSheet
import io.github.princejoseph.meld.ui.components.HistorySheet
import io.github.princejoseph.meld.ui.components.LanguageSelectorSheet
import io.github.princejoseph.meld.ui.components.MeldLogo
import io.github.princejoseph.meld.ui.components.PrivacyPolicySheet
import io.github.princejoseph.meld.ui.components.SettingsSheet
import io.github.princejoseph.meld.ui.components.performTactileTick
import io.github.princejoseph.meld.ui.screens.CreateRoomScreen
import io.github.princejoseph.meld.ui.screens.DecisionRoomScreen
import io.github.princejoseph.meld.ui.screens.OnboardingScreen
import io.github.princejoseph.meld.ui.theme.MeldAppTheme
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.ObsidianCanvas
import io.github.princejoseph.meld.ui.theme.WarmCanvas
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private val viewModel: MeldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The previous launch crashed: show the saved report (plain Views) instead of the app,
        // so the user can share it rather than watch Meld close again.
        CrashReport.pending(this)?.let { report ->
            CrashReport.showScreen(this, report) { recreate() }
            return
        }
        handleDeepLink(intent)

        setContent {
            val darkOverride by viewModel.darkThemeOverride.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDark = darkOverride ?: systemDark

            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDark) {
                        SystemBarStyle.dark(ObsidianCanvas.toArgb())
                    } else {
                        SystemBarStyle.light(WarmCanvas.toArgb(), WarmCanvas.toArgb())
                    },
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(ObsidianCanvas.toArgb())
                    } else {
                        SystemBarStyle.light(WarmCanvas.toArgb(), WarmCanvas.toArgb())
                    }
                )
            }

            MeldAppTheme(darkTheme = isDark) {
                MeldAppShell(
                    viewModel = viewModel,
                    isDark = isDark
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        val isCustomScheme = data.scheme == "meld" && data.host == "r"
        val isAppLink = data.scheme == "https" && data.pathSegments.firstOrNull() == "r"
        if (isCustomScheme || isAppLink) {
            val roomId = io.github.princejoseph.meld.data.sanitizeRoomCode(data.lastPathSegment) ?: return
            viewModel.openRoom(roomId)
        }
    }
}

@Composable
private fun MeldAppShell(
    viewModel: MeldViewModel,
    isDark: Boolean
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val createState by viewModel.createState.collectAsStateWithLifecycle()
    val calendarSyncState by viewModel.calendarSyncState.collectAsStateWithLifecycle()
    val recentRooms by viewModel.recentRooms.collectAsStateWithLifecycle()
    val activeRoom by viewModel.activeRoom.collectAsStateWithLifecycle()
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val simulatedOffline by viewModel.simulatedOffline.collectAsStateWithLifecycle()
    val logoPulseCount by viewModel.logoPulseCount.collectAsStateWithLifecycle()
    val celebrationBurstCount by viewModel.celebrationBurstCount.collectAsStateWithLifecycle()
    val isSimulatingFriends by viewModel.isSimulatingFriends.collectAsStateWithLifecycle()
    val selectedLang by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()
    val autoCalendar by viewModel.autoCalendar.collectAsStateWithLifecycle()

    LaunchedEffect(selectedLang) {
        val lang = selectedLang ?: return@LaunchedEffect
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (current.isEmpty() || !current.startsWith(lang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
    }

    LaunchedEffect(hapticsEnabled) {
        io.github.princejoseph.meld.ui.components.hapticsGlobalEnabled = hapticsEnabled
    }

    var showHistorySheet by remember { mutableStateOf(false) }
    var showTopAvatarStudio by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var showOnboardingTour by remember { mutableStateOf(false) }
    var launchRevealFinished by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal 56dp Top Bar: MeldLogo left, unified Settings button right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MeldLogo(
                    pulseTrigger = logoPulseCount,
                    onClick = {
                        if (activeRoom != null) {
                            viewModel.closeRoom()
                        }
                    },
                    playIntro = false, // the full-screen splash docks the assembled mark here
                    modifier = Modifier.alpha(if (launchRevealFinished) 1f else 0f)
                )

                Box {
                    IconButton(
                        onClick = {
                            view.performTactileTick()
                            showSettingsSheet = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.hairline, CircleShape)
                            .testTag("settings_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (recentRooms.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.vermilion)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = activeRoom,
                    transitionSpec = {
                        if (targetState != null && initialState == null) {
                            (slideInHorizontally(tween(240)) { it / 8 } + fadeIn(tween(200)))
                                .togetherWith(fadeOut(tween(140)))
                        } else if (targetState == null && initialState != null) {
                            (slideInHorizontally(tween(220)) { -it / 8 } + fadeIn(tween(180)))
                                .togetherWith(fadeOut(tween(140)))
                        } else {
                            fadeIn(tween(160)).togetherWith(fadeOut(tween(120)))
                        }
                    },
                    contentKey = { it?.id },
                    label = "meld_screen_transition"
                ) { room ->
                    if (room == null) {
                        CreateRoomScreen(
                            state = createState,
                            calendarSyncState = calendarSyncState,
                            recentRooms = recentRooms,
                            onModeChange = viewModel::setMode,
                            onApplyQuickTemplate = viewModel::applyQuickTemplate,
                            onQuestionChange = viewModel::updateQuestion,
                            onQuestionVoiceChange = viewModel::setQuestionVoice,
                            onOptionChange = viewModel::updateOption,
                            onOptionTimePicked = viewModel::setOptionTimeSlot,
                            onAddOptionAfter = viewModel::addOptionAfter,
                            onConsumeOptionFocus = viewModel::consumeNewlyAddedOptionFocus,
                            onRemoveOption = viewModel::removeOption,
                            onMoveOption = viewModel::moveOption,
                            onAllowMultipleChange = viewModel::setAllowMultiple,
                            onAllowFriendsChange = viewModel::setAllowFriendsToAdd,
                            onAnonymousChange = viewModel::setIsAnonymous,
                            onRankedChange = viewModel::setIsRanked,
                            onRepeatWeeklyChange = viewModel::setRepeatWeekly,
                            cloudEnabled = viewModel.isCloudEnabled,
                            onDeadlineMinutesChange = viewModel::setDeadlineMinutes,
                            onSyncDeviceCalendar = viewModel::syncFromDeviceCalendar,
                            onImportIcsUri = viewModel::syncFromIcsUri,
                            onCalendarPermissionDenied = viewModel::setCalendarStatusMessage,
                            onToggleCalendarSlot = viewModel::toggleSuggestedSlot,
                            onApplyCalendarSlots = viewModel::applySelectedCalendarSlots,
                            onCreateRoom = viewModel::createRoom,
                            onJoinByCode = viewModel::joinRoomByCode,
                            onClearJoinCodeError = viewModel::clearJoinCodeError,
                            onOpenRecentRoom = viewModel::openRoom,
                            onOpenHistory = { showHistorySheet = true }
                        )
                    } else {
                        DecisionRoomScreen(
                            room = room,
                            identity = identity,
                            syncStatus = syncStatus,
                            simulatedOffline = simulatedOffline,
                            isSimulatingFriends = isSimulatingFriends,
                            celebrationBurstCount = celebrationBurstCount,
                            onBack = viewModel::closeRoom,
                            onUpdateVoterProfile = viewModel::updateVoterProfile,
                            onToggleVote = viewModel::toggleVote,
                            onSwipeVote = viewModel::castSwipeVote,
                            onTogglePriorityParticipant = viewModel::togglePriorityParticipant,
                            onAddOption = viewModel::addOptionToActiveRoom,
                            onCrownWinner = { explicitId -> viewModel.crownWinner(explicitId) },
                            onCreateRunoff = viewModel::createRunoffFromTied,
                            onReopenRoom = viewModel::reopenRoom,
                            onResetVotes = viewModel::resetVotes,
                            onDeadlineExpired = viewModel::finalizeExpiredRoom,
                            onToggleSimulatedOffline = viewModel::toggleSimulatedOffline,
                            onSimulateFriends = viewModel::simulateThreeFriends,
                            onOpenRoom = viewModel::openRoom,
                            onBuildResultSummary = viewModel::buildResultSummary,
                            onAddVoiceComment = viewModel::addVoiceComment,
                            onDeleteVoiceComment = viewModel::deleteVoiceComment,
                            onOpenSettings = { showSettingsSheet = true }
                        )
                    }
                }
            }
        }

    }

    // Rendered outside the status-bar-padded shell so the tour is truly edge-to-edge:
    // its background reaches under the system bars, and OnboardingScreen applies the
    // system-bar insets to its own content exactly once.
    if (hasCompletedOnboarding == false || showOnboardingTour) {
        OnboardingScreen(
            onFinish = {
                showOnboardingTour = false
                viewModel.completeOnboarding()
            }
        )
    }

    if (showHistorySheet) {
        HistorySheet(
            rooms = recentRooms,
            onOpenRoom = viewModel::openRoom,
            onDeleteRoom = viewModel::deleteHistoryRoom,
            onDismiss = { showHistorySheet = false }
        )
    }

    if (showTopAvatarStudio) {
        AvatarStudioSheet(
            identity = identity,
            onSave = viewModel::updateVoterProfile,
            onDismiss = { showTopAvatarStudio = false }
        )
    }

    if (showLanguageSheet) {
        LanguageSelectorSheet(
            onSelectLanguage = viewModel::setLanguage,
            onDismiss = { showLanguageSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            identity = identity,
            isDarkTheme = isDark,
            hapticsEnabled = hapticsEnabled,
            recentRoomsCount = recentRooms.size,
            currentLanguageCode = selectedLang ?: "en",
            onSaveStandardProfile = viewModel::updateVoterProfile,
            onSaveAnonymousBadge = viewModel::updateAnonymousBadge,
            onToggleDarkTheme = { viewModel.toggleTheme(isDark) },
            onToggleHaptics = viewModel::setHapticsEnabled,
            autoCalendar = autoCalendar,
            onToggleAutoCalendar = viewModel::setAutoCalendar,
            onOpenHistory = { showHistorySheet = true },
            onOpenLanguageSelector = { showLanguageSheet = true },
            onOpenFeatureTour = { showOnboardingTour = true },
            onOpenPrivacyPolicy = { showPrivacySheet = true },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showPrivacySheet) {
        PrivacyPolicySheet(
            onDismiss = { showPrivacySheet = false }
        )
    }

    // Full-screen launch animation on cold start, drawn above everything (incl. the first-run tour).
    if (!launchRevealFinished) {
        io.github.princejoseph.meld.ui.components.MeldSplash(onFinished = { launchRevealFinished = true })
    }
}

