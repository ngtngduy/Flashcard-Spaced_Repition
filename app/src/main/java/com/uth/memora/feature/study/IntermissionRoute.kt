package com.uth.memora.feature.study

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun IntermissionRoute(
    deckId: String,
    onResumeStudy: () -> Unit,
    onFinishEarly: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Nghỉ giữa chặng (Anti-burnout Break)",
        owner = "Người 2 – UI & Luồng học",
        description = "Figma 2241:113 · Giao diện nền tối #121212, biểu tượng tách cà phê, thẻ tiến độ và nút \"Tiếp tục học ngay\".",
        modifier = modifier
    )
}
