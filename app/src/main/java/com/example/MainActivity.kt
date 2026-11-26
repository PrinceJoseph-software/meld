package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
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
import com.example.ui.MeldViewModel
import com.example.ui.components.AvatarStudioSheet
import com.example.ui.components.GeometricAvatar
import com.example.ui.components.HistorySheet
import com.example.ui.components.LanguageSelectorSheet
import com.example.ui.components.MeldLogo
import com.example.ui.components.SettingsSheet
import com.example.ui.components.ThemeToggle
import com.example.ui.components.performTactileTick
import com.example.ui.screens.CreateRoomScreen
import com.example.ui.screens.DecisionRoomScreen
import com.example.ui.theme.MeldAppTheme
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.WarmCanvas
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: MeldViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        if (data.scheme == "meld" && data.host == "r") {
            val roomId = com.example.data.sanitizeRoomCode(data.lastPathSegment) ?: return
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

    LaunchedEffect(selectedLang) {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (!current.startsWith(selectedLang)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(selectedLang))
        }
    }

    LaunchedEffect(hapticsEnabled) {
        com.example.ui.components.hapticsGlobalEnabled = hapticsEnabled
    }

    var showHistorySheet by remember { mutableStateOf(false) }
    var showTopAvatarStudio by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var launchRevealFinished by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Minimal 56dp Top Bar: MeldLogo left, History + Sun/Moon ThemeToggle right
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
                    modifier = Modifier.alpha(if (launchRevealFinished) 1f else 0f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val openStudioCd = stringResource(R.string.avatar_studio_title)
                    IconButton(
                        onClick = {
                            view.performTactileTick()
                            showTopAvatarStudio = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.hairline, CircleShape)
                            .semantics { contentDescription = openStudioCd }
                            .testTag("top_avatar_studio_button")
                    ) {
                        GeometricAvatar(
                            avatarStyleIndex = identity.avatarStyleIndex,
                            colorIndex = identity.badgeColorIndex,
                            size = 28.dp,
                            borderColor = colors.surfaceElevated,
                            reactionTrigger = identity.avatarStyleIndex + identity.badgeColorIndex
                        )
                    }

                    Box {
                        IconButton(
                            onClick = {
                                view.performTactileTick()
                                showHistorySheet = true
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.hairline, CircleShape)
                                .testTag("history_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.History,
                                contentDescription = stringResource(R.string.cd_open_history),
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(19.dp)
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

                    IconButton(
                        onClick = {
                            view.performTactileTick()
                            showLanguageSheet = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.hairline, CircleShape)
                            .testTag("language_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription = "Change Language",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

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
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    ThemeToggle(
                        isDark = isDark,
                        onToggle = { viewModel.toggleTheme(isDark) }
                    )
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
                            onOptionChange = viewModel::updateOption,
                            onAddOptionAfter = viewModel::addOptionAfter,
                            onConsumeOptionFocus = viewModel::consumeNewlyAddedOptionFocus,
                            onRemoveOption = viewModel::removeOption,
                            onMoveOption = viewModel::moveOption,
                            onAllowMultipleChange = viewModel::setAllowMultiple,
                            onAllowFriendsChange = viewModel::setAllowFriendsToAdd,
                            onAnonymousChange = viewModel::setIsAnonymous,
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
                            onBuildResultSummary = viewModel::buildResultSummary,
                            onAddVoiceComment = viewModel::addVoiceComment,
                            onDeleteVoiceComment = viewModel::deleteVoiceComment,
                            onOpenSettings = { showSettingsSheet = true }
                        )
                    }
                }
            }
        }

        // Non-blocking launch circle-joining choreography (zero loading screen; home screen stays visible & interactive)
        if (!launchRevealFinished) {
            LaunchCircleJoiningOverlay(
                onFinished = { launchRevealFinished = true }
            )
        }
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
            hapticsEnabled = hapticsEnabled,
            onSaveAnonymousBadge = viewModel::updateAnonymousBadge,
            onToggleHaptics = viewModel::setHapticsEnabled,
            onOpenStandardAvatarStudio = { showTopAvatarStudio = true },
            onOpenLanguageSelector = { showLanguageSheet = true },
            onDismiss = { showSettingsSheet = false }
        )
    }
}

/**
 * Non-blocking launch choreography matching the top-left MeldLogo:
 * Two circles enter fast from opposite sides of the header, join/hit at center-top with a shockwave,
 * swirl around each other, morph into the interlocking physical Meld capsules + diamond, and dock into
 * the exact top-left MeldLogo coordinates without ever blocking touch input on the Home Screen.
 */
@Composable
private fun LaunchCircleJoiningOverlay(
    onFinished: () -> Unit
) {
    val colors = MeldTheme.colors
    val sideOffsetFraction = remember { Animatable(0.42f) }
    val rollAngle = remember { Animatable(360f) }
    val impactSquash = remember { Animatable(1f) }
    val orbitRadiusDp = remember { Animatable(0f) }
    val swirlAngleDeg = remember { Animatable(0f) }
    val morphToCapsule = remember { Animatable(0f) }
    val diamondAppear = remember { Animatable(0f) }
    val shockwaveProgress = remember { Animatable(1f) }
    val dockToTopLeft = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            // Brief settle for Android window open transition
            delay(180L)

            // Phase 1: Fast roll-in from opposite sides of the screen
            val rollIn = launch {
                sideOffsetFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = CubicBezierEasing(0.20f, 0.96f, 0.35f, 1.0f)
                    )
                )
            }
            launch {
                rollAngle.animateTo(0f, tween(310, easing = FastOutSlowInEasing))
            }
            rollIn.join()

            // Phase 2: Collision shockwave + squash + bounce into swirl orbit
            launch {
                shockwaveProgress.snapTo(0f)
                shockwaveProgress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            }
            launch {
                impactSquash.snapTo(0.82f)
                impactSquash.animateTo(1f, spring(stiffness = 620f, dampingRatio = 0.58f))
            }
            orbitRadiusDp.snapTo(8f)
            orbitRadiusDp.animateTo(13f, tween(90, easing = FastOutSlowInEasing))

            // Phase 3: Swirl around each other + morph into interlocking Meld icon
            val swirl = launch {
                swirlAngleDeg.animateTo(
                    targetValue = 390f,
                    animationSpec = tween(420, easing = CubicBezierEasing(0.32f, 0f, 0.2f, 1f))
                )
            }
            val spiral = launch {
                delay(110L)
                orbitRadiusDp.animateTo(0f, spring(stiffness = 300f, dampingRatio = 0.72f))
            }
            launch {
                delay(200L)
                morphToCapsule.animateTo(1f, spring(stiffness = 460f, dampingRatio = 0.68f))
            }
            launch {
                delay(320L)
                diamondAppear.animateTo(1f, spring(stiffness = 520f, dampingRatio = 0.62f))
            }
            swirl.join()
            spiral.join()

            // Phase 4: Dock cleanly into top-left MeldLogo slot (34dp left center, 28dp top center)
            dockToTopLeft.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = 420f, dampingRatio = 0.80f)
            )
            onFinished()
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val dock = dockToTopLeft.value.coerceIn(0f, 1f)
        val startCenter = Offset(size.width / 2f, 28.dp.toPx())
        val endCenter = Offset(16.dp.toPx() + 18.dp.toPx(), 28.dp.toPx())
        val pivotCenter = Offset(
            lerp(startCenter.x, endCenter.x, dock),
            lerp(startCenter.y, endCenter.y, dock)
        )

        val baseSizePx = lerp(46.dp.toPx(), 36.dp.toPx(), dock)
        val m = morphToCapsule.value.coerceIn(0f, 1f)
        val circleDiameter = baseSizePx * 0.36f
        val finalW = baseSizePx * 0.74f
        val finalH = baseSizePx * 0.30f
        val capsuleW = lerp(circleDiameter, finalW, m)
        val capsuleH = lerp(circleDiameter, finalH, m)
        val corner = CornerRadius(capsuleH / 2f, capsuleH / 2f)
        val cutoutPx = 2.dp.toPx() * m

        val sidePx = sideOffsetFraction.value * size.width
        val orbitPx = orbitRadiusDp.value.dp.toPx()
        val orbitRad = Math.toRadians(swirlAngleDeg.value.toDouble())
        val orbitDx = (orbitPx * cos(orbitRad)).toFloat()
        val orbitDy = (orbitPx * sin(orbitRad)).toFloat()

        val squashX = impactSquash.value
        val squashY = if (squashX < 0.999f) 1f + (1f - squashX) * 0.85f else 1f

        val shock = shockwaveProgress.value
        if (shock < 0.98f) {
            drawCircle(
                color = colors.vermilion.copy(alpha = (1f - shock) * 0.7f),
                radius = lerp(baseSizePx * 0.2f, baseSizePx * 0.95f, shock),
                center = pivotCenter,
                style = Stroke(width = lerp(2.4f, 0.8f, shock).dp.toPx())
            )
        }

        // Left Ink disc
        withTransform({
            translate(
                left = -sidePx - orbitDx,
                top = -orbitDy
            )
            scale(squashX, squashY, pivot = pivotCenter)
            rotate(35f * m - rollAngle.value, pivot = pivotCenter)
        }) {
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.14f),
                topLeft = Offset(
                    pivotCenter.x - capsuleW / 2f + 0.8.dp.toPx(),
                    pivotCenter.y - capsuleH / 2f + 1.4.dp.toPx()
                ),
                size = Size(capsuleW, capsuleH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = colors.inkPrimary,
                topLeft = Offset(pivotCenter.x - capsuleW / 2f, pivotCenter.y - capsuleH / 2f),
                size = Size(capsuleW, capsuleH),
                cornerRadius = corner
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                ),
                topLeft = Offset(pivotCenter.x - capsuleW / 2f, pivotCenter.y - capsuleH / 2f),
                size = Size(capsuleW, capsuleH),
                cornerRadius = corner
            )
        }

        // Right Vermilion disc
        withTransform({
            translate(
                left = sidePx + orbitDx,
                top = orbitDy
            )
            scale(squashX, squashY, pivot = pivotCenter)
            rotate(-35f * m + rollAngle.value, pivot = pivotCenter)
        }) {
            if (cutoutPx > 0.2f) {
                drawRoundRect(
                    color = colors.canvas,
                    topLeft = Offset(
                        pivotCenter.x - capsuleW / 2f - cutoutPx,
                        pivotCenter.y - capsuleH / 2f - cutoutPx
                    ),
                    size = Size(capsuleW + cutoutPx * 2f, capsuleH + cutoutPx * 2f),
                    cornerRadius = CornerRadius((capsuleH + cutoutPx * 2f) / 2f)
                )
            }
            drawRoundRect(
                color = colors.vermilion.copy(alpha = 0.24f),
                topLeft = Offset(
                    pivotCenter.x - capsuleW / 2f + 0.8.dp.toPx(),
                    pivotCenter.y - capsuleH / 2f + 1.4.dp.toPx()
                ),
                size = Size(capsuleW, capsuleH),
                cornerRadius = corner
            )
            drawRoundRect(
                color = colors.vermilion,
                topLeft = Offset(pivotCenter.x - capsuleW / 2f, pivotCenter.y - capsuleH / 2f),
                size = Size(capsuleW, capsuleH),
                cornerRadius = corner
            )
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)
                ),
                topLeft = Offset(pivotCenter.x - capsuleW / 2f, pivotCenter.y - capsuleH / 2f),
                size = Size(capsuleW, capsuleH),
                cornerRadius = corner
            )
        }

        // Center interlocking diamond
        val dScale = diamondAppear.value.coerceAtLeast(0f)
        if (dScale > 0.05f) {
            val diamondRadius = finalH * 0.40f * dScale
            val diamondPath = Path().apply {
                moveTo(pivotCenter.x, pivotCenter.y - diamondRadius)
                lineTo(pivotCenter.x + diamondRadius, pivotCenter.y)
                lineTo(pivotCenter.x, pivotCenter.y + diamondRadius)
                lineTo(pivotCenter.x - diamondRadius, pivotCenter.y)
                close()
            }
            drawPath(path = diamondPath, color = colors.canvas)
            drawPath(
                path = diamondPath,
                color = colors.inkPrimary,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}
