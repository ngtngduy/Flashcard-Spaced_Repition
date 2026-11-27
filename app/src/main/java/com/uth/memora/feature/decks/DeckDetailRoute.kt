package com.uth.memora.feature.decks

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun DeckDetailRoute(
    deckId: String,
    onStartStudy: (String) -> Unit,
    onAddCard: (String) -> Unit,
    onEditCard: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Chi tiết Bộ từ (Deck Detail)",
        owner = "Người 2 – UI & Luồng học",
        description = "Hiển thị danh sách thẻ trong bộ $deckId, tìm kiếm không dấu và thao tác thẻ.",
        modifier = modifier
    )
}
