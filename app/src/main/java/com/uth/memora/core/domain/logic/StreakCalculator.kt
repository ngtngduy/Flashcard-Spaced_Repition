package com.uth.memora.core.domain.logic

/**
 * Kết quả tính toán cập nhật chuỗi ngày học (Streak) sau một lượt học hợp lệ (Mục 10.2 & Mục 11).
 */
data class StreakUpdateResult(
    val newStreak: Int,
    val isFreezeUsed: Boolean,
    val isFreezeAvailable: Boolean,
    val lastStudyLearningDay: Long,
    val lastFreezeUsedDay: Long?,
    val nextFreezeAvailableInDays: Int
)

/**
 * Bộ tính toán chuỗi ngày học liên tiếp (Streak) theo ngày học tính từ 4:00 sáng (Mục 10.2 & Mục 11).
 */
object StreakCalculator {

    const val FREEZE_RECOVERY_DAYS: Long = 30L

    /**
     * Tính chuỗi ngày học mới khi người học hoàn thành ít nhất 1 lượt đánh giá trong [todayLearningDay].
     *
     * Quy tắc cơ bản:
     * - Lần học đầu tiên (hoặc streak <= 0): bắt đầu chuỗi = 1.
     * - Học nhiều lần trong cùng ngày học (dayDiff <= 0): giữ nguyên chuỗi hiện tại.
     * - Học ngày học kế tiếp (dayDiff == 1): tăng chuỗi +1.
     * - Gián đoạn từ 1 ngày trở lên (dayDiff >= 2): reset chuỗi về 1.
     */
    fun calculateNextStreak(
        currentStreak: Int,
        lastStudyDay: Long?,
        todayLearningDay: Long,
        isFreezeAvailable: Boolean = true,
        lastFreezeUsedDay: Long? = null
    ): StreakUpdateResult {
        if (lastStudyDay == null || currentStreak <= 0) {
            return StreakUpdateResult(
                newStreak = 1,
                isFreezeUsed = false,
                isFreezeAvailable = isFreezeAvailable,
                lastStudyLearningDay = todayLearningDay,
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = 0
            )
        }

        val dayDiff = todayLearningDay - lastStudyDay
        return when {
            dayDiff <= 0L -> StreakUpdateResult(
                newStreak = currentStreak,
                isFreezeUsed = false,
                isFreezeAvailable = isFreezeAvailable,
                lastStudyLearningDay = maxOf(lastStudyDay, todayLearningDay),
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = 0
            )

            dayDiff == 1L -> StreakUpdateResult(
                newStreak = currentStreak + 1,
                isFreezeUsed = false,
                isFreezeAvailable = isFreezeAvailable,
                lastStudyLearningDay = todayLearningDay,
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = 0
            )

            else -> StreakUpdateResult(
                newStreak = 1,
                isFreezeUsed = false,
                isFreezeAvailable = isFreezeAvailable,
                lastStudyLearningDay = todayLearningDay,
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = 0
            )
        }
    }
}
