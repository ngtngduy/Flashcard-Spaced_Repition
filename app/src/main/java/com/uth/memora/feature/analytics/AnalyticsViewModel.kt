package com.uth.memora.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.StatisticsRepository
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.model.StudyStatistics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ViewModel cho màn Phân tích tiến độ học tập (Figma 10:707 & 2235:67 · Mục 6.9 & 10.2).
 */
class AnalyticsViewModel(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState(isLoading = true))
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        observeStatistics()
    }

    /**
     * Quan sát luồng thống kê thời gian thực từ [StatisticsRepository].
     */
    fun observeStatistics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            statisticsRepository
                .observeStatistics()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Không thể tải dữ liệu phân tích học tập."
                        )
                    }
                }
                .collect { stats ->
                    _uiState.value = mapStatisticsToUiState(stats)
                }
        }
    }

    companion object {
        /**
         * Ánh xạ [StudyStatistics] từ tầng miền sang [AnalyticsUiState] phục vụ hiển thị UI.
         */
        fun mapStatisticsToUiState(stats: StudyStatistics): AnalyticsUiState {
            val clampedRetention = stats.retentionRatePercent.coerceIn(0f, 100f)
            val retentionText = if (stats.reviewsLast7DaysCount <= 0 && clampedRetention == 0f) {
                "0%"
            } else {
                "${clampedRetention.roundToInt()}%"
            }

            val totalCards = stats.totalActiveCardsCount.coerceAtLeast(0)
            val maxStageCount = RetentionStage.entries
                .maxOfOrNull { stage -> (stats.stageDistribution[stage] ?: 0).coerceAtLeast(0) }
                ?: 0

            val stageItems = RetentionStage.entries.map { stage ->
                val count = (stats.stageDistribution[stage] ?: 0).coerceAtLeast(0)
                val percent = if (totalCards <= 0) {
                    0
                } else {
                    ((count * 100f) / totalCards.toFloat()).roundToInt().coerceIn(0, 100)
                }
                val barFraction = when {
                    totalCards <= 0 || count <= 0 || maxStageCount <= 0 -> 0f
                    else -> (count.toFloat() / totalCards.toFloat()).coerceIn(0.04f, 1f)
                }
                StageChartItem(
                    stage = stage,
                    count = count,
                    percentOfTotal = percent,
                    barFraction = barFraction
                )
            }

            val freezeStatus = when {
                stats.isStreakProtectedToday -> StreakFreezeStatusUi.PROTECTED_TODAY
                stats.streakFreezeAvailable -> StreakFreezeStatusUi.READY
                else -> StreakFreezeStatusUi.RECOVERING
            }

            return AnalyticsUiState(
                isLoading = false,
                statistics = stats,
                currentStreakDays = stats.currentStreakDays.coerceAtLeast(0),
                retentionRatePercentText = retentionText,
                retentionRateValue = clampedRetention,
                reviewsLast7DaysCount = stats.reviewsLast7DaysCount.coerceAtLeast(0),
                masteredCardsCount = stats.masteredCardsCount.coerceAtLeast(0),
                totalActiveCardsCount = totalCards,
                formattedStudyTime = formatStudyTimeVi(stats.totalStudyTimeSeconds),
                newCardsLearnedToday = stats.newCardsLearnedToday.coerceAtLeast(0),
                dailyNewCardLimit = stats.dailyNewCardLimit.coerceAtLeast(1),
                reviewsToday = stats.reviewsToday.coerceAtLeast(0),
                dueCardsCount = stats.dueCardsCount.coerceAtLeast(0),
                streakFreezeStatus = freezeStatus,
                streakFreezeAvailable = stats.streakFreezeAvailable,
                isStreakProtectedToday = stats.isStreakProtectedToday,
                nextFreezeAvailableInDays = stats.nextFreezeAvailableInDays.coerceAtLeast(0),
                stageItems = stageItems,
                errorMessage = null
            )
        }

        /**
         * Chuyển đổi tổng số giây học hợp lệ sang chuỗi tiếng Việt dễ đọc
         * (VD: "0 phút", "1 phút", "45 phút", "2 giờ 15 phút").
         */
        fun formatStudyTimeVi(totalSeconds: Long): String {
            val safeSeconds = totalSeconds.coerceAtLeast(0L)
            if (safeSeconds == 0L) return "0 phút"
            if (safeSeconds < 60L) return "1 phút"

            val totalMinutes = safeSeconds / 60L
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L

            return when {
                hours <= 0L -> "$totalMinutes phút"
                minutes == 0L -> "$hours giờ"
                else -> "$hours giờ $minutes phút"
            }
        }
    }
}
