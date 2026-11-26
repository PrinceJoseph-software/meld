package com.example.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.example.data.AvatarSpec
import com.example.data.RoomDetailsUiModel
import com.example.ui.theme.VoterBadgeColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure Android Canvas renderer for custom Bauhaus GeometricAvatars (used in Glance Home Screen
 * Widgets and high-res Share Cards) so avatar customization appears identically on and off app.
 */
object AvatarBitmapRenderer {
    fun renderAvatarBitmap(
        avatarStyleIndex: Int,
        colorIndex: Int,
        sizePx: Int = 72,
        borderColorArgb: Int = 0xFFFFFFFF.toInt()
    ): Bitmap {
        val safePx = sizePx.coerceIn(32, 160)
        val bitmap = Bitmap.createBitmap(safePx, safePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgArgb = VoterBadgeColors[colorIndex.coerceIn(0, VoterBadgeColors.lastIndex)].toArgb()
        val spec = AvatarSpec.fromPacked(avatarStyleIndex)
        val w = safePx.toFloat()
        val h = safePx.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = min(w, h) / 2f

        val clipPath = Path().apply {
            addCircle(cx, cy, radius - 1f, Path.Direction.CW)
        }

        canvas.save()
        canvas.clipPath(clipPath)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgArgb }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val fg = 0xFFFFFFFF.toInt()
        val ink = 0xFF121214.toInt()

        drawPatternLayer(canvas, spec.pattern, w, h, cx, cy, fg, ink)
        drawChassisLayer(canvas, spec.chassis, w, h, cx, cy, fg, ink)
        drawEyesLayer(canvas, spec.eyes, w, h, cx, cy, fg, ink)
        drawAccessoryLayer(canvas, spec.accessory, w, h, cx, cy, fg, ink)
        drawFrameLayer(canvas, spec.frame, w, h, cx, cy, fg, ink)

        canvas.restore()

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = (safePx * 0.06f).coerceAtLeast(2f)
            color = borderColorArgb
        }
        canvas.drawCircle(cx, cy, radius - borderPaint.strokeWidth / 2f, borderPaint)
        return bitmap
    }

    private fun drawPatternLayer(
        canvas: Canvas,
        pattern: Int,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        fg: Int,
        ink: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (pattern) {
            1 -> {
                p.color = (fg and 0x00FFFFFF) or 0x33000000
                p.strokeWidth = w * 0.1f
                canvas.save()
                canvas.rotate(-32f, cx, cy)
                for (i in -2..2) {
                    val yOff = cy + i * (h * 0.24f)
                    canvas.drawLine(-w * 0.2f, yOff, w * 1.2f, yOff, p)
                }
                canvas.restore()
            }
            2 -> {
                p.color = (fg and 0x00FFFFFF) or 0x3D000000
                val r = w * 0.055f
                listOf(
                    0.22f to 0.24f, 0.78f to 0.24f,
                    0.22f to 0.78f, 0.78f to 0.78f
                ).forEach { (fx, fy) ->
                    canvas.drawCircle(w * fx, h * fy, r, p)
                }
            }
            3 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.05f
                p.color = (fg and 0x00FFFFFF) or 0x42000000
                canvas.drawCircle(cx, cy, w * 0.36f, p)
                canvas.drawCircle(cx, cy, w * 0.21f, p)
            }
            4 -> {
                p.color = (ink and 0x00FFFFFF) or 0x2E000000
                canvas.drawRect(cx, 0f, w, h, p)
            }
        }
    }

    private fun drawChassisLayer(
        canvas: Canvas,
        chassis: Int,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        fg: Int,
        ink: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (chassis) {
            0 -> {
                p.color = fg
                canvas.drawRoundRect(
                    RectF(w * 0.18f, h * 0.27f, w * 0.82f, h * 0.59f),
                    w * 0.16f, w * 0.16f, p
                )
            }
            1 -> {
                p.color = fg
                canvas.drawCircle(cx, cy * 0.94f, w * 0.28f, p)
            }
            2 -> {
                p.color = (ink and 0x00FFFFFF) or 0x3D000000
                canvas.drawArc(RectF(0f, 0f, w, h), 90f, 180f, true, p)
                p.color = fg
                canvas.drawRoundRect(
                    RectF(w * 0.22f, h * 0.30f, w * 0.78f, h * 0.60f),
                    w * 0.12f, w * 0.12f, p
                )
            }
            3 -> {
                p.color = fg
                val path = Path().apply {
                    moveTo(cx, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.74f)
                    lineTo(w * 0.18f, h * 0.74f)
                    close()
                }
                canvas.drawPath(path, p)
            }
            4 -> {
                p.color = fg
                val path = Path().apply {
                    moveTo(cx, h * 0.16f)
                    lineTo(w * 0.82f, cy)
                    lineTo(cx, h * 0.84f)
                    lineTo(w * 0.18f, cy)
                    close()
                }
                canvas.drawPath(path, p)
            }
            5 -> {
                p.color = fg
                canvas.drawRoundRect(
                    RectF(w * 0.22f, h * 0.25f, w * 0.78f, h * 0.65f),
                    w * 0.14f, w * 0.14f, p
                )
                p.color = (fg and 0x00FFFFFF) or 0x47000000
                canvas.drawRoundRect(
                    RectF(w * 0.22f, h * 0.70f, w * 0.78f, h * 0.79f),
                    w * 0.04f, w * 0.04f, p
                )
            }
            6 -> {
                p.color = fg
                canvas.drawRoundRect(
                    RectF(w * 0.20f, h * 0.24f, w * 0.80f, h * 0.70f),
                    w * 0.06f, w * 0.06f, p
                )
            }
            7 -> {
                p.color = fg
                canvas.drawCircle(cx, cy, w * 0.25f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.06f
                p.color = (fg and 0x00FFFFFF) or 0x8C000000.toInt()
                canvas.save()
                canvas.rotate(-22f, cx, cy)
                canvas.drawOval(RectF(w * 0.10f, h * 0.34f, w * 0.90f, h * 0.66f), p)
                canvas.restore()
            }
            8 -> {
                p.color = fg
                val path = Path().apply {
                    moveTo(w * 0.21f, h * 0.23f)
                    lineTo(w * 0.79f, h * 0.23f)
                    lineTo(w * 0.75f, h * 0.56f)
                    lineTo(cx, h * 0.80f)
                    lineTo(w * 0.25f, h * 0.56f)
                    close()
                }
                canvas.drawPath(path, p)
            }
            9 -> {
                p.color = fg
                canvas.drawCircle(cx, cy, w * 0.29f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.055f
                p.color = ink
                canvas.drawLine(cx, h * 0.26f, cx, h * 0.32f, p)
            }
        }
    }

    private fun drawEyesLayer(
        canvas: Canvas,
        eyes: Int,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        fg: Int,
        ink: Int
    ) {
        val leftX = cx - w * 0.12f
        val rightX = cx + w * 0.12f
        val eyeY = cy - h * 0.04f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }

        when (eyes) {
            0 -> {
                canvas.drawCircle(leftX, eyeY, w * 0.065f, p)
                canvas.drawCircle(rightX, eyeY, w * 0.065f, p)
            }
            1 -> {
                canvas.drawCircle(leftX, eyeY, w * 0.065f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.06f
                p.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(
                    RectF(rightX - w * 0.07f, eyeY - h * 0.04f, rightX + w * 0.07f, eyeY + h * 0.05f),
                    190f, 160f, false, p
                )
            }
            2 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.06f
                p.strokeCap = Paint.Cap.ROUND
                canvas.drawArc(
                    RectF(leftX - w * 0.07f, eyeY - h * 0.03f, leftX + w * 0.07f, eyeY + h * 0.07f),
                    195f, 150f, false, p
                )
                canvas.drawArc(
                    RectF(rightX - w * 0.07f, eyeY - h * 0.03f, rightX + w * 0.07f, eyeY + h * 0.07f),
                    195f, 150f, false, p
                )
            }
            3 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.045f
                p.strokeCap = Paint.Cap.ROUND
                listOf(leftX, rightX).forEach { ex ->
                    val r = w * 0.07f
                    canvas.drawLine(ex - r, eyeY, ex + r, eyeY, p)
                    canvas.drawLine(ex, eyeY - r, ex, eyeY + r, p)
                }
            }
            4 -> {
                canvas.drawRoundRect(
                    RectF(cx - w * 0.21f, eyeY - h * 0.055f, cx + w * 0.21f, eyeY + h * 0.055f),
                    w * 0.05f, w * 0.05f, p
                )
                p.color = fg
                canvas.drawCircle(cx + w * 0.08f, eyeY, w * 0.03f, p)
            }
            5 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.06f
                p.strokeCap = Paint.Cap.ROUND
                canvas.drawLine(leftX - w * 0.06f, eyeY - h * 0.02f, leftX + w * 0.05f, eyeY + h * 0.02f, p)
                canvas.drawLine(rightX + w * 0.06f, eyeY - h * 0.02f, rightX - w * 0.05f, eyeY + h * 0.02f, p)
            }
        }
    }

    private fun drawAccessoryLayer(
        canvas: Canvas,
        accessory: Int,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        fg: Int,
        ink: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (accessory) {
            1 -> {
                p.color = fg
                p.strokeWidth = w * 0.05f
                canvas.drawLine(cx, h * 0.22f, cx, h * 0.07f, p)
                p.color = 0xFFFFD166.toInt()
                canvas.drawCircle(cx, h * 0.07f, w * 0.065f, p)
            }
            2 -> {
                p.color = ink
                canvas.drawRoundRect(RectF(w * 0.04f, cy - h * 0.16f, w * 0.16f, cy + h * 0.16f), w * 0.06f, w * 0.06f, p)
                canvas.drawRoundRect(RectF(w * 0.84f, cy - h * 0.16f, w * 0.96f, cy + h * 0.16f), w * 0.06f, w * 0.06f, p)
            }
            3 -> {
                p.color = 0xFFFFD166.toInt()
                val path = Path().apply {
                    moveTo(cx - w * 0.18f, h * 0.24f)
                    lineTo(cx - w * 0.14f, h * 0.08f)
                    lineTo(cx - w * 0.04f, h * 0.17f)
                    lineTo(cx, h * 0.05f)
                    lineTo(cx + w * 0.04f, h * 0.17f)
                    lineTo(cx + w * 0.14f, h * 0.08f)
                    lineTo(cx + w * 0.18f, h * 0.24f)
                    close()
                }
                canvas.drawPath(path, p)
            }
            4 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.05f
                p.color = 0xFFFFE066.toInt()
                canvas.drawOval(RectF(cx - w * 0.20f, h * 0.07f, cx + w * 0.20f, h * 0.17f), p)
            }
            5 -> {
                val eyeY = cy - h * 0.04f
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.045f
                p.color = ink
                canvas.drawCircle(cx - w * 0.12f, eyeY, w * 0.11f, p)
                canvas.drawCircle(cx + w * 0.12f, eyeY, w * 0.11f, p)
                canvas.drawLine(cx - w * 0.01f, eyeY, cx + w * 0.01f, eyeY, p)
            }
            6 -> {
                p.color = 0xFFFFD166.toInt()
                val sx = w * 0.78f
                val sy = h * 0.20f
                val sr = w * 0.09f
                val spark = Path().apply {
                    moveTo(sx, sy - sr)
                    lineTo(sx + sr * 0.3f, sy - sr * 0.3f)
                    lineTo(sx + sr, sy)
                    lineTo(sx + sr * 0.3f, sy + sr * 0.3f)
                    lineTo(sx, sy + sr)
                    lineTo(sx - sr * 0.3f, sy + sr * 0.3f)
                    lineTo(sx - sr, sy)
                    lineTo(sx - sr * 0.3f, sy - sr * 0.3f)
                    close()
                }
                canvas.drawPath(spark, p)
            }
        }
    }

    private fun drawFrameLayer(
        canvas: Canvas,
        frame: Int,
        w: Float,
        h: Float,
        cx: Float,
        cy: Float,
        fg: Int,
        ink: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (frame) {
            1 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.04f
                p.color = (ink and 0x00FFFFFF) or 0x59000000
                canvas.drawCircle(cx, cy, w * 0.45f, p)
            }
            2 -> {
                p.color = (fg and 0x00FFFFFF) or 0x8C000000.toInt()
                for (i in 0 until 8) {
                    val angle = i * (Math.PI / 4.0)
                    val tx = cx + (w * 0.44f * cos(angle)).toFloat()
                    val ty = cy + (h * 0.44f * sin(angle)).toFloat()
                    canvas.drawCircle(tx, ty, w * 0.025f, p)
                }
            }
            3 -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = w * 0.055f
                p.strokeCap = Paint.Cap.ROUND
                p.color = (fg and 0x00FFFFFF) or 0xBF000000.toInt()
                canvas.drawArc(RectF(w * 0.07f, h * 0.07f, w * 0.93f, h * 0.93f), -125f, 70f, false, p)
                canvas.drawArc(RectF(w * 0.07f, h * 0.07f, w * 0.93f, h * 0.93f), 55f, 70f, false, p)
            }
        }
    }

    /**
     * Generates a crisp Swiss-editorial PNG Poll Card with voter avatars and corner QR code
     * for instant sharing into chat apps or Screen Share snapshots.
     */
    fun renderShareablePollCard(room: RoomDetailsUiModel): Bitmap {
        val width = 1080
        val height = (480 + room.options.take(5).size * 116).coerceIn(680, 1280)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Warm archival paper background + subtle dot grid
        canvas.drawColor(0xFFF6F5F1.toInt())
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1A121214 }
        var gx = 28f
        while (gx < width) {
            var gy = 28f
            while (gy < height) {
                canvas.drawCircle(gx, gy, 2f, dotPaint)
                gy += 36f
            }
            gx += 36f
        }

        // Outer hairline border card
        val cardRect = RectF(44f, 44f, width - 44f, height - 44f)
        val cardFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xFFE2DFD7.toInt()
        }
        canvas.drawRoundRect(cardRect, 28f, 28f, cardFill)
        canvas.drawRoundRect(cardRect, 28f, 28f, cardStroke)

        val monoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 24f
            color = 0xFFFF451A.toInt()
        }
        val statusText = if (room.isLocked) "MELD • DECIDED • #${room.id}" else "MELD • LIVE POLL • #${room.id}"
        canvas.drawText(statusText, 90f, 114f, monoPaint)

        // Question headline
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = 48f
            color = 0xFF121214.toInt()
        }
        val qLines = wrapText(room.question, titlePaint, 640f).take(2)
        var yCursor = 180f
        qLines.forEach { line ->
            canvas.drawText(line, 90f, yCursor, titlePaint)
            yCursor += 56f
        }

        // Top-right QR code for instant scanning
        val qrSize = 180
        val qrBitmap = renderQrBitmap(room.shareUrl, qrSize)
        canvas.drawBitmap(qrBitmap, (width - 90 - qrSize).toFloat(), 88f, null)

        // Option bars with voter avatars pushing each bar
        var rowTop = 300f
        val barLeft = 90f
        val barRight = width - 90f
        val barWidth = barRight - barLeft

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 28f
            color = 0xFF121214.toInt()
        }
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 26f
            textAlign = Paint.Align.RIGHT
            color = 0xFF5C5C66.toInt()
        }

        room.options.take(5).forEach { opt ->
            val optCard = RectF(barLeft, rowTop, barRight, rowTop + 92f)
            val isAccent = opt.isWinner || opt.isLeading
            val accentColor = if (opt.isWinner || opt.isUnanimous) 0xFF0D9488.toInt() else 0xFFFF451A.toInt()

            val bgOpt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF9F8F5.toInt() }
            val strokeOpt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = if (isAccent) 3f else 2f
                color = if (isAccent) accentColor else 0xFFE2DFD7.toInt()
            }
            canvas.drawRoundRect(optCard, 18f, 18f, bgOpt)

            if (opt.fraction > 0.01f) {
                val fillW = barWidth * opt.fraction.coerceIn(0f, 1f)
                val fillRect = RectF(barLeft, rowTop, barLeft + fillW, rowTop + 92f)
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = (accentColor and 0x00FFFFFF) or 0x1F000000
                }
                canvas.save()
                val clipOpt = Path().apply { addRoundRect(optCard, 18f, 18f, Path.Direction.CW) }
                canvas.clipPath(clipOpt)
                canvas.drawRect(fillRect, fillPaint)
                val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
                canvas.drawRect(barLeft + fillW - 4f, rowTop, barLeft + fillW, rowTop + 92f, tipPaint)
                canvas.restore()
            }
            canvas.drawRoundRect(optCard, 18f, 18f, strokeOpt)

            canvas.drawText(opt.label.take(36), barLeft + 24f, rowTop + 54f, labelPaint)
            canvas.drawText("${opt.percentage}% (${opt.voteCount})", barRight - 24f, rowTop + 54f, pctPaint)

            // Draw up to 3 voter avatars right on the card
            var avatarX = barRight - 240f
            opt.voters.take(3).forEach { voter ->
                val avBmp = renderAvatarBitmap(voter.avatarStyleIndex, voter.badgeColorIndex, sizePx = 44)
                canvas.drawBitmap(avBmp, avatarX, rowTop + 24f, null)
                avatarX -= 30f
            }

            rowTop += 112f
        }

        // Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 22f
            color = 0xFF5C5C66.toInt()
        }
        canvas.drawText(
            "Scan QR or open ${room.shareUrl} • ${room.totalVotes} votes",
            90f,
            height - 68f,
            footerPaint
        )

        return bitmap
    }

    private fun renderQrBitmap(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) 0xFF121214.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bmp
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val lines = mutableListOf<String>()
        var current = ""
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = w
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines.ifEmpty { listOf(text) }
    }
}
