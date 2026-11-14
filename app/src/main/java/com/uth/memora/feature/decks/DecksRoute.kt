package com.uth.memora.feature.decks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun DecksRoute(
    onDeckClick: (String) -> Unit,
    onCreateDeckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Quản lý Bộ từ (Deck List)",
        owner = "Người 2 – UI & Luồng học",
        description = "Figma 1:2 / 2101:9 · Danh sách bộ thẻ cá nhân, bộ từ mẫu có sẵn và nút tạo bộ từ mới.",
        modifier = modifier
    )
}
