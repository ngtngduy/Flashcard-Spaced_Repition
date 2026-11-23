package com.uth.memora.feature.study

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Route điều hướng độc lập cho màn hình Nghỉ giữa chặng (Figma 2241:113 & Mục 9.2).
 * Giữ nguyên chữ ký hàm để tương thích với MemoraNavHost.kt.
 */
@Composable
fun IntermissionRoute(
    deckId: String,
    onResumeStudy: () -> Unit,
    onFinishEarly: () -> Unit,
    modifier: Modifier = Modifier
) {
    IntermissionScreen(
        completedChunk = 1,
        totalChunks = 2,
        completedCardsCount = 15,
        modifier = modifier,
        onContinueNextChunk = onResumeStudy,
        onPauseSession = onFinishEarly
    )
}
