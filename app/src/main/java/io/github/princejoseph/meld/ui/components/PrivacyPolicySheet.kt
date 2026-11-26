package io.github.princejoseph.meld.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.princejoseph.meld.ui.theme.InstrumentSerifFamily
import io.github.princejoseph.meld.ui.theme.JetBrainsMonoFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicySheet(
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("privacy_policy_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            imageVector = Icons.Outlined.PrivacyTip,
                            contentDescription = null,
                            tint = colors.vermilion,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Privacy Policy",
                            fontFamily = InstrumentSerifFamily,
                            fontSize = 26.sp,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "Zero-login, local-first by design",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 12.sp,
                            color = colors.inkSecondary
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("privacy_done_button")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = colors.vermilion)
                }
            }

            PrivacySectionCard(
                icon = Icons.Outlined.Storage,
                title = "Local-First Storage & Zero Accounts",
                body = "Meld requires no sign-up, email, or phone number. Your display profile, Bauhaus avatar, rooms, votes, and preferences stay on your device in Room & DataStore."
            )

            PrivacySectionCard(
                icon = Icons.Outlined.Shield,
                title = "Anonymous Mode & Salted Hashing",
                body = "In secret-ballot rooms, anti-spam protection hashes your network identifier with a local device salt (SHA-256). Raw IP addresses are never saved or exposed."
            )

            PrivacySectionCard(
                icon = Icons.Outlined.MicNone,
                title = "Offline Microphone & Calendar Access",
                body = "Voice notes are recorded locally in private app storage using Android MediaRecorder—never sent to external speech servers. Calendar sync reads availability locally."
            )

            PrivacySectionCard(
                icon = Icons.Outlined.Lock,
                title = "No Ads, No Trackers, No Cloud Backup",
                body = "Meld contains zero analytics or ad SDKs. Android Auto Backup is disabled (allowBackup=false). Deleting a room or voice note immediately erases its local files."
            )

            Text(
                text = "Effective Nov 26, 2026 • Full document in PRIVACY_POLICY.md",
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp,
                color = colors.inkSecondary
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PrivacySectionCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    val colors = MeldTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.canvas),
        border = BorderStroke(1.dp, colors.hairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.vermilion,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.inkPrimary
                )
                Text(
                    text = body,
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.inkSecondary
                )
            }
        }
    }
}
