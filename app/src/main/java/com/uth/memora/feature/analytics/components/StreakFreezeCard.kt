package com.uth.memora.feature.analytics.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.domain.logic.StreakCalculator
import com.uth.memora.feature.analytics.StreakFreezeStatusUi
import com.uth.memora.feature.theme.KickerBlue
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning

/**
 * Thẻ Khiên bảo vệ chuỗi ngày học (Streak Freeze - Figma Node 2235:67 · Mục 10.2 & 11).
 *
 * Hiển thị rõ 3 trạng thái:
 * 1. [StreakFreezeStatusUi.READY]: Sẵn sàng tự động kích hoạt nếu người học lỡ đúng 1 ngày học.
 * 2. [StreakFreezeStatusUi.PROTECTED_TODAY]: Đã kích hoạt bảo vệ chuỗi hôm nay (không bị mất chuỗi).
 * 3. [StreakFreezeStatusUi.RECOVERING]: Đang hồi phục trong chu kỳ 30 ngày ("Còn X ngày nữa hồi khiên").
 */
@Composable
fun StreakFreezeCard(
    status: StreakFreezeStatusUi,
    currentStreakDays: Int,
    nextFreezeAvailableInDays: Int,
    modifier: Modifier = Modifier
) {
    val cycleDays = StreakCalculator.FREEZE_RECOVERY_DAYS.toInt()
    val safeRemainingDays = nextFreezeAvailableInDays.coerceIn(0, cycleDays)

    val (badgeText, badgeColor, icon, titleText, descriptionText) = when (status) {
        StreakFreezeStatusUi.READY -> FreezeCardStyle(
            badgeText = "Sẵn sàng bảo vệ",
            badgeColor = StatusSuccess,
            icon = Icons.Default.Shield,
            titleText = "Khiên bảo vệ chuỗi đang hoạt động",
            descriptionText = "Tự động giữ nguyên chuỗi $currentStreakDays ngày nếu bạn vô tình bỏ lỡ đúng 1 ngày học (tối đa 1 khiên / 30 ngày)."
        )

        StreakFreezeStatusUi.PROTECTED_TODAY -> FreezeCardStyle(
            badgeText = "Đã kích hoạt bảo vệ hôm nay",
            badgeColor = PrimaryBlue,
            icon = Icons.Default.AcUnit,
            titleText = "Chuỗi $currentStreakDays ngày đã được Khiên giữ an toàn",
            descriptionText = "Khiên đã tự động kích hoạt cho ngày học bị gián đoạn gần nhất. Khiên mới sẽ tự hồi lại sau chu kỳ 30 ngày."
        )

        StreakFreezeStatusUi.RECOVERING -> FreezeCardStyle(
            badgeText = "Còn $safeRemainingDays ngày nữa hồi khiên",
            badgeColor = StatusWarning,
            icon = Icons.Default.HourglassBottom,
            titleText = "Khiên bảo vệ đang trong chu kỳ hồi phục",
            descriptionText = "Bạn đã dùng Khiên trong 30 ngày qua. Hãy duy trì ít nhất 1 thẻ/ngày trong $safeRemainingDays ngày tới để hồi lại Khiên mới."
        )
    }

    val recoveryProgress = when (status) {
        StreakFreezeStatusUi.READY -> 1f
        StreakFreezeStatusUi.PROTECTED_TODAY -> 0.05f
        StreakFreezeStatusUi.RECOVERING -> {
            val elapsed = (cycleDays - safeRemainingDays).coerceIn(0, cycleDays)
            (elapsed.toFloat() / cycleDays.toFloat()).coerceIn(0.05f, 1f)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (status == StreakFreezeStatusUi.PROTECTED_TODAY) {
                PrimaryBlue.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Khiên bảo vệ chuỗi Streak Freeze",
                            tint = badgeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "KHIÊN BẢO VỆ CHUỖI · 30 NGÀY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = KickerBlue
                        )
                        Text(
                            text = "Streak Freeze",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = descriptionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chu kỳ hồi khiên 30 ngày",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (status) {
                            StreakFreezeStatusUi.READY -> "1 / 1 khiên khả dụng"
                            StreakFreezeStatusUi.PROTECTED_TODAY -> "Vừa kích hoạt • Hồi sau $cycleDays ngày"
                            StreakFreezeStatusUi.RECOVERING -> "Còn $safeRemainingDays / $cycleDays ngày"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                LinearProgressIndicator(
                    progress = { recoveryProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = badgeColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

private data class FreezeCardStyle(
    val badgeText: String,
    val badgeColor: androidx.compose.ui.graphics.Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val titleText: String,
    val descriptionText: String
)

@Preview(showBackground = true, name = "Streak Freeze - 3 States Dark")
@Composable
private fun StreakFreezeCardDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StreakFreezeCard(
                status = StreakFreezeStatusUi.READY,
                currentStreakDays = 14,
                nextFreezeAvailableInDays = 0
            )
            StreakFreezeCard(
                status = StreakFreezeStatusUi.PROTECTED_TODAY,
                currentStreakDays = 14,
                nextFreezeAvailableInDays = 30
            )
            StreakFreezeCard(
                status = StreakFreezeStatusUi.RECOVERING,
                currentStreakDays = 15,
                nextFreezeAvailableInDays = 18
            )
        }
    }
}
