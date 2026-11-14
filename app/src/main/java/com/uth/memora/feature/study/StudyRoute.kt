package com.uth.memora.feature.study

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun StudyRoute(
    deckId: String,
    onIntermission: (String) -> Unit,
    onFinishSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Phiên học Spaced Repetition (SM-2)",
        owner = "Người 2 – UI & Luồng học",
        description = "Figma 2101:16 · Thẻ lật 3D, 4 nút đánh giá (Chưa nhớ, Khó, Nhớ tốt, Dễ) và Floating Toast hoàn tác 5 giây.",
        modifier = modifier
    )
}
