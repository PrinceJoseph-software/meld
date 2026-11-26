package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AvatarSpec
import com.example.data.VoterChip
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.VoterBadgeColors
import kotlinx.coroutines.launch

@Composable
fun GeometricAvatar(
    avatarStyleIndex: Int,
    colorIndex: Int,
    size: Dp = 28.dp,
    borderColor: Color = MeldTheme.colors.canvas,
    reactionTrigger: Int = 0,
    pushBias: Float = 0f,
    nudgePulse: Int = 0,
    modifier: Modifier = Modifier
) {
    val bg = VoterBadgeColors[colorIndex.coerceIn(0, VoterBadgeColors.lastIndex)]
    val spec = remember(avatarStyleIndex) { AvatarSpec.fromPacked(avatarStyleIndex) }

    val popScale = remember { Animatable(0.65f) }
    val tiltAngle = remember { Animatable(pushBias * 8f) }
    val lungeX = remember { Animatable(0f) }
    val hopY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        popScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = 520f, dampingRatio = 0.56f)
        )
    }

    LaunchedEffect(pushBias) {
        tiltAngle.animateTo(
            targetValue = pushBias * 9f,
            animationSpec = spring(stiffness = 400f, dampingRatio = 0.7f)
        )
    }

    LaunchedEffect(reactionTrigger, nudgePulse) {
        if (reactionTrigger > 0 || nudgePulse > 0) {
            val dir = if (pushBias == 0f) 1f else pushBias
            launch {
                popScale.animateTo(1.18f, spring(stiffness = 720f, dampingRatio = 0.52f))
                popScale.animateTo(1f, spring(stiffness = 460f, dampingRatio = 0.65f))
            }
            launch {
                lungeX.animateTo(dir * 7f, tween(90, easing = FastOutSlowInEasing))
                lungeX.animateTo(0f, spring(stiffness = 380f, dampingRatio = 0.52f))
            }
            launch {
                hopY.animateTo(-4f, tween(80, easing = FastOutSlowInEasing))
                hopY.animateTo(0f, spring(stiffness = 460f, dampingRatio = 0.58f))
            }
            launch {
                tiltAngle.animateTo(dir * 19f, tween(75, easing = FastOutSlowInEasing))
                tiltAngle.animateTo(pushBias * 9f, spring(stiffness = 420f, dampingRatio = 0.62f))
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = popScale.value
                scaleY = popScale.value
                rotationZ = tiltAngle.value
                translationX = lungeX.value
                translationY = hopY.value
            }
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val cx = w / 2f
            val cy = h / 2f
            val fg = Color.White
            val ink = Color(0xFF121214)

            // 1. Background Pattern Layer
            drawPatternLayer(spec.pattern, w, h, cx, cy, fg, ink)

            // 2. Chassis / Face Structure Layer
            drawChassisLayer(spec.chassis, w, h, cx, cy, fg, ink)

            // 3. Eye Expression Layer
            drawEyesLayer(spec.eyes, w, h, cx, cy, fg, ink)

            // 4. Accessory / Headgear Layer
            drawAccessoryLayer(spec.accessory, w, h, cx, cy, fg, ink)

            // 5. Frame / Rim Detail Layer
            drawFrameLayer(spec.frame, w, h, cx, cy, fg, ink)
        }
    }
}

private fun DrawScope.drawPatternLayer(
    pattern: Int,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    fg: Color,
    ink: Color
) {
    when (pattern) {
        1 -> {
            // Diagonal Stripes
            val stroke = w * 0.08f
            for (i in -1..2) {
                val offset = i * w * 0.34f
                drawLine(
                    color = ink.copy(alpha = 0.22f),
                    start = Offset(offset, h),
                    end = Offset(offset + w, 0f),
                    strokeWidth = stroke
                )
            }
        }
        2 -> {
            // Micro Dot Grid
            val step = w * 0.25f
            val r = w * 0.032f
            for (yStep in 1..3) {
                for (xStep in 1..3) {
                    drawCircle(
                        color = fg.copy(alpha = 0.24f),
                        radius = r,
                        center = Offset(xStep * step, yStep * step)
                    )
                }
            }
        }
        3 -> {
            // Concentric Hardware Ring
            drawCircle(
                color = fg.copy(alpha = 0.22f),
                radius = w * 0.36f,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.06f)
            )
        }
        4 -> {
            // Bauhaus Quarter Split
            val splitPath = Path().apply {
                moveTo(0f, h)
                lineTo(w, 0f)
                lineTo(w, h)
                close()
            }
            drawPath(splitPath, color = ink.copy(alpha = 0.24f))
        }
        else -> Unit // 0: Solid Matte
    }
}

private fun DrawScope.drawChassisLayer(
    chassis: Int,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    fg: Color,
    ink: Color
) {
    when (chassis) {
        0 -> {
            // Visor Bot
            val visorW = w * 0.64f
            val visorH = h * 0.28f
            drawRoundRect(
                color = ink,
                topLeft = Offset(cx - visorW / 2f, cy - visorH / 2f - h * 0.04f),
                size = Size(visorW, visorH),
                cornerRadius = CornerRadius(visorH / 2f, visorH / 2f)
            )
            drawLine(
                color = fg.copy(alpha = 0.85f),
                start = Offset(cx - w * 0.11f, cy + h * 0.20f),
                end = Offset(cx + w * 0.11f, cy + h * 0.20f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )
        }
        1 -> {
            // Cyclops Aperture
            drawCircle(
                color = fg.copy(alpha = 0.32f),
                radius = w * 0.29f,
                center = Offset(cx, cy - h * 0.02f),
                style = Stroke(width = w * 0.075f)
            )
            drawCircle(
                color = ink.copy(alpha = 0.85f),
                radius = w * 0.18f,
                center = Offset(cx, cy - h * 0.02f)
            )
        }
        2 -> {
            // Bauhaus Split Face
            drawRoundRect(
                color = ink.copy(alpha = 0.28f),
                topLeft = Offset(cx - w * 0.32f, cy - h * 0.20f),
                size = Size(w * 0.64f, h * 0.44f),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
            )
            drawArc(
                color = fg,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.16f, cy + h * 0.06f),
                size = Size(w * 0.32f, h * 0.18f),
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
            )
        }
        3 -> {
            // Stepped Totem
            val mouthW = w * 0.32f
            drawRoundRect(
                color = ink,
                topLeft = Offset(cx - mouthW / 2f, cy + h * 0.14f),
                size = Size(mouthW, h * 0.11f),
                cornerRadius = CornerRadius(h * 0.055f)
            )
        }
        4 -> {
            // Diamond Crest
            rotate(45f, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = fg.copy(alpha = 0.22f),
                    topLeft = Offset(cx - w * 0.26f, cy - h * 0.26f),
                    size = Size(w * 0.52f, h * 0.52f),
                    cornerRadius = CornerRadius(w * 0.08f)
                )
            }
            drawCircle(ink, w * 0.055f, Offset(cx, cy + h * 0.18f))
        }
        5 -> {
            // Tri-Dot Signal
            drawCircle(ink, w * 0.055f, Offset(cx, cy - h * 0.22f))
            drawArc(
                color = fg,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.18f, cy + h * 0.05f),
                size = Size(w * 0.36f, h * 0.22f),
                style = Stroke(width = w * 0.07f, cap = StrokeCap.Round)
            )
        }
        6 -> {
            // Pixel Grid Chassis
            val plateW = w * 0.58f
            val plateH = h * 0.36f
            drawRect(
                color = ink.copy(alpha = 0.8f),
                topLeft = Offset(cx - plateW / 2f, cy - plateH / 2f + h * 0.01f),
                size = Size(plateW, plateH)
            )
            drawLine(
                color = fg.copy(alpha = 0.4f),
                start = Offset(cx - plateW * 0.35f, cy + h * 0.12f),
                end = Offset(cx + plateW * 0.35f, cy + h * 0.12f),
                strokeWidth = w * 0.045f
            )
        }
        7 -> {
            // Orbital Core
            rotate(-24f, pivot = Offset(cx, cy)) {
                drawOval(
                    color = fg.copy(alpha = 0.36f),
                    topLeft = Offset(cx - w * 0.38f, cy - h * 0.14f),
                    size = Size(w * 0.76f, h * 0.28f),
                    style = Stroke(width = w * 0.055f)
                )
            }
            drawCircle(ink.copy(alpha = 0.45f), w * 0.24f, Offset(cx, cy))
        }
        8 -> {
            // Crescent Shield
            drawArc(
                color = ink.copy(alpha = 0.35f),
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = true,
                topLeft = Offset(cx - w * 0.34f, cy - h * 0.34f),
                size = Size(w * 0.68f, h * 0.68f)
            )
            drawCircle(fg, w * 0.055f, Offset(cx, cy + h * 0.18f))
        }
        else -> {
            // 9: Chrono Dial
            drawCircle(
                color = ink.copy(alpha = 0.55f),
                radius = w * 0.30f,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.06f)
            )
            drawLine(
                color = fg.copy(alpha = 0.8f),
                start = Offset(cx, cy + h * 0.18f),
                end = Offset(cx, cy + h * 0.27f),
                strokeWidth = w * 0.055f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawEyesLayer(
    eyes: Int,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    fg: Color,
    ink: Color
) {
    val eyeY = cy - h * 0.04f
    val leftX = cx - w * 0.14f
    val rightX = cx + w * 0.14f
    when (eyes) {
        1 -> {
            // Playful Wink
            drawCircle(fg, w * 0.075f, Offset(leftX, eyeY))
            drawLine(
                color = fg,
                start = Offset(rightX - w * 0.07f, eyeY),
                end = Offset(rightX + w * 0.07f, eyeY),
                strokeWidth = w * 0.075f,
                cap = StrokeCap.Round
            )
        }
        2 -> {
            // Happy Arcs
            drawArc(
                color = fg,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(leftX - w * 0.07f, eyeY - h * 0.05f),
                size = Size(w * 0.14f, h * 0.12f),
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
            )
            drawArc(
                color = fg,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(rightX - w * 0.07f, eyeY - h * 0.05f),
                size = Size(w * 0.14f, h * 0.12f),
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
            )
        }
        3 -> {
            // Starlight Diamonds
            listOf(leftX, rightX).forEach { ex ->
                val r = w * 0.078f
                val d = Path().apply {
                    moveTo(ex, eyeY - r)
                    lineTo(ex + r, eyeY)
                    lineTo(ex, eyeY + r)
                    lineTo(ex - r, eyeY)
                    close()
                }
                drawPath(d, color = fg)
            }
        }
        4 -> {
            // Laser Scan Bar
            drawRoundRect(
                color = fg,
                topLeft = Offset(cx - w * 0.22f, eyeY - h * 0.045f),
                size = Size(w * 0.44f, h * 0.09f),
                cornerRadius = CornerRadius(h * 0.045f)
            )
            drawCircle(ink, w * 0.032f, Offset(cx + w * 0.06f, eyeY))
        }
        5 -> {
            // Determined Slant
            drawLine(
                color = fg,
                start = Offset(leftX - w * 0.06f, eyeY - h * 0.04f),
                end = Offset(leftX + w * 0.06f, eyeY + h * 0.02f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = fg,
                start = Offset(rightX + w * 0.06f, eyeY - h * 0.04f),
                end = Offset(rightX - w * 0.06f, eyeY + h * 0.02f),
                strokeWidth = w * 0.07f,
                cap = StrokeCap.Round
            )
        }
        else -> {
            // 0: Crisp Dots
            val r = w * 0.072f
            drawCircle(fg, r, Offset(leftX, eyeY))
            drawCircle(fg, r, Offset(rightX, eyeY))
        }
    }
}

private fun DrawScope.drawAccessoryLayer(
    accessory: Int,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    fg: Color,
    ink: Color
) {
    when (accessory) {
        1 -> {
            // Radio Antenna
            drawLine(
                color = fg,
                start = Offset(cx, h * 0.24f),
                end = Offset(cx, h * 0.08f),
                strokeWidth = w * 0.055f,
                cap = StrokeCap.Round
            )
            drawCircle(ink, w * 0.07f, Offset(cx, h * 0.08f))
            drawCircle(fg, w * 0.038f, Offset(cx, h * 0.08f))
        }
        2 -> {
            // Studio Headphones
            drawArc(
                color = ink,
                startAngle = 195f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.38f, h * 0.12f),
                size = Size(w * 0.76f, h * 0.56f),
                style = Stroke(width = w * 0.065f, cap = StrokeCap.Round)
            )
            drawRoundRect(
                color = fg,
                topLeft = Offset(w * 0.08f, cy - h * 0.12f),
                size = Size(w * 0.11f, h * 0.24f),
                cornerRadius = CornerRadius(w * 0.05f)
            )
            drawRoundRect(
                color = fg,
                topLeft = Offset(w * 0.81f, cy - h * 0.12f),
                size = Size(w * 0.11f, h * 0.24f),
                cornerRadius = CornerRadius(w * 0.05f)
            )
        }
        3 -> {
            // Geometric Crown Pin
            val crown = Path().apply {
                moveTo(cx - w * 0.18f, h * 0.24f)
                lineTo(cx - w * 0.18f, h * 0.12f)
                lineTo(cx - w * 0.07f, h * 0.18f)
                lineTo(cx, h * 0.09f)
                lineTo(cx + w * 0.07f, h * 0.18f)
                lineTo(cx + w * 0.18f, h * 0.12f)
                lineTo(cx + w * 0.18f, h * 0.24f)
                close()
            }
            drawPath(crown, color = fg)
        }
        4 -> {
            // Halo Ring
            drawOval(
                color = fg.copy(alpha = 0.9f),
                topLeft = Offset(cx - w * 0.22f, h * 0.09f),
                size = Size(w * 0.44f, h * 0.11f),
                style = Stroke(width = w * 0.055f)
            )
        }
        5 -> {
            // Drafting Specs
            val eyeY = cy - h * 0.04f
            drawCircle(
                color = ink,
                radius = w * 0.12f,
                center = Offset(cx - w * 0.14f, eyeY),
                style = Stroke(width = w * 0.05f)
            )
            drawCircle(
                color = ink,
                radius = w * 0.12f,
                center = Offset(cx + w * 0.14f, eyeY),
                style = Stroke(width = w * 0.05f)
            )
            drawLine(
                color = ink,
                start = Offset(cx - w * 0.02f, eyeY),
                end = Offset(cx + w * 0.02f, eyeY),
                strokeWidth = w * 0.05f
            )
        }
        6 -> {
            // Spark Crest
            val s = w * 0.09f
            val spark = Path().apply {
                moveTo(cx, h * 0.07f)
                lineTo(cx + s * 0.7f, h * 0.16f)
                lineTo(cx, h * 0.25f)
                lineTo(cx - s * 0.7f, h * 0.16f)
                close()
            }
            drawPath(spark, color = fg)
        }
        else -> Unit // 0: None
    }
}

private fun DrawScope.drawFrameLayer(
    frame: Int,
    w: Float,
    h: Float,
    cx: Float,
    cy: Float,
    fg: Color,
    ink: Color
) {
    when (frame) {
        1 -> {
            // Double Bezel
            drawCircle(
                color = fg.copy(alpha = 0.55f),
                radius = w * 0.43f,
                center = Offset(cx, cy),
                style = Stroke(width = w * 0.04f)
            )
        }
        2 -> {
            // Dashed Gear Ring
            drawCircle(
                color = ink.copy(alpha = 0.55f),
                radius = w * 0.44f,
                center = Offset(cx, cy),
                style = Stroke(
                    width = w * 0.05f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.09f, w * 0.06f), 0f)
                )
            )
        }
        3 -> {
            // Offset Hardware Corner Arc
            drawArc(
                color = fg.copy(alpha = 0.75f),
                startAngle = -55f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = Offset(w * 0.06f, h * 0.06f),
                size = Size(w * 0.88f, h * 0.88f),
                style = Stroke(width = w * 0.055f, cap = StrokeCap.Round)
            )
        }
        else -> Unit // 0: Clean Ring
    }
}

@Composable
fun OverlappingAvatarStack(
    voters: List<VoterChip>,
    reactionTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    if (voters.isEmpty()) return
    val colors = MeldTheme.colors
    val visible = voters.take(5)
    val overflow = (voters.size - visible.size).coerceAtLeast(0)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        visible.forEachIndexed { idx, voter ->
            GeometricAvatar(
                avatarStyleIndex = voter.avatarStyleIndex,
                colorIndex = voter.badgeColorIndex,
                size = 28.dp,
                borderColor = colors.canvas,
                reactionTrigger = reactionTrigger,
                modifier = Modifier.offset(x = (-7 * idx).dp)
            )
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (-7 * visible.size).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceInset)
                    .border(1.5.dp, colors.canvas, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$overflow",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    color = colors.inkPrimary
                )
            }
        }
    }
}
