package com.uth.memora.feature.analytics

import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.model.StudyStatistics

/**
 * Ba trạng thái trực quan của Khiên bảo vệ chuỗi (Streak Freeze - Figma 2235:67 & Mục 10.2).
 */
enum class StreakFreezeStatusUi {
    /** Khiên đang sẵn sàng tự động bảo vệ nếu người học lỡ đúng 1 ngày */
    READY,

    /** Khiên đã tự động kích hoạt bảo vệ chuỗi ngày học trong ngày hôm nay */
    PROTECTED_TODAY,

    /** Khiên đã được sử dụng và đang trong chu kỳ hồi phục 30 ngày */
    RECOVERING
}

/**
 * Dữ liệu hiển thị cho từng thanh ngang trong biểu đồ phân bố 6 cấp độ ghi nhớ (Figma 10:707 & Mục 7.5).
 */
data class StageChartItem(
    val stage: RetentionStage,
    val count: Int,
    val percentOfTotal: Int,
    val barFraction: Float
)

/**
 * Trạng thái giao diện của màn Phân tích tiến độ học tập (Figma 10:707 & 2235:67).
 */
data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val statistics: StudyStatistics = StudyStatistics(),
    val currentStreakDays: Int = 0,
    val retentionRatePercentText: String = "0%",
    val retentionRateValue: Float = 0f,
    val reviewsLast7DaysCount: Int = 0,
    val masteredCardsCount: Int = 0,
    val totalActiveCardsCount: Int = 0,
    val formattedStudyTime: String = "0 phút",
    val newCardsLearnedToday: Int = 0,
    val dailyNewCardLimit: Int = 20,
    val reviewsToday: Int = 0,
    val dueCardsCount: Int = 0,
    val streakFreezeStatus: StreakFreezeStatusUi = StreakFreezeStatusUi.READY,
    val streakFreezeAvailable: Boolean = true,
    val isStreakProtectedToday: Boolean = false,
    val nextFreezeAvailableInDays: Int = 0,
    val stageItems: List<StageChartItem> = RetentionStage.entries.map { stage ->
        StageChartItem(
            stage = stage,
            count = 0,
            percentOfTotal = 0,
            barFraction = 0f
        )
    },
    val errorMessage: String? = null
) {
    /**
     * Tỷ lệ thẻ thành thạo trên tổng số thẻ đang hoạt động (0f..1f, an toàn khi chưa có thẻ).
     */
    val masteredFraction: Float
        get() = if (totalActiveCardsCount <= 0) {
            0f
        } else {
            (masteredCardsCount.toFloat() / totalActiveCardsCount.toFloat()).coerceIn(0f, 1f)
        }
}
