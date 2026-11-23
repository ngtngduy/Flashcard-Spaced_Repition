package com.uth.memora.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.StudySessionRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
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
     * Đánh dấu thẻ mới ngay khi lần đầu xuất hiện trên màn hình (Mục 6.5).
     */
    private suspend fun markDisplayedIfNew(card: Card): Card {
        val shouldMark = (studyMode == StudyMode.NEW_CARDS || card.state == CardState.NEW) &&
            displayedNewCardIds.add(card.id)
        if (!shouldMark) return card

        return repository.markNewCardDisplayed(card.id).getOrDefault(card)
    }
}
