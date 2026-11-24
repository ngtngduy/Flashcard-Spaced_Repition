package com.uth.memora.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
 * Màn hình Trang chính (Home Dashboard - Figma Node 1:2 & Mục 6.5, 6.6, 10.1).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = onRefreshSync,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Hero Section: Lời chào theo buổi + Ngày hiện tại + Nút "+ Thêm flashcard" (Figma 4:31)
                    HomeGreetingHeader(
                        greetingText = greetingText,
                        dateText = dateText,
                        onAddCardClick = onNavigateToAddCard
                    )

                    // Thông báo đồng bộ / lỗi nếu có
                    uiState.syncMessage?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

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

                    // Thẻ CTA 1: Học từ mới theo hạn mức (Figma 2011:6)
                    HomeStudyCtaCard(
                        title = "Học từ mới (Còn ${uiState.remainingNewCardsToday} thẻ theo hạn mức)",
                        count = uiState.remainingNewCardsToday,
                        borderColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToStudy("all", StudyMode.NEW_CARDS) }
                    )

                    // Thẻ CTA 2: Ôn tập hôm nay các thẻ đến hạn (Figma 2015:4)
                    HomeStudyCtaCard(
                        title = "Ôn tập hôm nay (${uiState.dueCardsCount} thẻ đến hạn)",
                        count = uiState.dueCardsCount,
                        borderColor = Color(0xFFFF4B0F),
                        onClick = { onNavigateToStudy("all", StudyMode.DUE_REVIEW) }
                    )

                    // Danh sách bộ từ nổi bật đang hoạt động để bấm vào chi tiết bộ từ
                    if (uiState.deckItems.isNotEmpty()) {
                        HomeFeaturedDecksSection(
                            deckItems = uiState.deckItems,
                            onDeckClick = onNavigateToDeckDetail
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thẻ CTA lớn trên Trang chính cho "Học từ mới" (Figma 2011:6 - viền xanh #417DFF 3.dp)
 * và "Ôn tập hôm nay" (Figma 2015:4 - viền cam #FF4B0F 3.dp).
 */
@Composable
fun HomeStudyCtaCard(
    title: String,
    count: Int,
    borderColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 172.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(3.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Danh sách bộ từ đang hoạt động trên Trang chính giúp truy cập nhanh màn Chi tiết bộ từ.
 */
@Composable
fun HomeFeaturedDecksSection(
    deckItems: List<HomeDeckItem>,
    onDeckClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Bộ từ của bạn",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        deckItems.forEach { item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDeckClick(item.deck.id) },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            text = item.deck.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.totalCardsCount} thẻ · ${item.dueCardsCount} đến hạn · ${item.newCardsCount} mới",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Xem chi tiết bộ từ ${item.deck.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ô 1: Thẻ mới hôm nay (Figma 4:8)
        HomeStatCard(
            title = "Thẻ mới hôm nay",
            icon = Icons.Default.EventAvailable,
            iconTint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1.15f)
        ) {
            Text(
                text = "$newCardsLearnedToday/$dailyNewCardLimit",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
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
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = iconTint.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = greetingText,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Thêm flashcard mới",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Thêm flashcard",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
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
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = quote,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
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

@Preview(name = "HomeScreen 360dp - Dark", widthDp = 360, showBackground = true)
@Composable
private fun HomeScreenHeaderDarkPreview() {
    MemoraTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                newCardsLearnedToday = 7,
                dailyNewCardLimit = 20,
                remainingNewCardsToday = 13,
                dueCardsCount = 24,
                totalDecksCount = 5,
                currentStreakDays = 12
            ),
            onNavigateToStudy = { _, _ -> },
            onNavigateToDeckDetail = {},
            onNavigateToAddCard = {}
        )
    }
}

@Preview(name = "HomeScreen 360dp - Light", widthDp = 360, showBackground = true)
@Composable
private fun HomeScreenHeaderLightPreview() {
    MemoraTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState(
                isLoading = false,
                newCardsLearnedToday = 7,
                dailyNewCardLimit = 20,
                remainingNewCardsToday = 13,
                dueCardsCount = 24,
                totalDecksCount = 5,
                currentStreakDays = 12
            ),
            onNavigateToStudy = { _, _ -> },
            onNavigateToDeckDetail = {},
            onNavigateToAddCard = {}
        )
    }
}
