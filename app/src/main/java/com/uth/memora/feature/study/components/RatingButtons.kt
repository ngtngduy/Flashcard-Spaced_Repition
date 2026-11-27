package com.uth.memora.feature.study.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyMode
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.PrimaryBlueLight
import com.uth.memora.feature.theme.StatusError
import com.uth.memora.feature.theme.StatusInfo
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning
import com.uth.memora.feature.theme.SurfaceLight

/**
 * Khu vực nút bấm đánh giá mức độ nhớ theo chế độ học (Mục 6.5, 6.6 & 7.2):
 * - StudyMode.NEW_CARDS (Figma 2104:23 / 2101:14): 2 nút ngang "Học lại (Xếp cuối hàng đợi)" và "Đã nhớ (Ôn sau 1 ngày)"
 * - StudyMode.DUE_REVIEW (Figma 2101:15 / 2101:16): 4 nút SM-2 (Chưa nhớ/Đỏ, Khó/Cam, Nhớ tốt/Xanh lá, Dễ/Xanh dương)
 *   kèm mốc thời gian xem trước từ previewLabels[quality]
 */
@Composable
fun RatingButtons(
    studyMode: StudyMode,
    previewLabels: Map<ReviewQuality, String>,
    onRate: (ReviewQuality) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (studyMode) {
            StudyMode.NEW_CARDS -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Bạn đã nhớ từ này chưa?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (enabled) {
                            "Chọn để xếp lịch học tiếp theo."
                        } else {
                            "Lật thẻ để xem nghĩa và ví dụ trước khi đánh giá."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val againColor = if (isLight) Color(0xFFDC2626) else StatusError
                    val goodColor = if (isLight) PrimaryBlueLight else PrimaryBlue

                    NewCardActionButton(
                        title = "Học lại",
                        subtitle = "(Xếp cuối hàng đợi)",
                        icon = Icons.Default.Refresh,
                        accentColor = againColor,
                        isPrimaryFilled = false,
                        enabled = enabled,
                        onClick = { onRate(ReviewQuality.AGAIN) },
                        modifier = Modifier.weight(1f)
                    )

                    NewCardActionButton(
                        title = "Đã nhớ",
                        subtitle = "(${previewLabels[ReviewQuality.GOOD] ?: "Ôn sau 1 ngày"})",
                        icon = Icons.Default.Check,
                        accentColor = goodColor,
                        isPrimaryFilled = true,
                        enabled = enabled,
                        onClick = { onRate(ReviewQuality.GOOD) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            StudyMode.DUE_REVIEW -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Bạn nhớ từ này thế nào?",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (enabled) {
                            "Chọn mức độ nhớ để lên lịch ôn tiếp."
                        } else {
                            "Lật thẻ để xem đáp án trước khi chọn mức độ nhớ."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ratings = listOf(
                        Triple(
                            ReviewQuality.AGAIN,
                            previewLabels[ReviewQuality.AGAIN] ?: "1 phút",
                            if (isLight) Color(0xFFDC2626) else StatusError
                        ),
                        Triple(
                            ReviewQuality.HARD,
                            previewLabels[ReviewQuality.HARD] ?: "10 phút",
                            if (isLight) Color(0xFFB45309) else StatusWarning
                        ),
                        Triple(
                            ReviewQuality.GOOD,
                            previewLabels[ReviewQuality.GOOD] ?: "1 ngày",
                            if (isLight) Color(0xFF15803D) else StatusSuccess
                        ),
                        Triple(
                            ReviewQuality.EASY,
                            previewLabels[ReviewQuality.EASY] ?: "4 ngày",
                            if (isLight) PrimaryBlueLight else StatusInfo
                        )
                    )

                    ratings.forEach { (quality, intervalText, accentColor) ->
                        Sm2RatingButton(
                            label = quality.labelVi,
                            intervalText = intervalText,
                            accentColor = accentColor,
                            enabled = enabled,
                            onClick = { onRate(quality) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewCardActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isPrimaryFilled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surface
        isPrimaryFilled -> accentColor
        else -> accentColor.copy(alpha = 0.12f)
    }
    val borderColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant
        isPrimaryFilled -> accentColor
        else -> accentColor.copy(alpha = 0.45f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        isPrimaryFilled -> Color.White
        else -> accentColor
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 74.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = if (isPrimaryFilled && enabled) 0.9f else 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Sm2RatingButton(
    label: String,
    intervalText: String,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (enabled) {
        accentColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (enabled) {
        accentColor.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (enabled) {
        accentColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 66.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = intervalText,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else textColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(name = "RatingButtons (NEW_CARDS) - Dark", showBackground = true)
@Composable
private fun RatingButtonsNewCardsDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RatingButtons(
                studyMode = StudyMode.NEW_CARDS,
                previewLabels = mapOf(
                    ReviewQuality.AGAIN to "Xếp cuối hàng đợi",
                    ReviewQuality.GOOD to "Ôn sau 1 ngày"
                ),
                onRate = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "RatingButtons (DUE_REVIEW) - Dark", showBackground = true)
@Composable
private fun RatingButtonsDueReviewDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RatingButtons(
                studyMode = StudyMode.DUE_REVIEW,
                previewLabels = mapOf(
                    ReviewQuality.AGAIN to "1 phút",
                    ReviewQuality.HARD to "10 phút",
                    ReviewQuality.GOOD to "1 ngày",
                    ReviewQuality.EASY to "4 ngày"
                ),
                onRate = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "RatingButtons (DUE_REVIEW) - Light", showBackground = true)
@Composable
private fun RatingButtonsDueReviewLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            RatingButtons(
                studyMode = StudyMode.DUE_REVIEW,
                previewLabels = mapOf(
                    ReviewQuality.AGAIN to "1 phút",
                    ReviewQuality.HARD to "10 phút",
                    ReviewQuality.GOOD to "3 ngày",
                    ReviewQuality.EASY to "7 ngày"
                ),
                onRate = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
