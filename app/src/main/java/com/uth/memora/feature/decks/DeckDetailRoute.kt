package com.uth.memora.feature.decks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.core.model.StudyMode
import com.uth.memora.memoraViewModelFactory

/**
 * Điểm vào điều hướng của màn Chi tiết Bộ từ (Deck Detail - Figma 2101:11).
 * Kết nối [DeckDetailViewModel] thông qua [memoraViewModelFactory] với [DeckDetailScreen].
 */
@Composable
fun DeckDetailRoute(
    deckId: String,
    onStartStudy: (String, StudyMode) -> Unit,
    onAddCard: (String) -> Unit,
    onEditCard: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DeckDetailViewModel = viewModel(
        key = "deck_detail_$deckId",
        factory = memoraViewModelFactory { container ->
            DeckDetailViewModel(
                deckId = deckId,
                deckRepository = container.deckRepository,
                cardRepository = container.cardRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    DeckDetailScreen(
        uiState = uiState,
        onBack = onBack,
        onStartStudy = {
            val targetMode = if (uiState.dueCardsCount == 0 && uiState.newCardsCount > 0) {
                StudyMode.NEW_CARDS
            } else {
                StudyMode.DUE_REVIEW
            }
            onStartStudy(deckId, targetMode)
        },
        onAddCard = { onAddCard(deckId) },
        onEditCard = { card -> onEditCard(deckId, card.id) },
        onRequestDeleteCard = viewModel::requestDeleteCard,
        onConfirmDeleteCard = viewModel::confirmDeleteCard,
        onDismissDeleteCardDialog = viewModel::dismissDeleteCardDialog,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onClearSearch = viewModel::clearSearch,
        onStageFilterSelected = viewModel::onStageFilterSelected,
        onRequestResetCardProgress = viewModel::requestResetCardProgress,
        onConfirmResetCardProgress = viewModel::confirmResetCardProgress,
        onDismissResetCardDialog = viewModel::dismissResetCardDialog,
        onRequestResetDeckProgress = viewModel::requestResetDeckProgress,
        onConfirmResetDeckProgress = viewModel::confirmResetDeckProgress,
        onDismissResetDeckDialog = viewModel::dismissResetDeckDialog,
        onRetryLoad = viewModel::loadDeckAndObserveCards,
        modifier = modifier
    )
}
