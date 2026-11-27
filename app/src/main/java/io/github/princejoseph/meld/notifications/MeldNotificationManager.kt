package io.github.princejoseph.meld.notifications

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.princejoseph.meld.MainActivity
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.data.sanitizeMeldInput
import io.github.princejoseph.meld.data.sanitizeRoomCode

object MeldNotificationManager {
    const val CHANNEL_DEADLINES = "meld_deadlines"
    const val CHANNEL_DECISIONS = "meld_decisions"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val deadlineChannel = NotificationChannel(
            CHANNEL_DEADLINES,
            context.getString(R.string.notif_channel_deadlines_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_deadlines_desc)
            enableVibration(true)
        }

        val decisionChannel = NotificationChannel(
            CHANNEL_DECISIONS,
            context.getString(R.string.notif_channel_decisions_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_channel_decisions_desc)
        }

        nm.createNotificationChannel(deadlineChannel)
        nm.createNotificationChannel(decisionChannel)
    }

    fun canPostNotifications(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun showDeadlineApproaching(
        context: Context,
        roomId: String,
        question: String,
        remainingText: String,
        leadingLabel: String?,
        totalVotes: Int
    ) {
        val safeRoomId = sanitizeRoomCode(roomId) ?: return
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val safeQuestion = sanitizeMeldInput(question, 120)
        val body = if (!leadingLabel.isNullOrBlank()) {
            context.getString(
                R.string.notif_deadline_body_with_leader,
                remainingText,
                sanitizeMeldInput(leadingLabel, 60),
                totalVotes
            )
        } else {
            context.getString(R.string.notif_deadline_body_simple, remainingText, totalVotes)
        }

        val intent = openRoomPendingIntent(context, safeRoomId, requestCode = safeRoomId.hashCode())
        val notification = NotificationCompat.Builder(context, CHANNEL_DEADLINES)
            .setSmallIcon(R.drawable.ic_meld_notification)
            .setContentTitle(context.getString(R.string.notif_deadline_title, safeQuestion))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .addAction(
                0,
                context.getString(R.string.notif_action_vote_now),
                intent
            )
            .build()

        // Permission is checked by canPostNotifications() above; runCatching covers a revoke race.
        @SuppressLint("MissingPermission")
        runCatching {
            NotificationManagerCompat.from(context)
                .notify("deadline_$safeRoomId".hashCode(), notification)
        }
    }

    fun showDecisionReached(
        context: Context,
        roomId: String,
        question: String,
        winnerLabel: String?,
        voteCount: Int
    ) {
        val safeRoomId = sanitizeRoomCode(roomId) ?: return
        ensureChannels(context)
        if (!canPostNotifications(context)) return

        val safeQuestion = sanitizeMeldInput(question, 120)
        val body = if (!winnerLabel.isNullOrBlank()) {
            context.getString(
                R.string.notif_decision_body_winner,
                sanitizeMeldInput(winnerLabel, 80),
                voteCount
            )
        } else {
            context.getString(R.string.notif_decision_body_closed)
        }

        val intent = openRoomPendingIntent(context, safeRoomId, requestCode = ("dec_$safeRoomId").hashCode())
        val notification = NotificationCompat.Builder(context, CHANNEL_DECISIONS)
            .setSmallIcon(R.drawable.ic_meld_notification)
            .setContentTitle(context.getString(R.string.notif_decision_title, safeQuestion))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(intent)
            .addAction(
                0,
                context.getString(R.string.notif_action_view_result),
                intent
            )
            .build()

        // Permission is checked by canPostNotifications() above; runCatching covers a revoke race.
        @SuppressLint("MissingPermission")
        runCatching {
            NotificationManagerCompat.from(context)
                .notify("decision_$safeRoomId".hashCode(), notification)
        }
    }

    private fun openRoomPendingIntent(
        context: Context,
        roomId: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("meld://r/$roomId")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
