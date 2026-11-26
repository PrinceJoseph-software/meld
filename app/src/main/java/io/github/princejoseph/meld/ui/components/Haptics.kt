package io.github.princejoseph.meld.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

private fun Context.resolveVibrator(): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

var hapticsGlobalEnabled: Boolean = true

private fun View.vibrateSafe(effectId: Int, fallbackMs: Long, amplitude: Int) {
    if (!hapticsGlobalEnabled) return
    runCatching {
        val vibrator = context.resolveVibrator() ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(fallbackMs)
        }
    }
}

/** Crisp light tick for toggles, mode tabs, avatar picks, and drag steps. */
fun View.performTactileTick() {
    if (!hapticsGlobalEnabled) return
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CLOCK_TICK
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(constant)
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.EFFECT_TICK
    } else {
        0
    }
    vibrateSafe(effect, fallbackMs = 10L, amplitude = 90)
}

/** Decisive click for casting/un-casting a vote on a poll option or time slot. */
fun View.performTactileVote() {
    if (!hapticsGlobalEnabled) return
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONTEXT_CLICK
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
    performHapticFeedback(constant)
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.EFFECT_CLICK
    } else {
        0
    }
    vibrateSafe(effect, fallbackMs = 16L, amplitude = 150)
}

/** Authoritative double/heavy confirmation for creating rooms, copying links, and crowning a winner. */
fun View.performTactileConfirm() {
    if (!hapticsGlobalEnabled) return
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.EFFECT_HEAVY_CLICK
    } else {
        0
    }
    vibrateSafe(effect, fallbackMs = 24L, amplitude = 210)
}
