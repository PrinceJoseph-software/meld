package io.github.princejoseph.meld.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.princejoseph.meld.ui.components.MeldLogo
import io.github.princejoseph.meld.ui.components.draftingDotGrid
import io.github.princejoseph.meld.ui.components.performTactileConfirm
import io.github.princejoseph.meld.ui.components.performTactileTick
import io.github.princejoseph.meld.ui.theme.InstrumentSerifFamily
import io.github.princejoseph.meld.ui.theme.JetBrainsMonoFamily
import io.github.princejoseph.meld.ui.theme.MeldTheme
import io.github.princejoseph.meld.ui.theme.PlusJakartaSansFamily
import kotlinx.coroutines.launch

private data class OnboardingSlide(
    val badge: String,
    val title: String,
    val description: String,
    val highlight: String,
    val icon: ImageVector
)

private val ONBOARDING_SLIDES = listOf(
    OnboardingSlide(
        badge = "01 / ZERO-LOGIN POLLS",
        title = "Decide Together in Seconds",
        description = "Create Quick Polls or Time Slots with zero sign-up. Share a 6-character code or QR link and let everyone vote—even offline.",
        highlight = "Automatic runoff tie-breakers, deadlines, and one-tap winner crowning.",
        icon = Icons.Outlined.HowToVote
    ),
    OnboardingSlide(
        badge = "02 / TIME COORDINATION",
        title = "Find Overlapping Free Time",
        description = "Sync your device calendar or import an .ics file locally to surface open blocks, visualize Yes/No distribution, and flag VIP conflicts.",
        highlight = "Your calendar events stay 100% on your device.",
        icon = Icons.Outlined.CalendarMonth
    ),
    OnboardingSlide(
        badge = "03 / VOICE OPINIONS",
        title = "Speak the Why Behind Votes",
        description = "Record real offline audio voice notes directly inside any room, attach them to specific options, and play them back with live waveforms.",
        highlight = "No external popups—works offline with typed notes fallback.",
        icon = Icons.Outlined.GraphicEq
    ),
    OnboardingSlide(
        badge = "04 / PRIVACY & BADGES",
        title = "Your Identity, Your Rules",
        description = "Design a custom Bauhaus geometric avatar in Settings, or switch to an Anonymous Mask with anti-spam protection for secret ballots.",
        highlight = "No accounts, no trackers, and full 18-language support.",
        icon = Icons.Outlined.Shield
    )
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MeldTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_SLIDES.size })
    val isLastPage = pagerState.currentPage == ONBOARDING_SLIDES.lastIndex

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_screen"),
        color = colors.canvas
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .draftingDotGrid(colors.dotGrid)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar with MeldLogo & Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MeldLogo(
                    pulseTrigger = pagerState.currentPage,
                    onClick = {}
                )

                TextButton(
                    onClick = {
                        view.performTactileTick()
                        onFinish()
                    },
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = "Skip",
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkSecondary
                    )
                }
            }

            // Swipeable feature tour pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                val slide = ONBOARDING_SLIDES[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(colors.vermilion.copy(alpha = 0.12f))
                            .border(1.5.dp, colors.vermilion.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = null,
                            tint = colors.vermilion,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = slide.badge,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = colors.vermilion
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = slide.title,
                        fontFamily = InstrumentSerifFamily,
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        textAlign = TextAlign.Center,
                        color = colors.inkPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = slide.description,
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                        color = colors.inkSecondary
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated),
                        border = BorderStroke(1.dp, colors.hairline)
                    ) {
                        Text(
                            text = slide.highlight,
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            color = colors.inkPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // Bottom indicator + Next / Get Started CTA
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(ONBOARDING_SLIDES.size) { index ->
                        val selected = pagerState.currentPage == index
                        val width by animateDpAsState(
                            targetValue = if (selected) 24.dp else 8.dp,
                            label = "dot_width"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (selected) colors.vermilion else colors.hairline,
                            label = "dot_color"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(width)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (isLastPage) {
                            view.performTactileConfirm()
                            onFinish()
                        } else {
                            view.performTactileTick()
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("onboarding_next_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.vermilion)
                ) {
                    Text(
                        text = if (isLastPage) "Get Started" else "Next",
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
