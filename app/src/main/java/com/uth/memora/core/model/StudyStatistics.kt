package com.uth.memora.core.model

/**
 * Dữ liệu thống kê tiến độ học tập (Mục 6.9 & Mục 10.2).
 */
data class StudyStatistics(
    /** Số thẻ có ngày học đầu tiên (firstSeenDay) là hôm nay */
    val newCardsLearnedToday: Int = 0,
    /** Hạn mức thẻ mới mỗi ngày (5–50, mặc định 20) */
    val dailyNewCardLimit: Int = 20,
    /** Số lượt đánh giá hợp lệ hôm nay (không tính đã hoàn tác) */
    val reviewsToday: Int = 0,
    /** Số thẻ đến hạn cần ôn hôm nay */
    val dueCardsCount: Int = 0,
    /** Số thẻ mới còn có thể học hôm nay theo hạn mức */
    val remainingNewCardsToday: Int = 0,
    /** Chuỗi ngày học liên tiếp (Streak) */
    val currentStreakDays: Int = 0,
    /** Tổng thời gian học (tính bằng giây, mỗi khoảng tối đa 60s) */
    val totalStudyTimeSeconds: Long = 0L,
    /** Số thẻ đạt Cấp 5 (Thành thạo - interval >= 21 ngày) */
    val masteredCardsCount: Int = 0,
    /** Tổng số thẻ chưa xóa */
    val totalActiveCardsCount: Int = 0,
    /** Phân bố số lượng thẻ theo 6 cấp độ ghi nhớ (Cấp 0 đến Cấp 5) */
    val stageDistribution: Map<RetentionStage, Int> = RetentionStage.entries.associateWith { 0 },
    /** Tỷ lệ nhớ (%) trong 7 ngày gần nhất trên Thẻ đã học */
    val retentionRatePercent: Float = 0f,
    /** Số lượt đánh giá hợp lệ trong 7 ngày gần nhất (Figma 10:707 · 2212:5) */
    val reviewsLast7DaysCount: Int = 0,
    /** Khiên bảo vệ chuỗi ngày học còn khả dụng trong chu kỳ 30 ngày (Mục 10.2 & 11) */
    val streakFreezeAvailable: Boolean = true,
    /** Trạng thái chuỗi ngày học đang được khiên bảo vệ trong ngày hôm nay */
    val isStreakProtectedToday: Boolean = false,
    /** Số ngày còn lại để khiên bảo vệ tự hồi phục (0 nếu đang sẵn sàng) */
    val nextFreezeAvailableInDays: Int = 0
)
