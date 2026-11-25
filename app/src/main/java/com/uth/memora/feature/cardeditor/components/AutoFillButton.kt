package com.uth.memora.feature.cardeditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import com.uth.memora.core.model.UserPreferences
import com.uth.memora.feature.theme.MemoraTheme

private val AiPurpleContainer = Color(0xFFF3E8FF)
private val AiPurpleBorder = Color(0xFFC084FC)
private val AiPurpleText = Color(0xFF6B21A8)
private val AiPurpleBadgeText = Color(0xFF7E22CE)

/**
 * Nút "Gợi ý điền bằng AI" kèm huy hiệu hạn mức lượt dùng trong ngày theo Figma 2227:58 / 2227:50 (Mục 6.3 & 11):
 * - Nền tím nhạt `#F3E8FF`, viền `#C084FC`, chữ `#6B21A8`, bo góc `12.dp`
 * - Huy hiệu trắng hiển thị "Còn X/30 lượt"
 * - Tự động khóa khi đang gọi AI ([isLoading]) hoặc khi hết hạn mức trong ngày (`remainingQuota <= 0`)
 */
@Composable
fun AutoFillButton(
    remainingQuota: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxDailyQuota: Int = UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val isQuotaAvailable = remainingQuota > 0
    val isInteractive = enabled && isQuotaAvailable && !isLoading

    val containerColor = if (isQuotaAvailable) {
        AiPurpleContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isQuotaAvailable) {
        AiPurpleBorder
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (isQuotaAvailable) {
        AiPurpleText
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val badgeBgColor = if (isQuotaAvailable) {
        Color.White
    } else {
        MaterialTheme.colorScheme.surface
    }
    val badgeTextColor = if (isQuotaAvailable) {
        AiPurpleBadgeText
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isInteractive, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Gợi ý điền bằng AI",
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = when {
                    isLoading -> "Đang gợi ý bằng AI..."
                    !isQuotaAvailable -> "Đã hết lượt AI hôm nay"
                    else -> "Gợi ý điền bằng AI"
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeBgColor
            ) {
                Text(
                    text = "Còn ${remainingQuota.coerceAtLeast(0)}/$maxDailyQuota lượt",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = badgeTextColor,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Preview(name = "AutoFillButton - Default", widthDp = 360, showBackground = true)
@Composable
private fun AutoFillButtonDefaultPreview() {
    MemoraTheme(darkTheme = true) {
        AutoFillButton(
            remainingQuota = 30,
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(name = "AutoFillButton - Out of Quota", widthDp = 360, showBackground = true)
@Composable
private fun AutoFillButtonEmptyQuotaPreview() {
    MemoraTheme(darkTheme = true) {
        AutoFillButton(
            remainingQuota = 0,
            onClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
