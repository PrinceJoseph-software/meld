package io.github.princejoseph.meld.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.princejoseph.meld.ui.theme.MeldTheme

@Composable
fun MeldSpringSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current

    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.vermilion else colors.surfaceInset,
        animationSpec = tween(180),
        label = "switch_track"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) colors.vermilion else colors.hairline,
        animationSpec = tween(180),
        label = "switch_border"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = spring(stiffness = 520f, dampingRatio = 0.72f),
        label = "switch_thumb"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    view.performTactileTick()
                    onCheckedChange(!checked)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
fun AnimatedCheckmarkBox(
    checked: Boolean,
    isWinner: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val activeColor = if (isWinner) colors.emerald else colors.vermilion

    val fillColor by animateColorAsState(
        targetValue = if (checked || isWinner) activeColor else Color.Transparent,
        animationSpec = tween(160),
        label = "check_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked || isWinner) activeColor else colors.inkSecondary.copy(alpha = 0.45f),
        animationSpec = tween(160),
        label = "check_border"
    )
    val pathProgress by animateFloatAsState(
        targetValue = if (checked || isWinner) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "check_path"
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(fillColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (pathProgress > 0.01f) {
            Canvas(modifier = Modifier.size(14.dp)) {
                val fullPath = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.52f)
                    lineTo(size.width * 0.42f, size.height * 0.78f)
                    lineTo(size.width * 0.86f, size.height * 0.24f)
                }
                val measure = PathMeasure().apply { setPath(fullPath, false) }
                val animatedPath = Path()
                measure.getSegment(0f, measure.length * pathProgress, animatedPath, true)
                drawPath(
                    path = animatedPath,
                    color = Color.White,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

@Composable
fun AnimatedCrownIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = 440f, dampingRatio = 0.62f),
        label = "crown_scale"
    )
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width * scale
        val h = size.height * scale
        val ox = (size.width - w) / 2f
        val oy = (size.height - h) / 2f
        val path = Path().apply {
            moveTo(ox + w * 0.12f, oy + h * 0.78f)
            lineTo(ox + w * 0.12f, oy + h * 0.30f)
            lineTo(ox + w * 0.36f, oy + h * 0.54f)
            lineTo(ox + w * 0.50f, oy + h * 0.20f)
            lineTo(ox + w * 0.64f, oy + h * 0.54f)
            lineTo(ox + w * 0.88f, oy + h * 0.30f)
            lineTo(ox + w * 0.88f, oy + h * 0.78f)
            close()
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
