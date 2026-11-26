package com.uth.memora.feature.analytics.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.feature.analytics.StageChartItem
import com.uth.memora.feature.theme.KickerBlue
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.StatusError
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning

/**
 * Biểu đồ thanh ngang phân bố 6 cấp độ ghi nhớ [RetentionStage] (Figma Node 10:707 · Mục 6.9 & 7.5).
 *
 * Bảo đảm vẽ cân đối, không phát sinh lỗi chia 0 (`NaN`) kể cả khi tổng thẻ đang bằng 0.
 */
@Composable
fun StageChart(
    stageItems: List<StageChartItem>,
    totalActiveCardsCount: Int,
    masteredCardsCount: Int,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "PHÂN BỐ TRÍ NHỚ DÀI HẠN",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = KickerBlue
                    )
                    Text(
                        text = "6 cấp độ ghi nhớ (Retention Stages)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$totalActiveCardsCount thẻ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                stageItems.forEach { item ->
                    StageBarRow(item = item)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mục tiêu dài hạn: đưa thẻ lên Cấp 5 (khoảng cách ôn ≥ 21 ngày)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$masteredCardsCount thành thạo",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = StatusSuccess,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StageBarRow(
    item: StageChartItem,
    modifier: Modifier = Modifier
) {
    val barColor = stageColorFor(item.stage)
    val safeFraction = if (item.barFraction.isNaN() || item.barFraction.isInfinite()) {
        0f
    } else {
        item.barFraction.coerceIn(0f, 1f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(barColor)
                )

                Text(
                    text = item.stage.titleVi,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "(${item.stage.intervalDescriptionVi})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${item.count} thẻ • ${item.percentOfTotal}%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (safeFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = safeFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(barColor)
                )
            }
        }
    }
}

@Composable
private fun stageColorFor(stage: RetentionStage): Color {
    return when (stage) {
        RetentionStage.LEVEL_0_UNLEARNED -> MaterialTheme.colorScheme.onSurfaceVariant
        RetentionStage.LEVEL_1_STARTING -> StatusError
        RetentionStage.LEVEL_2_SHORT_TERM -> StatusWarning
        RetentionStage.LEVEL_3_MID_TERM -> KickerBlue
        RetentionStage.LEVEL_4_NEAR_MASTERED -> PrimaryBlue
        RetentionStage.LEVEL_5_MASTERED -> StatusSuccess
    }
}

@Preview(showBackground = true, name = "StageChart - Populated Dark")
@Composable
private fun StageChartPopulatedPreview() {
    MemoraTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            StageChart(
                stageItems = listOf(
                    StageChartItem(RetentionStage.LEVEL_0_UNLEARNED, count = 15, percentOfTotal = 15, barFraction = 0.15f),
                    StageChartItem(RetentionStage.LEVEL_1_STARTING, count = 10, percentOfTotal = 10, barFraction = 0.10f),
                    StageChartItem(RetentionStage.LEVEL_2_SHORT_TERM, count = 20, percentOfTotal = 20, barFraction = 0.20f),
                    StageChartItem(RetentionStage.LEVEL_3_MID_TERM, count = 15, percentOfTotal = 15, barFraction = 0.15f),
                    StageChartItem(RetentionStage.LEVEL_4_NEAR_MASTERED, count = 15, percentOfTotal = 15, barFraction = 0.15f),
                    StageChartItem(RetentionStage.LEVEL_5_MASTERED, count = 25, percentOfTotal = 25, barFraction = 0.25f)
                ),
                totalActiveCardsCount = 100,
                masteredCardsCount = 25
            )
        }
    }
}

@Preview(showBackground = true, name = "StageChart - Empty Zero Cards Light")
@Composable
private fun StageChartEmptyPreview() {
    MemoraTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            StageChart(
                stageItems = RetentionStage.entries.map {
                    StageChartItem(stage = it, count = 0, percentOfTotal = 0, barFraction = 0f)
                },
                totalActiveCardsCount = 0,
                masteredCardsCount = 0
            )
        }
    }
}
