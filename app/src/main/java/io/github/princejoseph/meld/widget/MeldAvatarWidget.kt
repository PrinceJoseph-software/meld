package io.github.princejoseph.meld.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MeldAvatarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MeldAvatarWidget()
}

class MeldAvatarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (identity, topRoom) = withContext(Dispatchers.IO) {
            val dao = MeldDatabase.getInstance(context).meldDao()
            val idStore = IdentityStore(context)
            val user = idStore.ensureIdentity()
            val rooms = dao.getRecentRoomsOnce()
            user to rooms.firstOrNull()
        }

        val myAvatarBitmap = withContext(Dispatchers.Default) {
            AvatarBitmapRenderer.renderAvatarBitmap(
                avatarStyleIndex = identity.avatarStyleIndex,
                colorIndex = identity.badgeColorIndex,
                sizePx = 96
            )
        }

        val now = System.currentTimeMillis()
        val roomIsLocked = topRoom != null && (topRoom.room.isLocked || (topRoom.room.deadlineEpochMs != null && now >= topRoom.room.deadlineEpochMs))
        val votesByOpt = topRoom?.votes?.groupBy { it.optionId }.orEmpty()
        val leadingOption = topRoom?.options?.maxByOrNull { votesByOpt[it.id]?.size ?: 0 }
        val totalVotes = topRoom?.votes?.size ?: 0
        val alreadyVotedLeader = if (topRoom != null && leadingOption != null) {
            topRoom.votes.any { it.optionId == leadingOption.id && it.voterUid == identity.uid }
        } else false

        provideContent {
            val surfaceBg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF141417))
            val insetBg = ColorProvider(day = Color(0xFFF3F1EC), night = Color(0xFF1E1E24))
            val primaryInk = ColorProvider(day = Color(0xFF121214), night = Color(0xFFF4F4F6))
            val secondaryInk = ColorProvider(day = Color(0xFF5C5C66), night = Color(0xFF8E8E98))
            val vermilion = ColorProvider(day = Color(0xFFFF451A), night = Color(0xFFFF572E))
            val emerald = ColorProvider(day = Color(0xFF0D9488), night = Color(0xFF14B8A6))
            val whiteColor = ColorProvider(day = Color.White, night = Color.White)

            val openIntent = if (topRoom != null) {
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse("meld://r/${topRoom.room.id}")
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                Intent(context, MainActivity::class.java)
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(surfaceBg)
                    .cornerRadius(20.dp)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionStartActivity(openIntent)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(myAvatarBitmap),
                        contentDescription = "Your Meld avatar",
                        modifier = GlanceModifier.size(46.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = identity.effectiveName,
                            maxLines = 1,
                            style = TextStyle(color = primaryInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = topRoom?.room?.question ?: "Ready to push a decision",
                            maxLines = 1,
                            style = TextStyle(color = secondaryInk, fontSize = 11.sp)
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(10.dp))

                if (topRoom != null && leadingOption != null) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .background(insetBg)
                                .cornerRadius(10.dp)
                                .clickable(actionStartActivity(openIntent))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (roomIsLocked) "WINNER" else "LEADING ($totalVotes votes)",
                                style = TextStyle(
                                    color = if (roomIsLocked) emerald else vermilion,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = leadingOption.label,
                                maxLines = 1,
                                style = TextStyle(color = primaryInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            )
                        }

                        if (!roomIsLocked) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Box(
                                modifier = GlanceModifier
                                    .background(if (alreadyVotedLeader) emerald else vermilion)
                                    .cornerRadius(10.dp)
                                    .clickable(
                                        actionRunCallback<WidgetPushLeaderAction>(
                                            actionParametersOf(
                                                WidgetPushLeaderAction.KEY_ROOM_ID to topRoom.room.id,
                                                WidgetPushLeaderAction.KEY_OPTION_ID to leadingOption.id
                                            )
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (alreadyVotedLeader) "PUSHED" else "PUSH",
                                    style = TextStyle(color = whiteColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .background(vermilion)
                            .cornerRadius(10.dp)
                            .clickable(actionStartActivity<MainActivity>())
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start a Meld Poll",
                            style = TextStyle(color = whiteColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

class WidgetPushLeaderAction : ActionCallback {
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
