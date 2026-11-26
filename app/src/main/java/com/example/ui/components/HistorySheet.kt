package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.RecentRoomSummary
import com.example.data.RoomMode
import com.example.ui.theme.InstrumentSerifFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.MonoTabularTextStyle
import com.example.ui.theme.PlusJakartaSansFamily
import java.util.concurrent.TimeUnit

private enum class HistoryFilter {
    ALL,
    ACTIVE,
    LOCKED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    rooms: List<RecentRoomSummary>,
    onOpenRoom: (String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredRooms = remember(rooms, filter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        rooms.filter { room ->
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.ACTIVE -> !room.isLocked
                HistoryFilter.LOCKED -> room.isLocked
            }
            val matchesQuery = q.isEmpty() ||
                room.question.lowercase().contains(q) ||
                room.id.lowercase().contains(q) ||
                (room.winnerLabel?.lowercase()?.contains(q) == true)
            matchesFilter && matchesQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.hairline)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.history_title),
                        fontFamily = InstrumentSerifFamily,
                        fontSize = 26.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = stringResource(R.string.history_subtitle),
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceInset)
                        .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${rooms.size}",
                        style = MonoTabularTextStyle.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkPrimary
                        )
                    )
                }
            }

            // Search input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceInset)
                    .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.history_search_placeholder),
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 13.sp,
                            color = colors.inkSecondary.copy(alpha = 0.65f)
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = colors.inkPrimary
                        ),
                        cursorBrush = SolidColor(colors.vermilion),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_search_input")
                    )
                }
            }

            // Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterChoices = listOf(
                    HistoryFilter.ALL to stringResource(R.string.history_filter_all),
                    HistoryFilter.ACTIVE to stringResource(R.string.history_filter_active),
                    HistoryFilter.LOCKED to stringResource(R.string.history_filter_locked)
                )
                filterChoices.forEach { (candidate, label) ->
                    val selected = filter == candidate
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) colors.inkPrimary else colors.surfaceInset)
                            .border(
                                1.dp,
                                if (selected) colors.inkPrimary else colors.hairline,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                view.performTactileTick()
                                filter = candidate
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (selected) colors.canvas else colors.inkSecondary
                        )
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.hairline)

            if (filteredRooms.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = colors.inkSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = stringResource(R.string.history_empty),
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 13.sp,
                        color = colors.inkSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredRooms,
                        key = { it.id }
                    ) { room ->
                        HistoryRoomRow(
                            room = room,
                            onClick = {
                                view.performTactileTick()
                                onOpenRoom(room.id)
                                onDismiss()
                            },
                            onDelete = {
                                view.performTactileTick()
                                onDeleteRoom(room.id)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRoomRow(
    room: RecentRoomSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MeldTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceInset)
            .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .testTag("history_room_${room.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.hairline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (room.mode == RoomMode.TIME_SLOTS) "TIME" else "POLL",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        color = colors.inkSecondary
                    )
                }

                Text(
                    text = "#${room.id} · ${formatRelativeTime(room.createdAtEpochMs)}",
                    style = MonoTabularTextStyle.copy(
                        fontSize = 10.sp,
                        color = colors.inkSecondary
                    )
                )

                if (room.isLocked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.emerald.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.room_locked_tag),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = colors.emerald
                        )
                    }
                }
            }

            Text(
                text = room.question,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val metaLine = buildString {
                append("${room.optionCount} options · ${room.totalVotes} votes")
                if (!room.winnerLabel.isNullOrBlank()) {
                    append(" · ")
                    append(stringResource(R.string.history_winner_prefix, room.winnerLabel))
                }
            }
            Text(
                text = metaLine,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 11.sp,
                color = if (!room.winnerLabel.isNullOrBlank()) colors.emerald else colors.inkSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(48.dp)
                .testTag("delete_history_room_${room.id}")
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(R.string.cd_delete_history_room),
                tint = colors.inkSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = colors.inkSecondary,
            modifier = Modifier
                .padding(end = 6.dp)
                .size(16.dp)
        )
    }
}

private fun formatRelativeTime(createdAtMs: Long): String {
    val diff = (System.currentTimeMillis() - createdAtMs).coerceAtLeast(0L)
    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
