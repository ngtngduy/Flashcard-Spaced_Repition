package com.uth.memora.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.core.model.StudyMode
import com.uth.memora.memoraViewModelFactory

/**
 * Điểm vào điều hướng của màn Trang chính (Home Dashboard - Figma 1:2).
 * Kết nối [HomeViewModel] thông qua [memoraViewModelFactory] với [HomeScreen].
 */
@Composable
fun HomeRoute(
    onNavigateToStudy: (String, StudyMode) -> Unit,
    onNavigateToDeckDetail: (String) -> Unit,
    onNavigateToAddCard: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel: HomeViewModel = viewModel(
        factory = memoraViewModelFactory { container ->
            HomeViewModel(
                deckRepository = container.deckRepository,
                statisticsRepository = container.statisticsRepository,
                userPreferencesRepository = container.userPreferencesRepository,
                cardRepository = container.cardRepository,
                syncStatusProvider = container.syncStatusProvider
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    HomeScreen(
        uiState = uiState,
        onNavigateToStudy = onNavigateToStudy,
        onNavigateToDeckDetail = onNavigateToDeckDetail,
        onNavigateToAddCard = onNavigateToAddCard,
        onRefreshSync = viewModel::triggerImmediateSync,
        onRetryLoad = viewModel::observeHomeData,
        modifier = modifier
    )
}
