package io.github.princejoseph.meld.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.data.OptionUiModel
import io.github.princejoseph.meld.data.VoiceCommentUiModel
import io.github.princejoseph.meld.ui.theme.InstrumentSerifFamily
import io.github.princejoseph.meld.ui.theme.JetBrainsMonoFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.sin

@Composable
fun VoiceCommentsSection(
    comments: List<VoiceCommentUiModel>,
    options: List<OptionUiModel>,
    isRoomLocked: Boolean,
    onAddComment: (optionId: String?, transcript: String, durationSeconds: Int, audioFilePath: String?) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val context = LocalContext.current
    val view = LocalView.current

    var selectedOptionId by remember { mutableStateOf<String?>(null) }
    var activePlayingId by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var currentRecordingPath by remember { mutableStateOf<String?>(null) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showFallbackDialog by remember { mutableStateOf(false) }
    var fallbackText by remember { mutableStateOf("") }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var speech: TextToSpeech? = null
        speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speech?.language = Locale.getDefault()
            }
        }
        speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                activePlayingId = utteranceId
            }
            override fun onDone(utteranceId: String?) {
                if (activePlayingId == utteranceId) activePlayingId = null
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (activePlayingId == utteranceId) activePlayingId = null
            }
        })
        tts = speech
        onDispose {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
            speech.stop()
            speech.shutdown()
        }
    }

    fun stopAllPlayback() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        tts?.stop()
        activePlayingId = null
    }

    fun startInlineRecording() {
        stopAllPlayback()
        val dir = File(context.filesDir, "voice_notes").apply { mkdirs() }
        val outFile = File(dir, "vc_${UUID.randomUUID()}.m4a")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val started = runCatching {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(32_000)
            newRecorder.setAudioSamplingRate(16_000)
            newRecorder.setOutputFile(outFile.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
        }.isSuccess

        if (started) {
            recorder = newRecorder
            currentRecordingPath = outFile.absolutePath
            recordingSeconds = 0
            isRecording = true
        } else {
            runCatching { newRecorder.release() }
            runCatching { outFile.delete() }
            isRecording = false
            showFallbackDialog = true
        }
    }

    fun stopAndSaveInlineRecording() {
        val activeRecorder = recorder
        val path = currentRecordingPath
        val dur = recordingSeconds.coerceIn(1, 60)
        recorder = null
        currentRecordingPath = null
        isRecording = false

        val stoppedCleanly = runCatching {
            activeRecorder?.stop()
        }.isSuccess
        runCatching { activeRecorder?.release() }

        if (stoppedCleanly && path != null && File(path).exists() && File(path).length() > 0L) {
            view.performTactileConfirm()
            onAddComment(selectedOptionId, "Voice note (0:${dur.toString().padStart(2, '0')})", dur, path)
        } else {
            if (path != null) runCatching { File(path).delete() }
            showFallbackDialog = true
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording && recordingSeconds < 60) {
                delay(1000L)
                recordingSeconds += 1
            }
            if (isRecording && recordingSeconds >= 60) {
                stopAndSaveInlineRecording()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startInlineRecording()
        } else {
            showFallbackDialog = true
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_comments_section"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated),
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.vermilion.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = null,
                            tint = colors.vermilion,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${stringResource(R.string.voice_comments_header)} (${comments.size})",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = colors.inkPrimary
                    )
                }

                if (!isRoomLocked) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                view.performTactileTick()
                                showFallbackDialog = true
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.canvas)
                                .border(1.dp, colors.hairline, CircleShape)
                                .testTag("type_voice_comment_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditNote,
                                contentDescription = stringResource(R.string.voice_comments_add_opinion),
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                view.performTactileTick()
                                if (isRecording) {
                                    stopAndSaveInlineRecording()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        startInlineRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) colors.inkPrimary else colors.vermilion)
                                .testTag("record_voice_comment_button")
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Outlined.Stop else Icons.Outlined.Mic,
                                contentDescription = if (isRecording) {
                                    stringResource(R.string.voice_comments_stop)
                                } else {
                                    stringResource(R.string.voice_comments_record)
                                },
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // Topic Selector (General or attached to a specific option)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.voice_comments_target_picker),
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 11.sp,
                    color = colors.inkSecondary
                )

                FilterChip(
                    selected = selectedOptionId == null,
                    onClick = {
                        view.performTactileTick()
                        selectedOptionId = null
                    },
                    label = { Text("General", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.inkPrimary,
                        selectedLabelColor = colors.surfaceElevated
                    )
                )

                options.forEach { opt ->
                    FilterChip(
                        selected = selectedOptionId == opt.id,
                        onClick = {
                            view.performTactileTick()
                            selectedOptionId = opt.id
                        },
                        label = {
                            Text(
                                text = opt.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.inkPrimary,
                            selectedLabelColor = colors.surfaceElevated
                        )
                    )
                }
            }

            // Live Inline Recording Indicator
            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "rec_alpha"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.vermilion.copy(alpha = 0.08f))
                        .border(1.dp, colors.vermilion.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.vermilion.copy(alpha = pulseAlpha))
                        )
                        Text(
                            text = "Recording offline • 0:${recordingSeconds.toString().padStart(2, '0')}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = colors.vermilion
                        )
                    }
                    TextButton(
                        onClick = { stopAndSaveInlineRecording() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "Save",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.vermilion
                        )
                    }
                }
            }

            // List of Voice Comments or Empty state
            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.canvas)
                        .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.voice_comments_empty),
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 13.sp,
                        color = colors.inkSecondary,
                        lineHeight = 18.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    comments.forEach { comment ->
                        val isPlaying = activePlayingId == comment.id
                        VoiceCommentCard(
                            comment = comment,
                            isPlaying = isPlaying,
                            onPlayToggle = {
                                if (isPlaying) {
                                    stopAllPlayback()
                                } else {
                                    view.performTactileTick()
                                    stopAllPlayback()
                                    val audioPath = comment.audioFilePath
                                    if (!audioPath.isNullOrBlank() && File(audioPath).exists()) {
                                        val player = MediaPlayer()
                                        val played = runCatching {
                                            player.setDataSource(audioPath)
                                            player.prepare()
                                            player.setOnCompletionListener {
                                                if (activePlayingId == comment.id) {
                                                    activePlayingId = null
                                                }
                                                runCatching { player.release() }
                                                if (mediaPlayer === player) mediaPlayer = null
                                            }
                                            player.start()
                                        }.isSuccess
                                        if (played) {
                                            mediaPlayer = player
                                            activePlayingId = comment.id
                                        } else {
                                            runCatching { player.release() }
                                            activePlayingId = comment.id
                                            tts?.speak(comment.transcript, TextToSpeech.QUEUE_FLUSH, null, comment.id)
                                        }
                                    } else {
                                        activePlayingId = comment.id
                                        tts?.speak(comment.transcript, TextToSpeech.QUEUE_FLUSH, null, comment.id)
                                    }
                                }
                            },
                            onDelete = {
                                if (isPlaying) stopAllPlayback()
                                view.performTactileConfirm()
                                onDeleteComment(comment.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackDialog = false },
            containerColor = colors.surfaceElevated,
            title = {
                Text(
                    text = stringResource(R.string.voice_comments_add_opinion),
                    fontFamily = InstrumentSerifFamily,
                    fontSize = 22.sp,
                    color = colors.inkPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = fallbackText,
                    onValueChange = { fallbackText = it.take(300) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.voice_comments_mic_prompt),
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 13.sp
                        )
                    },
                    singleLine = false,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("voice_comment_fallback_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleaned = fallbackText.trim()
                        if (cleaned.isNotEmpty()) {
                            val estimatedSec = (cleaned.split(Regex("\\s+")).size / 2).coerceIn(2, 30)
                            view.performTactileConfirm()
                            onAddComment(selectedOptionId, cleaned, estimatedSec, null)
                            fallbackText = ""
                        }
                        showFallbackDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.vermilion)
                ) {
                    Text(stringResource(R.string.action_add_inline), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFallbackDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = colors.inkSecondary)
                }
            }
        )
    }
}

@Composable
private fun VoiceCommentCard(
    comment: VoiceCommentUiModel,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MeldTheme.colors

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_comment_${comment.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.canvas),
        border = BorderStroke(1.dp, if (isPlaying) colors.vermilion else colors.hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GeometricAvatar(
                        avatarStyleIndex = comment.voterAvatarStyleIndex,
                        colorIndex = comment.voterBadgeColorIndex,
                        size = 28.dp,
                        borderColor = colors.canvas,
                        reactionTrigger = if (isPlaying) 1 else 0
                    )
                    Column {
                        Text(
                            text = comment.voterName,
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.inkPrimary
                        )
                        if (comment.optionLabel != null) {
                            Text(
                                text = "On: ${comment.optionLabel}",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 11.sp,
                                color = colors.vermilion,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayToggle,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) colors.vermilion else colors.vermilion.copy(alpha = 0.12f))
                            .testTag("play_voice_comment_${comment.id}")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                            contentDescription = if (isPlaying) "Stop" else "Play",
                            tint = if (isPlaying) Color.White else colors.vermilion,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (comment.isMyComment) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete comment",
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Audio Waveform Equalizer Display
            AudioWaveformBar(
                isPlaying = isPlaying,
                durationSeconds = comment.durationSeconds,
                modifier = Modifier.fillMaxWidth()
            )

            // Transcript / Audio Label
            Text(
                text = if (comment.audioFilePath != null) comment.transcript else "\"${comment.transcript}\"",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.inkPrimary
            )
        }
    }
}

@Composable
private fun AudioWaveformBar(
    isPlaying: Boolean,
    durationSeconds: Int,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surfaceElevated)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = if (isPlaying) {
                    "Playing voice note, $durationSeconds seconds"
                } else {
                    "Voice note waveform, $durationSeconds seconds"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val barCount = 18
        repeat(barCount) { i ->
            val heightFraction = if (isPlaying) {
                val wave = (sin(phase + i * 0.45f) + 1f) / 2f
                0.25f + 0.75f * wave
            } else {
                val staticWave = (sin(i * 0.55f) + 1f) / 2f
                0.20f + 0.45f * staticWave
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((16.dp * heightFraction).coerceIn(3.dp, 16.dp))
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isPlaying) colors.vermilion else colors.inkSecondary.copy(alpha = 0.4f))
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "0:${durationSeconds.toString().padStart(2, '0')}",
            fontFamily = JetBrainsMonoFamily,
            fontSize = 11.sp,
            color = if (isPlaying) colors.vermilion else colors.inkSecondary
        )
    }
}
