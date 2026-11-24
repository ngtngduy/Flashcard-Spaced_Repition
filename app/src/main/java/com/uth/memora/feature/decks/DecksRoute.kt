package com.uth.memora.feature.decks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.memoraViewModelFactory

/**
 * Điểm vào điều hướng của màn Quản lý Bộ từ (Deck List - Figma 8:323).
 * Kết nối [DeckListViewModel] thông qua [memoraViewModelFactory] với [DeckListScreen].
 */
@Composable
fun DecksRoute(
    onDeckClick: (String) -> Unit,
    onCreateDeckClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: DeckListViewModel = viewModel(
        factory = memoraViewModelFactory { container ->
            DeckListViewModel(
                deckRepository = container.deckRepository,
                cardRepository = container.cardRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    DeckListScreen(
        uiState = uiState,
        onSelectTab = viewModel::selectTab,
        onDeckClick = onDeckClick,
        onCreateDeckClick = {
            viewModel.openCreateDeckDialog()
            onCreateDeckClick()
        },
        onEditDeckClick = viewModel::openEditDeckDialog,
        onDeleteDeckClick = viewModel::requestDeleteDeck,
        onImportPrebuiltDeckClick = viewModel::importPrebuiltDeck,
        modifier = modifier
    )
}
