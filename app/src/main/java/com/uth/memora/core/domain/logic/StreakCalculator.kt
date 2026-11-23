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
 * Trạng thái hiển thị chuỗi ngày học trên Trang chính / Phân tích trước khi người dùng học hôm nay (Mục 10.2).
 */
data class StreakDisplayState(
    val displayedStreak: Int,
    val isFreezeAvailable: Boolean,
    val isProtectedToday: Boolean,
    val nextFreezeAvailableInDays: Int
)

/**
 * Bộ tính toán chuỗi ngày học liên tiếp (Streak) & Khiên bảo vệ chuỗi (Streak Freeze - Mục 10.2 & Mục 11).
 *
 * Quy tắc nghiệp vụ:
 * - Ngày học tính từ 4:00 sáng đến 3:59 sáng hôm sau (LearningDayCalculator).
 * - Học cùng ngày (dayDiff <= 0): giữ nguyên chuỗi.
 * - Học ngày kế tiếp (dayDiff == 1): chuỗi + 1.
 * - Lỡ đúng 1 ngày (dayDiff == 2):
 *   + Nếu Khiên khả dụng: Khiên tự kích hoạt bảo vệ ngày vừa lỡ (không reset chuỗi), cộng +1 cho hôm nay,
 *     đánh dấu đã dùng khiên vào ngày vừa lỡ (`todayLearningDay - 1`).
 *   + Nếu không có Khiên: chuỗi bị đứt, bắt đầu lại từ 1.
 * - Lỡ từ 2 ngày liên tiếp trở lên (dayDiff >= 3): Khiên KHÔNG bảo vệ, reset chuỗi về 1 (không tiêu thụ khiên).
 * - Chu kỳ hồi phục Khiên: tối đa 1 khiên trong mỗi chu kỳ 30 ngày kể từ lần dùng gần nhất.
 */
object StreakCalculator {

    const val FREEZE_RECOVERY_DAYS: Long = 30L

    /**
     * Kiểm tra Khiên bảo vệ chuỗi có đang khả dụng tại [todayLearningDay] hay không (Mục 10.2 & 11).
     */
    fun isFreezeAvailableOn(
        todayLearningDay: Long,
        isFreezeAvailable: Boolean,
        lastFreezeUsedDay: Long?
    ): Boolean {
        return if (lastFreezeUsedDay == null) {
            isFreezeAvailable
        } else {
            (todayLearningDay - lastFreezeUsedDay) >= FREEZE_RECOVERY_DAYS
        }
    }

    /**
     * Số ngày học còn lại cho tới khi Khiên hồi phục (0 nếu Khiên đã sẵn sàng).
     */
    fun daysUntilNextFreeze(
        todayLearningDay: Long,
        isFreezeAvailable: Boolean,
        lastFreezeUsedDay: Long?
    ): Int {
        if (isFreezeAvailableOn(todayLearningDay, isFreezeAvailable, lastFreezeUsedDay)) {
            return 0
        }
        if (lastFreezeUsedDay == null) {
            return FREEZE_RECOVERY_DAYS.toInt()
        }
        val elapsed = todayLearningDay - lastFreezeUsedDay
        return (FREEZE_RECOVERY_DAYS - elapsed).toInt().coerceAtLeast(1)
    }

    /**
     * Tính chuỗi ngày học mới khi người học hoàn thành lượt đánh giá đầu tiên trong [todayLearningDay].
     */
    fun calculateNextStreak(
        currentStreak: Int,
        lastStudyDay: Long?,
        todayLearningDay: Long,
        isFreezeAvailable: Boolean = true,
        lastFreezeUsedDay: Long? = null
    ): StreakUpdateResult {
        val effectiveFreezeAvailable = isFreezeAvailableOn(
            todayLearningDay = todayLearningDay,
            isFreezeAvailable = isFreezeAvailable,
            lastFreezeUsedDay = lastFreezeUsedDay
        )
        val remainingRecoveryDays = daysUntilNextFreeze(
            todayLearningDay = todayLearningDay,
            isFreezeAvailable = isFreezeAvailable,
            lastFreezeUsedDay = lastFreezeUsedDay
        )

        if (lastStudyDay == null || currentStreak <= 0) {
            return StreakUpdateResult(
                newStreak = 1,
                isFreezeUsed = false,
                isFreezeAvailable = effectiveFreezeAvailable,
                lastStudyLearningDay = todayLearningDay,
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )
        }

        val dayDiff = todayLearningDay - lastStudyDay
        return when {
            dayDiff <= 0L -> StreakUpdateResult(
                newStreak = currentStreak,
                isFreezeUsed = false,
                isFreezeAvailable = effectiveFreezeAvailable,
                lastStudyLearningDay = maxOf(lastStudyDay, todayLearningDay),
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )

            dayDiff == 1L -> StreakUpdateResult(
                newStreak = currentStreak + 1,
                isFreezeUsed = false,
                isFreezeAvailable = effectiveFreezeAvailable,
                lastStudyLearningDay = todayLearningDay,
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )

            dayDiff == 2L && effectiveFreezeAvailable -> {
                val missedDay = todayLearningDay - 1L
                StreakUpdateResult(
                    newStreak = currentStreak + 1,
                    isFreezeUsed = true,
                    isFreezeAvailable = false,
                    lastStudyLearningDay = todayLearningDay,
                    lastFreezeUsedDay = missedDay,
                    nextFreezeAvailableInDays = daysUntilNextFreeze(
                        todayLearningDay = todayLearningDay,
                        isFreezeAvailable = false,
                        lastFreezeUsedDay = missedDay
                    )
                )
            }

            else -> StreakUpdateResult(
                newStreak = 1,
                isFreezeUsed = false,
                isFreezeAvailable = effectiveFreezeAvailable,
                lastStudyLearningDay = todayLearningDay,
                lastFreezeUsedDay = lastFreezeUsedDay,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )
        }
    }

    /**
     * Tính trạng thái hiển thị chuỗi ngày học hiện tại (chỉ đọc, chưa ghi nhận lượt học mới hôm nay).
     */
    fun evaluateDisplayStreak(
        currentStreak: Int,
        lastStudyDay: Long?,
        todayLearningDay: Long,
        isFreezeAvailable: Boolean = true,
        lastFreezeUsedDay: Long? = null
    ): StreakDisplayState {
        val effectiveFreezeAvailable = isFreezeAvailableOn(
            todayLearningDay = todayLearningDay,
            isFreezeAvailable = isFreezeAvailable,
            lastFreezeUsedDay = lastFreezeUsedDay
        )
        val remainingRecoveryDays = daysUntilNextFreeze(
            todayLearningDay = todayLearningDay,
            isFreezeAvailable = isFreezeAvailable,
            lastFreezeUsedDay = lastFreezeUsedDay
        )

        if (lastStudyDay == null || currentStreak <= 0) {
            return StreakDisplayState(
                displayedStreak = 0,
                isFreezeAvailable = effectiveFreezeAvailable,
                isProtectedToday = false,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )
        }

        val dayDiff = todayLearningDay - lastStudyDay
        return when {
            dayDiff <= 1L -> StreakDisplayState(
                displayedStreak = currentStreak,
                isFreezeAvailable = effectiveFreezeAvailable,
                isProtectedToday = false,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )

            dayDiff == 2L && effectiveFreezeAvailable -> StreakDisplayState(
                displayedStreak = currentStreak,
                isFreezeAvailable = false,
                isProtectedToday = true,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )

            else -> StreakDisplayState(
                displayedStreak = 0,
                isFreezeAvailable = effectiveFreezeAvailable,
                isProtectedToday = false,
                nextFreezeAvailableInDays = remainingRecoveryDays
            )
        }
    }
}
