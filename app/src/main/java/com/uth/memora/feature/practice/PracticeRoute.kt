package com.uth.memora.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun PracticeRoute(
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Luyện đặt câu & AI Chatbot",
        owner = "Người 3 – AI, Cloud & Tài khoản",
        description = "Figma 2061:119 · Gợi ý tối đa 5 từ mục tiêu, ghi âm giọng nói 4 trạng thái, hiệu ứng Shimmer loading và chấm điểm ngữ pháp với Gemini AI.",
        modifier = modifier
    )
}
