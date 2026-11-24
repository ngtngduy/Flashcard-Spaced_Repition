package com.uth.memora.feature.decks

import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck

/**
 * Các tab lọc danh sách bộ từ trên màn hình Quản lý Bộ từ (Figma 8:323 & Mục 6.2).
 */
enum class DeckFilterTab(val titleVi: String) {
    ALL("Tất cả"),
    USER("Bộ từ của tôi"),
    PREBUILT("Bộ có sẵn")
}

/**
 * Thông tin tổng hợp của một bộ từ để hiển thị trên danh sách (kèm số lượng thẻ và trạng thái sao chép).
 */
data class DeckSummaryItem(
    val deck: Deck,
    val totalCardsCount: Int = 0,
    val dueCardsCount: Int = 0,
    val newCardsCount: Int = 0,
    val masteredCardsCount: Int = 0,
    val isPrebuilt: Boolean = false,
    val importedUserDeckId: String? = null
) {
    val isAlreadyImported: Boolean
        get() = importedUserDeckId != null
}

/**
 * Kết quả tìm kiếm thẻ từ vựng khớp từ khóa trên màn Quản lý Bộ từ (Mục 6.9).
 */
data class DeckSearchCardMatch(
    val card: Card,
    val deckName: String
)

/**
 * Trạng thái giao diện cho màn Quản lý Bộ từ (Deck List - Figma 8:323).
 */
data class DeckListUiState(
    val isLoading: Boolean = true,
    val selectedTab: DeckFilterTab = DeckFilterTab.USER,
    val searchQuery: String = "",
    val activeDecks: List<Deck> = emptyList(),
    val prebuiltDecks: List<Deck> = emptyList(),
    val userDeckItems: List<DeckSummaryItem> = emptyList(),
    val prebuiltDeckItems: List<DeckSummaryItem> = emptyList(),
    val filteredUserDeckItems: List<DeckSummaryItem> = emptyList(),
    val filteredPrebuiltDeckItems: List<DeckSummaryItem> = emptyList(),
    val matchingCards: List<DeckSearchCardMatch> = emptyList(),
    val isCreateOrEditDialogVisible: Boolean = false,
    val editingDeck: Deck? = null,
    val dialogNameInput: String = "",
    val dialogDescriptionInput: String = "",
    val dialogNameError: String? = null,
    val dialogDescriptionError: String? = null,
    val isSavingDeck: Boolean = false,
    val deletingDeck: Deck? = null,
    val isDeletingDeck: Boolean = false,
    val importingPrebuiltIds: Set<String> = emptySet(),
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
) {
    val totalAllDecksCount: Int
        get() = userDeckItems.size + prebuiltDeckItems.size
}
