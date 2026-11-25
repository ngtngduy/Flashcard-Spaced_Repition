package com.uth.memora.feature.cardeditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.memoraViewModelFactory

/**
 * Điểm vào điều hướng của màn Thêm / Sửa Flashcard (Figma 2101:9).
 * Kết nối [CardEditorViewModel] thông qua [memoraViewModelFactory] với [CardEditorScreen].
 */
@Composable
fun CardEditorRoute(
    deckId: String,
    cardId: String?,
    onCardSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: CardEditorViewModel = viewModel(
        key = "card_editor_${deckId}_${cardId ?: "new"}",
        factory = memoraViewModelFactory { container ->
            CardEditorViewModel(
                initialDeckId = deckId,
                cardId = cardId,
                deckRepository = container.deckRepository,
                cardRepository = container.cardRepository,
                userPreferencesRepository = container.userPreferencesRepository,
                aiCardAutoFillService = container.aiCardAutoFillService
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.shouldCloseScreen) {
        if (uiState.shouldCloseScreen) {
            viewModel.onScreenClosed()
            onCardSaved()
        }
    }

    CardEditorScreen(
        uiState = uiState,
        onSelectDeck = viewModel::onSelectDeck,
        onWordChange = viewModel::onWordChange,
        onPhoneticChange = viewModel::onPhoneticChange,
        onPartOfSpeechChange = viewModel::onPartOfSpeechChange,
        onMeaningChange = viewModel::onMeaningChange,
        onExampleChange = viewModel::onExampleChange,
        onAutoFillClick = viewModel::autoFillWithAi,
        onToggleContinuousAddMode = viewModel::onToggleContinuousAddMode,
        onSaveClick = { viewModel.saveCard(closeAfterSave = false) },
        onBack = onBack,
        modifier = modifier
    )
}
