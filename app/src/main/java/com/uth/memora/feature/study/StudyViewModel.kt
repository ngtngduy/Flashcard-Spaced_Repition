package com.uth.memora.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.StudySessionRepository
import com.uth.memora.core.domain.logic.ReviewQueueChunker
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.ReviewLog
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel điều phối phiên học Flashcard cho cả 2 chế độ:
 * - Học từ mới (StudyMode.NEW_CARDS - Mục 6.5)
 * - Ôn tập đến hạn (StudyMode.DUE_REVIEW - Mục 6.6)
 * Hỗ trợ hoàn tác (Undo) lượt đánh giá gần nhất trong cửa sổ 5 giây (Mục 9.1),
 * chia chặng chống quá tải bằng ReviewQueueChunker khi hàng đợi > 30 thẻ (Mục 9.2),
 * và tải số thẻ dự kiến đến hạn ngày mai khi kết thúc phiên (Figma 2199:19).
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

    // Danh sách các chặng thẻ được chia bởi ReviewQueueChunker (Mục 9.2)
    private var chunks: List<List<Card>> = emptyList()

    // Lưu các thẻ mới đã được đánh dấu hiển thị lần đầu trong phiên (Mục 6.5)
    private val displayedNewCardIds = mutableSetOf<String>()

    // Ghi nhớ lượt đánh giá gần nhất có xếp lặp lại thẻ xuống cuối hàng đợi hay không
    private var lastRatingRequeuedCard: Boolean = false
    private var lastRatingDurationSeconds: Int = 0

    // Job đếm ngược 5 giây cho cửa sổ Hoàn tác (Undo - Mục 9.1)
    private var undoCountdownJob: Job? = null

    private val normalizedDeckId: String?
        get() = if (deckId == "all" || deckId.isBlank()) null else deckId

    init {
        loadQueue()
    }

    /**
     * Tải hàng đợi thẻ theo chế độ học (NEW_CARDS hoặc DUE_REVIEW - Mục 6.5, 6.6, 7.6 & 9.2).
     * Chia hàng đợi > 30 thẻ thành các chặng ~15 thẻ qua ReviewQueueChunker.splitIntoChunks().
     */
    fun loadQueue() {
        undoCountdownJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    canUndo = false,
                    undoToastMessage = null,
                    errorMessage = null
                )
            }

            runCatching {
                when (studyMode) {
                    StudyMode.NEW_CARDS -> repository.getNewCardsQueue(normalizedDeckId)
                    StudyMode.DUE_REVIEW -> repository.getDueReviewQueue(normalizedDeckId)
                }
            }.onSuccess { loadedCards ->
                if (loadedCards.isEmpty()) {
                    chunks = emptyList()
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
                            currentChunkIndex = 0,
                            totalChunks = 1,
                            totalReviewedCount = 0,
                            totalStudyDurationSeconds = 0,
                            tomorrowDueCount = tomorrowCount
                        )
                    }
                    return@onSuccess
                }

                chunks = ReviewQueueChunker.splitIntoChunks(loadedCards)
                val firstChunk = chunks.first().toMutableList()
                val firstCard = markDisplayedIfNew(firstChunk[0])
                firstChunk[0] = firstCard
                val labels = repository.getButtonPreviewLabels(firstCard)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        queue = firstChunk,
                        currentIndex = 0,
                        isFlipped = false,
                        previewLabels = labels,
                        totalCardsInChunk = firstChunk.size,
                        currentChunkIndex = 0,
                        totalChunks = chunks.size,
                        totalReviewedCount = 0,
                        totalStudyDurationSeconds = 0,
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
     * Ghi nhận đánh giá thẻ (2 nút cho NEW_CARDS hoặc 4 nút SM-2 cho DUE_REVIEW - Mục 6.5, 6.6, 7.2 & 9.2).
     * Khi hoàn thành 1 chặng và vẫn còn chặng tiếp theo, bật isIntermissionPending = true;
     * khi hoàn tất toàn bộ phiên, gọi repository.getTomorrowDueCount() cho màn Tổng kết (Figma 2199:19).
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
                lastRatingDurationSeconds = durationSeconds
                if (shouldRepeatInSession) {
                    mutableQueue.add(updatedCard)
                }

                val nextIndex = state.currentIndex + 1
                val newReviewedCount = state.totalReviewedCount + 1
                val newStudyDuration = state.totalStudyDurationSeconds + durationSeconds
                val toastText = if (studyMode == StudyMode.NEW_CARDS && quality == ReviewQuality.AGAIN) {
                    "Đã ghi nhận: Học lại · Xếp cuối hàng đợi"
                } else {
                    "Đã ghi nhận: ${quality.labelVi} · Hẹn ôn sau $intervalLabel"
                }

                if (nextIndex >= mutableQueue.size) {
                    val hasMoreChunks = state.currentChunkIndex + 1 < state.totalChunks
                    if (hasMoreChunks) {
                        _uiState.update {
                            it.copy(
                                queue = mutableQueue,
                                currentIndex = nextIndex,
                                isFlipped = false,
                                canUndo = true,
                                undoToastMessage = toastText,
                                isIntermissionPending = true,
                                isSessionFinished = false,
                                totalCardsInChunk = mutableQueue.size,
                                totalReviewedCount = newReviewedCount,
                                totalStudyDurationSeconds = newStudyDuration
                            )
                        }
                    } else {
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
                                isIntermissionPending = false,
                                isSessionFinished = true,
                                totalCardsInChunk = mutableQueue.size,
                                totalReviewedCount = newReviewedCount,
                                totalStudyDurationSeconds = newStudyDuration,
                                tomorrowDueCount = tomorrowCount
                            )
                        }
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
                            totalReviewedCount = newReviewedCount,
                            totalStudyDurationSeconds = newStudyDuration,
                            cardShownAtMillis = System.currentTimeMillis()
                        )
                    }
                }

                startUndoCountdownWindow()
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
     * Tiếp tục sang chặng kế tiếp sau khi nghỉ giữa chặng (Mục 9.2).
     */
    fun continueToNextChunk() {
        val state = _uiState.value
        val nextChunkIndex = state.currentChunkIndex + 1
        if (nextChunkIndex !in chunks.indices) {
            finishSessionFromIntermission()
            return
        }

        undoCountdownJob?.cancel()
        viewModelScope.launch {
            val nextChunk = chunks[nextChunkIndex].toMutableList()
            val firstCard = markDisplayedIfNew(nextChunk[0])
            nextChunk[0] = firstCard
            val labels = repository.getButtonPreviewLabels(firstCard)

            _uiState.update {
                it.copy(
                    queue = nextChunk,
                    currentIndex = 0,
                    isFlipped = false,
                    previewLabels = labels,
                    canUndo = false,
                    undoToastMessage = null,
                    totalCardsInChunk = nextChunk.size,
                    currentChunkIndex = nextChunkIndex,
                    isIntermissionPending = false,
                    isSessionFinished = false,
                    cardShownAtMillis = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * Khi người dùng chọn "Nghỉ ngơi tại đây" từ màn Nghỉ giữa chặng, tải số thẻ ngày mai và mở Tổng kết phiên.
     */
    fun finishSessionFromIntermission() {
        undoCountdownJob?.cancel()
        viewModelScope.launch {
            val tomorrowCount = runCatching {
                repository.getTomorrowDueCount(normalizedDeckId)
            }.getOrDefault(0)

            _uiState.update {
                it.copy(
                    canUndo = false,
                    undoToastMessage = null,
                    isIntermissionPending = false,
                    isSessionFinished = true,
                    tomorrowDueCount = tomorrowCount
                )
            }
        }
    }

    /**
     * Hoàn tác lượt đánh giá gần nhất trong vòng 5 giây (Mục 9.1):
     * - Gọi repository.undoLastReview() để khôi phục lịch SM-2 cũ của thẻ
     * - Lùi currentIndex - 1, gỡ bản sao ở cuối hàng đợi (nếu lượt vừa rồi có xếp lặp lại) và hiện lại mặt trước thẻ
     */
    fun undoLastRating() {
        val state = _uiState.value
        if (!state.canUndo || state.currentIndex <= 0) return

        undoCountdownJob?.cancel()
        viewModelScope.launch {
            repository.undoLastReview().onSuccess { restoredCard ->
                val targetIndex = (state.currentIndex - 1).coerceAtLeast(0)
                val mutableQueue = state.queue.toMutableList()

                if (lastRatingRequeuedCard && mutableQueue.size > targetIndex + 1) {
                    mutableQueue.removeAt(mutableQueue.lastIndex)
                }
                lastRatingRequeuedCard = false

                if (targetIndex in mutableQueue.indices) {
                    mutableQueue[targetIndex] = restoredCard
                }

                val restoredLabels = repository.getButtonPreviewLabels(restoredCard)

                _uiState.update {
                    it.copy(
                        queue = mutableQueue,
                        currentIndex = targetIndex,
                        isFlipped = false,
                        previewLabels = restoredLabels,
                        canUndo = false,
                        undoToastMessage = null,
                        isSessionFinished = false,
                        isIntermissionPending = false,
                        totalCardsInChunk = mutableQueue.size,
                        totalReviewedCount = (state.totalReviewedCount - 1).coerceAtLeast(0),
                        totalStudyDurationSeconds = (state.totalStudyDurationSeconds - lastRatingDurationSeconds).coerceAtLeast(0),
                        cardShownAtMillis = System.currentTimeMillis()
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        canUndo = false,
                        undoToastMessage = null,
                        errorMessage = error.message ?: "Không thể hoàn tác lượt đánh giá vừa rồi."
                    )
                }
            }
        }
    }

    /**
     * Khởi chạy bộ đếm ngược 5 giây (5000ms) cho phép người học bấm Hoàn tác (Mục 9.1).
     */
    private fun startUndoCountdownWindow() {
        undoCountdownJob?.cancel()
        undoCountdownJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            _uiState.update {
                it.copy(
                    canUndo = false,
                    undoToastMessage = null
                )
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

    companion object {
        const val UNDO_WINDOW_MILLIS = 5_000L
    }
}
