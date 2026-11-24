package com.uth.memora.feature.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.StudyMode
import com.uth.memora.feature.theme.MemoraTheme
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

                // Khung châm ngôn học tập (Figma 4:74)
                HomeQuoteBanner(
                    quote = "“Điều tuyệt vời của việc học là không ai có thể lấy nó khỏi bạn.”"
                )
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

@Preview(name = "HomeScreen Header - Dark", showBackground = true)
@Composable
private fun HomeScreenHeaderDarkPreview() {
    MemoraTheme(darkTheme = true) {
        HomeScreen(
            uiState = HomeUiState(isLoading = false),
            onNavigateToStudy = { _, _ -> },
            onNavigateToDeckDetail = {},
            onNavigateToAddCard = {}
        )
    }
}

@Preview(name = "HomeScreen Header - Light", showBackground = true)
@Composable
private fun HomeScreenHeaderLightPreview() {
    MemoraTheme(darkTheme = false) {
        HomeScreen(
            uiState = HomeUiState(isLoading = false),
            onNavigateToStudy = { _, _ -> },
            onNavigateToDeckDetail = {},
            onNavigateToAddCard = {}
        )
    }
}
