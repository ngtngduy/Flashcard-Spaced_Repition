package com.uth.memora.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.uth.memora.feature.theme.PrimaryBlueLight
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning
import com.uth.memora.feature.theme.SurfaceLight

/**
 * Dữ liệu hiển thị cho mỗi trang giới thiệu Onboarding (Figma 2053:63..102 & Mục 6.1).
 */
data class OnboardingPageData(
    val stepLabel: String,
    val kicker: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val badgeLabel: String,
    val icon: ImageVector,
    val accentColorDark: Color,
    val accentColorLight: Color
)

/**
 * Danh sách 4 trang giới thiệu ứng dụng Memora khớp Figma (2053:63, 2053:76, 2053:89, 2053:102) và nghiệp vụ.
 */
val onboardingPages: List<OnboardingPageData> = listOf(
    OnboardingPageData(
        stepLabel = "01 / 04",
        kicker = "FLASHCARD & SM-2",
        title = "Học thông minh với SM-2",
        subtitle = "Học với Flashcard · Ôn tập ngắt quãng",
        description = "Tự nhớ câu trả lời trước khi lật thẻ và ôn lại theo lịch Spaced Repetition với khoảng cách tăng dần khi bạn nhớ tốt hơn.",
        badgeLabel = "Thuật toán SM-2 chuẩn hóa",
        icon = Icons.Default.Psychology,
        accentColorDark = PrimaryBlue,
        accentColorLight = PrimaryBlueLight
    ),
    OnboardingPageData(
        stepLabel = "02 / 04",
        kicker = "TRỢ LÝ AI THÔNG MINH",
        title = "Luyện đặt câu & Chấm điểm AI",
        subtitle = "Ôn đúng thẻ, đúng lúc",
        description = "Thực hành đặt câu tiếng Anh với từ đang học, nhận điểm số CEFR, nhận xét ngữ pháp và câu sửa gợi ý tức thì từ AI.",
        badgeLabel = "Chấm câu AI · 20 câu/ngày",
        icon = Icons.Default.AutoAwesome,
        accentColorDark = Color(0xFF38BDF8),
        accentColorLight = Color(0xFF0369A1)
    ),
    OnboardingPageData(
        stepLabel = "03 / 04",
        kicker = "TIẾN ĐỘ & CHUỖI NGÀY",
        title = "Ghi nhớ dài hạn 6 cấp độ & Streak",
        subtitle = "Ghi nhớ lâu dài",
        description = "Theo dõi hành trình từ Cấp 0 (Chưa học) đến Cấp 5 (Thành thạo ≥ 21 ngày) cùng chuỗi ngày học liên tiếp và Khiên bảo vệ.",
        badgeLabel = "Cấp 0 → Cấp 5 · Khiên bảo vệ Streak",
        icon = Icons.Default.LocalFireDepartment,
        accentColorDark = StatusWarning,
        accentColorLight = Color(0xFFB45309)
    ),
    OnboardingPageData(
        stepLabel = "04 / 04",
        kicker = "OFFLINE-FIRST & CLOUD SYNC",
        title = "Học Offline mọi lúc mọi nơi",
        subtitle = "Đồng bộ đám mây tự động",
        description = "Mọi bộ từ và lịch ôn được lưu cục bộ trên máy giúp bạn học mượt mà không cần mạng, tự động đồng bộ khi trực tuyến.",
        badgeLabel = "Sẵn sàng 100% khi không có mạng",
        icon = Icons.Default.CloudOff,
        accentColorDark = StatusSuccess,
        accentColorLight = Color(0xFF15803D)
    )
)

/**
 * Composable hiển thị nội dung 1 trang giới thiệu Onboarding theo bố cục Figma (2053:63..102):
 * - Lề ngang 24.dp đồng bộ, hỗ trợ cuộn dọc an toàn trên màn hình nhỏ (360dp)
 * - Khung minh họa có card badge, vòng sáng radial gradient và độ tương phản chuẩn cả Dark/Light mode
 */
@Composable
fun OnboardingPage(
    page: OnboardingPageData,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val accentColor = if (isLight) page.accentColorLight else page.accentColorDark

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Khung minh họa trung tâm (Card badge + Gradient tint + Vòng icon đôi + Status pill)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 264.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f))
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = if (isLight) 0.14f else 0.22f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemoraSectionKicker(
                            text = page.kicker,
                            color = accentColor
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = page.stepLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Vòng tròn hào quang ngoài + Vòng icon vector lớn ở tâm
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.08f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(88.dp),
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = page.icon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    MemoraStatusBadge(
                        text = page.badgeLabel,
                        accentColor = accentColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

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
            color = accentColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Mô tả chi tiết khớp Figma 2053:67 / 80 / 93 / 106 (#ACACAC ở Dark)
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(name = "OnboardingPage - Dark (360dp)", showBackground = true, widthDp = 360)
@Composable
private fun OnboardingPageDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            OnboardingPage(
                page = onboardingPages.first(),
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

@Preview(name = "OnboardingPage - Light (360dp)", showBackground = true, widthDp = 360)
@Composable
private fun OnboardingPageLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            OnboardingPage(
                page = onboardingPages[2],
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}
