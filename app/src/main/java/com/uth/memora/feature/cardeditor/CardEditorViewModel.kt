package com.uth.memora.feature.cardeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.sm2.LearningDayCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel quản lý màn hình Thêm / Sửa thẻ từ vựng & AI Auto-Fill (Figma 2101:9 & 2227:58, Mục 6.3 & 11):
 * - Chọn bộ từ đích và 7 loại từ [PartOfSpeech]
 * - Kiểm tra hợp lệ: word (1..60), phonetic (<= 60), meaning (1..300), example (<= 300), ảnh (<= 500KB)
 * - Gợi ý điền bằng AI (30 lượt/ngày) kèm Shimmer loading trên các ô đang chờ
 * - Cảnh báo từ trùng trong cùng bộ từ (`allowDuplicateWord = false` -> xác nhận -> `allowDuplicateWord = true`)
 * - Hỗ trợ cả chế độ Thêm liên tục (reset form giữ nguyên bộ từ) và chế độ Chỉnh sửa thẻ (`cardId != null`)
 */
class CardEditorViewModel(
    private val initialDeckId: String,
    private val cardId: String?,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiCardAutoFillService: AiCardAutoFillService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CardEditorUiState(
            isLoading = true,
            isEditMode = !cardId.isNullOrBlank(),
            cardId = cardId,
            selectedDeckId = initialDeckId,
            continuousAddMode = cardId.isNullOrBlank()
        )
    )
    val uiState: StateFlow<CardEditorUiState> = _uiState.asStateFlow()

    private var cachedActiveCards: List<Card> = emptyList()

    init {
        initializeEditor()
    }

    private fun initializeEditor() {
        viewModelScope.launch {
            runCatching {
                userPreferencesRepository.resetDailyQuotasIfNeeded(LearningDayCalculator.toLearningDay())
            }

            if (!cardId.isNullOrBlank()) {
                val existingCard = runCatching { cardRepository.getCardById(cardId) }.getOrNull()
                if (existingCard != null) {
                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            originalCard = existingCard,
                            selectedDeckId = existingCard.deckId,
                            word = existingCard.word,
                            phonetic = existingCard.phonetic,
                            partOfSpeech = existingCard.partOfSpeech,
                            meaning = existingCard.meaning,
                            example = existingCard.example,
                            localImagePath = existingCard.localImagePath,
                            continuousAddMode = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            continuousAddMode = false,
                            errorMessage = "Không tìm thấy thẻ từ vựng cần chỉnh sửa."
                        )
                    }
                }
            }

            combine(
                deckRepository.observeActiveDecks(),
                cardRepository.observeAllActiveCards(),
                userPreferencesRepository.observeUserPreferences()
            ) { decks, allCards, prefs ->
                val cleanDecks = decks.filter { !it.isDeleted }
                val cleanCards = allCards.filter { !it.isDeleted }
                cachedActiveCards = cleanCards

                val countByDeck = cleanCards.groupingBy { it.deckId }.eachCount()
                val deckOptions = cleanDecks.map { deck ->
                    CardEditorDeckOption(
                        deck = deck,
                        cardCount = countByDeck[deck.id] ?: 0
                    )
                }

                Pair(deckOptions, prefs.aiAutoFillRemainingToday)
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải dữ liệu bộ từ."
                    )
                }
            }.collect { (deckOptions, remainingQuota) ->
                _uiState.update { current ->
                    val resolvedDeckId = when {
                        deckOptions.any { it.deck.id == current.selectedDeckId } -> current.selectedDeckId
                        deckOptions.isNotEmpty() -> deckOptions.first().deck.id
                        else -> current.selectedDeckId
                    }
                    current.copy(
                        isLoading = false,
                        availableDecks = deckOptions,
                        selectedDeckId = resolvedDeckId,
                        aiAutoFillRemainingToday = remainingQuota
                    )
                }
            }
        }
    }

    fun onSelectDeck(deckId: String) {
        _uiState.update {
            it.copy(
                selectedDeckId = deckId,
                deckError = null
            )
        }
    }

    fun onWordChange(newWord: String) {
        val trimmedLen = newWord.trim().length
        val error = if (trimmedLen > Card.MAX_WORD_LENGTH) {
            "Từ hoặc cụm từ không được vượt quá ${Card.MAX_WORD_LENGTH} ký tự."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                word = newWord,
                wordError = error,
                feedbackMessage = null
            )
        }
    }

    fun onPhoneticChange(newPhonetic: String) {
        val error = if (newPhonetic.trim().length > Card.MAX_PHONETIC_LENGTH) {
            "Phiên âm IPA không được vượt quá ${Card.MAX_PHONETIC_LENGTH} ký tự."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                phonetic = newPhonetic,
                phoneticError = error
            )
        }
    }

    fun onPartOfSpeechChange(newPos: PartOfSpeech?) {
        _uiState.update { it.copy(partOfSpeech = newPos) }
    }

    fun onMeaningChange(newMeaning: String) {
        val trimmedLen = newMeaning.trim().length
        val error = if (trimmedLen > Card.MAX_MEANING_LENGTH) {
            "Nghĩa tiếng Việt không được vượt quá ${Card.MAX_MEANING_LENGTH} ký tự."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                meaning = newMeaning,
                meaningError = error
            )
        }
    }

    fun onExampleChange(newExample: String) {
        val error = if (newExample.trim().length > Card.MAX_EXAMPLE_LENGTH) {
            "Câu ví dụ không được vượt quá ${Card.MAX_EXAMPLE_LENGTH} ký tự."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                example = newExample,
                exampleError = error
            )
        }
    }

    fun onImageSelected(imagePathOrUri: String?, fileSizeBytes: Long? = null) {
        if (fileSizeBytes != null && fileSizeBytes > CardEditorUiState.MAX_IMAGE_SIZE_BYTES) {
            _uiState.update {
                it.copy(
                    imageError = "Dung lượng ảnh vượt quá 500KB. Vui lòng chọn ảnh nhỏ hơn."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                localImagePath = imagePathOrUri,
                imageError = null
            )
        }
    }

    fun onRemoveImage() {
        _uiState.update {
            it.copy(
                localImagePath = null,
                imageError = null
            )
        }
    }

    fun onToggleContinuousAddMode(enabled: Boolean) {
        _uiState.update { it.copy(continuousAddMode = enabled) }
    }

    /**
     * Gợi ý điền nhanh Phiên âm IPA, Loại từ, Nghĩa tiếng Việt và Câu ví dụ bằng AI (Figma 2227:58 & Mục 6.3).
     * Tiêu thụ 1 lượt trong hạn mức 30 lượt/ngày khi thành công.
     */
    fun autoFillWithAi() {
        val state = _uiState.value
        if (state.isAutoFilling) return

        val cleanWord = state.word.trim()
        if (cleanWord.isEmpty()) {
            _uiState.update {
                it.copy(wordError = "Vui lòng nhập từ hoặc cụm từ tiếng Anh trước khi gọi AI.")
            }
            return
        }
        if (cleanWord.length > Card.MAX_WORD_LENGTH) {
            _uiState.update {
                it.copy(wordError = "Từ hoặc cụm từ tối đa ${Card.MAX_WORD_LENGTH} ký tự.")
            }
            return
        }
        if (state.aiAutoFillRemainingToday <= 0) {
            _uiState.update {
                it.copy(errorMessage = "Bạn đã dùng hết ${state.aiAutoFillMaxDaily} lượt gợi ý AI hôm nay.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAutoFilling = true,
                    wordError = null,
                    errorMessage = null,
                    feedbackMessage = null
                )
            }

            // Đảm bảo hiệu ứng Shimmer hiển thị mượt tối thiểu 350ms
            val startMillis = System.currentTimeMillis()
            val result = cardRepository.autoFillCardContent(cleanWord)
                .recoverCatching { repoErr ->
                    aiCardAutoFillService?.autoFillCard(cleanWord)?.getOrThrow()
                        ?: throw repoErr
                }
            val elapsed = System.currentTimeMillis() - startMillis
            if (elapsed < 350L) {
                delay(350L - elapsed)
            }

            result
                .onSuccess { autoFillData ->
                    // RoomCardRepository.autoFillCardContent đã trừ 1 lượt hạn mức, chỉ đọc lại số lượt còn lại (Bug M2-1)
                    val remaining = runCatching {
                        userPreferencesRepository.getUserPreferences().aiAutoFillRemainingToday
                    }.getOrDefault((state.aiAutoFillRemainingToday - 1).coerceAtLeast(0))

                    _uiState.update { current ->
                        current.copy(
                            isAutoFilling = false,
                            phonetic = autoFillData.phonetic.take(Card.MAX_PHONETIC_LENGTH),
                            partOfSpeech = autoFillData.partOfSpeech ?: current.partOfSpeech,
                            meaning = autoFillData.meaning.take(Card.MAX_MEANING_LENGTH),
                            example = autoFillData.example.take(Card.MAX_EXAMPLE_LENGTH),
                            phoneticError = null,
                            meaningError = null,
                            exampleError = null,
                            aiAutoFillRemainingToday = remaining,
                            feedbackMessage = "Đã tự động điền thông tin cho từ \"${autoFillData.word}\"."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAutoFilling = false,
                            errorMessage = error.message ?: "Không thể gợi ý bằng AI lúc này."
                        )
                    }
                }
        }
    }

    /**
     * Lưu thẻ mới hoặc cập nhật thẻ đang chỉnh sửa.
     */
    fun saveCard(closeAfterSave: Boolean = false) {
        performSaveCard(allowDuplicateWord = false, closeAfterSave = closeAfterSave)
    }

    /**
     * Người dùng xác nhận "Vẫn lưu" khi phát hiện từ trùng trong cùng bộ từ (G8).
     */
    fun confirmSaveDuplicateWord() {
        val closeAfter = _uiState.value.pendingCloseAfterDuplicateConfirm
        _uiState.update {
            it.copy(
                isDuplicateWordDialogVisible = false,
                duplicateWordCandidate = ""
            )
        }
        performSaveCard(allowDuplicateWord = true, closeAfterSave = closeAfter)
    }

    fun dismissDuplicateWordDialog() {
        _uiState.update {
            it.copy(
                isDuplicateWordDialogVisible = false,
                duplicateWordCandidate = "",
                pendingCloseAfterDuplicateConfirm = false
            )
        }
    }

    fun onScreenClosed() {
        _uiState.update { it.copy(shouldCloseScreen = false) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }

    private fun performSaveCard(allowDuplicateWord: Boolean, closeAfterSave: Boolean) {
        val state = _uiState.value
        if (state.isSaving) return

        val trimmedWord = state.word.trim()
        val trimmedPhonetic = state.phonetic.trim()
        val trimmedMeaning = state.meaning.trim()
        val trimmedExample = state.example.trim()
        val targetDeckId = state.selectedDeckId.trim()

        val deckError = if (targetDeckId.isEmpty()) {
            "Vui lòng chọn bộ từ vựng để lưu thẻ."
        } else {
            null
        }

        val wordError = when {
            trimmedWord.isEmpty() -> "Vui lòng nhập từ hoặc cụm từ tiếng Anh."
            trimmedWord.length > Card.MAX_WORD_LENGTH ->
                "Từ hoặc cụm từ tối đa ${Card.MAX_WORD_LENGTH} ký tự."
            else -> null
        }

        val phoneticError = if (trimmedPhonetic.length > Card.MAX_PHONETIC_LENGTH) {
            "Phiên âm IPA tối đa ${Card.MAX_PHONETIC_LENGTH} ký tự."
        } else {
            null
        }

        val meaningError = when {
            trimmedMeaning.isEmpty() -> "Vui lòng nhập nghĩa tiếng Việt."
            trimmedMeaning.length > Card.MAX_MEANING_LENGTH ->
                "Nghĩa tiếng Việt tối đa ${Card.MAX_MEANING_LENGTH} ký tự."
            else -> null
        }

        val exampleError = if (trimmedExample.length > Card.MAX_EXAMPLE_LENGTH) {
            "Câu ví dụ tối đa ${Card.MAX_EXAMPLE_LENGTH} ký tự."
        } else {
            null
        }

        val deckCardCount = state.availableDecks
            .firstOrNull { it.deck.id == targetDeckId }
            ?.cardCount ?: 0

        if (!state.isEditMode && deckCardCount >= Deck.MAX_CARDS_PER_DECK) {
            _uiState.update {
                it.copy(errorMessage = "Bộ từ này đã đạt giới hạn tối đa ${Deck.MAX_CARDS_PER_DECK} thẻ.")
            }
            return
        }

        if (deckError != null || wordError != null || phoneticError != null ||
            meaningError != null || exampleError != null
        ) {
            _uiState.update {
                it.copy(
                    deckError = deckError,
                    wordError = wordError,
                    phoneticError = phoneticError,
                    meaningError = meaningError,
                    exampleError = exampleError
                )
            }
            return
        }

        // Kiểm tra trùng từ trong cùng bộ từ (nếu chưa xác nhận allowDuplicateWord)
        if (!allowDuplicateWord) {
            val isDuplicateInDeck = cachedActiveCards.any { card ->
                card.deckId == targetDeckId &&
                    card.id != state.cardId &&
                    card.word.trim().equals(trimmedWord, ignoreCase = true)
            }
            if (isDuplicateInDeck) {
                _uiState.update {
                    it.copy(
                        isDuplicateWordDialogVisible = true,
                        duplicateWordCandidate = trimmedWord,
                        pendingCloseAfterDuplicateConfirm = closeAfterSave
                    )
                }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                    feedbackMessage = null
                )
            }

            if (state.isEditMode && !state.cardId.isNullOrBlank()) {
                cardRepository.updateCardContent(
                    cardId = state.cardId,
                    deckId = targetDeckId,
                    word = trimmedWord,
                    meaning = trimmedMeaning,
                    phonetic = trimmedPhonetic,
                    partOfSpeech = state.partOfSpeech,
                    example = trimmedExample,
                    localImagePath = state.localImagePath
                ).onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            shouldCloseScreen = true,
                            feedbackMessage = "Đã cập nhật thẻ \"$trimmedWord\"."
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Không thể cập nhật thẻ từ vựng."
                        )
                    }
                }
            } else {
                // Nếu người dùng bấm "+ Thêm flashcard" từ Trang chính khi chưa có bộ từ nào ("deck_everyday"),
                // tự động tạo bộ từ mặc định "Từ vựng hằng ngày" trước khi lưu thẻ (Bug M2-3)
                val deckExists = state.availableDecks.any { it.deck.id == targetDeckId } ||
                    runCatching { deckRepository.getDeckById(targetDeckId) }.getOrNull() != null
                val effectiveDeckId = if (deckExists) {
                    targetDeckId
                } else {
                    val existingFirstDeck = state.availableDecks.firstOrNull()?.deck?.id
                    if (!existingFirstDeck.isNullOrBlank()) {
                        existingFirstDeck
                    } else {
                        val createdDeck = deckRepository.createDeck(
                            name = "Từ vựng hằng ngày",
                            description = "Bộ từ vựng mặc định được tạo tự động khi thêm thẻ nhanh"
                        ).getOrElse { createErr ->
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    errorMessage = createErr.message ?: "Không thể tạo bộ từ vựng mặc định."
                                )
                            }
                            return@launch
                        }
                        createdDeck.id
                    }
                }

                cardRepository.addCard(
                    deckId = effectiveDeckId,
                    word = trimmedWord,
                    meaning = trimmedMeaning,
                    phonetic = trimmedPhonetic,
                    partOfSpeech = state.partOfSpeech,
                    example = trimmedExample,
                    localImagePath = state.localImagePath,
                    allowDuplicateWord = allowDuplicateWord
                ).onSuccess { savedCard ->
                    val shouldResetForContinuousAdd = !closeAfterSave && state.continuousAddMode
                    _uiState.update { current ->
                        if (shouldResetForContinuousAdd) {
                            current.copy(
                                isSaving = false,
                                selectedDeckId = effectiveDeckId,
                                word = "",
                                phonetic = "",
                                partOfSpeech = null,
                                meaning = "",
                                example = "",
                                localImagePath = null,
                                wordError = null,
                                phoneticError = null,
                                meaningError = null,
                                exampleError = null,
                                imageError = null,
                                cardsAddedInSessionCount = current.cardsAddedInSessionCount + 1,
                                feedbackMessage = "Đã thêm thẻ \"${savedCard.word}\". Tiếp tục nhập thẻ mới!"
                            )
                        } else {
                            current.copy(
                                isSaving = false,
                                cardsAddedInSessionCount = current.cardsAddedInSessionCount + 1,
                                shouldCloseScreen = true,
                                feedbackMessage = "Đã thêm thẻ \"${savedCard.word}\"."
                            )
                        }
                    }
                }.onFailure { error ->
                    val message = error.message.orEmpty()
                    if (!allowDuplicateWord && (message.contains("đã có", ignoreCase = true) ||
                            message.contains("trùng", ignoreCase = true) ||
                            message.contains("duplicate", ignoreCase = true))
                    ) {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                isDuplicateWordDialogVisible = true,
                                duplicateWordCandidate = trimmedWord,
                                pendingCloseAfterDuplicateConfirm = closeAfterSave
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                errorMessage = message.ifBlank { "Không thể lưu thẻ từ vựng." }
                            )
                        }
                    }
                }
            }
        }
    }
}
