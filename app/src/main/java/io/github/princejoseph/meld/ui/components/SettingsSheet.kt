package io.github.princejoseph.meld.ui.components

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
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.data.AvatarSpec
import io.github.princejoseph.meld.data.VoterIdentity
import io.github.princejoseph.meld.ui.theme.InstrumentSerifFamily
import io.github.princejoseph.meld.ui.theme.JetBrainsMonoFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily
import io.github.princejoseph.meld.ui.theme.VoterBadgeColors
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

private val ACCESSORY_LABELS = listOf(
    "None", "Antenna", "Headphones", "Crown", "Halo", "Specs", "Spark"
)

private val FRAME_LABELS = listOf(
    "Clean", "Bezel", "Gear Ring", "Arc"
)

private val MASK_ALIASES = listOf(
    "Agent 07", "Phantom", "Incognito", "Specter", "Cipher",
    "Shadow", "Ghost", "Enigma", "Mirage", "Nomad"
)

private enum class BadgeEditorMode {
    STANDARD,
    ANONYMOUS
}

private enum class AnonymousCustomizerTab {
    SHAPES,
    PALETTES,
    PATTERNS,
    EXPRESSIONS,
    ACCESSORIES,
    FRAMES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    identity: VoterIdentity,
    isDarkTheme: Boolean,
    hapticsEnabled: Boolean,
    recentRoomsCount: Int,
    currentLanguageCode: String,
    onSaveStandardProfile: (displayName: String, avatarStyleIndex: Int, badgeColorIndex: Int) -> Unit,
    onSaveAnonymousBadge: (avatarStyleIndex: Int, badgeColorIndex: Int, maskName: String) -> Unit,
    onToggleDarkTheme: () -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLanguageSelector: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialStdSpec = remember(identity.avatarStyleIndex) {
        AvatarSpec.fromPacked(identity.avatarStyleIndex)
    }
    val initialAnonSpec = remember(identity.anonymousAvatarStyleIndex) {
        AvatarSpec.fromPacked(identity.anonymousAvatarStyleIndex)
    }

    var editorMode by remember { mutableStateOf(BadgeEditorMode.STANDARD) }

    var stdName by remember(identity.displayName) { mutableStateOf(identity.displayName) }
    var stdColor by remember(identity.badgeColorIndex) {
        mutableIntStateOf(identity.badgeColorIndex.coerceIn(0, VoterBadgeColors.lastIndex))
    }
    var stdSpec by remember(initialStdSpec) { mutableStateOf(initialStdSpec) }

    var anonMaskName by remember(identity.anonymousMaskName) { mutableStateOf(identity.anonymousMaskName) }
    var anonColor by remember(identity.anonymousBadgeColorIndex) {
        mutableIntStateOf(identity.anonymousBadgeColorIndex.coerceIn(0, VoterBadgeColors.lastIndex))
    }
    var anonSpec by remember(initialAnonSpec) { mutableStateOf(initialAnonSpec) }

    val activeSpec = if (editorMode == BadgeEditorMode.STANDARD) stdSpec else anonSpec
    val activeColor = if (editorMode == BadgeEditorMode.STANDARD) stdColor else anonColor
    val activeName = if (editorMode == BadgeEditorMode.STANDARD) stdName else anonMaskName

    fun updateActiveSpec(transform: (AvatarSpec) -> AvatarSpec) {
        if (editorMode == BadgeEditorMode.STANDARD) {
            stdSpec = transform(stdSpec)
        } else {
            anonSpec = transform(anonSpec)
        }
    }

    fun updateActiveColor(newColor: Int) {
        if (editorMode == BadgeEditorMode.STANDARD) {
            stdColor = newColor
        } else {
            anonColor = newColor
        }
    }

    fun updateActiveName(newName: String) {
        if (editorMode == BadgeEditorMode.STANDARD) {
            stdName = newName.take(28)
        } else {
            anonMaskName = newName.take(24)
        }
    }

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
                        onSaveStandardProfile(stdName, stdSpec.toPacked(), stdColor)
                        onSaveAnonymousBadge(anonSpec.toPacked(), anonColor, anonMaskName)
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
                                text = if (editorMode == BadgeEditorMode.STANDARD) {
                                    stringResource(R.string.avatar_studio_title)
                                } else {
                                    stringResource(R.string.settings_anon_badge_title)
                                },
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = if (editorMode == BadgeEditorMode.STANDARD) {
                                    "Customize your public avatar and display name"
                                } else {
                                    stringResource(R.string.settings_anon_badge_subtitle)
                                },
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 11.sp,
                                color = colors.inkSecondary
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                view.performTactileConfirm()
                                val randomSpec = AvatarSpec(
                                    chassis = Random.nextInt(AvatarSpec.CHASSIS_COUNT),
                                    eyes = Random.nextInt(AvatarSpec.EYES_COUNT),
                                    accessory = Random.nextInt(AvatarSpec.ACCESSORY_COUNT),
                                    pattern = Random.nextInt(AvatarSpec.PATTERN_COUNT),
                                    frame = Random.nextInt(AvatarSpec.FRAME_COUNT)
                                )
                                updateActiveSpec { randomSpec }
                                updateActiveColor(Random.nextInt(VoterBadgeColors.size))
                                if (editorMode == BadgeEditorMode.ANONYMOUS) {
                                    updateActiveName(MASK_ALIASES.random())
                                }
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

                    // Mode Switcher: My Profile (Standard) vs Anonymous Mask
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BadgeEditorMode.values().forEach { mode ->
                            val selected = editorMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) colors.vermilion else Color.Transparent)
                                    .clickable {
                                        view.performTactileTick()
                                        editorMode = mode
                                        testPushPulse += 1
                                    }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (mode == BadgeEditorMode.STANDARD) "My Profile" else "Anonymous Mask",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (selected) Color.White else colors.inkSecondary
                                )
                            }
                        }
                    }

                    // Live Interactive Stage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        GeometricAvatar(
                            avatarStyleIndex = activeSpec.toPacked(),
                            colorIndex = activeColor,
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
                            // Name / Mask name field
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
                                if (activeName.isEmpty()) {
                                    Text(
                                        text = if (editorMode == BadgeEditorMode.STANDARD) {
                                            "Display name (e.g. Alex)"
                                        } else {
                                            stringResource(R.string.settings_anon_mask_hint)
                                        },
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 13.sp,
                                        color = colors.inkSecondary.copy(alpha = 0.6f)
                                    )
                                }
                                BasicTextField(
                                    value = activeName,
                                    onValueChange = { updateActiveName(it) },
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

                    // Tabs for Geometric Shapes, Color Palettes, Surface Patterns, Eyes, Accessories, Frames
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
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
                                        AnonymousCustomizerTab.EXPRESSIONS -> "Eyes"
                                        AnonymousCustomizerTab.ACCESSORIES -> "Gear"
                                        AnonymousCustomizerTab.FRAMES -> "Frame"
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
                                        val isSelected = activeSpec.chassis == index
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) colors.vermilion else colors.surfaceElevated)
                                                .border(1.dp, if (isSelected) colors.vermilion else colors.hairline, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    updateActiveSpec { it.copy(chassis = index) }
                                                    testPushPulse += 1
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GeometricAvatar(
                                                avatarStyleIndex = activeSpec.copy(chassis = index).toPacked(),
                                                colorIndex = activeColor,
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
                                        val isSelected = activeColor == index
                                        val paletteName = PALETTE_NAMES.getOrElse(index) { "Color ${index + 1}" }
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
                                                .semantics {
                                                    contentDescription = if (isSelected) {
                                                        "$paletteName palette, selected"
                                                    } else {
                                                        "$paletteName palette"
                                                    }
                                                }
                                                .clickable {
                                                    view.performTactileTick()
                                                    updateActiveColor(index)
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
                                        val isSelected = activeSpec.pattern == index
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) colors.vermilion else colors.surfaceElevated)
                                                .border(1.dp, if (isSelected) colors.vermilion else colors.hairline, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    updateActiveSpec { it.copy(pattern = index) }
                                                    testPushPulse += 1
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GeometricAvatar(
                                                avatarStyleIndex = activeSpec.copy(pattern = index).toPacked(),
                                                colorIndex = activeColor,
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
                                        val isSelected = activeSpec.eyes == index
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) colors.inkPrimary else colors.surfaceElevated)
                                                .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    updateActiveSpec { it.copy(eyes = index) }
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
                        AnonymousCustomizerTab.ACCESSORIES -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Headgear & Accessories",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(ACCESSORY_LABELS) { index, label ->
                                        val isSelected = activeSpec.accessory == index
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) colors.vermilion else colors.surfaceElevated)
                                                .border(1.dp, if (isSelected) colors.vermilion else colors.hairline, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    updateActiveSpec { it.copy(accessory = index) }
                                                    testPushPulse += 1
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GeometricAvatar(
                                                avatarStyleIndex = activeSpec.copy(accessory = index).toPacked(),
                                                colorIndex = activeColor,
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
                        AnonymousCustomizerTab.FRAMES -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Border Frame Style",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 11.sp,
                                    color = colors.inkSecondary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(FRAME_LABELS) { index, label ->
                                        val isSelected = activeSpec.frame == index
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (isSelected) colors.vermilion else colors.surfaceElevated)
                                                .border(1.dp, if (isSelected) colors.vermilion else colors.hairline, RoundedCornerShape(10.dp))
                                                .clickable {
                                                    view.performTactileTick()
                                                    updateActiveSpec { it.copy(frame = index) }
                                                    testPushPulse += 1
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            GeometricAvatar(
                                                avatarStyleIndex = activeSpec.copy(frame = index).toPacked(),
                                                colorIndex = activeColor,
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
                    // Room History
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onDismiss()
                                onOpenHistory()
                            }
                            .padding(vertical = 6.dp)
                            .testTag("settings_history_row"),
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
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = null,
                                    tint = colors.vermilion,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.cd_open_history),
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = if (recentRoomsCount == 1) "1 saved room" else "$recentRoomsCount saved rooms",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }
                        Text(
                            text = "View",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.vermilion
                        )
                    }

                    HorizontalDivider(color = colors.hairline)

                    // Dark / Light Appearance Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_theme_row"),
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
                                    imageVector = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                    contentDescription = null,
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Appearance",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = if (isDarkTheme) "Obsidian Dark" else "Warm Archival Paper",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkSecondary
                                )
                            }
                        }

                        MeldSpringSwitch(
                            checked = isDarkTheme,
                            onCheckedChange = {
                                view.performTactileTick()
                                onToggleDarkTheme()
                            }
                        )
                    }

                    HorizontalDivider(color = colors.hairline)

                    // Language Selector
                    val activeLangItem = SUPPORTED_LANGUAGES.firstOrNull { currentLanguageCode.startsWith(it.code) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onDismiss()
                                onOpenLanguageSelector()
                            }
                            .padding(vertical = 6.dp)
                            .testTag("settings_language_row"),
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
                                    text = activeLangItem?.let { "${it.nativeName} (${it.EnglishName})" } ?: "English",
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
