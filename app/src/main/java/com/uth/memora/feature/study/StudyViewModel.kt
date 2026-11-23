package com.uth.memora.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.StudySessionRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.ReviewLog
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel điều phối phiên học Flashcard cho cả 2 chế độ:
 * - Học từ mới (StudyMode.NEW_CARDS - Mục 6.5)
 * - Ôn tập đến hạn (StudyMode.DUE_REVIEW - Mục 6.6)
 */
class StudyViewModel(
    private val repository: StudySessionRepository,
    private val deckId: String,
    private val studyMode: StudyMode = StudyMode.DUE_REVIEW
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StudyUiState(
            isLoading = true,
            deckId = deckId,
            studyMode = studyMode
        )
    )
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    // Lưu các thẻ mới đã được đánh dấu hiển thị lần đầu trong phiên (Mục 6.5)
    private val displayedNewCardIds = mutableSetOf<String>()

    // Ghi nhớ lượt đánh giá gần nhất có xếp lặp lại thẻ xuống cuối hàng đợi hay không
    private var lastRatingRequeuedCard: Boolean = false

    private val normalizedDeckId: String?
        get() = if (deckId == "all" || deckId.isBlank()) null else deckId

    init {
        loadQueue()
    }

    /**
     * Tải hàng đợi thẻ theo chế độ học (NEW_CARDS hoặc DUE_REVIEW - Mục 6.5, 6.6 & 7.6).
     */
    fun loadQueue() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            runCatching {
                when (studyMode) {
                    StudyMode.NEW_CARDS -> repository.getNewCardsQueue(normalizedDeckId)
                    StudyMode.DUE_REVIEW -> repository.getDueReviewQueue(normalizedDeckId)
                }
            }.onSuccess { loadedCards ->
                if (loadedCards.isEmpty()) {
                    val tomorrowCount = runCatching {
                        repository.getTomorrowDueCount(normalizedDeckId)
                    }.getOrDefault(0)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            queue = emptyList(),
                            currentIndex = 0,
                            isFlipped = false,
                            previewLabels = emptyMap(),
                            totalCardsInChunk = 0,
                            tomorrowDueCount = tomorrowCount
                        )
                    }
                    return@onSuccess
                }

                val mutableQueue = loadedCards.toMutableList()
                val firstCard = markDisplayedIfNew(mutableQueue[0])
                mutableQueue[0] = firstCard
                val labels = repository.getButtonPreviewLabels(firstCard)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        queue = mutableQueue,
                        currentIndex = 0,
                        isFlipped = false,
                        previewLabels = labels,
                        totalCardsInChunk = mutableQueue.size,
                        currentChunkIndex = 0,
                        totalChunks = 1,
                        isSessionFinished = false,
                        isIntermissionPending = false,
                        cardShownAtMillis = System.currentTimeMillis()
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải danh sách thẻ học."
                    )
                }
            }
        }
    }

    /**
     * Lật mặt trước / mặt sau của thẻ hiện tại.
     */
    fun toggleFlip() {
        _uiState.update { state ->
            if (state.currentCard == null) state else state.copy(isFlipped = !state.isFlipped)
        }
    }

    /**
     * Đặt lại mốc thời gian bắt đầu xem thẻ (cardShownAtMillis) và đóng mặt sau (isFlipped = false)
     * mỗi khi chuyển sang thẻ mới hoặc hoàn tác (Mục 6.5, 6.6 & 10.2).
     */
    fun onCardShown(nowMillis: Long = System.currentTimeMillis()) {
        _uiState.update {
            it.copy(
                isFlipped = false,
                cardShownAtMillis = nowMillis
            )
        }
    }

    /**
     * Tính thời gian xem thẻ hợp lệ từ 1 đến 60 giây để không cộng thời gian bỏ máy (Mục 10.2).
     */
    private fun calculateCardViewDurationSeconds(
        cardShownAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        return ((nowMillis - cardShownAtMillis) / 1000L)
            .toInt()
            .coerceIn(1, ReviewLog.MAX_SECONDS_PER_REVIEW)
    }

    /**
     * Ghi nhận đánh giá thẻ (2 nút cho NEW_CARDS hoặc 4 nút SM-2 cho DUE_REVIEW - Mục 6.5, 6.6 & 7.2).
     * Nếu thẻ cần lặp lại trong phiên (bấm Học lại / Chưa nhớ / bước phút LEARNING) thì xếp xuống cuối hàng đợi.
     */
    fun submitRating(quality: ReviewQuality) {
        val state = _uiState.value
        val currentCard = state.currentCard ?: return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val durationSeconds = calculateCardViewDurationSeconds(
                cardShownAtMillis = state.cardShownAtMillis,
                nowMillis = now
            )

            val intervalLabel = state.previewLabels[quality] ?: when (quality) {
                ReviewQuality.AGAIN -> if (studyMode == StudyMode.NEW_CARDS) "cuối hàng đợi" else "1 phút"
                ReviewQuality.HARD -> "10 phút"
                ReviewQuality.GOOD -> "1 ngày"
                ReviewQuality.EASY -> "4 ngày"
            }

            repository.submitCardReview(
                cardId = currentCard.id,
                quality = quality,
                durationValidSeconds = durationSeconds
            ).onSuccess { updatedCard ->
                val mutableQueue = state.queue.toMutableList()
                mutableQueue[state.currentIndex] = updatedCard

                val shouldRepeatInSession = (studyMode == StudyMode.NEW_CARDS && quality == ReviewQuality.AGAIN) ||
                    updatedCard.state == CardState.LEARNING
                lastRatingRequeuedCard = shouldRepeatInSession
                if (shouldRepeatInSession) {
                    mutableQueue.add(updatedCard)
                }

                val nextIndex = state.currentIndex + 1
                val toastText = if (studyMode == StudyMode.NEW_CARDS && quality == ReviewQuality.AGAIN) {
                    "Đã ghi nhận: Học lại · Xếp cuối hàng đợi"
                } else {
                    "Đã ghi nhận: ${quality.labelVi} · Hẹn ôn sau $intervalLabel"
                }

                if (nextIndex >= mutableQueue.size) {
                    val tomorrowCount = runCatching {
                        repository.getTomorrowDueCount(normalizedDeckId)
                    }.getOrDefault(0)

                    _uiState.update {
                        it.copy(
                            queue = mutableQueue,
                            currentIndex = nextIndex,
                            isFlipped = false,
                            canUndo = true,
                            undoToastMessage = toastText,
                            isSessionFinished = true,
                            totalCardsInChunk = mutableQueue.size,
                            tomorrowDueCount = tomorrowCount
                        )
                    }
                } else {
                    val nextCard = markDisplayedIfNew(mutableQueue[nextIndex])
                    mutableQueue[nextIndex] = nextCard
                    val nextLabels = repository.getButtonPreviewLabels(nextCard)

                    _uiState.update {
                        it.copy(
                            queue = mutableQueue,
                            currentIndex = nextIndex,
                            isFlipped = false,
                            previewLabels = nextLabels,
                            canUndo = true,
                            undoToastMessage = toastText,
                            totalCardsInChunk = mutableQueue.size,
                            cardShownAtMillis = System.currentTimeMillis()
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = error.message ?: "Không thể lưu kết quả đánh giá thẻ."
                    )
                }
            }
        }
    }

    /**
     * Đánh dấu thẻ mới ngay khi lần đầu xuất hiện trên màn hình (Mục 6.5).
     */
    private suspend fun markDisplayedIfNew(card: Card): Card {
        val shouldMark = (studyMode == StudyMode.NEW_CARDS || card.state == CardState.NEW) &&
            displayedNewCardIds.add(card.id)
        if (!shouldMark) return card

        return repository.markNewCardDisplayed(card.id).getOrDefault(card)
    }
}
