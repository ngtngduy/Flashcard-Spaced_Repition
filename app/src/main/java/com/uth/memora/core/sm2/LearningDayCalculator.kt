package com.uth.memora.core.sm2

import java.time.Instant
import java.time.ZoneId

/**
 * Bộ tính toán "Ngày học" (Learning Day - Mục 5, 7.6 & 9.6).
 *
 * Quy tắc:
 * - Một ngày học tính từ 04:00 sáng đến 03:59 sáng hôm sau theo giờ địa phương.
 * - Giúp người học khuya (00:00 - 03:59) không bị đứt chuỗi ngày học (Streak) hoặc lệch lịch ôn.
 * - Nếu giờ máy bị chỉnh lùi trước lần đánh giá gần nhất, lấy mốc lớn hơn và phát cảnh báo.
 */
object LearningDayCalculator {

    private const val DAY_START_HOUR = 4L

    data class TimeCheckResult(
        val effectiveTimeMillis: Long,
        val learningDayEpoch: Long,
        val isClockRolledBack: Boolean
    )

    /**
     * Chuyển đổi một thời điểm (epoch millis) thành số nguyên Ngày học (Epoch Day tính từ mốc 04:00 sáng).
     */
    fun toLearningDay(
        timeMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zonedDateTime = Instant.ofEpochMilli(timeMillis).atZone(zoneId)
        val shifted = zonedDateTime.minusHours(DAY_START_HOUR)
        return shifted.toLocalDate().toEpochDay()
    }

    /**
     * Kiểm tra tính hợp lệ của giờ máy so với thời điểm đánh giá gần nhất (Mục 9.6).
     */
    fun resolveSafeTime(
        deviceNowMillis: Long = System.currentTimeMillis(),
        lastReviewedAtMillis: Long? = null,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TimeCheckResult {
        val isRolledBack = lastReviewedAtMillis != null && deviceNowMillis < lastReviewedAtMillis
        val safeMillis = if (isRolledBack) lastReviewedAtMillis!! else deviceNowMillis
        return TimeCheckResult(
            effectiveTimeMillis = safeMillis,
            learningDayEpoch = toLearningDay(safeMillis, zoneId),
            isClockRolledBack = isRolledBack
        )
    }
}
