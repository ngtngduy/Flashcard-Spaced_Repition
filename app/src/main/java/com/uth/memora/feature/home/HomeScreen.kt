package com.uth.memora.feature.home

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uth.memora.core.model.StudyMode
import com.uth.memora.feature.common.MemoraLinearProgressBar
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusWarning
import com.uth.memora.feature.theme.SurfaceLight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Màn hình Trang chính (Home Dashboard - Figma Node 1:2 & Mục 6.5, 6.6).
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onNavigateToStudy: (String, StudyMode) -> Unit,
    onNavigateToDeckDetail: (String) -> Unit,
    onNavigateToAddCard: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshSync: () -> Unit = {}
) {
    val greetingText = remember { buildVietnameseGreeting() }
    val dateText = remember { formatVietnameseDate(LocalDate.now()) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thanh tiêu đề Trang chính (Figma 4:32)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Trang chính",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero Section: Lời chào theo buổi + Ngày hiện tại + Nút "+ Thêm flashcard" (Figma 4:31)
                HomeGreetingHeader(
                    greetingText = greetingText,
                    dateText = dateText,
                    onAddCardClick = onNavigateToAddCard
                )

                // Hàng 3 thẻ thống kê nhanh: Thẻ mới hôm nay, Bộ từ vựng, Chuỗi ngày học + Khiên (Figma 4:8, 4:36, 4:47, 2235:61, 2235:66)
                HomeStatsRow(
                    newCardsLearnedToday = uiState.newCardsLearnedToday,
                    dailyNewCardLimit = uiState.dailyNewCardLimit,
                    newCardsProgress = uiState.newCardsProgressFraction,
                    totalDecksCount = uiState.totalDecksCount,
                    currentStreakDays = uiState.currentStreakDays,
                    streakFreezeAvailable = uiState.streakFreezeAvailable,
                    isStreakProtectedToday = uiState.isStreakProtectedToday
                )

                // Khung châm ngôn học tập (Figma 4:74)
                HomeQuoteBanner(
                    quote = "“Điều tuyệt vời của việc học là không ai có thể lấy nó khỏi bạn.”"
                )
            }
        }
    }
}

/**
 * Hàng 3 ô thống kê nhanh trên Trang chính theo Figma Node 1:2 (4:8, 4:36, 4:47):
 * - Thẻ mới hôm nay (x/20 + thanh tiến độ nhỏ 4.dp)
 * - Bộ từ vựng (tổng số bộ từ đang hoạt động)
 * - Chuỗi ngày học (Streak + Khiên bảo vệ chuỗi 2235:61 & 2235:66)
 */
@Composable
fun HomeStatsRow(
    newCardsLearnedToday: Int,
    dailyNewCardLimit: Int,
    newCardsProgress: Float,
    totalDecksCount: Int,
    currentStreakDays: Int,
    modifier: Modifier = Modifier,
    streakFreezeAvailable: Boolean = true,
    isStreakProtectedToday: Boolean = false
) {
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val shieldActiveColor = if (isLight) Color(0xFF0369A1) else Color(0xFF0284C7)
    val isShieldActive = streakFreezeAvailable || isStreakProtectedToday
    val shieldTint = if (isShieldActive) {
        shieldActiveColor
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shieldIcon = if (isShieldActive) {
        Icons.Default.VerifiedUser
    } else {
        Icons.Default.Shield
    }
    val shieldStatusLabel = when {
        isStreakProtectedToday -> "Khiên: Đang bảo vệ"
        streakFreezeAvailable -> "Khiên: Sẵn sàng"
        else -> "Khiên: Đã dùng"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Ô 1: Thẻ mới hôm nay (Figma 4:8)
        HomeStatCard(
            title = "Thẻ mới hôm nay",
            icon = Icons.Default.EventAvailable,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1.25f)
        ) {
            Text(
                text = "$newCardsLearnedToday/$dailyNewCardLimit",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            MemoraLinearProgressBar(
                progress = newCardsProgress,
                height = 4.dp
            )
        }

        // Ô 2: Tổng bộ từ (Figma 4:36)
        HomeStatCard(
            title = "Bộ từ vựng",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            iconTint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(0.85f)
        ) {
            Text(
                text = "$totalDecksCount",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Ô 3: Chuỗi ngày học Streak + Icon Khiên bảo vệ (Figma 4:47, 2235:61, 2235:66)
        HomeStatCard(
            title = "Chuỗi ngày học",
            icon = Icons.Default.LocalFireDepartment,
            iconTint = StatusWarning,
            modifier = Modifier
                .weight(1.15f)
                .widthIn(min = 100.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currentStreakDays ngày",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = shieldIcon,
                    contentDescription = shieldStatusLabel,
                    tint = shieldTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = shieldStatusLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = shieldTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeStatCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 90.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = iconTint.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column {
                content()
            }
        }
    }
}

/**
 * Phần đầu Trang chính gồm lời chào theo buổi, ngày hiện tại và nút "+ Thêm flashcard" (Figma 1:12..1:16).
 */
@Composable
fun HomeGreetingHeader(
    greetingText: String,
    dateText: String,
    onAddCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = greetingText,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onAddCardClick,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Thêm flashcard mới",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Thêm flashcard",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

/**
 * Khung câu trích dẫn tạo động lực học tập trên Trang chính (Figma Node 4:74).
 */
@Composable
fun HomeQuoteBanner(
    quote: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = quote,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

internal fun buildVietnameseGreeting(hour: Int = LocalTime.now().hour): String {
    return when (hour) {
        in 5..11 -> "Chào buổi sáng!"
        in 12..17 -> "Chào buổi chiều!"
        else -> "Chào buổi tối!"
    }
}

internal fun formatVietnameseDate(date: LocalDate): String {
    val dayOfWeekVi = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Thứ Hai"
        DayOfWeek.TUESDAY -> "Thứ Ba"
        DayOfWeek.WEDNESDAY -> "Thứ Tư"
        DayOfWeek.THURSDAY -> "Thứ Năm"
        DayOfWeek.FRIDAY -> "Thứ Sáu"
        DayOfWeek.SATURDAY -> "Thứ Bảy"
        DayOfWeek.SUNDAY -> "Chủ Nhật"
    }
    return "$dayOfWeekVi, ngày ${date.dayOfMonth} tháng ${date.monthValue}"
}

@Preview(name = "HomeScreen Stats - Dark", showBackground = true)
@Composable
private fun HomeScreenHeaderDarkPreview() {
    MemoraTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                newCardsLearnedToday = 7,
                dailyNewCardLimit = 20,
                totalDecksCount = 5,
                currentStreakDays = 12
            ),
            onNavigateToStudy = { _, _ -> },
            onNavigateToDeckDetail = {},
            onNavigateToAddCard = {}
        )
    }
}

@Preview(name = "HomeScreen Stats - Light", showBackground = true)
@Composable
private fun HomeScreenHeaderLightPreview() {
    MemoraTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                newCardsLearnedToday = 7,
                dailyNewCardLimit = 20,
                totalDecksCount = 5,
                currentStreakDays = 12
            ),
            onNavigateToStudy = { _, _ -> },
            onNavigateToDeckDetail = {},
            onNavigateToAddCard = {}
        )
    }
}
