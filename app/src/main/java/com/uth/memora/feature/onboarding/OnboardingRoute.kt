package com.uth.memora.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun OnboardingRoute(
    onFinishOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Giới thiệu ứng dụng (Onboarding)",
        owner = "Người 2 – UI & Luồng học",
        description = "Màn hình vuốt 3 slide giới thiệu tính năng Spaced Repetition (SM-2), AI Chatbot và Chuỗi học.",
        modifier = modifier
    )
}
