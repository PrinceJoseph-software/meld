package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.OptionUiModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.MonoTabularTextStyle
import com.example.ui.theme.PlusJakartaSansFamily
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionCard(
    option: OptionUiModel,
    isRoomLocked: Boolean,
    onVoteClick: () -> Unit,
    enableSwipeVote: Boolean = false,
    onSwipeVote: ((Boolean) -> Unit)? = null,
    nudgePulse: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val swipeOffsetX = remember(option.id) { Animatable(0f) }
    val swipeThresholdPx = with(density) { 68.dp.toPx() }
    val maxDragPx = with(density) { 116.dp.toPx() }
    var armedDirection by remember { mutableStateOf(0) } // -1 = No, 0 = neutral, +1 = Yes

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isRoomLocked) 0.98f else 1f,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.68f),
        label = "option_press_scale"
    )

    val animatedFraction by animateFloatAsState(
        targetValue = option.fraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.85f),
        label = "option_progress_width"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isRoomLocked && !option.isWinner) 0.45f else 1f,
        animationSpec = tween(durationMillis = 240),
        label = "option_locked_alpha"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            option.isWinner -> colors.emerald
            option.isPendingSync -> Color(0xFFD97706)
            option.isVotedByMe -> colors.vermilion
            else -> colors.hairline
        },
        animationSpec = tween(180),
        label = "option_border"
    )

    val borderWidth = if (option.isWinner) 2.dp else 1.dp
    val fillColor = if (option.isWinner) colors.emeraldFill else colors.progressFill
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
    ) {
        // Behind-card Swipe-to-Vote action track (Time Slots mode)
        if (enableSwipeVote && !isRoomLocked && abs(swipeOffsetX.value) > 2f) {
            val isSwipingRight = swipeOffsetX.value > 0f
            val progress = (abs(swipeOffsetX.value) / swipeThresholdPx).coerceIn(0f, 1f)
            val trackColor = if (isSwipingRight) {
                colors.emerald.copy(alpha = 0.14f + 0.78f * progress)
            } else {
                colors.vermilion.copy(alpha = 0.14f + 0.78f * progress)
            }
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(trackColor)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isSwipingRight) Arrangement.Start else Arrangement.End
            ) {
                if (isSwipingRight) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = if (progress > 0.55f) Color.White else colors.emerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.swipe_yes_label),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = if (progress > 0.55f) Color.White else colors.emerald
                    )
                } else {
                    Text(
                        text = stringResource(R.string.swipe_no_label),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        color = if (progress > 0.55f) Color.White else colors.vermilion
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = if (progress > 0.55f) Color.White else colors.vermilion,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        val swipeModifier = if (enableSwipeVote && !isRoomLocked && onSwipeVote != null) {
            Modifier.pointerInput(option.id, isRoomLocked) {
                detectHorizontalDragGestures(
                    onDragStart = { armedDirection = 0 },
                    onDragEnd = {
                        val currentX = swipeOffsetX.value
                        scope.launch {
                            when {
                                currentX >= swipeThresholdPx -> {
                                    view.performTactileConfirm()
                                    onSwipeVote(true)
                                }
                                currentX <= -swipeThresholdPx -> {
                                    view.performTactileConfirm()
                                    onSwipeVote(false)
                                }
                            }
                            armedDirection = 0
                            swipeOffsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(stiffness = 520f, dampingRatio = 0.72f)
                            )
                        }
                    },
                    onDragCancel = {
                        armedDirection = 0
                        scope.launch {
                            swipeOffsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(stiffness = 520f, dampingRatio = 0.72f)
                            )
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val next = (swipeOffsetX.value + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                        val newArmed = when {
                            next >= swipeThresholdPx -> 1
                            next <= -swipeThresholdPx -> -1
                            else -> 0
                        }
                        if (newArmed != 0 && newArmed != armedDirection) {
                            view.performTactileTick()
                        }
                        armedDirection = newArmed
                        scope.launch {
                            swipeOffsetX.snapTo(next)
                        }
                    }
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                .then(swipeModifier)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .alpha(cardAlpha)
                .defaultMinSize(minHeight = 68.dp)
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(colors.surfaceElevated)
                .border(borderWidth, borderColor, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isRoomLocked,
                    role = Role.Checkbox,
                    onClick = {
                        view.performTactileVote()
                        onVoteClick()
                    }
                )
                .testTag("option_card_${option.id}")
        ) {
            if (animatedFraction > 0.002f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .background(fillColor)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(
                                if (option.isWinner || option.isUnanimous) colors.emerald.copy(alpha = 0.55f)
                                else colors.vermilion.copy(alpha = 0.45f)
                            )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (option.isWinner) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.emerald.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedCrownIcon(tint = colors.emerald)
                    }
                } else {
                    AnimatedCheckmarkBox(
                        checked = option.isVotedByMe,
                        isWinner = false
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = option.label,
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            color = colors.inkPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        AnimatedVisibility(
                            visible = option.isUnanimous && !option.isWinner,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            StatusBadgePill(
                                text = stringResource(R.string.badge_unanimous),
                                tint = colors.emerald
                            )
                        }

                        AnimatedVisibility(
                            visible = option.isLeading && !option.isUnanimous && !option.isWinner,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            StatusBadgePill(
                                text = stringResource(R.string.badge_leading),
                                tint = colors.vermilion
                            )
                        }

                        AnimatedVisibility(
                            visible = option.isTiedLeader && !option.isWinner,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            StatusBadgePill(
                                text = stringResource(R.string.badge_tied),
                                tint = colors.inkSecondary
                            )
                        }

                        AnimatedVisibility(
                            visible = option.isDeclinedByMe && !option.isVotedByMe && !option.isWinner,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            StatusBadgePill(
                                text = stringResource(R.string.badge_declined),
                                tint = colors.inkSecondary
                            )
                        }

                        AnimatedVisibility(
                            visible = option.isPendingSync,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            StatusBadgePill(
                                text = stringResource(R.string.badge_queued),
                                tint = Color(0xFFD97706)
                            )
                        }

                        AnimatedVisibility(
                            visible = option.hasPriorityConflict && !option.isWinner,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            StatusBadgePill(
                                text = stringResource(R.string.conflict_badge_pill),
                                tint = Color(0xFFD97706)
                            )
                        }

                        AnimatedVisibility(
                            visible = option.isWinner,
                            enter = fadeIn(tween(180)),
                            exit = fadeOut(tween(140))
                        ) {
                            StatusBadgePill(
                                text = stringResource(R.string.badge_winner),
                                tint = colors.emerald
                            )
                        }
                    }

                    if (option.voters.isNotEmpty()) {
                        // Kinetic Decision Line: small avatars visibly lean & push the vertical decision line forward
                        val lineAccent = if (option.isWinner || option.isUnanimous) colors.emerald else colors.vermilion
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .testTag("option_push_track_${option.id}"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            val trackWidth = maxWidth
                            val avatarSize = 20.dp
                            val visiblePushers = option.voters.take(3)
                            val clusterWidth = avatarSize + 10.dp * (visiblePushers.size - 1).coerceAtLeast(0)
                            val tipX = (trackWidth * animatedFraction).coerceIn(10.dp, trackWidth - 4.dp)
                            val clusterX = (tipX - clusterWidth - 3.dp).coerceIn(0.dp, (trackWidth - clusterWidth - 6.dp).coerceAtLeast(0.dp))

                            // Base hairline rail
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(colors.hairline.copy(alpha = 0.7f))
                            )

                            // Pushed filled segment up to the moving decision line
                            Box(
                                modifier = Modifier
                                    .width(tipX)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(lineAccent.copy(alpha = 0.65f))
                            )

                            // Moving vertical "line of the decision" being pushed
                            Box(
                                modifier = Modifier
                                    .offset(x = tipX - 1.5.dp)
                                    .width(3.dp)
                                    .height(18.dp)
                                    .clip(CircleShape)
                                    .background(lineAccent)
                            )

                            // Small avatars positioned right behind the moving line pushing it forward
                            Row(
                                modifier = Modifier.offset(x = clusterX),
                                horizontalArrangement = Arrangement.spacedBy((-10).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                visiblePushers.forEach { voter ->
                                    GeometricAvatar(
                                        avatarStyleIndex = voter.avatarStyleIndex,
                                        colorIndex = voter.badgeColorIndex,
                                        size = avatarSize,
                                        borderColor = colors.surfaceElevated,
                                        reactionTrigger = option.voteCount,
                                        pushBias = if (isRoomLocked && !option.isWinner) 0f else 1f,
                                        nudgePulse = nudgePulse
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            option.voters.forEach { voter ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceInset)
                                        .border(0.75.dp, colors.hairline, RoundedCornerShape(12.dp))
                                        .padding(start = 3.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    GeometricAvatar(
                                        avatarStyleIndex = voter.avatarStyleIndex,
                                        colorIndex = voter.badgeColorIndex,
                                        size = 18.dp,
                                        borderColor = colors.surfaceInset,
                                        reactionTrigger = option.voteCount,
                                        pushBias = if (isRoomLocked && !option.isWinner) 0f else 1f,
                                        nudgePulse = nudgePulse
                                    )
                                    Text(
                                        text = voter.initials,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 10.sp,
                                        color = colors.inkSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    RollingTabularText(
                        value = option.percentage,
                        suffix = "%",
                        style = MonoTabularTextStyle.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when {
                                option.isWinner || option.isUnanimous -> colors.emerald
                                option.isLeading -> colors.vermilion
                                else -> colors.inkPrimary
                            }
                        )
                    )
                    RollingTabularText(
                        value = option.voteCount,
                        suffix = if (option.voteCount == 1) " vote" else " votes",
                        style = MonoTabularTextStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = colors.inkSecondary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadgePill(
    text: String,
    tint: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(tint.copy(alpha = 0.13f))
            .border(0.8.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.7.sp,
            color = tint
        )
    }
}
