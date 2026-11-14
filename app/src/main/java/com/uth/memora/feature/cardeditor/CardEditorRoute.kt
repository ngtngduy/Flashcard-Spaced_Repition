package com.uth.memora.feature.cardeditor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.uth.memora.feature.common.PlaceholderScreen

@Composable
fun CardEditorRoute(
    deckId: String,
    cardId: String?,
    onCardSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = if (cardId == null) "Thêm thẻ mới (Add Flashcard)" else "Chỉnh sửa thẻ (Edit Flashcard)",
        owner = "Người 2 – UI & Luồng học",
        description = "Figma 2101:9 · Form nhập từ vựng, nút Gợi ý điền bằng AI (30/30 lượt/ngày) và chọn ảnh thẻ.",
        modifier = modifier
    )
}
