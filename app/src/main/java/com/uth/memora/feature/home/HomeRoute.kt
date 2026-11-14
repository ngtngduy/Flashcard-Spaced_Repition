package com.uth.memora.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun HomeRoute(
    onNavigateToStudy: (String) -> Unit,
    onNavigateToDeckDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Trang chính (Home Dashboard)",
        owner = "Người 2 – UI & Luồng học",
        description = "Figma 1:2 · Thẻ tiến độ hôm nay, Thẻ Streak & Khiên bảo vệ chuỗi, Danh sách bộ từ cần ôn và Nút học nhanh.",
        modifier = modifier
    )
}
