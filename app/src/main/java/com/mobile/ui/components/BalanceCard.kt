package com.mobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.data.Data
import com.mobile.data.MonthlyGrowth
import com.mobile.ui.theme.LocalEthiopianColors

@Composable
fun BalanceCard(
    totalBalance: Double,
    bankCount: Int,
    accountCount: Int,
    trendData: List<Float>,
    growth: MonthlyGrowth = MonthlyGrowth(0.0, true, "0.0% this month"),
    modifier: Modifier = Modifier
) {
    val autoHide by com.mobile.data.SettingsRepository.autoHideBalances.collectAsState()
    var hidden by remember(autoHide) { mutableStateOf(autoHide) }
    val colors = LocalEthiopianColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
            .shadow(
                elevation = 6.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                ambientColor = Color(0x33000000),
                spotColor = colors.emeraldPrimary.copy(alpha = 0.25f)
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            // Header Row: Label + Eye Privacy Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL BALANCE",
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.border, CircleShape)
                        .clickable { hidden = !hidden },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (hidden) "Show balance" else "Hide balance",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Balance Amount
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.animateContentSize()
            ) {
                if (hidden) {
                    Text(
                        text = "••••••••",
                        color = colors.textPrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                } else {
                    Text(
                        text = "ETB ",
                        color = colors.emeraldPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = Data.formatBalance(totalBalance),
                        color = colors.textPrimary,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Growth Indicator Badge
            val badgeBg = if (growth.isPositive) colors.emeraldPrimary.copy(alpha = 0.12f) else Color(0xFFFF5252).copy(alpha = 0.12f)
            val badgeColor = if (growth.isPositive) colors.emeraldPrimary else Color(0xFFFF5252)
            val badgeIcon = if (growth.isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = growth.formattedText,
                    color = badgeColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Minimalist Sparkline Chart with true dynamic normalization
            Sparkline(
                data = trendData,
                lineColor = if (growth.isPositive) colors.emeraldPrimary else Color(0xFFFF8A65),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Metadata Footer
            Text(
                text = "$bankCount Institutions · $accountCount Accounts",
                color = colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun Sparkline(
    data: List<Float>,
    lineColor: Color = Color(0xFF00C853),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas

        val width = size.width
        val height = size.height
        val paddingY = 4.dp.toPx()
        val availableHeight = (height - (paddingY * 2)).coerceAtLeast(1f)

        val minVal = data.minOrNull() ?: 0f
        val maxVal = data.maxOrNull() ?: 0f
        val range = maxVal - minVal

        val step = width / (data.size - 1)

        val path = Path().apply {
            data.forEachIndexed { index, value ->
                val x = index * step
                val normalizedY = if (range > 0.0001f) ((value - minVal) / range).coerceIn(0f, 1f) else 0.5f
                val y = height - paddingY - (normalizedY * availableHeight)
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        val fillPath = Path().apply {
            addPath(path)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.20f), Color.Transparent)
            )
        )
    }
}
