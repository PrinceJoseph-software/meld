package io.github.princejoseph.meld.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.princejoseph.meld.data.RoomDetailsUiModel
import java.io.File
import java.io.FileOutputStream

object PollCardImageExporter {

    fun exportAndShare(context: Context, room: RoomDetailsUiModel): Boolean {
        val uri = exportToUri(context, room) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${room.question}\n${room.shareUrl}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "Share Meld Poll Card"))
        }.isSuccess
    }

    fun exportToFile(context: Context, room: RoomDetailsUiModel): File? {
        return runCatching {
            val bitmap = renderCardBitmap(room)
            val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val file = File(dir, "meld_card_${room.id}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            file
        }.getOrNull()
    }

    fun exportToUri(context: Context, room: RoomDetailsUiModel): Uri? {
        val file = exportToFile(context, room) ?: return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun renderCardBitmap(room: RoomDetailsUiModel): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaper = Color.parseColor("#F8F6F2")
        val cardSurface = Color.parseColor("#FFFFFF")
        val inkPrimary = Color.parseColor("#141416")
        val inkSecondary = Color.parseColor("#6A6864")
        val hairline = Color.parseColor("#E3DED6")
        val vermilion = Color.parseColor("#E5482B")
        val emerald = Color.parseColor("#178563")

        canvas.drawColor(bgPaper)

        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DDD7CE")
        }
        var gx = 36f
        while (gx < width) {
            var gy = 36f
            while (gy < height) {
                canvas.drawCircle(gx, gy, 2.2f, dotPaint)
                gy += 36f
            }
            gx += 36f
        }

        val outerCard = RectF(56f, 56f, width - 56f, height - 56f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardSurface }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = hairline
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(outerCard, 40f, 40f, cardPaint)
        canvas.drawRoundRect(outerCard, 40f, 40f, borderPaint)

        val padLeft = 104f
        val contentW = width - 208f

        // Header brand & room code pill
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawCircle(padLeft + 14f, 134f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkPrimary })
        canvas.drawCircle(padLeft + 34f, 134f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = vermilion })
        canvas.drawText("MELD DECISION", padLeft + 64f, 144f, brandPaint)

        val codeText = "#${room.id}"
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = vermilion
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val codeW = codePaint.measureText(codeText)
        val pillRect = RectF(width - 104f - codeW - 44f, 106f, width - 104f, 162f)
        canvas.drawRoundRect(pillRect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, 229, 72, 43)
        })
        canvas.drawText(codeText, pillRect.left + 22f, 144f, codePaint)

        // Question headline
        val qPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = 56f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val qLayout = StaticLayout.Builder.obtain(room.question, 0, room.question.length, qPaint, contentW.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(3)
            .build()
        canvas.save()
        canvas.translate(padLeft, 200f)
        qLayout.draw(canvas)
        canvas.restore()

        var yCursor = 200f + qLayout.height + 24f

        // Status subtitle
        val statusText = buildString {
            append(if (room.isLocked) "FINALIZED" else "LIVE POLL")
            append("  •  ")
            append("${room.totalVotes} ${if (room.totalVotes == 1) "VOTE" else "VOTES"}")
            if (room.isRanked) {
                append("  •  RANKED")
                if (room.isLocked && room.rankedRounds > 1) append(" · ${room.rankedRounds} ROUNDS")
            }
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (room.isLocked) emerald else inkSecondary
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText(statusText, padLeft, yCursor + 24f, subPaint)
        yCursor += 64f

        // Ranked Options (up to 5)
        val displayedOptions = room.options.take(5)
        val rowHeight = 112f
        val rowGap = 20f
        val optLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = 32f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val optStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }

        displayedOptions.forEachIndexed { idx, opt ->
            val rowRect = RectF(padLeft, yCursor, width - 104f, yCursor + rowHeight)
            val isWinnerOrLead = opt.isWinner || (!room.isLocked && opt.isLeading)
            val accent = if (opt.isWinner) emerald else vermilion

            canvas.drawRoundRect(rowRect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgPaper })

            val fillWidth = (rowRect.width() * opt.fraction.coerceIn(0f, 1f))
            if (fillWidth > 12f) {
                canvas.save()
                val clipPath = android.graphics.Path().apply {
                    addRoundRect(rowRect, 22f, 22f, android.graphics.Path.Direction.CW)
                }
                canvas.clipPath(clipPath)
                canvas.drawRect(
                    rowRect.left,
                    rowRect.top,
                    rowRect.left + fillWidth,
                    rowRect.bottom,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (opt.isWinner) Color.argb(38, 23, 133, 99) else Color.argb(30, 229, 72, 43)
                    }
                )
                canvas.restore()
            }

            if (isWinnerOrLead) {
                canvas.drawRoundRect(rowRect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accent
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                })
            }

            val prefix = if (opt.isWinner) "★ " else "${idx + 1}. "
            val maxLabelWidth = contentW - 220f
            var label = prefix + opt.label
            while (optLabelPaint.measureText(label) > maxLabelWidth && label.length > 4) {
                label = label.dropLast(2) + "…"
            }
            canvas.drawText(label, rowRect.left + 28f, rowRect.centerY() + 11f, optLabelPaint)

            val statText = "${opt.percentage}% (${opt.voteCount})"
            optStatPaint.color = if (isWinnerOrLead) accent else inkSecondary
            val statW = optStatPaint.measureText(statText)
            canvas.drawText(statText, rowRect.right - 28f - statW, rowRect.centerY() + 10f, optStatPaint)

            yCursor += rowHeight + rowGap
        }

        // Footer with QR Code
        val footerTop = height - 250f
        canvas.drawLine(padLeft, footerTop - 24f, width - 104f, footerTop - 24f, borderPaint)

        val qrBitmap = renderQrBitmap(room.shareUrl, 152)
        if (qrBitmap != null) {
            canvas.drawBitmap(qrBitmap, padLeft, footerTop, null)
            qrBitmap.recycle()
        }

        val footTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkPrimary
            textSize = 30f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val footUrlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSecondary
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        canvas.drawText("Scan to vote or view live results", padLeft + 180f, footerTop + 56f, footTitlePaint)
        canvas.drawText(room.shareUrl, padLeft + 180f, footerTop + 102f, footUrlPaint)

        return bitmap
    }

    private fun renderQrBitmap(content: String, sizePx: Int): Bitmap? {
        return runCatching {
            val hints = mapOf(EncodeHintType.MARGIN to 0)
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix.get(x, y)) Color.parseColor("#141416") else Color.WHITE)
                }
            }
            bmp
        }.getOrNull()
    }
}
