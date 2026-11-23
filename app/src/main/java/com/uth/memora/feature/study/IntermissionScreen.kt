package com.uth.memora.feature.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
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
import com.uth.memora.feature.common.MemoraLinearProgressBar
import com.uth.memora.feature.common.MemoraSectionKicker
import com.uth.memora.feature.theme.BackgroundDark
import com.uth.memora.feature.theme.BorderDark
import com.uth.memora.feature.theme.KickerBlue
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.SurfaceDark
import com.uth.memora.feature.theme.SurfaceVariantDark
import com.uth.memora.feature.theme.TextPrimaryDark
import com.uth.memora.feature.theme.TextSecondaryDark

/**
 * Màn hình Nghỉ giữa chặng chống quá tải (Anti-burnout Break - Figma Node 2241:113 & Mục 9.2):
 * - Bắt buộc dùng nền tối #121212 chống mỏi mắt ngay cả khi thiết bị ở chế độ Sáng
 * - Biểu tượng tách cà phê trong vòng tròn #172137 viền #2B416B
 * - Tiêu đề khích lệ, thông điệp khoa học nghỉ ngắn 2–5 phút và thẻ tiến độ chặng X/Y
 */
@Composable
fun IntermissionScreen(
    completedChunk: Int,
    totalChunks: Int,
    completedCardsCount: Int,
    modifier: Modifier = Modifier
) {
    val safeTotalChunks = totalChunks.coerceAtLeast(1)
    val safeCompletedChunk = completedChunk.coerceIn(1, safeTotalChunks)
    val chunkProgress = (safeCompletedChunk.toFloat() / safeTotalChunks.toFloat()).coerceIn(0f, 1f)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = BackgroundDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Biểu tượng tách cà phê (Figma 2241:116)
            Surface(
                modifier = Modifier.size(90.dp),
                shape = CircleShape,
                color = Color(0xFF172137),
                border = BorderStroke(1.dp, Color(0xFF2B416B))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Coffee,
                        contentDescription = "Nghỉ giải lao",
                        tint = KickerBlue,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tiêu đề & lời khích lệ chống quá tải (Figma 2241:117..119)
            Text(
                text = "Nghỉ giải lao chút nhé!",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = TextPrimaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tuyệt vời! Bạn vừa xong $completedCardsCount thẻ!",
                style = MaterialTheme.typography.titleSmall,
                color = KickerBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Khoa học chứng minh: Não bộ củng cố trí nhớ tốt nhất khi nghỉ ngắn 2–5 phút giữa các phiên học. Hãy uống một ngụm nước nhé!",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = TextSecondaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Thẻ tiến độ phiên hôm nay (Figma 2241:120..124)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = SurfaceDark,
                border = BorderStroke(1.dp, BorderDark)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MemoraSectionKicker(
                        text = "TIẾN ĐỘ PHIÊN HÔM NAY",
                        color = KickerBlue
                    )

                    MemoraLinearProgressBar(
                        progress = chunkProgress,
                        color = PrimaryBlue,
                        trackColor = SurfaceVariantDark
                    )

                    Text(
                        text = "Đã xong chặng $safeCompletedChunk/$safeTotalChunks • Đã hoàn thành $completedCardsCount thẻ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimaryDark
                    )
                }
            }
        }
    }
}

@Preview(name = "IntermissionScreen - Always Dark (#121212)", showBackground = true)
@Composable
private fun IntermissionScreenPreview() {
    MemoraTheme(darkTheme = false) {
        IntermissionScreen(
            completedChunk = 1,
            totalChunks = 2,
            completedCardsCount = 15
        )
    }
}
