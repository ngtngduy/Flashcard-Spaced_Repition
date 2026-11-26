package com.uth.memora.feature.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.model.StudyStatistics
import com.uth.memora.feature.analytics.components.StreakFreezeCard
import com.uth.memora.feature.theme.KickerBlue
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning

/**
 * Màn hình Phân tích tiến độ học tập (Figma Node 10:707 & 2235:67 · Mục 6.9 & 10.2).
 */
@Composable
fun AnalyticsScreen(
    uiState: AnalyticsUiState,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
            return@Surface
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "analytics_header") {
                AnalyticsHeader(onRefresh = onRefresh)
            }

            uiState.errorMessage?.let { errorMsg ->
                item(key = "analytics_error") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            text = errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            item(key = "analytics_summary_grid") {
                AnalyticsSummarySection(uiState = uiState)
            }

            item(key = "analytics_streak_freeze") {
                StreakFreezeCard(
                    status = uiState.streakFreezeStatus,
                    currentStreakDays = uiState.currentStreakDays,
                    nextFreezeAvailableInDays = uiState.nextFreezeAvailableInDays
                )
            }
        }
    }
}

@Composable
private fun AnalyticsHeader(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "THỐNG KÊ SM-2",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = KickerBlue
            )
            Text(
                text = "Phân tích tiến độ",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Theo dõi chuỗi ngày học, tỷ lệ nhớ 7 ngày và 6 cấp độ ghi nhớ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onRefresh,
            modifier = Modifier.semantics {
                contentDescription = "Làm mới số liệu phân tích"
            }
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Làm mới số liệu phân tích",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Khu vực 4 thẻ tổng quan chính theo Figma Node 10:707 (`2212:5`):
 * 1. Chuỗi ngày học (`currentStreakDays`)
 * 2. Tỷ lệ ghi nhớ 7 ngày (`retentionRatePercent%` kèm `reviewsLast7DaysCount` lượt đánh giá)
 * 3. Số thẻ thành thạo (`masteredCardsCount / totalActiveCardsCount`)
 * 4. Tổng thời gian học (`formattedStudyTime`)
 */
@Composable
fun AnalyticsSummarySection(
    uiState: AnalyticsUiState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thẻ nổi bật: Tỷ lệ ghi nhớ 7 ngày & Số lượt đánh giá 7 ngày qua (Figma 2212:5)
        RetentionHighlightCard(
            retentionRateText = uiState.retentionRatePercentText,
            retentionRateValue = uiState.retentionRateValue,
            reviewsLast7DaysCount = uiState.reviewsLast7DaysCount,
            reviewsTodayCount = uiState.reviewsToday
        )

        // Hàng 1: Chuỗi ngày học & Thẻ thành thạo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryMetricCard(
                title = "Chuỗi ngày học",
                value = "${uiState.currentStreakDays} ngày",
                subtitle = if (uiState.isStreakProtectedToday) {
                    "Đã bảo vệ bởi Khiên"
                } else if (uiState.currentStreakDays > 0) {
                    "Đang duy trì liên tiếp"
                } else {
                    "Học 1 thẻ để bắt đầu"
                },
                icon = Icons.Default.LocalFireDepartment,
                accentColor = StatusWarning,
                modifier = Modifier.weight(1f)
            )

            SummaryMetricCard(
                title = "Thẻ thành thạo",
                value = "${uiState.masteredCardsCount} / ${uiState.totalActiveCardsCount}",
                subtitle = "Cấp 5 (≥ 21 ngày)",
                icon = Icons.Default.CheckCircle,
                accentColor = StatusSuccess,
                modifier = Modifier.weight(1f)
            )
        }

        // Hàng 2: Tổng thời gian học & Nhiệm vụ hôm nay
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryMetricCard(
                title = "Tổng thời gian học",
                value = uiState.formattedStudyTime,
                subtitle = "${uiState.reviewsToday} lượt ôn hôm nay",
                icon = Icons.Default.Schedule,
                accentColor = PrimaryBlue,
                modifier = Modifier.weight(1f)
            )

            SummaryMetricCard(
                title = "Thẻ mới hôm nay",
                value = "${uiState.newCardsLearnedToday} / ${uiState.dailyNewCardLimit}",
                subtitle = "Còn ${uiState.dueCardsCount} thẻ đến hạn",
                icon = Icons.Default.Psychology,
                accentColor = KickerBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RetentionHighlightCard(
    retentionRateText: String,
    retentionRateValue: Float,
    reviewsLast7DaysCount: Int,
    reviewsTodayCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TỶ LỆ GHI NHỚ 7 NGÀY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = KickerBlue
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = retentionRateText,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "nhớ đúng hạn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$reviewsLast7DaysCount lượt / 7 ngày",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (retentionRateValue / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = PrimaryBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                text = if (reviewsLast7DaysCount > 0) {
                    "Tính trên $reviewsLast7DaysCount lượt đánh giá hợp lệ trong 7 ngày học gần nhất (hôm nay: $reviewsTodayCount lượt)."
                } else {
                    "Chưa có lượt đánh giá trong 7 ngày qua. Hãy bắt đầu ôn tập để đo hiệu quả ghi nhớ SM-2."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(showBackground = true, name = "Analytics Summary - Dark")
@Composable
private fun AnalyticsScreenDarkPreview() {
    MemoraTheme(darkTheme = true) {
        AnalyticsScreen(
            uiState = AnalyticsViewModel.mapStatisticsToUiState(
                StudyStatistics(
                    newCardsLearnedToday = 12,
                    dailyNewCardLimit = 20,
                    reviewsToday = 28,
                    dueCardsCount = 7,
                    currentStreakDays = 14,
                    totalStudyTimeSeconds = 5_100L,
                    masteredCardsCount = 42,
                    totalActiveCardsCount = 120,
                    stageDistribution = mapOf(
                        RetentionStage.LEVEL_0_UNLEARNED to 18,
                        RetentionStage.LEVEL_1_STARTING to 10,
                        RetentionStage.LEVEL_2_SHORT_TERM to 20,
                        RetentionStage.LEVEL_3_MID_TERM to 16,
                        RetentionStage.LEVEL_4_NEAR_MASTERED to 14,
                        RetentionStage.LEVEL_5_MASTERED to 42
                    ),
                    retentionRatePercent = 91.5f,
                    reviewsLast7DaysCount = 164,
                    streakFreezeAvailable = true
                )
            )
        )
    }
}

@Preview(showBackground = true, name = "Analytics Summary - Light")
@Composable
private fun AnalyticsScreenLightPreview() {
    MemoraTheme(darkTheme = false) {
        AnalyticsScreen(
            uiState = AnalyticsViewModel.mapStatisticsToUiState(
                StudyStatistics(
                    newCardsLearnedToday = 5,
                    dailyNewCardLimit = 20,
                    reviewsToday = 10,
                    dueCardsCount = 3,
                    currentStreakDays = 6,
                    totalStudyTimeSeconds = 1_800L,
                    masteredCardsCount = 15,
                    totalActiveCardsCount = 60,
                    retentionRatePercent = 88f,
                    reviewsLast7DaysCount = 52,
                    streakFreezeAvailable = false,
                    isStreakProtectedToday = true,
                    nextFreezeAvailableInDays = 30
                )
            )
        )
    }
}
