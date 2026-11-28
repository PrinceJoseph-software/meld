package io.github.princejoseph.meld.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.princejoseph.meld.ui.theme.MeldTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Size of the mark in the top bar's MeldLogo (its Canvas is 36dp, centred 34dp from the left edge). */
private val LOGO_MARK_SIZE = 36.dp
private val LOGO_CENTER_X = 34.dp
private val LOGO_CENTER_Y = 28.dp // centre of the 56dp top bar, below the status bar
private val SPLASH_MARK_SIZE = 132.dp

/**
 * Full-screen launch animation (cold start only). The two Meld capsules roll in from opposite
 * edges and lock together with a small impact, the diamond pops in, then the finished mark glides
 * into the top-bar logo while the background fades away to reveal the app.
 *
 * It starts on the same plain background the system splash shows, so the hand-off is seamless.
 * Skipped when the user has turned animations off. Takes ~1.4s; touches are swallowed meanwhile.
 */
@Composable
fun MeldSplash(onFinished: () -> Unit) {
    val colors = MeldTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)

    val rollIn = remember { Animatable(1f) }        // 1 = off-screen, 0 = joined
    val squash = remember { Animatable(1f) }
    val shockwave = remember { Animatable(1f) }     // 0..1 expanding ring, 1 = hidden
    val diamond = remember { Animatable(0f) }
    val dock = remember { Animatable(0f) }          // 0 = centre of screen, 1 = top-bar logo

    LaunchedEffect(Unit) {
        val animationsOff = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        if (animationsOff) {
            onFinished()
            return@LaunchedEffect
        }
        delay(80L) // let the first frame land on screen
        rollIn.animateTo(0f, tween(520, easing = CubicBezierEasing(0.16f, 0.84f, 0.28f, 1f)))
        if (hapticsGlobalEnabled) runCatching { view.performTactileTick() }
        // Impact squash and ring run in the background; the sequence only waits for the diamond.
        launch {
            squash.snapTo(0.86f)
            squash.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f))
        }
        launch {
            shockwave.snapTo(0f)
            shockwave.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
        delay(50L)
        diamond.animateTo(1f, tween(260, easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)))
        delay(80L)
        dock.animateTo(1f, tween(420, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
        onFinished()
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("meld_splash")
    ) {
        val d = dock.value
        val bgAlpha = (1f - d * 1.35f).coerceIn(0f, 1f)
        drawRect(colors.canvas.copy(alpha = bgAlpha))

        val start = Offset(size.width / 2f, size.height / 2f)
        val end = Offset(LOGO_CENTER_X.toPx(), statusBarTop + LOGO_CENTER_Y.toPx())
        val center = Offset(lerp(start.x, end.x, d), lerp(start.y, end.y, d))
        val markPx = lerp(SPLASH_MARK_SIZE.toPx(), LOGO_MARK_SIZE.toPx(), d)

        // Collision ring
        val ring = shockwave.value
        if (ring < 0.99f) {
            drawCircle(
                color = colors.vermilion.copy(alpha = (1f - ring) * 0.6f),
                radius = lerp(markPx * 0.25f, markPx * 1.15f, ring),
                center = center,
                style = Stroke(width = lerp(5f, 1f, ring).dp.toPx())
            )
        }

        val travel = (size.width / 2f + markPx) * rollIn.value
        val spin = 300f * rollIn.value
        scale(squash.value, 2f - squash.value, pivot = center) {
            drawMeldMark(
                center = center,
                markPx = markPx,
                inkOffset = Offset(-travel, 0f),
                inkRotation = 35f - spin,
                accentOffset = Offset(travel, 0f),
                accentRotation = -35f + spin,
                diamondScale = diamond.value,
                ink = colors.inkPrimary,
                accent = colors.vermilion,
                background = colors.canvas,
            )
        }
    }
}

/**
 * The Meld mark at [markPx] (same proportions as MeldLogo's 36dp mark): an ink capsule at +35°,
 * an accent capsule at -35° with a background-coloured cut-out where it crosses, and a diamond.
 */
private fun DrawScope.drawMeldMark(
    center: Offset,
    markPx: Float,
    inkOffset: Offset,
    inkRotation: Float,
    accentOffset: Offset,
    accentRotation: Float,
    diamondScale: Float,
    ink: Color,
    accent: Color,
    background: Color,
) {
    val w = markPx * 0.74f
    val h = markPx * 0.30f
    val cut = markPx * (2f / 36f)
    fun capsule(color: Color, grow: Float = 0f) = drawRoundRect(
        color = color,
        topLeft = Offset(center.x - w / 2f - grow, center.y - h / 2f - grow),
        size = Size(w + grow * 2f, h + grow * 2f),
        cornerRadius = CornerRadius((h + grow * 2f) / 2f),
    )
    translate(inkOffset.x, inkOffset.y) { rotate(inkRotation, pivot = center) { capsule(ink) } }
    translate(accentOffset.x, accentOffset.y) {
        rotate(accentRotation, pivot = center) {
            capsule(background, grow = cut)
            capsule(accent)
        }
    }
    if (diamondScale > 0.02f) {
        val r = h * 0.40f * diamondScale
        val diamondPath = Path().apply {
            moveTo(center.x, center.y - r)
            lineTo(center.x + r, center.y)
            lineTo(center.x, center.y + r)
            lineTo(center.x - r, center.y)
            close()
        }
        drawPath(diamondPath, background)
        drawPath(diamondPath, ink, style = Stroke(width = markPx * (1.4f / 36f)))
    }
}

