package io.github.princejoseph.meld.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.ui.theme.MeldTheme
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ThemeToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val description = stringResource(R.string.cd_toggle_theme)

    val rotation by animateFloatAsState(
        targetValue = if (isDark) 180f else 0f,
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.72f),
        label = "theme_toggle_rot"
    )
    val morph by animateFloatAsState(
        targetValue = if (isDark) 1f else 0f,
        animationSpec = spring(stiffness = 420f, dampingRatio = 0.78f),
        label = "theme_toggle_morph"
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = {
                    view.performTactileTick()
                    onToggle()
                }
            )
            .semantics { contentDescription = description }
            .testTag("theme_toggle_button"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated)
                .border(1.dp, colors.hairline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(18.dp)) {
                val strokeWidth = 1.5.dp.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)

                rotate(rotation, pivot = center) {
                    if (morph < 0.5f) {
                        // Crisp 1.5px geometric Sun
                        val sunRadius = size.minDimension * 0.22f
                        drawCircle(
                            color = colors.inkPrimary,
                            radius = sunRadius,
                            center = center,
                            style = Stroke(width = strokeWidth)
                        )
                        val innerRay = size.minDimension * 0.34f
                        val outerRay = size.minDimension * 0.45f
                        for (i in 0 until 8) {
                            val angle = Math.toRadians(i * 45.0)
                            val sx = center.x + (innerRay * cos(angle)).toFloat()
                            val sy = center.y + (innerRay * sin(angle)).toFloat()
                            val ex = center.x + (outerRay * cos(angle)).toFloat()
                            val ey = center.y + (outerRay * sin(angle)).toFloat()
                            drawLine(
                                color = colors.inkPrimary,
                                start = Offset(sx, sy),
                                end = Offset(ex, ey),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    } else {
                        // Crisp 1.5px geometric Crescent Moon
                        val r = size.minDimension * 0.36f
                        val path = androidx.compose.ui.graphics.Path().apply {
                            addArc(
                                oval = androidx.compose.ui.geometry.Rect(
                                    center.x - r,
                                    center.y - r,
                                    center.x + r,
                                    center.y + r
                                ),
                                startAngleDegrees = -65f,
                                sweepAngleDegrees = 250f
                            )
                            val innerOffset = r * 0.42f
                            arcTo(
                                rect = androidx.compose.ui.geometry.Rect(
                                    center.x - r + innerOffset,
                                    center.y - r - innerOffset * 0.45f,
                                    center.x + r + innerOffset,
                                    center.y + r - innerOffset * 0.45f
                                ),
                                startAngleDegrees = 175f,
                                sweepAngleDegrees = -215f,
                                forceMoveTo = false
                            )
                            close()
                        }
                        drawPath(
                            path = path,
                            color = colors.inkPrimary,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
