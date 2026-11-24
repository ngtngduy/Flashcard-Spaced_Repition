package com.uth.memora.feature.decks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.domain.VietnameseTextNormalizer
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.sm2.LearningDayCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Trạng thái giao diện cho màn Chi tiết Bộ từ (Deck Detail - Figma 2101:11, Mục 6.2 & 7.5).
 */
data class DeckDetailUiState(
    val deckId: String,
    val isLoading: Boolean = true,
    val deck: Deck? = null,
    val allCards: List<Card> = emptyList(),
    val filteredCards: List<Card> = emptyList(),
    val searchQuery: String = "",
    val selectedStageFilter: RetentionStage? = null,
    val stageDistribution: Map<RetentionStage, Int> = emptyMap(),
    val masteredCardsCount: Int = 0,
    val masteredPercentage: Int = 0,
    val dueCardsCount: Int = 0,
    val newCardsCount: Int = 0,
    val deletingCard: Card? = null,
    val isDeletingCard: Boolean = false,
    val resettingCard: Card? = null,
    val isResettingCard: Boolean = false,
    val isResetDeckDialogVisible: Boolean = false,
    val isResettingDeck: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
) {
    val totalCardsCount: Int
        get() = allCards.size
}

/**
 * ViewModel quản lý dữ liệu màn Chi tiết Bộ từ (Figma 2101:11):
 * - Thống kê tỷ lệ hoàn thành (% thẻ đạt Cấp 5 – Thành thạo)
 * - Phân bố số lượng thẻ theo 6 cấp độ ghi nhớ (Cấp 0..Cấp 5)
 * - Tìm kiếm thẻ không dấu & lọc theo cấp độ ghi nhớ
 * - Xóa mềm thẻ từ vựng
 */
class DeckDetailViewModel(
    private val deckId: String,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckDetailUiState(deckId = deckId, isLoading = true))
    val uiState: StateFlow<DeckDetailUiState> = _uiState.asStateFlow()

    init {
        loadDeckAndObserveCards()
    }

    fun loadDeckAndObserveCards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val loadedDeck = runCatching { deckRepository.getDeckById(deckId) }.getOrNull()
            _uiState.update { it.copy(deck = loadedDeck) }

            cardRepository.observeCardsByDeck(deckId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Không thể tải danh sách thẻ của bộ từ."
                        )
                    }
                }
                .collect { cards ->
                    val activeCards = cards.filter { !it.isDeleted }
                    val nowMillis = System.currentTimeMillis()
                    val currentLearningDay = LearningDayCalculator.toLearningDay(nowMillis)

                    val distribution = RetentionStage.entries.associateWith { stage ->
                        activeCards.count { it.retentionStage == stage }
                    }
                    val masteredCount = distribution[RetentionStage.LEVEL_5_MASTERED] ?: 0
                    val totalCount = activeCards.size
                    val percentage = if (totalCount > 0) {
                        ((masteredCount.toFloat() / totalCount.toFloat()) * 100f).roundToInt()
                    } else {
                        0
                    }

                    val dueCount = activeCards.count { it.isDue(currentLearningDay, nowMillis) }
                    val newCount = activeCards.count { it.state == CardState.NEW }

                    val latestDeck = runCatching { deckRepository.getDeckById(deckId) }.getOrNull()
                        ?: _uiState.value.deck

                    val currentState = _uiState.value
                    val filtered = applyCardFilters(
                        cards = activeCards,
                        query = currentState.searchQuery,
                        stageFilter = currentState.selectedStageFilter
                    )

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            deck = latestDeck,
                            allCards = activeCards,
                            filteredCards = filtered,
                            stageDistribution = distribution,
                            masteredCardsCount = masteredCount,
                            masteredPercentage = percentage,
                            dueCardsCount = dueCount,
                            newCardsCount = newCount,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        val state = _uiState.value
        val filtered = applyCardFilters(
            cards = state.allCards,
            query = newQuery,
            stageFilter = state.selectedStageFilter
        )
        _uiState.update {
            it.copy(
                searchQuery = newQuery,
                filteredCards = filtered
            )
        }
    }

    fun clearSearch() {
        onSearchQueryChange("")
    }

    fun onStageFilterSelected(stage: RetentionStage?) {
        val state = _uiState.value
        val filtered = applyCardFilters(
            cards = state.allCards,
            query = state.searchQuery,
            stageFilter = stage
        )
        _uiState.update {
            it.copy(
                selectedStageFilter = stage,
                filteredCards = filtered
            )
        }
    }

    fun requestDeleteCard(card: Card) {
        _uiState.update { it.copy(deletingCard = card) }
    }

    fun dismissDeleteCardDialog() {
        _uiState.update { it.copy(deletingCard = null, isDeletingCard = false) }
    }

    fun confirmDeleteCard() {
        val targetCard = _uiState.value.deletingCard ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingCard = true) }
            cardRepository.softDeleteCard(targetCard.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            deletingCard = null,
                            isDeletingCard = false,
                            feedbackMessage = "Đã xóa thẻ \"${targetCard.word}\"."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            deletingCard = null,
                            isDeletingCard = false,
                            errorMessage = error.message ?: "Không thể xóa thẻ từ vựng."
                        )
                    }
                }
        }
    }

    /**
     * Yêu cầu xác nhận trước khi đặt lại tiến độ SM-2 của một thẻ về Cấp 0 – Chưa học (Mục 6.2 & 7.5).
     */
    fun requestResetCardProgress(card: Card) {
        _uiState.update { it.copy(resettingCard = card) }
    }

    fun dismissResetCardDialog() {
        _uiState.update { it.copy(resettingCard = null, isResettingCard = false) }
    }

    fun confirmResetCardProgress() {
        val targetCard = _uiState.value.resettingCard ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isResettingCard = true) }
            cardRepository.resetCardProgress(targetCard.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            resettingCard = null,
                            isResettingCard = false,
                            feedbackMessage = "Đã đặt lại tiến độ thẻ \"${targetCard.word}\" về Cấp 0 – Chưa học."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            resettingCard = null,
                            isResettingCard = false,
                            errorMessage = error.message ?: "Không thể đặt lại tiến độ thẻ."
                        )
                    }
                }
        }
    }

    /**
     * Yêu cầu xác nhận trước khi đặt lại toàn bộ tiến độ SM-2 của bộ từ về Cấp 0 – Chưa học (Mục 6.2 & 7.5).
     */
    fun requestResetDeckProgress() {
        _uiState.update { it.copy(isResetDeckDialogVisible = true) }
    }

    fun dismissResetDeckDialog() {
        _uiState.update { it.copy(isResetDeckDialogVisible = false, isResettingDeck = false) }
    }

    fun confirmResetDeckProgress() {
        viewModelScope.launch {
            _uiState.update { it.copy(isResettingDeck = true) }
            cardRepository.resetDeckProgress(deckId)
                .onSuccess {
                    val deckName = _uiState.value.deck?.name ?: "bộ từ"
                    _uiState.update {
                        it.copy(
                            isResetDeckDialogVisible = false,
                            isResettingDeck = false,
                            feedbackMessage = "Đã đặt lại toàn bộ tiến độ của \"$deckName\" về Cấp 0 – Chưa học."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isResetDeckDialogVisible = false,
                            isResettingDeck = false,
                            errorMessage = error.message ?: "Không thể đặt lại tiến độ bộ từ."
                        )
                    }
                }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }

    private fun applyCardFilters(
        cards: List<Card>,
        query: String,
        stageFilter: RetentionStage?
    ): List<Card> {
        val trimmed = query.trim()
        return cards.filter { card ->
            val matchesStage = stageFilter == null || card.retentionStage == stageFilter
            val matchesSearch = trimmed.isEmpty() ||
                VietnameseTextNormalizer.matchesQuery(card.word, trimmed) ||
                VietnameseTextNormalizer.matchesQuery(card.meaning, trimmed) ||
                VietnameseTextNormalizer.matchesQuery(card.example, trimmed)
            matchesStage && matchesSearch
        }
    }
}
