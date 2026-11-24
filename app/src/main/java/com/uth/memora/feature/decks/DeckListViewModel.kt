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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel quản lý danh sách Bộ từ của tôi, Bộ từ có sẵn, tìm kiếm không dấu,
 * tạo/sửa/xóa mềm bộ từ và sao chép 1 lần bộ từ có sẵn (Figma 8:323, Mục 6.2 & 6.9).
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeckListUiState(isLoading = true))
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    private var cachedAllCards: List<Card> = emptyList()

    init {
        observeDecksAndCards()
    }

    /**
     * Quan sát đồng thời danh sách bộ từ cá nhân, bộ từ có sẵn và toàn bộ thẻ từ vựng.
     */
    fun observeDecksAndCards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            combine(
                deckRepository.observeActiveDecks(),
                deckRepository.observePrebuiltDecks(),
                cardRepository.observeAllActiveCards()
            ) { activeDecks, prebuiltDecks, allCards ->
                val cleanActiveDecks = activeDecks.filter { !it.isDeleted }
                val cleanCards = allCards.filter { !it.isDeleted }
                cachedAllCards = cleanCards

                val nowMillis = System.currentTimeMillis()
                val currentLearningDay = LearningDayCalculator.toLearningDay(nowMillis)
                val cardsByDeckId = cleanCards.groupBy { it.deckId }

                val userItems = cleanActiveDecks.map { deck ->
                    val deckCards = cardsByDeckId[deck.id].orEmpty()
                    DeckSummaryItem(
                        deck = deck,
                        totalCardsCount = deckCards.size,
                        dueCardsCount = deckCards.count { it.isDue(currentLearningDay, nowMillis) },
                        newCardsCount = deckCards.count { it.state == CardState.NEW },
                        masteredCardsCount = deckCards.count { it.retentionStage == RetentionStage.LEVEL_5_MASTERED },
                        isPrebuilt = false,
                        importedUserDeckId = null
                    )
                }

                val prebuiltItems = prebuiltDecks.map { prebuilt ->
                    val prebuiltCards = cardsByDeckId[prebuilt.id].orEmpty()
                    val importedDeck = cleanActiveDecks.firstOrNull { it.sourcePrebuiltId == prebuilt.id }
                    val fallbackCount = if (prebuiltCards.isNotEmpty()) {
                        prebuiltCards.size
                    } else {
                        importedDeck?.let { cardsByDeckId[it.id]?.size } ?: 0
                    }
                    DeckSummaryItem(
                        deck = prebuilt,
                        totalCardsCount = fallbackCount,
                        dueCardsCount = 0,
                        newCardsCount = fallbackCount,
                        masteredCardsCount = 0,
                        isPrebuilt = true,
                        importedUserDeckId = importedDeck?.id
                    )
                }

                Triple(cleanActiveDecks, prebuiltDecks, Pair(userItems, prebuiltItems))
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải danh sách bộ từ."
                    )
                }
            }.collect { (cleanActiveDecks, prebuiltDecks, itemsPair) ->
                val (userItems, prebuiltItems) = itemsPair
                val currentQuery = _uiState.value.searchQuery
                val (filteredUser, filteredPrebuilt, matchedCards) = filterDecksAndCards(
                    query = currentQuery,
                    userItems = userItems,
                    prebuiltItems = prebuiltItems,
                    allCards = cachedAllCards,
                    activeDecks = cleanActiveDecks
                )

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        activeDecks = cleanActiveDecks,
                        prebuiltDecks = prebuiltDecks,
                        userDeckItems = userItems,
                        prebuiltDeckItems = prebuiltItems,
                        filteredUserDeckItems = filteredUser,
                        filteredPrebuiltDeckItems = filteredPrebuilt,
                        matchingCards = matchedCards,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun selectTab(tab: DeckFilterTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /**
     * Tìm kiếm không phân biệt hoa/thường và dấu tiếng Việt trên bộ từ và thẻ từ vựng (Mục 6.9).
     */
    fun onSearchQueryChange(newQuery: String) {
        val state = _uiState.value
        val (filteredUser, filteredPrebuilt, localMatchedCards) = filterDecksAndCards(
            query = newQuery,
            userItems = state.userDeckItems,
            prebuiltItems = state.prebuiltDeckItems,
            allCards = cachedAllCards,
            activeDecks = state.activeDecks
        )

        _uiState.update {
            it.copy(
                searchQuery = newQuery,
                filteredUserDeckItems = filteredUser,
                filteredPrebuiltDeckItems = filteredPrebuilt,
                matchingCards = localMatchedCards
            )
        }

        val trimmed = newQuery.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                runCatching { cardRepository.searchCards(deckId = null, query = trimmed) }
                    .onSuccess { repoCards ->
                        if (_uiState.value.searchQuery == newQuery && repoCards.isNotEmpty()) {
                            val deckMap = _uiState.value.activeDecks.associateBy { it.id }
                            val matches = repoCards
                                .filter { !it.isDeleted && deckMap.containsKey(it.deckId) }
                                .map { card ->
                                    DeckSearchCardMatch(
                                        card = card,
                                        deckName = deckMap[card.deckId]?.name.orEmpty()
                                    )
                                }
                            _uiState.update { it.copy(matchingCards = matches) }
                        }
                    }
            }
        }
    }

    fun clearSearch() {
        onSearchQueryChange("")
    }

    /**
     * Mở hộp thoại tạo bộ từ mới.
     */
    fun openCreateDeckDialog() {
        _uiState.update {
            it.copy(
                isCreateOrEditDialogVisible = true,
                editingDeck = null,
                dialogNameInput = "",
                dialogDescriptionInput = "",
                dialogNameError = null,
                dialogDescriptionError = null
            )
        }
    }

    /**
     * Mở hộp thoại chỉnh sửa tên và mô tả bộ từ.
     */
    fun openEditDeckDialog(deck: Deck) {
        _uiState.update {
            it.copy(
                isCreateOrEditDialogVisible = true,
                editingDeck = deck,
                dialogNameInput = deck.name,
                dialogDescriptionInput = deck.description,
                dialogNameError = null,
                dialogDescriptionError = null
            )
        }
    }

    fun dismissDeckFormDialog() {
        _uiState.update {
            it.copy(
                isCreateOrEditDialogVisible = false,
                editingDeck = null,
                dialogNameError = null,
                dialogDescriptionError = null,
                isSavingDeck = false
            )
        }
    }

    fun onDialogNameChange(name: String) {
        val trimmed = name.trim()
        val nameError = when {
            trimmed.length > Deck.MAX_NAME_LENGTH ->
                "Tên bộ từ không được vượt quá ${Deck.MAX_NAME_LENGTH} ký tự."
            else -> null
        }
        _uiState.update {
            it.copy(
                dialogNameInput = name,
                dialogNameError = nameError
            )
        }
    }

    fun onDialogDescriptionChange(description: String) {
        val descError = if (description.length > Deck.MAX_DESCRIPTION_LENGTH) {
            "Mô tả không được vượt quá ${Deck.MAX_DESCRIPTION_LENGTH} ký tự."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                dialogDescriptionInput = description,
                dialogDescriptionError = descError
            )
        }
    }

    /**
     * Lưu tạo mới hoặc cập nhật bộ từ sau khi kiểm tra hợp lệ (tên 1..50 ký tự, mô tả <= 200 ký tự, không trùng tên).
     */
    fun submitDeckForm() {
        val state = _uiState.value
        val trimmedName = state.dialogNameInput.trim()
        val trimmedDesc = state.dialogDescriptionInput.trim()

        val isDuplicateName = state.activeDecks.any { existing ->
            existing.id != state.editingDeck?.id &&
                existing.name.trim().equals(trimmedName, ignoreCase = true)
        }

        val nameError = when {
            trimmedName.isEmpty() -> "Vui lòng nhập tên bộ từ."
            trimmedName.length > Deck.MAX_NAME_LENGTH ->
                "Tên bộ từ tối đa ${Deck.MAX_NAME_LENGTH} ký tự."
            isDuplicateName -> "Tên bộ từ đã tồn tại. Vui lòng chọn tên khác."
            state.editingDeck == null && state.activeDecks.size >= Deck.MAX_DECKS_PER_USER ->
                "Bạn đã đạt giới hạn tối đa ${Deck.MAX_DECKS_PER_USER} bộ từ."
            else -> null
        }

        val descError = if (trimmedDesc.length > Deck.MAX_DESCRIPTION_LENGTH) {
            "Mô tả tối đa ${Deck.MAX_DESCRIPTION_LENGTH} ký tự."
        } else {
            null
        }

        if (nameError != null || descError != null) {
            _uiState.update {
                it.copy(
                    dialogNameError = nameError,
                    dialogDescriptionError = descError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingDeck = true) }
            val editing = state.editingDeck
            val result = if (editing == null) {
                deckRepository.createDeck(name = trimmedName, description = trimmedDesc)
            } else {
                deckRepository.updateDeck(
                    deckId = editing.id,
                    name = trimmedName,
                    description = trimmedDesc
                )
            }

            result
                .onSuccess { savedDeck ->
                    val message = if (editing == null) {
                        "Đã tạo bộ từ \"${savedDeck.name}\"."
                    } else {
                        "Đã cập nhật bộ từ \"${savedDeck.name}\"."
                    }
                    _uiState.update {
                        it.copy(
                            isSavingDeck = false,
                            isCreateOrEditDialogVisible = false,
                            editingDeck = null,
                            feedbackMessage = message
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSavingDeck = false,
                            dialogNameError = error.message ?: "Không thể lưu bộ từ."
                        )
                    }
                }
        }
    }

    /**
     * Yêu cầu xác nhận trước khi xóa mềm bộ từ.
     */
    fun requestDeleteDeck(deck: Deck) {
        _uiState.update { it.copy(deletingDeck = deck) }
    }

    fun dismissDeleteDeckDialog() {
        _uiState.update { it.copy(deletingDeck = null, isDeletingDeck = false) }
    }

    /**
     * Thực hiện xóa mềm bộ từ (`deckRepository.softDeleteDeck(deckId)`).
     */
    fun confirmDeleteDeck() {
        val targetDeck = _uiState.value.deletingDeck ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingDeck = true) }
            deckRepository.softDeleteDeck(targetDeck.id)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            deletingDeck = null,
                            isDeletingDeck = false,
                            feedbackMessage = "Đã xóa bộ từ \"${targetDeck.name}\"."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            deletingDeck = null,
                            isDeletingDeck = false,
                            errorMessage = error.message ?: "Không thể xóa bộ từ."
                        )
                    }
                }
        }
    }

    /**
     * Sao chép bộ từ có sẵn vào bộ từ cá nhân (chỉ cho phép sao chép 1 lần qua `sourcePrebuiltId`).
     */
    fun importPrebuiltDeck(prebuiltDeckId: String) {
        val state = _uiState.value
        val existingImported = state.activeDecks.firstOrNull { it.sourcePrebuiltId == prebuiltDeckId }
        if (existingImported != null) {
            _uiState.update {
                it.copy(feedbackMessage = "Bộ từ này đã có trong \"Bộ từ của tôi\".")
            }
            return
        }
        if (prebuiltDeckId in state.importingPrebuiltIds) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(importingPrebuiltIds = it.importingPrebuiltIds + prebuiltDeckId)
            }
            deckRepository.importPrebuiltDeck(prebuiltDeckId)
                .onSuccess { importedDeck ->
                    _uiState.update {
                        it.copy(
                            importingPrebuiltIds = it.importingPrebuiltIds - prebuiltDeckId,
                            feedbackMessage = "Đã sao chép \"${importedDeck.name}\" vào Bộ từ của tôi."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            importingPrebuiltIds = it.importingPrebuiltIds - prebuiltDeckId,
                            errorMessage = error.message ?: "Không thể sao chép bộ từ có sẵn."
                        )
                    }
                }
        }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }

    private fun filterDecksAndCards(
        query: String,
        userItems: List<DeckSummaryItem>,
        prebuiltItems: List<DeckSummaryItem>,
        allCards: List<Card>,
        activeDecks: List<Deck>
    ): Triple<List<DeckSummaryItem>, List<DeckSummaryItem>, List<DeckSearchCardMatch>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return Triple(userItems, prebuiltItems, emptyList())
        }

        val deckMap = activeDecks.associateBy { it.id }

        val matchedCards = allCards
            .filter { card ->
                deckMap.containsKey(card.deckId) && (
                    VietnameseTextNormalizer.matchesQuery(card.word, trimmed) ||
                        VietnameseTextNormalizer.matchesQuery(card.meaning, trimmed) ||
                        VietnameseTextNormalizer.matchesQuery(card.example, trimmed)
                    )
            }
            .map { card ->
                DeckSearchCardMatch(
                    card = card,
                    deckName = deckMap[card.deckId]?.name.orEmpty()
                )
            }

        val deckIdsWithMatchingCards = matchedCards.map { it.card.deckId }.toSet()

        val filteredUser = userItems.filter { item ->
            VietnameseTextNormalizer.matchesQuery(item.deck.name, trimmed) ||
                VietnameseTextNormalizer.matchesQuery(item.deck.description, trimmed) ||
                item.deck.id in deckIdsWithMatchingCards
        }

        val filteredPrebuilt = prebuiltItems.filter { item ->
            VietnameseTextNormalizer.matchesQuery(item.deck.name, trimmed) ||
                VietnameseTextNormalizer.matchesQuery(item.deck.description, trimmed)
        }

        return Triple(filteredUser, filteredPrebuilt, matchedCards)
    }
}
