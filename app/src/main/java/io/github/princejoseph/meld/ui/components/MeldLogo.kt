package io.github.princejoseph.meld.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeldLogo(
    pulseTrigger: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val logoDescription = stringResource(R.string.cd_meld_logo)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    var replaySequence by remember { mutableIntStateOf(0) }

    // Non-blocking kinetic app-open choreography:
    // 1) Two physical circular pucks roll in fast from opposite sides
    // 2) They hit each other edge-to-edge with a shockwave & squash bounce
    // 3) They swirl around a decaying orbit so both discs are distinctly visible
    // 4) They morph & extrude with real-life bevel depth into the interlocking Meld icon
    val sideOffsetDp = remember { Animatable(92f) }
    val pucksAlpha = remember { Animatable(0f) }
    val rollAngle = remember { Animatable(360f) }
    val impactSquash = remember { Animatable(1f) }
    val orbitRadiusDp = remember { Animatable(0f) }
    val swirlAngleDeg = remember { Animatable(0f) }
    val morphToCapsule = remember { Animatable(0f) }
    val diamondAppear = remember { Animatable(0f) }
    val shockwaveProgress = remember { Animatable(1f) }
    val wordmarkAlpha = remember { Animatable(1f) }

    LaunchedEffect(replaySequence) {
        coroutineScope {
            // Reset start pose: wide sides off-center, full circles
            sideOffsetDp.snapTo(92f)
            pucksAlpha.snapTo(if (replaySequence == 0) 0f else 1f)
            rollAngle.snapTo(360f)
            impactSquash.snapTo(1f)
            orbitRadiusDp.snapTo(0f)
            swirlAngleDeg.snapTo(0f)
            morphToCapsule.snapTo(0f)
            diamondAppear.snapTo(0f)
            shockwaveProgress.snapTo(1f)
            wordmarkAlpha.snapTo(if (replaySequence == 0) 0.55f else 0.75f)

            // Wait for Android window open transition to settle so cold launch plays identically to tapping the logo
            if (replaySequence == 0) {
                delay(280L)
            }

            // Phase 1: Fast roll-in from sides toward edge-to-edge impact (~6.2dp from center)
            launch {
                pucksAlpha.animateTo(1f, tween(durationMillis = 55))
            }
            val rollIn = launch {
                sideOffsetDp.animateTo(
                    targetValue = 6.2f,
                    animationSpec = tween(
                        durationMillis = 330,
                        easing = CubicBezierEasing(0.22f, 0.96f, 0.36f, 1.0f)
                    )
                )
            }
            launch {
                rollAngle.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                )
            }
            launch {
                wordmarkAlpha.animateTo(1f, tween(durationMillis = 380))
            }
            rollIn.join()

            // Phase 2: Impact collision — shockwave ring + tactile squash + rebound into orbit
            launch {
                shockwaveProgress.snapTo(0f)
                shockwaveProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                )
            }
            launch {
                impactSquash.snapTo(0.84f)
                impactSquash.animateTo(1f, spring(stiffness = 620f, dampingRatio = 0.58f))
            }
            sideOffsetDp.snapTo(0f)
            orbitRadiusDp.snapTo(6.2f)
            orbitRadiusDp.animateTo(
                targetValue = 9.5f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
            )

            // Phase 3: Orbital swirl — both discs chase & swirl cleanly around center while spiraling inward
            val swirl = launch {
                swirlAngleDeg.snapTo(0f)
                swirlAngleDeg.animateTo(
                    targetValue = 410f,
                    animationSpec = tween(durationMillis = 440, easing = CubicBezierEasing(0.32f, 0.0f, 0.2f, 1.0f))
                )
            }
            val spiralIn = launch {
                delay(130L)
                orbitRadiusDp.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(stiffness = 280f, dampingRatio = 0.72f)
                )
            }

            // Phase 4: Real-life extrusion & interlocking Meld icon render
            launch {
                delay(240L)
                morphToCapsule.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(stiffness = 460f, dampingRatio = 0.68f)
                )
            }
            launch {
                delay(390L)
                diamondAppear.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(stiffness = 520f, dampingRatio = 0.62f)
                )
            }
            swirl.join()
            spiralIn.join()
        }
    }

    // Ambient Idle: 5-second breathing scale (1.0 -> 1.04 -> 1.0) on center diamond only
    val infiniteTransition = rememberInfiniteTransition(label = "meld_idle")
    val diamondBreath by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "diamond_breath"
    )

    // Interactive Press / Tap: separate capsules by 3dp along their axis
    val pressSeparation by animateDpAsState(
        targetValue = if (isPressed) 3.dp else 0.dp,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.65f),
        label = "press_separation"
    )

    // Vote / Room Created Pulse: 1.18x rotational snap [0, -8, 4, 0]
    val pulseScale = remember { Animatable(1f) }
    val pulseRotation = remember { Animatable(0f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger > 0) {
            launch {
                pulseScale.snapTo(1f)
                pulseScale.animateTo(1.18f, spring(stiffness = 700f, dampingRatio = 0.55f))
                pulseScale.animateTo(1f, spring(stiffness = 460f, dampingRatio = 0.65f))
            }
            launch {
                pulseRotation.snapTo(0f)
                pulseRotation.animateTo(-8f, tween(70, easing = FastOutSlowInEasing))
                pulseRotation.animateTo(4f, tween(80, easing = FastOutSlowInEasing))
                pulseRotation.animateTo(0f, spring(stiffness = 460f, dampingRatio = 0.68f))
            }
        }
    }

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    view.performTactileVote()
                    replaySequence += 1
                    onClick()
                }
            )
            .semantics { contentDescription = logoDescription }
            .padding(end = 8.dp)
            .testTag("meld_logo_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val w = size.width
            val h = size.height
            val center = Offset(w / 2f, h / 2f)
            val alpha = pucksAlpha.value.coerceIn(0f, 1f)

            val m = morphToCapsule.value.coerceIn(0f, 1.12f)
            val mClamped = m.coerceIn(0f, 1f)
            val circleDiameter = h * 0.36f
            val finalW = w * 0.74f
            val finalH = h * 0.30f

            val capsuleW = lerp(circleDiameter, finalW, mClamped)
            val capsuleH = lerp(circleDiameter, finalH, mClamped)
            val corner = CornerRadius(capsuleH / 2f, capsuleH / 2f)
            val cutoutPx = 2.dp.toPx() * mClamped

            val sidePx = sideOffsetDp.value.dp.toPx()
            val orbitPx = orbitRadiusDp.value.dp.toPx()
            val orbitRad = Math.toRadians(swirlAngleDeg.value.toDouble())
            val orbitDx = (orbitPx * cos(orbitRad)).toFloat()
            val orbitDy = (orbitPx * sin(orbitRad)).toFloat()
            val sepPx = pressSeparation.toPx()

            val squashX = impactSquash.value
            val squashY = if (squashX < 0.999f) 1f + (1f - squashX) * 0.85f else 1f

            // Collision shockwave ring
            val shock = shockwaveProgress.value
            if (shock < 0.98f) {
                drawCircle(
                    color = colors.vermilion.copy(alpha = (1f - shock) * 0.72f * alpha),
                    radius = lerp(w * 0.16f, w * 0.76f, shock),
                    center = center,
                    style = Stroke(width = lerp(2.2f, 0.7f, shock).dp.toPx())
                )
            }

            withTransform({
                scale(pulseScale.value, pulseScale.value, pivot = center)
                rotate(pulseRotation.value, pivot = center)
            }) {
                // Left / Ink physical disc -> rolls in fast, swirls, morphs into +35deg capsule with soft cast shadow & specular rim
                val inkOffsetX = -sidePx * 0.6f - orbitDx - sepPx * 0.45f
                val inkOffsetY = -orbitDy - sepPx * 0.25f

                withTransform({
                    translate(left = inkOffsetX, top = inkOffsetY)
                    scale(squashX, squashY, pivot = center)
                    rotate(35f * mClamped - rollAngle.value, pivot = center)
                }) {
                    // Subtle physical elevation shadow
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.14f * alpha),
                        topLeft = Offset(
                            center.x - capsuleW / 2f + 0.8.dp.toPx(),
                            center.y - capsuleH / 2f + 1.4.dp.toPx()
                        ),
                        size = Size(capsuleW, capsuleH),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = colors.inkPrimary.copy(alpha = alpha),
                        topLeft = Offset(center.x - capsuleW / 2f, center.y - capsuleH / 2f),
                        size = Size(capsuleW, capsuleH),
                        cornerRadius = corner
                    )
                    // Physical top-edge specular sheen
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f * alpha),
                                Color.Transparent
                            )
                        ),
                        topLeft = Offset(center.x - capsuleW / 2f, center.y - capsuleH / 2f),
                        size = Size(capsuleW, capsuleH),
                        cornerRadius = corner
                    )
                }

                // Right / Vermilion physical disc -> rolls in fast from the right side of the header, swirls, morphs into -35deg interlocking capsule
                val vermOffsetX = sidePx * 1.45f + orbitDx + sepPx * 0.45f
                val vermOffsetY = orbitDy + sepPx * 0.25f

                withTransform({
                    translate(left = vermOffsetX, top = vermOffsetY)
                    scale(squashX, squashY, pivot = center)
                    rotate(-35f * mClamped + rollAngle.value, pivot = center)
                }) {
                    if (cutoutPx > 0.2f) {
                        drawRoundRect(
                            color = colors.canvas.copy(alpha = alpha),
                            topLeft = Offset(
                                center.x - capsuleW / 2f - cutoutPx,
                                center.y - capsuleH / 2f - cutoutPx
                            ),
                            size = Size(capsuleW + cutoutPx * 2f, capsuleH + cutoutPx * 2f),
                            cornerRadius = CornerRadius((capsuleH + cutoutPx * 2f) / 2f)
                        )
                    }
                    // Subtle physical elevation shadow
                    drawRoundRect(
                        color = colors.vermilion.copy(alpha = 0.24f * alpha),
                        topLeft = Offset(
                            center.x - capsuleW / 2f + 0.8.dp.toPx(),
                            center.y - capsuleH / 2f + 1.4.dp.toPx()
                        ),
                        size = Size(capsuleW, capsuleH),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = colors.vermilion.copy(alpha = alpha),
                        topLeft = Offset(center.x - capsuleW / 2f, center.y - capsuleH / 2f),
                        size = Size(capsuleW, capsuleH),
                        cornerRadius = corner
                    )
                    // Physical top-edge specular sheen
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f * alpha),
                                Color.Transparent
                            )
                        ),
                        topLeft = Offset(center.x - capsuleW / 2f, center.y - capsuleH / 2f),
                        size = Size(capsuleW, capsuleH),
                        cornerRadius = corner
                    )
                }

                // Center intersection diamond locks in after the two discs finish morphing
                val dScale = diamondAppear.value.coerceAtLeast(0f)
                if (dScale > 0.05f) {
                    val diamondRadius = finalH * 0.40f * diamondBreath * dScale
                    val diamondPath = Path().apply {
                        moveTo(center.x, center.y - diamondRadius)
                        lineTo(center.x + diamondRadius, center.y)
                        lineTo(center.x, center.y + diamondRadius)
                        lineTo(center.x - diamondRadius, center.y)
                        close()
                    }
                    drawPath(
                        path = diamondPath,
                        color = colors.canvas
                    )
                    drawPath(
                        path = diamondPath,
                        color = colors.inkPrimary,
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }
        }

        Text(
            text = "Meld",
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            letterSpacing = (-0.7).sp,
            color = colors.inkPrimary.copy(alpha = wordmarkAlpha.value)
        )
    }
}
