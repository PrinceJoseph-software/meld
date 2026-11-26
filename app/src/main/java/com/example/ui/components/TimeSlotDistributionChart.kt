package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.OptionUiModel
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.MeldTheme
import com.example.ui.theme.MonoTabularTextStyle
import com.example.ui.theme.PlusJakartaSansFamily

/**
 * Interactive diverging Yes / No bar chart (Recharts-inspired gridlines, axis ticks,
 * legend, and tap-to-inspect tooltip) for instant comparison of time slot popularity.
 */
@Composable
fun TimeSlotDistributionChart(
    options: List<OptionUiModel>,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val colors = MeldTheme.colors
    val view = LocalView.current
    var inspectedOptionId by remember(options.map { it.id }) {
        mutableStateOf<String?>(null)
    }

    val maxYes = remember(options) { (options.maxOfOrNull { it.voteCount } ?: 1).coerceAtLeast(1) }
    val maxNo = remember(options) { (options.maxOfOrNull { it.declinedCount } ?: 1).coerceAtLeast(1) }
    val scaleMax = remember(maxYes, maxNo) { maxOf(maxYes, maxNo, 1) }

    val bestOption = remember(options) {
        options.maxWithOrNull(
            compareBy<OptionUiModel> { it.voteCount - it.declinedCount }
                .thenBy { it.voteCount }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(14.dp))
            .padding(14.dp)
            .testTag("yes_no_distribution_chart"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header + Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.chart_title),
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                letterSpacing = 0.85.sp,
                color = colors.inkSecondary
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LegendChip(label = stringResource(R.string.chart_legend_no), swatch = colors.vermilion)
                LegendChip(label = stringResource(R.string.chart_legend_yes), swatch = colors.emerald)
            }
        }

        // Diverging CartesianGrid Axis Header (-Max | 0 | +Max)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(104.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "-$scaleMax",
                    style = MonoTabularTextStyle.copy(fontSize = 9.sp, color = colors.inkSecondary)
                )
                Text(
                    text = "0",
                    style = MonoTabularTextStyle.copy(fontSize = 9.sp, color = colors.inkPrimary)
                )
                Text(
                    text = "+$scaleMax",
                    style = MonoTabularTextStyle.copy(fontSize = 9.sp, color = colors.inkSecondary)
                )
            }
            Spacer(modifier = Modifier.width(46.dp))
        }

        // Chart Body with Recharts-style dashed CartesianGrid behind bars
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val labelColPx = 104.dp.toPx()
                val countColPx = 46.dp.toPx()
                val plotLeft = labelColPx
                val plotRight = (size.width - countColPx).coerceAtLeast(plotLeft + 20f)
                val plotWidth = plotRight - plotLeft
                val centerX = plotLeft + plotWidth / 2f

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                listOf(0f, 0.25f, 0.75f, 1f).forEach { frac ->
                    val x = plotLeft + plotWidth * frac
                    drawLine(
                        color = colors.hairline.copy(alpha = 0.75f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect
                    )
                }
                // Crisp 0-axis spine
                drawLine(
                    color = colors.inkSecondary.copy(alpha = 0.5f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.height),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { opt ->
                    val isTopPopular = bestOption?.id == opt.id && (opt.voteCount > 0 || opt.declinedCount > 0)
                    val isInspected = inspectedOptionId == opt.id
                    val yesRatio by animateFloatAsState(
                        targetValue = (opt.voteCount.toFloat() / scaleMax.toFloat()).coerceIn(0f, 1f),
                        animationSpec = spring(stiffness = 340f, dampingRatio = 0.78f),
                        label = "chart_yes_${opt.id}"
                    )
                    val noRatio by animateFloatAsState(
                        targetValue = (opt.declinedCount.toFloat() / scaleMax.toFloat()).coerceIn(0f, 1f),
                        animationSpec = spring(stiffness = 340f, dampingRatio = 0.78f),
                        label = "chart_no_${opt.id}"
                    )
                    val net = opt.voteCount - opt.declinedCount
                    val netStr = if (net > 0) "+$net" else "$net"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isInspected) colors.surfaceInset.copy(alpha = 0.85f)
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .clickable {
                                view.performTactileTick()
                                inspectedOptionId = if (isInspected) null else opt.id
                            }
                            .padding(vertical = 4.dp)
                            .testTag("chart_bar_row_${opt.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(104.dp).padding(end = 8.dp)) {
                            Text(
                                text = opt.label,
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = if (isTopPopular) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp,
                                color = if (isTopPopular) colors.emerald else colors.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isTopPopular) {
                                Text(
                                    text = stringResource(R.string.chart_top_slot),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp,
                                    letterSpacing = 0.5.sp,
                                    color = colors.emerald
                                )
                            }
                        }

                        // Diverging Bars: Left = NO (Vermilion), Right = YES (Emerald)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                if (noRatio > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(noRatio)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                                            .background(colors.vermilion.copy(alpha = if (isInspected) 1f else 0.84f))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (yesRatio > 0.01f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(yesRatio)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                            .background(colors.emerald.copy(alpha = if (isInspected) 1f else 0.88f))
                                    )
                                }
                            }
                        }

                        Text(
                            text = netStr,
                            style = MonoTabularTextStyle.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    net > 0 -> colors.emerald
                                    net < 0 -> colors.vermilion
                                    else -> colors.inkSecondary
                                }
                            ),
                            modifier = Modifier
                                .width(46.dp)
                                .padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        val inspected = options.firstOrNull { it.id == inspectedOptionId }
        AnimatedVisibility(
            visible = inspected != null,
            enter = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(140)) + fadeOut(tween(120))
        ) {
            inspected?.let { item ->
                val net = item.voteCount - item.declinedCount
                val netText = if (net > 0) "+$net" else "$net"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceInset)
                        .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("chart_tooltip_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.label,
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.chart_tooltip_summary,
                            item.voteCount,
                            item.declinedCount,
                            netText
                        ),
                        style = MonoTabularTextStyle.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkSecondary
                        )
                    )
                }
            }
        }

        if (inspected == null) {
            Text(
                text = stringResource(R.string.chart_tap_hint),
                fontFamily = JetBrainsMonoFamily,
                fontSize = 9.sp,
                color = colors.inkSecondary.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun LegendChip(
    label: String,
    swatch: androidx.compose.ui.graphics.Color
) {
    val colors = MeldTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(swatch)
        )
        Text(
            text = label,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 9.sp,
            color = colors.inkSecondary
        )
    }
}
