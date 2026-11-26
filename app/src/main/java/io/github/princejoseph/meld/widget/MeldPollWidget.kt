package io.github.princejoseph.meld.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.princejoseph.meld.MainActivity
import io.github.princejoseph.meld.data.ConnectivityObserver
import io.github.princejoseph.meld.data.IdentityStore
import io.github.princejoseph.meld.data.MeldDatabase
import io.github.princejoseph.meld.data.MeldRepository
import io.github.princejoseph.meld.data.RoomWithOptionsAndVotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MeldPollWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeldPollWidget()
}

private data class WidgetOptionRow(
    val id: String,
    val label: String,
    val voteCount: Int,
    val percentage: Int,
    val isVotedByMe: Boolean,
    val isWinner: Boolean,
    val isLeading: Boolean,
    val avatarKeys: List<String>
)

private data class WidgetPollSnapshot(
    val roomId: String,
    val question: String,
    val isLocked: Boolean,
    val isAnonymous: Boolean,
    val totalVotes: Int,
    val roomCount: Int,
    val activeIndex: Int,
    val options: List<WidgetOptionRow>
)

class MeldPollWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (rooms, myUid) = withContext(Dispatchers.IO) {
            val dao = MeldDatabase.getInstance(context).meldDao()
            val identity = IdentityStore(context).ensureIdentity()
            dao.getRecentRoomsOnce() to identity.uid
        }

        // Pre-render compact voter avatar bitmaps (capped for RemoteViews memory safety)
        val avatarBitmaps = withContext(Dispatchers.Default) {
            val map = mutableMapOf<String, Bitmap>()
            rooms.take(8).forEach { entry ->
                entry.votes.take(12).forEach { vote ->
                    val style = if (entry.room.isAnonymous) 1 else vote.voterAvatarStyleIndex
                    val color = if (entry.room.isAnonymous) 4 else vote.voterBadgeColorIndex
                    val key = "${style}_$color"
                    if (!map.containsKey(key) && map.size < 16) {
                        map[key] = AvatarBitmapRenderer.renderAvatarBitmap(
                            avatarStyleIndex = style,
                            colorIndex = color,
                            sizePx = 48
                        )
                    }
                }
            }
            map
        }

        provideContent {
            val prefs = currentState<Preferences>()
            val requestedIndex = prefs[MeldWidgetUpdater.KEY_ROOM_INDEX] ?: 0
            val snapshot = buildSnapshot(rooms, myUid, requestedIndex)
            MeldPollWidgetContent(
                context = context,
                snapshot = snapshot,
                avatarBitmaps = avatarBitmaps
            )
        }
    }

    private fun buildSnapshot(
        rooms: List<RoomWithOptionsAndVotes>,
        myUid: String,
        requestedIndex: Int
    ): WidgetPollSnapshot? {
        if (rooms.isEmpty()) return null
        val safeIdx = ((requestedIndex % rooms.size) + rooms.size) % rooms.size
        val entry = rooms[safeIdx]
        val now = System.currentTimeMillis()
        val expired = entry.room.deadlineEpochMs != null && now >= entry.room.deadlineEpochMs
        val isLocked = entry.room.isLocked || expired
        val votesByOption = entry.votes.groupBy { it.optionId }
        val totalVotes = entry.votes.size
        val maxVotes = entry.options.maxOfOrNull { votesByOption[it.id]?.size ?: 0 } ?: 0

        val rows = entry.options
            .sortedWith(
                compareByDescending<io.github.princejoseph.meld.data.OptionEntity> {
                    if (isLocked && entry.room.winnerOptionId == it.id) Int.MAX_VALUE
                    else votesByOption[it.id]?.size ?: 0
                }.thenBy { it.sortOrder }
            )
            .take(3)
            .map { opt ->
                val optVotes = votesByOption[opt.id].orEmpty()
                val count = optVotes.size
                val pct = if (totalVotes > 0) ((count * 100f) / totalVotes).toInt() else 0
                val isWinner = isLocked && (entry.room.winnerOptionId == opt.id || (entry.room.winnerOptionId == null && count == maxVotes && maxVotes > 0))
                val isLeading = !isLocked && maxVotes > 0 && count == maxVotes
                val keys = optVotes.take(3).map { v ->
                    val style = if (entry.room.isAnonymous) 1 else v.voterAvatarStyleIndex
                    val color = if (entry.room.isAnonymous) 4 else v.voterBadgeColorIndex
                    "${style}_$color"
                }
                WidgetOptionRow(
                    id = opt.id,
                    label = opt.label,
                    voteCount = count,
                    percentage = pct,
                    isVotedByMe = optVotes.any { it.voterUid == myUid },
                    isWinner = isWinner,
                    isLeading = isLeading,
                    avatarKeys = keys
                )
            }

        return WidgetPollSnapshot(
            roomId = entry.room.id,
            question = entry.room.question,
            isLocked = isLocked,
            isAnonymous = entry.room.isAnonymous,
            totalVotes = totalVotes,
            roomCount = rooms.size,
            activeIndex = safeIdx,
            options = rows
        )
    }

    @Composable
    private fun MeldPollWidgetContent(
        context: Context,
        snapshot: WidgetPollSnapshot?,
        avatarBitmaps: Map<String, Bitmap>
    ) {
        val surfaceBg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF141417))
        val insetBg = ColorProvider(day = Color(0xFFF3F1EC), night = Color(0xFF1E1E24))
        val votedBg = ColorProvider(day = Color(0xFFFFEFEA), night = Color(0xFF2B1813))
        val winnerBg = ColorProvider(day = Color(0xFFE6F6F4), night = Color(0xFF112927))
        val primaryInk = ColorProvider(day = Color(0xFF121214), night = Color(0xFFF4F4F6))
        val secondaryInk = ColorProvider(day = Color(0xFF5C5C66), night = Color(0xFF8E8E98))
        val vermilion = ColorProvider(day = Color(0xFFFF451A), night = Color(0xFFFF572E))
        val emerald = ColorProvider(day = Color(0xFF0D9488), night = Color(0xFF14B8A6))
        val whiteColor = ColorProvider(day = Color.White, night = Color.White)

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(surfaceBg)
                .cornerRadius(20.dp)
                .padding(14.dp)
        ) {
            if (snapshot == null) {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MELD",
                        style = TextStyle(color = vermilion, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "No polls yet",
                        style = TextStyle(color = primaryInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = "Tap to launch a 5-second vote",
                        style = TextStyle(color = secondaryInk, fontSize = 12.sp)
                    )
                }
                return@Column
            }

            val openRoomIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("meld://r/${snapshot.roomId}")
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            // Header Row: Brand + Room Code + Switcher + New
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity(openRoomIntent)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (snapshot.isLocked) "DECIDED" else "LIVE",
                        style = TextStyle(
                            color = if (snapshot.isLocked) emerald else vermilion,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = "#${snapshot.roomId}",
                        style = TextStyle(color = secondaryInk, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    )
                    if (snapshot.isAnonymous) {
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Text(
                            text = "IP-LOCK",
                            style = TextStyle(color = emerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (snapshot.roomCount > 1) {
                    Box(
                        modifier = GlanceModifier
                            .background(insetBg)
                            .cornerRadius(8.dp)
                            .clickable(
                                actionRunCallback<WidgetCycleRoomAction>(
                                    actionParametersOf(WidgetCycleRoomAction.KEY_DELTA to -1)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "<",
                            style = TextStyle(color = primaryInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(insetBg)
                            .cornerRadius(8.dp)
                            .clickable(
                                actionRunCallback<WidgetCycleRoomAction>(
                                    actionParametersOf(WidgetCycleRoomAction.KEY_DELTA to 1)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = ">",
                            style = TextStyle(color = primaryInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }

                Box(
                    modifier = GlanceModifier
                        .background(vermilion)
                        .cornerRadius(8.dp)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+ NEW",
                        style = TextStyle(color = whiteColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(6.dp))

            // Question Title
            Text(
                text = snapshot.question,
                maxLines = 2,
                style = TextStyle(
                    color = primaryInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openRoomIntent))
            )

            Spacer(modifier = GlanceModifier.height(8.dp))

            // Interactive Off-App Voting Rows with Custom Voter Avatars
            snapshot.options.forEachIndexed { idx, opt ->
                val rowBg = when {
                    opt.isWinner -> winnerBg
                    opt.isVotedByMe -> votedBg
                    else -> insetBg
                }
                val rowModifier = GlanceModifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
                    .let { base ->
                        if (snapshot.isLocked) {
                            base.clickable(actionStartActivity(openRoomIntent))
                        } else {
                            base.clickable(
                                actionRunCallback<WidgetVoteAction>(
                                    actionParametersOf(
                                        WidgetVoteAction.KEY_ROOM_ID to snapshot.roomId,
                                        WidgetVoteAction.KEY_OPTION_ID to opt.id
                                    )
                                )
                            )
                        }
                    }

                Row(
                    modifier = rowModifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            opt.isWinner -> "★"
                            opt.isVotedByMe -> "●"
                            else -> "○"
                        },
                        style = TextStyle(
                            color = when {
                                opt.isWinner -> emerald
                                opt.isVotedByMe -> vermilion
                                else -> secondaryInk
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(7.dp))
                    Text(
                        text = opt.label,
                        maxLines = 1,
                        style = TextStyle(
                            color = primaryInk,
                            fontSize = 12.sp,
                            fontWeight = if (opt.isVotedByMe || opt.isWinner) FontWeight.Bold else FontWeight.Medium
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )

                    opt.avatarKeys.forEach { key ->
                        val bmp = avatarBitmaps[key]
                        if (bmp != null) {
                            Image(
                                provider = ImageProvider(bmp),
                                contentDescription = "Voter avatar",
                                modifier = GlanceModifier.size(18.dp)
                            )
                            Spacer(modifier = GlanceModifier.width(2.dp))
                        }
                    }

                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "${opt.percentage}%",
                        style = TextStyle(
                            color = if (opt.isWinner) emerald else if (opt.isLeading) vermilion else secondaryInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (idx < snapshot.options.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(5.dp))
                }
            }
        }
    }
}

class WidgetVoteAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val roomId = parameters[KEY_ROOM_ID] ?: return
        val optionId = parameters[KEY_OPTION_ID] ?: return
        withContext(Dispatchers.IO) {
            val db = MeldDatabase.getInstance(context)
            val identityStore = IdentityStore(context)
            val repo = MeldRepository(db.meldDao(), identityStore, ConnectivityObserver(context), context)
            repo.initIdentity()
            repo.toggleVote(roomId, optionId, isCurrentlyOnline = true)
        }
        MeldWidgetUpdater.refreshAll(context)
    }

    companion object {
        val KEY_ROOM_ID = ActionParameters.Key<String>("room_id")
        val KEY_OPTION_ID = ActionParameters.Key<String>("option_id")
    }
}

class WidgetCycleRoomAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[KEY_DELTA] ?: 1
        updateAppWidgetState(context, glanceId) { prefs ->
            val current = prefs[MeldWidgetUpdater.KEY_ROOM_INDEX] ?: 0
            prefs[MeldWidgetUpdater.KEY_ROOM_INDEX] = current + delta
            prefs[MeldWidgetUpdater.KEY_REVISION] = (prefs[MeldWidgetUpdater.KEY_REVISION] ?: 0) + 1
        }
        MeldPollWidget().update(context, glanceId)
    }

    companion object {
        val KEY_DELTA = ActionParameters.Key<Int>("delta")
    }
}
