package io.github.princejoseph.meld.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.data.OptionUiModel
import io.github.princejoseph.meld.ui.theme.JetBrainsMonoFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.MonoTabularTextStyle
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Prominent live countdown bar for polls with an optional deadline.
 */
@Composable
fun PollDeadlineBanner(
    createdAtEpochMs: Long,
    deadlineEpochMs: Long,
    remainingMs: Long,
    isLocked: Boolean,
    countdownText: String,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val expired = remainingMs <= 0L
    val isUrgent = !expired && !isLocked && remainingMs <= 60_000L

    val totalDurationMs = (deadlineEpochMs - createdAtEpochMs).coerceAtLeast(1L)
    val rawFraction = if (expired || isLocked) 0f else (remainingMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = rawFraction,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "deadline_bar_fraction"
    )

    val accentColor by animateColorAsState(
        targetValue = when {
            expired || isLocked -> colors.emerald
            isUrgent -> colors.vermilion
            else -> colors.inkPrimary
        },
        animationSpec = tween(220),
        label = "deadline_accent"
    )

    val closeTimeText = remember(deadlineEpochMs) {
        val diff = (deadlineEpochMs - createdAtEpochMs).coerceAtLeast(0L)
        val pattern = if (diff >= 20L * 3600_000L) "MMM d, h:mm a" else "h:mm a"
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(deadlineEpochMs))
    }

    val infinite = rememberInfiniteTransition(label = "deadline_urgent_pulse")
    val urgentAlpha by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "urgent_dot_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .border(
                1.dp,
                when {
                    expired -> colors.emerald.copy(alpha = 0.4f)
                    isUrgent -> colors.vermilion.copy(alpha = 0.45f)
                    else -> colors.hairline
                },
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("poll_deadline_banner"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = if (expired || isLocked) Icons.Outlined.LockClock else Icons.Outlined.Timer,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.deadline_card_label),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = colors.inkSecondary
                    )
                    Text(
                        text = if (expired) {
                            stringResource(R.string.deadline_expired_banner)
                        } else {
                            stringResource(R.string.deadline_closes_at, closeTimeText)
                        },
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = colors.inkPrimary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            expired -> colors.emerald.copy(alpha = 0.14f)
                            isUrgent -> colors.vermilion.copy(alpha = 0.14f)
                            else -> colors.surfaceInset
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            expired -> colors.emerald.copy(alpha = 0.35f)
                            isUrgent -> colors.vermilion.copy(alpha = 0.45f)
                            else -> colors.hairline
                        },
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!expired && !isLocked) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                (if (isUrgent) colors.vermilion else colors.emerald)
                                    .copy(alpha = if (isUrgent) urgentAlpha else 1f)
                            )
                    )
                }
                Text(
                    text = if (expired) stringResource(R.string.badge_timer_ended) else countdownText,
                    style = MonoTabularTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    ),
                    modifier = Modifier.testTag("poll_deadline_countdown_text")
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.surfaceInset)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(if (expired) 1f else animatedFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            expired -> colors.emerald.copy(alpha = 0.45f)
                            isUrgent -> colors.vermilion
                            else -> colors.inkPrimary.copy(alpha = 0.72f)
                        }
                    )
            )
        }
    }
}

/**
 * Gamified Decision Momentum Arena: Bauhaus voter avatars physically lean, lunge,
 * and push the decision bar toward consensus or battle head-to-head across the fulcrum.
 */
@Composable
fun DecisionPushArena(
    options: List<OptionUiModel>,
    totalVotes: Int,
    isLocked: Boolean,
    nudgePulse: Int,
    onNudge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val density = LocalDensity.current

    val leader = options.firstOrNull() ?: return
    val challenger = options.getOrNull(1)?.takeIf {
        it.voteCount > 0 && !isLocked && !leader.isUnanimous && !leader.isWinner
    }

    val levelLabel = when {
        isLocked -> stringResource(R.string.arena_level_locked)
        leader.isUnanimous || leader.percentage >= 67 -> stringResource(R.string.arena_level_surge)
        totalVotes >= 2 -> stringResource(R.string.arena_level_rally)
        else -> stringResource(R.string.arena_level_spark)
    }
    val levelTint = when {
        isLocked || leader.isUnanimous -> colors.emerald
        totalVotes >= 2 -> colors.vermilion
        else -> colors.inkSecondary
    }

    val infinite = rememberInfiniteTransition(label = "push_chevron_drift")
    val driftPx by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevron_drift"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(
                1.dp,
                when {
                    isLocked -> colors.emerald.copy(alpha = 0.4f)
                    totalVotes > 0 -> colors.vermilion.copy(alpha = 0.25f)
                    else -> colors.hairline
                },
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = totalVotes > 0) {
                view.performTactileTick()
                onNudge()
            }
            .padding(14.dp)
            .testTag("decision_push_arena"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.arena_label),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.85.sp,
                    color = colors.inkSecondary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(levelTint.copy(alpha = 0.14f))
                        .border(0.8.dp, levelTint.copy(alpha = 0.38f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = levelLabel,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        letterSpacing = 0.7.sp,
                        color = levelTint
                    )
                }
            }

            if (totalVotes > 0 && !isLocked) {
                Row(
                    modifier = Modifier
                        .defaultMinSize(minHeight = 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.vermilion.copy(alpha = 0.10f))
                        .border(1.dp, colors.vermilion.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable {
                            view.performTactileConfirm()
                            onNudge()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("nudge_push_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = colors.vermilion,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = stringResource(R.string.arena_nudge_action),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.6.sp,
                        color = colors.vermilion
                    )
                }
            }
        }

        if (totalVotes == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceInset)
                    .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.arena_idle_prompt),
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = colors.inkSecondary
                )
            }
        } else if (challenger != null) {
            // Head-to-head Tug Push: Leader squad pushes right -> against Challenger squad pushing left <-
            val pairTotal = (leader.voteCount + challenger.voteCount).coerceAtLeast(1)
            val targetRatio = (leader.voteCount.toFloat() / pairTotal.toFloat()).coerceIn(0.18f, 0.82f)
            val fulcrumRatio by animateFloatAsState(
                targetValue = targetRatio,
                animationSpec = spring(stiffness = 320f, dampingRatio = 0.68f),
                label = "tug_fulcrum_ratio"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Left squad pushing RIGHT ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { translationX = driftPx }
                ) {
                    leader.voters.take(3).forEachIndexed { idx, voter ->
                        GeometricAvatar(
                            avatarStyleIndex = voter.avatarStyleIndex,
                            colorIndex = voter.badgeColorIndex,
                            size = 26.dp,
                            borderColor = colors.surfaceElevated,
                            reactionTrigger = totalVotes,
                            pushBias = 1f,
                            nudgePulse = nudgePulse,
                            modifier = Modifier.offset(x = (-6 * idx).dp)
                        )
                    }
                }

                // Calibrated Tug-of-War Push Track
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceInset)
                        .border(1.dp, colors.hairline, RoundedCornerShape(6.dp))
                ) {
                    val trackWidthPx = with(density) { maxWidth.toPx() }
                    val markerPx = (trackWidthPx * fulcrumRatio).roundToInt()

                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fulcrumRatio.coerceAtLeast(0.05f))
                                .background(colors.vermilion.copy(alpha = 0.22f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight((1f - fulcrumRatio).coerceAtLeast(0.05f))
                                .background(colors.inkSecondary.copy(alpha = 0.14f))
                        )
                    }

                    // Tick marks on the track
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val steps = 8
                        for (i in 1 until steps) {
                            val x = size.width * (i.toFloat() / steps.toFloat())
                            drawLine(
                                color = colors.hairline,
                                start = Offset(x, size.height * 0.68f),
                                end = Offset(x, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    // Moving hardware fulcrum puck
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((markerPx - 4).coerceAtLeast(0), 0) }
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(colors.vermilion)
                    )
                }

                // Right squad pushing LEFT <-
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { translationX = -driftPx }
                ) {
                    challenger.voters.take(3).forEachIndexed { idx, voter ->
                        GeometricAvatar(
                            avatarStyleIndex = voter.avatarStyleIndex,
                            colorIndex = voter.badgeColorIndex,
                            size = 26.dp,
                            borderColor = colors.surfaceElevated,
                            reactionTrigger = totalVotes,
                            pushBias = -1f,
                            nudgePulse = nudgePulse,
                            modifier = Modifier.offset(x = (-6 * idx).dp)
                        )
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.arena_tug_caption,
                    leader.label,
                    leader.voteCount,
                    challenger.label,
                    challenger.voteCount
                ),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            // Consensus / Victory Push: all avatars rally and push the block toward the decision line
            val pushProgressTarget = if (isLocked || leader.isUnanimous) 1f else leader.fraction.coerceIn(0.20f, 1f)
            val animatedPush by animateFloatAsState(
                targetValue = pushProgressTarget,
                animationSpec = spring(stiffness = 310f, dampingRatio = 0.68f),
                label = "consensus_push_progress"
            )
            val barTint = if (isLocked || leader.isUnanimous) colors.emerald else colors.vermilion

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.graphicsLayer { translationX = driftPx }
                ) {
                    leader.voters.take(4).forEachIndexed { idx, voter ->
                        GeometricAvatar(
                            avatarStyleIndex = voter.avatarStyleIndex,
                            colorIndex = voter.badgeColorIndex,
                            size = 26.dp,
                            borderColor = colors.surfaceElevated,
                            reactionTrigger = totalVotes,
                            pushBias = 1f,
                            nudgePulse = nudgePulse,
                            modifier = Modifier.offset(x = (-6 * idx).dp)
                        )
                    }
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.surfaceInset)
                        .border(1.dp, colors.hairline, RoundedCornerShape(6.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedPush)
                            .background(barTint.copy(alpha = 0.24f))
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(barTint)
                        )
                    }
                }

                if (isLocked || leader.isUnanimous) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(colors.emerald.copy(alpha = 0.16f))
                            .border(1.dp, colors.emerald.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedCrownIcon(tint = colors.emerald, modifier = Modifier.size(14.dp))
                    }
                } else {
                    Text(
                        text = "${leader.percentage}%",
                        style = MonoTabularTextStyle.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.vermilion
                        )
                    )
                }
            }

            Text(
                text = if (isLocked) {
                    stringResource(R.string.arena_winner_caption, leader.label)
                } else {
                    stringResource(R.string.arena_consensus_caption, leader.label, leader.percentage)
                },
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = if (isLocked || leader.isUnanimous) colors.emerald else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
