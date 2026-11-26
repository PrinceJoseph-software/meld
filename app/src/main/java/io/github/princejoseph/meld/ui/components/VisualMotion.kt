package io.github.princejoseph.meld.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.princejoseph.meld.data.SyncStatus
import io.github.princejoseph.meld.ui.theme.MeldTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Tactile architectural drafting dot-matrix surface pattern.
 *
 * Intended for full-screen backgrounds only (screens, full-screen dialogs) — never inside
 * cards or buttons, where the dots read as noise behind text. The grid is centred so the
 * left/right and top/bottom margins match, and the dot positions are cached per size.
 */
fun Modifier.draftingDotGrid(dotColor: Color): Modifier = this.drawWithCache {
    val spacingPx = 24.dp.toPx()
    val radiusPx = 1.dp.toPx()
    val cols = (size.width / spacingPx).toInt()
    val rows = (size.height / spacingPx).toInt()
    val startX = (size.width - cols * spacingPx) / 2f
    val startY = (size.height - rows * spacingPx) / 2f
    val points = ArrayList<Offset>((cols + 1) * (rows + 1))
    for (r in 0..rows) {
        val y = startY + r * spacingPx
        for (c in 0..cols) {
            points.add(Offset(startX + c * spacingPx, y))
        }
    }
    onDrawBehind {
        drawPoints(
            points = points,
            pointMode = PointMode.Points,
            color = dotColor,
            strokeWidth = radiusPx * 2f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Mechanical split-flap style rolling counter for tabular numerals.
 */
@Composable
fun RollingTabularText(
    value: Int,
    suffix: String = "",
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (targetState >= initialState) {
                (slideInVertically(tween(190)) { it / 2 } + fadeIn(tween(160)))
                    .togetherWith(slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(120)))
            } else {
                (slideInVertically(tween(190)) { -it / 2 } + fadeIn(tween(160)))
                    .togetherWith(slideOutVertically(tween(140)) { it / 2 } + fadeOut(tween(120)))
            }
        },
        label = "rolling_tabular_text",
        modifier = modifier
    ) { target ->
        Text(
            text = "$target$suffix",
            style = style
        )
    }
}

/**
 * Pulsing hardware LED dot indicating Live Sync, Offline Outbox, or Active Syncing.
 */
@Composable
fun LiveHardwareLed(
    syncStatus: SyncStatus,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val infinite = rememberInfiniteTransition(label = "led_pulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (syncStatus == SyncStatus.SYNCING) 360 else 1100,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "led_alpha"
    )

    val ledColor = when (syncStatus) {
        SyncStatus.LIVE -> colors.emerald
        SyncStatus.OFFLINE -> Color(0xFFD97706) // Warm Ochre
        SyncStatus.SYNCING -> colors.vermilion
    }

    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(ledColor.copy(alpha = pulseAlpha))
    )
}

private data class GeoParticle(
    val vx: Float,
    val vy: Float,
    val sizeDp: Float,
    val rotationSpeed: Float,
    val shapeType: Int,
    val colorIndex: Int
)

/**
 * Crisp Swiss geometric particle burst (squares, diamonds, dashes — zero emoji slop).
 */
@Composable
fun GeometricBurstOverlay(
    burstTrigger: Int,
    isWinnerBurst: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (burstTrigger <= 0) return
    val colors = MeldTheme.colors
    val progress = remember { Animatable(0f) }

    val particles = remember(burstTrigger) {
        val rng = Random(burstTrigger * 7919)
        List(if (isWinnerBurst) 24 else 14) { i ->
            val angle = (i.toFloat() / (if (isWinnerBurst) 24f else 14f)) * 6.28318f + rng.nextFloat() * 0.4f
            val speed = 110f + rng.nextFloat() * 210f
            GeoParticle(
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 120f,
                sizeDp = 5f + rng.nextFloat() * 5f,
                rotationSpeed = (rng.nextFloat() - 0.5f) * 320f,
                shapeType = i % 3,
                colorIndex = i % 3
            )
        }
    }

    LaunchedEffect(burstTrigger) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 680, easing = FastOutSlowInEasing)
        )
    }

    if (progress.value < 0.99f) {
        val palette = listOf(colors.vermilion, colors.emerald, colors.inkPrimary)
        Canvas(modifier = modifier.fillMaxSize()) {
            val t = progress.value
            val alpha = (1f - t).coerceIn(0f, 1f)
            val originX = size.width * 0.5f
            val originY = size.height * 0.42f

            particles.forEach { p ->
                val x = originX + p.vx * t
                val y = originY + p.vy * t + 260f * t * t
                val s = p.sizeDp.dp.toPx() * (1f - t * 0.35f)
                val c = palette[p.colorIndex].copy(alpha = alpha)

                rotate(degrees = p.rotationSpeed * t, pivot = Offset(x, y)) {
                    when (p.shapeType) {
                        0 -> drawRect(
                            color = c,
                            topLeft = Offset(x - s / 2f, y - s / 2f),
                            size = Size(s, s)
                        )
                        1 -> {
                            val d = Path().apply {
                                moveTo(x, y - s * 0.65f)
                                lineTo(x + s * 0.65f, y)
                                lineTo(x, y + s * 0.65f)
                                lineTo(x - s * 0.65f, y)
                                close()
                            }
                            drawPath(d, color = c)
                        }
                        else -> drawRect(
                            color = c,
                            topLeft = Offset(x - s, y - s * 0.25f),
                            size = Size(s * 2f, s * 0.5f)
                        )
                    }
                }
            }
        }
    }
}
