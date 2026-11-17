package com.uth.memora.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uth.memora.feature.common.MemoraSectionKicker
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning

/**
 * Dữ liệu hiển thị cho mỗi trang giới thiệu Onboarding (Figma 2053:63..102 & Mục 6.1).
 */
data class OnboardingPageData(
    val kicker: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val badgeLabel: String,
    val icon: ImageVector,
    val accentColor: Color
)

/**
 * Danh sách 4 trang giới thiệu ứng dụng Memora khớp Figma (2053:63, 2053:76, 2053:89, 2053:102) và nghiệp vụ.
 */
val onboardingPages: List<OnboardingPageData> = listOf(
    OnboardingPageData(
        kicker = "INTRO 01 · FLASHCARD & SM-2",
        title = "Học thông minh với SM-2",
        subtitle = "Học với Flashcard · Ôn tập ngắt quãng",
        description = "Tự nhớ câu trả lời trước khi lật thẻ và ôn lại theo lịch Spaced Repetition với khoảng cách tăng dần khi bạn nhớ tốt hơn.",
        badgeLabel = "Thuật toán SM-2 chuẩn hóa",
        icon = Icons.Default.Psychology,
        accentColor = PrimaryBlue
    ),
    OnboardingPageData(
        kicker = "INTRO 02 · TRỢ LÝ AI",
        title = "Luyện đặt câu & Chấm điểm AI",
        subtitle = "Ôn đúng thẻ, đúng lúc",
        description = "Thực hành đặt câu tiếng Anh với từ đang học, nhận điểm số CEFR, nhận xét ngữ pháp và câu sửa gợi ý tức thì từ AI.",
        badgeLabel = "Chấm câu AI · 20 câu/ngày",
        icon = Icons.Default.AutoAwesome,
        accentColor = Color(0xFF38BDF8)
    ),
    OnboardingPageData(
        kicker = "INTRO 03 · TIẾN ĐỘ & CHUỖI NGÀY",
        title = "Ghi nhớ dài hạn 6 cấp độ & Streak",
        subtitle = "Ghi nhớ lâu dài",
        description = "Theo dõi hành trình từ Cấp 0 (Chưa học) đến Cấp 5 (Thành thạo ≥ 21 ngày) cùng chuỗi ngày học liên tiếp và Khiên bảo vệ.",
        badgeLabel = "Cấp 0 → Cấp 5 · Khiên bảo vệ Streak",
        icon = Icons.Default.LocalFireDepartment,
        accentColor = StatusWarning
    ),
    OnboardingPageData(
        kicker = "INTRO 04 · OFFLINE-FIRST",
        title = "Học Offline mọi lúc mọi nơi",
        subtitle = "Đồng bộ đám mây tự động",
        description = "Mọi bộ từ và lịch ôn được lưu cục bộ trên máy giúp bạn học mượt mà không cần mạng, tự động đồng bộ khi trực tuyến.",
        badgeLabel = "Sẵn sàng 100% khi không có mạng",
        icon = Icons.Default.CloudOff,
        accentColor = StatusSuccess
    )
)

/**
 * Composable hiển thị nội dung 1 trang giới thiệu Onboarding theo bố cục Figma (2053:63..102).
 */
@Composable
fun OnboardingPage(
    page: OnboardingPageData,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Khung minh họa trung tâm (Card + Gradient tint + Icon vector lớn + Badge)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, page.accentColor.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                page.accentColor.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    MemoraSectionKicker(
                        text = page.kicker,
                        color = page.accentColor
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = page.accentColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.5.dp, page.accentColor.copy(alpha = 0.45f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = page.accentColor,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    MemoraStatusBadge(
                        text = page.badgeLabel,
                        accentColor = page.accentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Tiêu đề chính 24.sp Bold khớp Figma 2053:66 / 79 / 92 / 105
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = page.subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = page.accentColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Mô tả chi tiết khớp Figma 2053:67 / 80 / 93 / 106 (#ACACAC ở Dark)
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(name = "OnboardingPage - Dark", showBackground = true)
@Composable
private fun OnboardingPageDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            OnboardingPage(
                page = onboardingPages.first(),
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}

@Preview(name = "OnboardingPage - Light", showBackground = true)
@Composable
private fun OnboardingPageLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            OnboardingPage(
                page = onboardingPages[2],
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
    }
}
