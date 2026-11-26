package io.github.princejoseph.meld.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.princejoseph.meld.data.IdentityStore
import io.github.princejoseph.meld.data.MeldDatabase
import io.github.princejoseph.meld.data.MeldRepository
import io.github.princejoseph.meld.data.sanitizeRoomCode
import java.util.concurrent.TimeUnit

object DeadlineWorkScheduler {
    private const val KEY_ROOM_ID = "room_id"

    fun scheduleForRoom(
        context: Context,
        roomId: String,
        createdAtEpochMs: Long,
        deadlineEpochMs: Long
    ) {
        val safeRoomId = sanitizeRoomCode(roomId) ?: return
        val now = System.currentTimeMillis()
        val totalRemaining = deadlineEpochMs - now
        if (totalRemaining <= 0L) return

        val wm = WorkManager.getInstance(context.applicationContext)
        val input = workDataOf(KEY_ROOM_ID to safeRoomId)

        // Schedule expiration & final-decision notification right when timer ends
        val expireReq = OneTimeWorkRequestBuilder<DeadlineExpirationWorker>()
            .setInitialDelay(totalRemaining, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()
        wm.enqueueUniqueWork(
            "meld_expire_$safeRoomId",
            ExistingWorkPolicy.REPLACE,
            expireReq
        )

        // Schedule approaching reminder at ~75% elapsed (at least 15s before deadline)
        val totalSpan = (deadlineEpochMs - createdAtEpochMs).coerceAtLeast(totalRemaining)
        val leadTimeMs = (totalSpan * 0.25).toLong().coerceIn(15_000L, 2L * 3600_000L)
        val reminderDelay = totalRemaining - leadTimeMs
        if (reminderDelay >= 10_000L) {
            val reminderReq = OneTimeWorkRequestBuilder<DeadlineReminderWorker>()
                .setInitialDelay(reminderDelay, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .build()
            wm.enqueueUniqueWork(
                "meld_approaching_$safeRoomId",
                ExistingWorkPolicy.REPLACE,
                reminderReq
            )
        }
    }

    fun cancelForRoom(context: Context, roomId: String) {
        val safeRoomId = sanitizeRoomCode(roomId) ?: return
        val wm = WorkManager.getInstance(context.applicationContext)
        wm.cancelUniqueWork("meld_approaching_$safeRoomId")
        wm.cancelUniqueWork("meld_expire_$safeRoomId")
    }

    internal const val EXTRA_ROOM_ID = KEY_ROOM_ID
}

class DeadlineReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val roomId = sanitizeRoomCode(inputData.getString(DeadlineWorkScheduler.EXTRA_ROOM_ID))
            ?: return Result.failure()
        val dao = MeldDatabase.getInstance(applicationContext).meldDao()
        val room = dao.getRoomOnce(roomId) ?: return Result.success()
        val deadline = room.deadlineEpochMs ?: return Result.success()
        val now = System.currentTimeMillis()
        val remainingMs = deadline - now
        if (room.isLocked || remainingMs <= 0L) return Result.success()

        val options = dao.getOptionsOnce(roomId)
        val votes = dao.getVotesOnce(roomId)
        val counts = votes.groupingBy { it.optionId }.eachCount()
        val leaderLabel = options.maxByOrNull { counts[it.id] ?: 0 }?.takeIf { (counts[it.id] ?: 0) > 0 }?.label
        val remainingText = formatShortRemaining(remainingMs)

        MeldNotificationManager.showDeadlineApproaching(
            context = applicationContext,
            roomId = roomId,
            question = room.question,
            remainingText = remainingText,
            leadingLabel = leaderLabel,
            totalVotes = votes.size
        )
        return Result.success()
    }

    private fun formatShortRemaining(ms: Long): String {
        val secs = (ms / 1000L).coerceAtLeast(1L)
        return when {
            secs < 60L -> "${secs}s"
            secs < 3600L -> "${(secs + 59L) / 60L}m"
            else -> "${secs / 3600L}h ${(secs % 3600L) / 60L}m"
        }
    }
}

class DeadlineExpirationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val roomId = sanitizeRoomCode(inputData.getString(DeadlineWorkScheduler.EXTRA_ROOM_ID))
            ?: return Result.failure()
        val dao = MeldDatabase.getInstance(applicationContext).meldDao()
        val repo = MeldRepository(dao, IdentityStore(applicationContext), appContext = applicationContext)
        // finalizeExpiredRoomIfNeeded already posts the "decision reached" notification
        // and refreshes widgets; posting again here produced a duplicate alert.
        repo.finalizeExpiredRoomIfNeeded(roomId)
        return Result.success()
    }
}
