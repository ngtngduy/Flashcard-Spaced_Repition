package com.uth.memora.feature.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uth.memora.feature.common.MemoraPrimaryButton
import com.uth.memora.feature.common.MemoraSecondaryButton
import com.uth.memora.feature.common.MemoraSectionCard
import com.uth.memora.feature.common.MemoraSectionKicker
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.SurfaceLight

/**
 * Màn hình Tổng kết phiên học theo Figma Node 2199:19 (Mục 6.5 & 6.6):
 * - Huy hiệu hoàn thành màu xanh lá (#182B20 / #2F6544 ở Dark)
 * - Khối thống kê tổng số thẻ đã học/ôn trong phiên và thời gian học
 * - Khối lịch ôn ngày mai (tomorrowDueCount)
 * - 2 nút hành động: "Về Trang chính" và "Luyện đặt câu với AI"
 */
@Composable
fun SessionSummaryScreen(
    reviewedCardsCount: Int,
    tomorrowDueCount: Int,
    onReturnHome: () -> Unit,
    onPracticeWithAi: () -> Unit,
    modifier: Modifier = Modifier,
    totalStudyDurationSeconds: Int = 0
) {
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val successAccent = if (isLight) Color(0xFF15803D) else StatusSuccess
    val durationText = formatStudyDurationVi(totalStudyDurationSeconds)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Huy hiệu chúc mừng hoàn thành phiên học (Figma 2199:27..35)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(88.dp),
                        shape = CircleShape,
                        color = if (isLight) successAccent.copy(alpha = 0.12f) else Color(0xFF182B20),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isLight) successAccent.copy(alpha = 0.4f) else Color(0xFF2F6544)
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Hoàn thành phiên học",
                                tint = successAccent,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Text(
                        text = "Hoàn thành phiên học!",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 24.sp,
                            lineHeight = 32.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Bạn vừa củng cố thêm một bước trên hành trình ghi nhớ từ vựng.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                // Khối Thống kê phiên (Figma 2199:28)
                MemoraSectionCard(modifier = Modifier.fillMaxWidth()) {
                    MemoraSectionKicker(text = "KẾT QUẢ PHIÊN NÀY")

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "$reviewedCardsCount thẻ",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 36.sp,
                            lineHeight = 44.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Số thẻ đã học / ôn trong phiên",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thời gian học",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Khối Lịch ôn ngày mai (Figma 2199:29)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isLight) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        Color(0xFF172137)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isLight) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        } else {
                            Color(0xFF2B416B)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MemoraSectionKicker(text = "LỊCH ÔN NGÀY MAI")

                        Text(
                            text = "Ngày mai bạn có $tomorrowDueCount thẻ dự kiến đến hạn",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Quay lại ngày mai để duy trì trí nhớ lâu dài.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2 nút hành động dưới cùng (Figma 2199:30 & Mục 6.7)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemoraPrimaryButton(
                    text = "Về Trang chính",
                    onClick = onReturnHome,
                    modifier = Modifier.fillMaxWidth()
                )

                MemoraSecondaryButton(
                    text = "Luyện đặt câu với AI",
                    onClick = onPracticeWithAi,
                    leadingIcon = Icons.Default.AutoAwesome,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun formatStudyDurationVi(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(1)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    return if (minutes > 0) {
        "${minutes} phút ${seconds} giây"
    } else {
        "${seconds} giây"
    }
}

@Preview(name = "SessionSummaryScreen - Dark", showBackground = true)
@Composable
private fun SessionSummaryScreenDarkPreview() {
    MemoraTheme(darkTheme = true) {
        SessionSummaryScreen(
            reviewedCardsCount = 24,
            tomorrowDueCount = 18,
            totalStudyDurationSeconds = 522,
            onReturnHome = {},
            onPracticeWithAi = {}
        )
    }
}

@Preview(name = "SessionSummaryScreen - Light", showBackground = true)
@Composable
private fun SessionSummaryScreenLightPreview() {
    MemoraTheme(darkTheme = false) {
        SessionSummaryScreen(
            reviewedCardsCount = 12,
            tomorrowDueCount = 9,
            totalStudyDurationSeconds = 195,
            onReturnHome = {},
            onPracticeWithAi = {}
        )
    }
}
