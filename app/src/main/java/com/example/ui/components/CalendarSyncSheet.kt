package com.example.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DeviceCalendarReader
import com.example.ui.CalendarSyncSource
import com.example.ui.CalendarSyncUiState
import com.example.ui.theme.InstrumentSerifFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.PlusJakartaSansFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSyncSheet(
    syncState: CalendarSyncUiState,
    onSyncDeviceCalendar: () -> Unit,
    onImportIcsUri: (android.net.Uri, String) -> Unit,
    onPermissionDenied: (String) -> Unit,
    onToggleSlot: (String) -> Unit,
    onApplySelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val deniedMsg = stringResource(R.string.calendar_permission_denied)
    val icsErrorMsg = stringResource(R.string.calendar_ics_error)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onSyncDeviceCalendar()
        } else {
            onPermissionDenied(deniedMsg)
        }
    }

    val icsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportIcsUri(uri, icsErrorMsg)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.inkPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.calendar_sheet_title),
                    fontFamily = InstrumentSerifFamily,
                    fontSize = 26.sp,
                    color = colors.inkPrimary
                )
                Text(
                    text = stringResource(R.string.calendar_sheet_subtitle),
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 13.sp,
                    color = colors.inkSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        view.performTactileTick()
                        if (DeviceCalendarReader.hasCalendarPermission(context)) {
                            onSyncDeviceCalendar()
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sync_device_calendar_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.hairline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surfaceInset,
                        contentColor = colors.inkPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_sync_device_calendar),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        view.performTactileTick()
                        icsPickerLauncher.launch(arrayOf("text/calendar", "application/ics", "*/*"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("import_ics_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.hairline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surfaceInset,
                        contentColor = colors.inkPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_import_ics),
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }

            if (syncState.statusMessage != null) {
                Text(
                    text = syncState.statusMessage,
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 13.sp,
                    color = colors.vermilion
                )
            }

            if (syncState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.vermilion,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = !syncState.isLoading && syncState.suggestedSlots.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.calendar_suggested_header),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            color = colors.inkSecondary
                        )
                        val sourceLabel = when (syncState.source) {
                            CalendarSyncSource.DEVICE_CALENDAR -> stringResource(R.string.calendar_source_device)
                            CalendarSyncSource.ICS_FILE -> stringResource(R.string.calendar_source_ics)
                            CalendarSyncSource.NONE -> ""
                        }
                        if (sourceLabel.isNotEmpty()) {
                            Text(
                                text = sourceLabel,
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 10.sp,
                                color = colors.emerald
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(syncState.suggestedSlots, key = { it.id }) { slot ->
                            val isSelected = syncState.selectedSlotIds.contains(slot.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) colors.vermilion.copy(alpha = 0.08f)
                                        else colors.surfaceInset
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) colors.vermilion else colors.hairline,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        view.performTactileTick()
                                        onToggleSlot(slot.id)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AnimatedCheckmarkBox(checked = isSelected)
                                Text(
                                    text = slot.label,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            view.performTactileConfirm()
                            onApplySelected()
                            onDismiss()
                        },
                        enabled = syncState.selectedSlotIds.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("apply_calendar_slots_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.vermilion,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(
                                R.string.action_apply_slots,
                                syncState.selectedSlotIds.size
                            ),
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
