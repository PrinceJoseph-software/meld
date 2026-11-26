package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private enum class StudioTab {
    CHASSIS,
    EYES,
    GEAR,
    PATTERN,
    FRAME,
    PALETTE
}

private val CHASSIS_LABELS = listOf(
    "Visor", "Aperture", "Split", "Totem", "Diamond",
    "Signal", "Pixel", "Orbital", "Shield", "Chrono"
)
private val EYE_LABELS = listOf(
    "Dots", "Wink", "Happy", "Star", "Scanner", "Focused"
)
private val GEAR_LABELS = listOf(
    "None", "Antenna", "Cans", "Crown", "Halo", "Specs", "Spark"
)
private val PATTERN_LABELS = listOf(
    "Solid", "Stripes", "Dots", "Target", "Split"
)
private val FRAME_LABELS = listOf(
    "Clean", "Bezel", "Gear", "Arc"
)
private val PALETTE_LABELS = listOf(
    "Vermilion", "Cobalt", "Emerald", "Ochre",
    "Steel", "Violet", "Coral", "Cyan"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AvatarStudioSheet(
    identity: VoterIdentity,
    onSave: (name: String, packedAvatarStyle: Int, badgeColorIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialSpec = remember(identity.avatarStyleIndex) {
        AvatarSpec.fromPacked(identity.avatarStyleIndex)
    }

    var draftName by remember(identity.displayName) { mutableStateOf(identity.displayName) }
    var draftColor by remember(identity.badgeColorIndex) {
        mutableIntStateOf(identity.badgeColorIndex.coerceIn(0, VoterBadgeColors.lastIndex))
    }
    var draftSpec by remember(initialSpec) { mutableStateOf(initialSpec) }
    var activeTab by remember { mutableStateOf(StudioTab.CHASSIS) }
    var testPushPulse by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.avatar_studio_title),
                        fontFamily = InstrumentSerifFamily,
                        fontSize = 26.sp,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = stringResource(R.string.avatar_studio_subtitle),
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 12.sp,
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
                        testPushPulse += 1
                    },
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("avatar_shuffle_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.hairline)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Casino,
                        contentDescription = null,
                        tint = colors.vermilion,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.avatar_action_shuffle),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.inkPrimary
                    )
                }
            }

            // Live Interactive Stage with Push Animation Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.canvas)
                    .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
                    .draftingDotGrid(colors.dotGrid)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GeometricAvatar(
                        avatarStyleIndex = draftSpec.toPacked(),
                        colorIndex = draftColor,
                        size = 84.dp,
                        borderColor = colors.surfaceElevated,
                        reactionTrigger = testPushPulse,
                        pushBias = if (testPushPulse > 0) 1f else 0f,
                        nudgePulse = testPushPulse
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draftName.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.voter_name_placeholder),
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 14.sp,
                                    color = colors.inkSecondary.copy(alpha = 0.6f)
                                )
                            }
                            BasicTextField(
                                value = draftName,
                                onValueChange = { draftName = it.take(28) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                ),
                                cursorBrush = SolidColor(colors.vermilion),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    view.performTactileConfirm()
                                    onSave(draftName, draftSpec.toPacked(), draftColor)
                                    onDismiss()
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("studio_name_input")
                            )
                        }

                        Row(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.vermilion.copy(alpha = 0.11f))
                                .border(1.dp, colors.vermilion.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable {
                                    view.performTactileConfirm()
                                    testPushPulse += 1
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("avatar_test_push_button"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = colors.vermilion,
                                modifier = Modifier.size(14.dp)
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
            }

            // Category Tabs
            val tabs = listOf(
                StudioTab.CHASSIS to R.string.avatar_tab_chassis,
                StudioTab.EYES to R.string.avatar_tab_eyes,
                StudioTab.GEAR to R.string.avatar_tab_gear,
                StudioTab.PATTERN to R.string.avatar_tab_pattern,
                StudioTab.FRAME to R.string.avatar_tab_frame,
                StudioTab.PALETTE to R.string.avatar_tab_palette
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                tabs.forEach { (tab, labelRes) ->
                    val selected = activeTab == tab
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selected) colors.inkPrimary else colors.surfaceInset)
                            .border(
                                1.dp,
                                if (selected) colors.inkPrimary else colors.hairline,
                                RoundedCornerShape(9.dp)
                            )
                            .clickable {
                                view.performTactileTick()
                                activeTab = tab
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("studio_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (selected) colors.canvas else colors.inkSecondary
                        )
                    }
                }
            }

            // Trait Selection Grid
            AnimatedContent(targetState = activeTab, label = "studio_tab_grid") { tab ->
                when (tab) {
                    StudioTab.CHASSIS -> TraitPreviewGrid(
                        count = AvatarSpec.CHASSIS_COUNT,
                        selectedIndex = draftSpec.chassis,
                        labels = CHASSIS_LABELS,
                        colorIndex = draftColor,
                        specForIndex = { idx -> draftSpec.copy(chassis = idx) },
                        onSelect = { idx ->
                            view.performTactileTick()
                            draftSpec = draftSpec.copy(chassis = idx)
                        }
                    )
                    StudioTab.EYES -> TraitPreviewGrid(
                        count = AvatarSpec.EYES_COUNT,
                        selectedIndex = draftSpec.eyes,
                        labels = EYE_LABELS,
                        colorIndex = draftColor,
                        specForIndex = { idx -> draftSpec.copy(eyes = idx) },
                        onSelect = { idx ->
                            view.performTactileTick()
                            draftSpec = draftSpec.copy(eyes = idx)
                        }
                    )
                    StudioTab.GEAR -> TraitPreviewGrid(
                        count = AvatarSpec.ACCESSORY_COUNT,
                        selectedIndex = draftSpec.accessory,
                        labels = GEAR_LABELS,
                        colorIndex = draftColor,
                        specForIndex = { idx -> draftSpec.copy(accessory = idx) },
                        onSelect = { idx ->
                            view.performTactileTick()
                            draftSpec = draftSpec.copy(accessory = idx)
                        }
                    )
                    StudioTab.PATTERN -> TraitPreviewGrid(
                        count = AvatarSpec.PATTERN_COUNT,
                        selectedIndex = draftSpec.pattern,
                        labels = PATTERN_LABELS,
                        colorIndex = draftColor,
                        specForIndex = { idx -> draftSpec.copy(pattern = idx) },
                        onSelect = { idx ->
                            view.performTactileTick()
                            draftSpec = draftSpec.copy(pattern = idx)
                        }
                    )
                    StudioTab.FRAME -> TraitPreviewGrid(
                        count = AvatarSpec.FRAME_COUNT,
                        selectedIndex = draftSpec.frame,
                        labels = FRAME_LABELS,
                        colorIndex = draftColor,
                        specForIndex = { idx -> draftSpec.copy(frame = idx) },
                        onSelect = { idx ->
                            view.performTactileTick()
                            draftSpec = draftSpec.copy(frame = idx)
                        }
                    )
                    StudioTab.PALETTE -> FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VoterBadgeColors.forEachIndexed { idx, swatchColor ->
                            val selected = draftColor == idx
                            Row(
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.surfaceInset else colors.canvas)
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) colors.inkPrimary else colors.hairline,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        view.performTactileTick()
                                        draftColor = idx
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("studio_color_$idx"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                )
                                Text(
                                    text = PALETTE_LABELS.getOrElse(idx) { "Tone ${idx + 1}" },
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    view.performTactileConfirm()
                    onSave(draftName, draftSpec.toPacked(), draftColor)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("studio_save_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inkPrimary,
                    contentColor = colors.canvas
                )
            ) {
                Text(
                    text = stringResource(R.string.avatar_action_save),
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TraitPreviewGrid(
    count: Int,
    selectedIndex: Int,
    labels: List<String>,
    colorIndex: Int,
    specForIndex: (Int) -> AvatarSpec,
    onSelect: (Int) -> Unit
) {
    val colors = MeldTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (idx in 0 until count) {
            val selected = idx == selectedIndex
            val spec = remember(idx, colorIndex, specForIndex) { specForIndex(idx) }
            Column(
                modifier = Modifier
                    .defaultMinSize(minWidth = 76.dp, minHeight = 68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) colors.vermilion.copy(alpha = 0.08f) else colors.canvas)
                    .border(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) colors.vermilion else colors.hairline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("studio_trait_${labels.getOrElse(idx) { "$idx" }.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                GeometricAvatar(
                    avatarStyleIndex = spec.toPacked(),
                    colorIndex = colorIndex,
                    size = 34.dp,
                    borderColor = colors.surfaceElevated
                )
                Text(
                    text = labels.getOrElse(idx) { "#${idx + 1}" },
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 10.sp,
                    color = if (selected) colors.vermilion else colors.inkSecondary
                )
            }
        }
    }
}
