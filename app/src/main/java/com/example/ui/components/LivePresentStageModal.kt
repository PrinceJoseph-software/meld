package com.example.ui.components

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.OptionUiModel
import com.example.data.RoomDetailsUiModel
import com.example.ui.theme.InstrumentSerifFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.MonoTabularTextStyle
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.widget.AvatarBitmapRenderer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Full-screen distraction-free Live Screen-Share & Cast Stage:
 * - Oversized kinetic Avatar Race Lanes for Google Meet / Zoom / Discord screen sharing
 * - Corner QR + Room Code for instant audience scan-to-vote
 * - Optional External Display Mirroring via Android Presentation API
 * - One-tap PNG Visual Poll Card export via FileProvider
 */
@Composable
fun LivePresentStageModal(
    room: RoomDetailsUiModel,
    nudgePulse: Int,
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val displayManager = remember {
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    }
    var presentationDisplays by remember {
        mutableStateOf(
            displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.toList().orEmpty()
        )
    }
    var activePresentation by remember { mutableStateOf<ExternalStagePresentation?>(null) }
    var isExportingCard by remember { mutableStateOf(false) }

    DisposableEffect(displayManager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                presentationDisplays = displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.toList().orEmpty()
            }

            override fun onDisplayRemoved(displayId: Int) {
                presentationDisplays = displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.toList().orEmpty()
                if (activePresentation?.display?.displayId == displayId) {
                    activePresentation?.dismiss()
                    activePresentation = null
                }
            }

            override fun onDisplayChanged(displayId: Int) {}
        }
        displayManager?.registerDisplayListener(listener, null)
        onDispose {
            displayManager?.unregisterDisplayListener(listener)
            activePresentation?.dismiss()
            activePresentation = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvas)
                .draftingDotGrid(colors.dotGrid)
                .statusBarsPadding()
                .padding(16.dp)
                .testTag("live_present_stage")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Presenter Control Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val pulse = rememberInfiniteTransition(label = "stage_live_dot")
                        val dotAlpha by pulse.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_alpha"
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.vermilion.copy(alpha = 0.12f))
                                .border(1.dp, colors.vermilion.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.vermilion.copy(alpha = dotAlpha))
                            )
                            Text(
                                text = stringResource(R.string.stage_live_badge),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = colors.vermilion
                            )
                        }

                        Text(
                            text = "#${room.id} · ${stringResource(R.string.stage_votes_count, room.totalVotes)}",
                            style = MonoTabularTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkSecondary
                            )
                        )
                    }

                    IconButton(
                        onClick = {
                            view.performTactileTick()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.hairline, CircleShape)
                            .testTag("stage_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.stage_close),
                            tint = colors.inkPrimary
                        )
                    }
                }

                // Stage Hero: Oversized Question + Corner QR Join Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = room.question,
                            fontFamily = InstrumentSerifFamily,
                            fontSize = 30.sp,
                            lineHeight = 34.sp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = stringResource(R.string.stage_share_hint),
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White)
                                .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                                .padding(6.dp)
                        ) {
                            StageQrCode(content = room.shareUrl, modifier = Modifier.size(84.dp))
                        }
                        Text(
                            text = stringResource(R.string.stage_scan_to_vote),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            color = colors.inkSecondary
                        )
                    }
                }

                // Kinetic Avatar Race Lanes
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    room.options.forEach { option ->
                        StageAvatarRaceLane(
                            option = option,
                            isRoomLocked = room.isLocked,
                            nudgePulse = nudgePulse
                        )
                    }
                }

                // Screen Share / External Cast & Poll Card Actions
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val targetDisplay = presentationDisplays.firstOrNull()
                    if (targetDisplay != null) {
                        val isMirroring = activePresentation != null
                        OutlinedButton(
                            onClick = {
                                view.performTactileConfirm()
                                if (isMirroring) {
                                    activePresentation?.dismiss()
                                    activePresentation = null
                                } else {
                                    runCatching {
                                        val pres = ExternalStagePresentation(context, targetDisplay, room, nudgePulse)
                                        pres.show()
                                        activePresentation = pres
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("stage_external_cast_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isMirroring) colors.emerald else colors.hairline)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Cast,
                                contentDescription = null,
                                tint = if (isMirroring) colors.emerald else colors.inkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isMirroring) {
                                    stringResource(R.string.stage_casting_active, targetDisplay.name)
                                } else {
                                    stringResource(R.string.stage_cast_display, targetDisplay.name)
                                },
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isMirroring) colors.emerald else colors.inkPrimary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isExportingCard) return@Button
                            view.performTactileConfirm()
                            isExportingCard = true
                            scope.launch {
                                shareVisualPollCard(context, room)
                                isExportingCard = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("stage_share_card_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.vermilion,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.stage_share_card),
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageAvatarRaceLane(
    option: OptionUiModel,
    isRoomLocked: Boolean,
    nudgePulse: Int
) {
    val colors = MeldTheme.colors
    val animatedFraction by animateFloatAsState(
        targetValue = option.fraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 280f, dampingRatio = 0.78f),
        label = "stage_lane_${option.id}"
    )
    val accent = if (option.isWinner || option.isUnanimous) colors.emerald else colors.vermilion

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(
                width = if (option.isWinner || option.isLeading) 1.5.dp else 1.dp,
                color = when {
                    option.isWinner -> colors.emerald
                    option.isLeading -> colors.vermilion.copy(alpha = 0.65f)
                    else -> colors.hairline
                },
                shape = RoundedCornerShape(14.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = option.label,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${option.percentage}% · ${option.voteCount}",
                style = MonoTabularTextStyle.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (option.isWinner || option.isLeading) accent else colors.inkSecondary
                )
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceInset),
            contentAlignment = Alignment.CenterStart
        ) {
            val laneWidth = maxWidth
            val pushWallX = (laneWidth * animatedFraction).coerceIn(8.dp, laneWidth - 4.dp)
            val avatars = option.voters.take(5)
            val avatarSize = 26.dp
            val clusterW = avatarSize + 14.dp * (avatars.size - 1).coerceAtLeast(0)
            val clusterX = (pushWallX - clusterW - 4.dp).coerceIn(2.dp, (laneWidth - clusterW - 4.dp).coerceAtLeast(2.dp))

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(pushWallX)
                    .background(accent.copy(alpha = 0.16f))
            )

            Box(
                modifier = Modifier
                    .offset(x = pushWallX - 2.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )

            if (avatars.isNotEmpty()) {
                Row(
                    modifier = Modifier.offset(x = clusterX),
                    horizontalArrangement = Arrangement.spacedBy((-12).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    avatars.forEach { voter ->
                        GeometricAvatar(
                            avatarStyleIndex = voter.avatarStyleIndex,
                            colorIndex = voter.badgeColorIndex,
                            size = avatarSize,
                            borderColor = colors.surfaceElevated,
                            reactionTrigger = option.voteCount,
                            pushBias = if (isRoomLocked && !option.isWinner) 0f else 1f,
                            nudgePulse = nudgePulse
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageQrCode(
    content: String,
    modifier: Modifier = Modifier
) {
    val matrix = remember(content) {
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    }
    Canvas(modifier = modifier) {
        val cols = matrix.width
        val rows = matrix.height
        if (cols <= 0 || rows <= 0) return@Canvas
        val cellSize = size.minDimension / cols.toFloat()
        val moduleColor = Color(0xFF121214)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (matrix.get(x, y)) {
                    drawRoundRect(
                        color = moduleColor,
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size = Size(cellSize * 0.92f, cellSize * 0.92f),
                        cornerRadius = CornerRadius(cellSize * 0.18f, cellSize * 0.18f)
                    )
                }
            }
        }
    }
}

private class ExternalStagePresentation(
    outerContext: Context,
    display: Display,
    private val room: RoomDetailsUiModel,
    private val nudgePulse: Int
) : Presentation(outerContext, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val composeView = ComposeView(context).apply {
            setContent {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MeldTheme.colors.canvas)
                        .padding(32.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = room.question,
                            fontFamily = InstrumentSerifFamily,
                            fontSize = 36.sp,
                            color = MeldTheme.colors.inkPrimary
                        )
                        room.options.forEach { opt ->
                            StageAvatarRaceLane(
                                option = opt,
                                isRoomLocked = room.isLocked,
                                nudgePulse = nudgePulse
                            )
                        }
                    }
                }
            }
        }
        setContentView(composeView)
    }
}

private suspend fun shareVisualPollCard(context: Context, room: RoomDetailsUiModel) {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = AvatarBitmapRenderer.renderShareablePollCard(room)
            val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
            val outFile = File(dir, "meld_${room.id}.png")
            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }.getOrNull()
    } ?: return

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "${room.question}\nVote live: ${room.shareUrl}")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(sendIntent, null))
    }
}
