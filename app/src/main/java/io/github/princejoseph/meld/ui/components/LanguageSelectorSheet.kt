package io.github.princejoseph.meld.ui.components

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.princejoseph.meld.R
import io.github.princejoseph.meld.ui.theme.InstrumentSerifFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme

data class LanguageItem(val code: String, val nativeName: String, val EnglishName: String)

val SUPPORTED_LANGUAGES = listOf(
    LanguageItem("en", "English", "English"),
    LanguageItem("es", "Español", "Spanish"),
    LanguageItem("fr", "Français", "French"),
    LanguageItem("de", "Deutsch", "German"),
    LanguageItem("ja", "日本語", "Japanese"),
    LanguageItem("zh", "中文", "Chinese"),
    LanguageItem("pt", "Português", "Portuguese"),
    LanguageItem("ru", "Русский", "Russian"),
    LanguageItem("hi", "हिन्दी", "Hindi"),
    LanguageItem("it", "Italiano", "Italian"),
    LanguageItem("ko", "한국어", "Korean"),
    LanguageItem("nl", "Nederlands", "Dutch"),
    LanguageItem("pl", "Polski", "Polish"),
    LanguageItem("tr", "Türkçe", "Turkish"),
    LanguageItem("vi", "Tiếng Việt", "Vietnamese"),
    LanguageItem("ar", "العربية", "Arabic"),
    LanguageItem("id", "Bahasa Indonesia", "Indonesian"),
    LanguageItem("sv", "Svenska", "Swedish")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectorSheet(
    onSelectLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MeldTheme.colors
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { "en" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Choose Language",
                        fontFamily = InstrumentSerifFamily,
                        fontSize = 26.sp,
                        color = colors.inkPrimary
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(text = "Done", color = colors.vermilion)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SUPPORTED_LANGUAGES) { lang ->
                    val isSelected = currentTag.startsWith(lang.code)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.vermilion.copy(alpha = 0.1f) else colors.canvas)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) colors.vermilion else colors.hairline,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(lang.code)
                                )
                                onSelectLanguage(lang.code)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = lang.nativeName,
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = colors.inkPrimary
                            )
                            Text(
                                text = lang.EnglishName,
                                fontSize = 12.sp,
                                color = colors.inkSecondary
                            )
                        }
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colors.vermilion)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
