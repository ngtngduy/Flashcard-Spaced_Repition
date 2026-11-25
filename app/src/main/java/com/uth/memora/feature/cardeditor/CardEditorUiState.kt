package com.uth.memora.feature.cardeditor

import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.UserPreferences

/**
 * Thông tin một bộ từ hiển thị trong hộp chọn bộ từ trên màn Thêm / Sửa thẻ (Figma 2101:9).
 */
data class CardEditorDeckOption(
    val deck: Deck,
    val cardCount: Int = 0
)

/**
 * Trạng thái giao diện cho màn Thêm / Sửa thẻ từ vựng (Card Editor - Figma 2101:9 & 2227:58, Mục 6.3 & 11).
 */
data class CardEditorUiState(
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val cardId: String? = null,
    val selectedDeckId: String = "",
    val availableDecks: List<CardEditorDeckOption> = emptyList(),
    val word: String = "",
    val phonetic: String = "",
    val partOfSpeech: PartOfSpeech? = null,
    val meaning: String = "",
    val example: String = "",
    val localImagePath: String? = null,
    val wordError: String? = null,
    val phoneticError: String? = null,
    val meaningError: String? = null,
    val exampleError: String? = null,
    val deckError: String? = null,
    val imageError: String? = null,
    val isAutoFilling: Boolean = false,
    val aiAutoFillRemainingToday: Int = UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT,
    val aiAutoFillMaxDaily: Int = UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT,
    val isSaving: Boolean = false,
    val isDuplicateWordDialogVisible: Boolean = false,
    val duplicateWordCandidate: String = "",
    val pendingCloseAfterDuplicateConfirm: Boolean = false,
    val continuousAddMode: Boolean = true,
    val cardsAddedInSessionCount: Int = 0,
    val shouldCloseScreen: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
) {
    val selectedDeckOption: CardEditorDeckOption?
        get() = availableDecks.firstOrNull { it.deck.id == selectedDeckId }
            ?: availableDecks.firstOrNull()

    val isAiQuotaAvailable: Boolean
        get() = aiAutoFillRemainingToday > 0

    val hasValidationError: Boolean
        get() = wordError != null ||
            phoneticError != null ||
            meaningError != null ||
            exampleError != null ||
            deckError != null ||
            imageError != null

    companion object {
        const val MAX_IMAGE_SIZE_BYTES = 500 * 1024L // 500KB (Figma 2197:23 & Mục 6.3)
    }
}
