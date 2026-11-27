package com.uth.memora.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun SettingsRoute(
    onNavigateToAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Cài đặt ứng dụng (Settings)",
        owner = "Người 1 – Trưởng nhóm · Data & Logic",
        description = "Figma 13:2 · Giao diện Sáng/Tối, thanh trượt số thẻ mới (5-50), giờ nhắc học, khối Đồng bộ đám mây + nút \"Đồng bộ ngay\" và Về ứng dụng Memora.",
        modifier = modifier
    )
}
