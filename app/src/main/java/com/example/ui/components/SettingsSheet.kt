package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AvatarSpec
import com.example.data.VoterIdentity
import com.example.ui.theme.InstrumentSerifFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.VoterBadgeColors
import kotlin.random.Random

private val SHAPE_LABELS = listOf(
    "Visor", "Aperture", "Split", "Totem", "Diamond",
    "Signal", "Pixel", "Orbital", "Shield", "Chrono"
)

private val PATTERN_LABELS = listOf(
    "Solid Matte", "Diagonal Stripes", "Micro Dot Grid", "Concentric Rings", "Quarter Split"
)

private val PALETTE_NAMES = listOf(
    "Vermilion", "Cobalt", "Emerald", "Ochre",
    "Steel Slate", "Royal Violet", "Sunset Coral", "Cyber Cyan"
)

private val MASK_ALIASES = listOf(
    "Agent 07", "Phantom", "Incognito", "Specter", "Cipher",
    "Shadow", "Ghost", "Enigma", "Mirage", "Nomad"
)

private enum class AnonymousCustomizerTab {
    SHAPES,
    PALETTES,
    PATTERNS,
    EXPRESSIONS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    identity: VoterIdentity,
    hapticsEnabled: Boolean,
    onSaveAnonymousBadge: (avatarStyleIndex: Int, badgeColorIndex: Int, maskName: String) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onOpenStandardAvatarStudio: () -> Unit,
    onOpenLanguageSelector: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialSpec = remember(identity.anonymousAvatarStyleIndex) {
        AvatarSpec.fromPacked(identity.anonymousAvatarStyleIndex)
    }

    var draftMaskName by remember(identity.anonymousMaskName) { mutableStateOf(identity.anonymousMaskName) }
    var draftColor by remember(identity.anonymousBadgeColorIndex) {
        mutableIntStateOf(identity.anonymousBadgeColorIndex.coerceIn(0, VoterBadgeColors.lastIndex))
    }
    var draftSpec by remember(initialSpec) { mutableStateOf(initialSpec) }
    var activeTab by remember { mutableStateOf(AnonymousCustomizerTab.SHAPES) }
    var testPushPulse by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.vermilion.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = colors.vermilion,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_title),
                            fontFamily = InstrumentSerifFamily,
                            fontSize = 26.sp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                    }
                }

                TextButton(
                    onClick = {
                        view.performTactileConfirm()
                        onSaveAnonymousBadge(draftSpec.toPacked(), draftColor, draftMaskName)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("settings_done_button")
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.Bold,
                        color = colors.vermilion
                    )
                }
            }

            // 1. IN-DEPTH ANONYMOUS USER BADGE CUSTOMIZATION STUDIO
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("anonymous_badge_studio_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.canvas),
                border = BorderStroke(1.dp, colors.hairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draftingDotGrid(colors.dotGrid)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_anon_badge_title),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = stringResource(R.string.settings_anon_badge_subtitle),
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 11.sp,
                                color = colors.inkSecondary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                view.performTactileConfirm()
                                draftSpec = AvatarSpec(
                                    chassis = Random.nextInt(AvatarSpec.CHASSIS_COUNT),
                                    eyes = Random.nextInt(AvatarSpec.EYES_COUNT),
                                    accessory = Random.nextInt(AvatarSpec.ACCESSORY_COUNT),
                                    pattern = Random.nextInt(AvatarSpec.PATTERN_COUNT),
                                    frame = Random.nextInt(AvatarSpec.FRAME_COUNT)
                                )
                                draftColor = Random.nextInt(VoterBadgeColors.size)
                                draftMaskName = MASK_ALIASES.random()
                                testPushPulse += 1
                            },
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("anon_shuffle_button"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colors.hairline),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Casino,
                                contentDescription = null,
                                tint = colors.vermilion,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.settings_anon_shuffle),
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkPrimary
                            )
                        }
                    }

                    // Live Interactive Stage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        GeometricAvatar(
                            avatarStyleIndex = draftSpec.toPacked(),
                            colorIndex = draftColor,
                            size = 80.dp,
                            borderColor = colors.surfaceElevated,
                            reactionTrigger = testPushPulse,
                            pushBias = if (testPushPulse > 0) 1f else 0f,
                            nudgePulse = testPushPulse
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Mask name field
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (draftMaskName.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.settings_anon_mask_hint),
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 13.sp,
                                        color = colors.inkSecondary.copy(alpha = 0.6f)
                                    )
                                }
                                BasicTextField(
                                    value = draftMaskName,
                                    onValueChange = { draftMaskName = it.take(24) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = PlusJakartaSansFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = colors.inkPrimary
                                    ),
                                    cursorBrush = SolidColor(colors.vermilion),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Words,
                                        imeAction = ImeAction.Done
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("anon_mask_input")
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.vermilion.copy(alpha = 0.12f))
                                    .border(1.dp, colors.vermilion.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        view.performTactileConfirm()
                                        testPushPulse += 1
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("anon_test_push_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = colors.vermilion,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = stringResource(R.string.avatar_action_test_push),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = colors.vermilion
                                )
                            }
                        }
                    }

                    // Tabs for Geometric Shapes, Color Palettes, Surface Patterns, Face Gear
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnonymousCustomizerTab.values().forEach { tab ->
                            val selected = activeTab == tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) colors.inkPrimary else Color.Transparent)
                                    .clickable {
                                        view.performTactileTick()
                                        activeTab = tab
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (tab) {
                                        AnonymousCustomizerTab.SHAPES -> "Shape"
                                        AnonymousCustomizerTab.PALETTES -> "Palette"
                                        AnonymousCustomizerTab.PATTERNS -> "Pattern"
                                        AnonymousCustomizerTab.EXPRESSIONS -> "Gear"
                                    },
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (selected) colors.surfaceElevated else colors.inkSecondary
                                )
                            }
                        }
                    }

                    // Tab Contents
                    when (activeTab) {
                        AnonymousCustomizerTab.SHAPES -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_shape_picker),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(SHAPE_LABELS) { index, label ->
                                        val isSelected = draftSpec.chassis == index
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) colors.vermilion else colors.surfaceElevated)
                                                .border(1.dp, if (isSelected) colors.vermilion else colors.hairline, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    draftSpec = draftSpec.copy(chassis = index)
                                                    testPushPulse += 1
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GeometricAvatar(
                                                avatarStyleIndex = draftSpec.copy(chassis = index).toPacked(),
                                                colorIndex = draftColor,
                                                size = 24.dp,
                                                borderColor = Color.Transparent
                                            )
                                            Text(
                                                text = label,
                                                fontFamily = PlusJakartaSansFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color.White else colors.inkPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        AnonymousCustomizerTab.PALETTES -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_palette_picker),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(VoterBadgeColors) { index, swatch ->
                                        val isSelected = draftColor == index
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(swatch)
                                                .border(
                                                    width = if (isSelected) 3.dp else 1.dp,
                                                    color = if (isSelected) colors.inkPrimary else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    view.performTactileTick()
                                                    draftColor = index
                                                    testPushPulse += 1
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        AnonymousCustomizerTab.PATTERNS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_pattern_picker),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(PATTERN_LABELS) { index, label ->
                                        val isSelected = draftSpec.pattern == index
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) colors.vermilion else colors.surfaceElevated)
                                                .border(1.dp, if (isSelected) colors.vermilion else colors.hairline, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    draftSpec = draftSpec.copy(pattern = index)
                                                    testPushPulse += 1
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GeometricAvatar(
                                                avatarStyleIndex = draftSpec.copy(pattern = index).toPacked(),
                                                colorIndex = draftColor,
                                                size = 24.dp,
                                                borderColor = Color.Transparent
                                            )
                                            Text(
                                                text = label,
                                                fontFamily = PlusJakartaSansFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color.White else colors.inkPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        AnonymousCustomizerTab.EXPRESSIONS -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_face_gear_picker),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(listOf("Dots", "Wink", "Happy", "Star", "Scanner", "Focused")) { index, label ->
                                        val isSelected = draftSpec.eyes == index
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) colors.inkPrimary else colors.surfaceElevated)
                                                .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    draftSpec = draftSpec.copy(eyes = index)
                                                    testPushPulse += 1
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                fontFamily = PlusJakartaSansFamily,
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color.White else colors.inkPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.settings_anon_preview_hint),
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 11.sp,
                        color = colors.inkSecondary.copy(alpha = 0.8f)
                    )
                }
            }

            // 2. APP PREFERENCES & IDENTITY CONTROLS
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.canvas),
                border = BorderStroke(1.dp, colors.hairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Standard Avatar Studio Shortcut
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onDismiss()
                                onOpenStandardAvatarStudio()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                GeometricAvatar(
                                    avatarStyleIndex = identity.avatarStyleIndex,
                                    colorIndex = identity.badgeColorIndex,
                                    size = 28.dp,
                                    borderColor = colors.surfaceElevated
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Standard Voter Profile",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = "${identity.effectiveName} • 67k+ Bauhaus Combos",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Text(
                            text = "Edit",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.vermilion
                        )
                    }

                    HorizontalDivider(color = colors.hairline)

                    // Language Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onDismiss()
                                onOpenLanguageSelector()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.vermilion.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Language,
                                    contentDescription = null,
                                    tint = colors.vermilion,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_language_label),
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = "18 localized language packs available",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Text(
                            text = "Change",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.vermilion
                        )
                    }

                    HorizontalDivider(color = colors.hairline)

                    // Tactile Haptics Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Vibration,
                                    contentDescription = null,
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_haptics_label),
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = stringResource(R.string.settings_haptics_desc),
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }

                        MeldSpringSwitch(
                            checked = hapticsEnabled,
                            onCheckedChange = {
                                if (it) view.performTactileConfirm()
                                onToggleHaptics(it)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
