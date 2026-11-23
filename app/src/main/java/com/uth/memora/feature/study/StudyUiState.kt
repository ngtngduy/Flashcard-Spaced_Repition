package com.uth.memora.feature.study

import com.uth.memora.core.model.Card
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyMode

/**
 * Trạng thái giao diện cho phiên học Flashcard (Học từ mới & Ôn tập SM-2 - Mục 6.5, 6.6 & 9).
 */
data class StudyUiState(
    val isLoading: Boolean = true,
    val deckId: String = "all",
    val studyMode: StudyMode = StudyMode.DUE_REVIEW,
    val queue: List<Card> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val previewLabels: Map<ReviewQuality, String> = emptyMap(),
    val canUndo: Boolean = false,
    val undoToastMessage: String? = null,
    val isSessionFinished: Boolean = false,
    val totalCardsInChunk: Int = 0,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val isIntermissionPending: Boolean = false,
    val cardShownAtMillis: Long = System.currentTimeMillis(),
    val tomorrowDueCount: Int = 0,
    val errorMessage: String? = null
) {
    /**
     * Thẻ hiện tại đang hiển thị trên màn hình (null nếu hàng đợi trống hoặc đã học xong).
     */
    val currentCard: Card?
        get() = queue.getOrNull(currentIndex)

    /**
     * Tỷ lệ hoàn thành trong chặng hiện tại (0f..1f).
     */
    val progressFraction: Float
        get() {
            val total = if (totalCardsInChunk > 0) totalCardsInChunk else queue.size
            if (total <= 0) return 0f
            return (currentIndex.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}
