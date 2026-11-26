package com.uth.memora.feature.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.memoraViewModelFactory

/**
 * Điểm vào điều hướng cho tab Phân tích tiến độ học tập (Figma Node 10:707 & 2235:67 · Mục 6.9 & 10.2).
 *
 * Giữ nguyên chữ ký hàm `AnalyticsRoute(modifier: Modifier = Modifier)` để tương thích hoàn toàn với `MemoraNavHost`.
 */
@Composable
fun AnalyticsRoute(
    modifier: Modifier = Modifier
) {
    val viewModel: AnalyticsViewModel = viewModel(
        factory = memoraViewModelFactory { container ->
            AnalyticsViewModel(
                statisticsRepository = container.statisticsRepository
            )
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnalyticsScreen(
        uiState = uiState,
        onRefresh = viewModel::observeStatistics,
        modifier = modifier
    )
}
