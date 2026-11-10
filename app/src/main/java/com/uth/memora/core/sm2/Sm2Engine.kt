package com.uth.memora.core.sm2

import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardScheduleSnapshot
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SyncStatus
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bộ máy tính lịch ôn lặp lại ngắt quãng theo thuật toán SM-2 (Mục 7.1 – 7.6).
 *
 * Thuần Kotlin, không phụ thuộc Android framework để dễ dàng chạy Unit Test 100% và dùng chung.
 */
object Sm2Engine {

    const val STEP_AGAIN_MINUTES = 1L
    const val STEP_HARD_MINUTES = 10L
    private const val MILLIS_PER_MINUTE = 60_000L

    data class ReviewComputationResult(
        val updatedCard: Card,
        val snapshotBeforeReview: CardScheduleSnapshot,
        val shouldRequeueInSameSession: Boolean,
        val nextDuePreviewLabelVi: String
    )

    /**
     * Tạo bản chụp lịch của thẻ trước khi cập nhật để phục vụ tính năng Hoàn tác (Undo - Mục 9.1).
     */
    fun createSnapshot(card: Card): CardScheduleSnapshot {
        return CardScheduleSnapshot(
            state = card.state,
            easeFactor = card.easeFactor,
            repetitions = card.repetitions,
            interval = card.interval,
            lapses = card.lapses,
            dueDay = card.dueDay,
            dueAtMillis = card.dueAtMillis,
            firstSeenDay = card.firstSeenDay,
            lastReviewedAtMillis = card.lastReviewedAtMillis
        )
    }

    /**
     * Khôi phục thẻ từ bản chụp khi người học bấm nút Hoàn tác (Mục 9.1).
     * Lưu ý: Hạn mức thẻ mới giữ nguyên vì thẻ đã từng được hiển thị, nên nếu snapshot.firstSeenDay == null
     * mà thẻ đã hiển thị hôm nay thì vẫn giữ firstSeenDay hiện tại.
     */
    fun restoreFromSnapshot(
        card: Card,
        snapshot: CardScheduleSnapshot,
        nowMillis: Long = System.currentTimeMillis()
    ): Card {
        return card.copy(
            state = snapshot.state,
            easeFactor = snapshot.easeFactor,
            repetitions = snapshot.repetitions,
            interval = snapshot.interval,
            lapses = snapshot.lapses,
            dueDay = snapshot.dueDay,
            dueAtMillis = snapshot.dueAtMillis,
            firstSeenDay = snapshot.firstSeenDay ?: card.firstSeenDay,
            lastReviewedAtMillis = snapshot.lastReviewedAtMillis,
            updatedAtMillis = nowMillis,
            syncStatus = SyncStatus.PENDING_SYNC
        )
    }

    /**
     * Đánh dấu một Thẻ mới ngay khi lần đầu tiên được hiển thị trong phiên học (Mục 6.5):
     * - Ghi nhận `firstSeenDay` để tính vào hạn mức thẻ mới trong ngày.
     * - Chuyển sang `LEARNING` với `dueAtMillis = nowMillis` để nếu người học thoát ứng dụng giữa chừng
     *   khi chưa kịp bấm nút, thẻ vẫn là Thẻ đang học và đến hạn ở lần mở sau.
     */
    fun markCardDisplayedFirstTime(
        card: Card,
        currentLearningDay: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Card {
        if (card.state != CardState.NEW) return card
        return card.copy(
            state = CardState.LEARNING,
            firstSeenDay = card.firstSeenDay ?: currentLearningDay,
            dueAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            syncStatus = SyncStatus.PENDING_SYNC
        )
    }

    /**
     * Công thức tính Hệ số dễ mới (Ease Factor - Mục 7.2):
     * EF' = max(1.3, EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)))
     */
    fun calculateNewEaseFactor(currentEf: Double, quality: ReviewQuality): Double {
        val diff = 5 - quality.q
        val delta = 0.1 - diff * (0.08 + diff * 0.02)
        val rawEf = currentEf + delta
        val roundedEf = (rawEf * 100.0).roundToInt() / 100.0
        return max(Card.MIN_EASE_FACTOR, roundedEf)
    }

    /**
     * Xử lý đánh giá trong luồng "Học từ mới" (2 lựa chọn: Đã nhớ / Học lại - Mục 6.5).
     * @param remembered true nếu bấm "Đã nhớ", false nếu bấm "Học lại".
     */
    fun evaluateNewCardStudy(
        card: Card,
        remembered: Boolean,
        currentLearningDay: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): ReviewComputationResult {
        val quality = if (remembered) ReviewQuality.GOOD else ReviewQuality.AGAIN
        return evaluateReview(card, quality, currentLearningDay, nowMillis)
    }

    /**
     * Xử lý đánh giá thẻ với 4 nút (Chưa nhớ, Khó, Nhớ tốt, Dễ - Mục 7.3 & 7.4).
     */
    fun evaluateReview(
        card: Card,
        quality: ReviewQuality,
        currentLearningDay: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): ReviewComputationResult {
        val snapshot = createSnapshot(card)
        val effectiveFirstSeenDay = card.firstSeenDay ?: currentLearningDay

        return when (card.state) {
            CardState.NEW, CardState.LEARNING -> evaluateLearningOrNewCard(
                card = card,
                snapshot = snapshot,
                quality = quality,
                firstSeenDay = effectiveFirstSeenDay,
                currentLearningDay = currentLearningDay,
                nowMillis = nowMillis
            )

            CardState.REVIEW -> evaluateGraduatedReviewCard(
                card = card,
                snapshot = snapshot,
                quality = quality,
                firstSeenDay = effectiveFirstSeenDay,
                currentLearningDay = currentLearningDay,
                nowMillis = nowMillis
            )
        }
    }

    /**
     * Quy tắc cho Thẻ mới và Thẻ đang học (bước tính bằng phút - Mục 7.3):
     * - Chưa nhớ (q=1): Giữ LEARNING, hiển thị lại sau 1 phút
     * - Khó (q=3): Giữ LEARNING, hiển thị lại sau 10 phút
     * - Nhớ tốt (q=4): Thành REVIEW, interval = 1 ngày, repetitions = 1
     * - Dễ (q=5): Thành REVIEW, interval = 4 ngày, repetitions = 2
     * Hệ số dễ (EF) không đổi khi thẻ còn ở trạng thái LEARNING.
     */
    private fun evaluateLearningOrNewCard(
        card: Card,
        snapshot: CardScheduleSnapshot,
        quality: ReviewQuality,
        firstSeenDay: Long,
        currentLearningDay: Long,
        nowMillis: Long
    ): ReviewComputationResult {
        return when (quality) {
            ReviewQuality.AGAIN -> {
                val updated = card.copy(
                    state = CardState.LEARNING,
                    interval = 0,
                    dueAtMillis = nowMillis + STEP_AGAIN_MINUTES * MILLIS_PER_MINUTE,
                    dueDay = null,
                    firstSeenDay = firstSeenDay,
                    lastReviewedAtMillis = nowMillis,
                    updatedAtMillis = nowMillis,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                ReviewComputationResult(
                    updatedCard = updated,
                    snapshotBeforeReview = snapshot,
                    shouldRequeueInSameSession = true,
                    nextDuePreviewLabelVi = "1 phút"
                )
            }

            ReviewQuality.HARD -> {
                val updated = card.copy(
                    state = CardState.LEARNING,
                    interval = 0,
                    dueAtMillis = nowMillis + STEP_HARD_MINUTES * MILLIS_PER_MINUTE,
                    dueDay = null,
                    firstSeenDay = firstSeenDay,
                    lastReviewedAtMillis = nowMillis,
                    updatedAtMillis = nowMillis,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                ReviewComputationResult(
                    updatedCard = updated,
                    snapshotBeforeReview = snapshot,
                    shouldRequeueInSameSession = true,
                    nextDuePreviewLabelVi = "10 phút"
                )
            }

            ReviewQuality.GOOD -> {
                val newInterval = 1
                val updated = card.copy(
                    state = CardState.REVIEW,
                    interval = newInterval,
                    repetitions = 1,
                    dueDay = currentLearningDay + newInterval,
                    dueAtMillis = null,
                    firstSeenDay = firstSeenDay,
                    lastReviewedAtMillis = nowMillis,
                    updatedAtMillis = nowMillis,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                ReviewComputationResult(
                    updatedCard = updated,
                    snapshotBeforeReview = snapshot,
                    shouldRequeueInSameSession = false,
                    nextDuePreviewLabelVi = "1 ngày"
                )
            }

            ReviewQuality.EASY -> {
                val newInterval = 4
                val updated = card.copy(
                    state = CardState.REVIEW,
                    interval = newInterval,
                    repetitions = 2,
                    dueDay = currentLearningDay + newInterval,
                    dueAtMillis = null,
                    firstSeenDay = firstSeenDay,
                    lastReviewedAtMillis = nowMillis,
                    updatedAtMillis = nowMillis,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                ReviewComputationResult(
                    updatedCard = updated,
                    snapshotBeforeReview = snapshot,
                    shouldRequeueInSameSession = false,
                    nextDuePreviewLabelVi = "4 ngày"
                )
            }
        }
    }

    /**
     * Quy tắc cho Thẻ đã học (REVIEW - Mục 7.4):
     * - Chưa nhớ (q=1): lapses + 1, EF giảm theo công thức, repetitions = 0, interval = 0, về LEARNING (1 phút).
     * - Khó, Nhớ tốt, Dễ (q=3,4,5):
     *   + Tính EF mới trước.
     *   + Nếu repetitions hiện tại == 1: interval mới = 6 ngày (hoặc tính cả số ngày quá hạn nếu ôn trễ).
     *   + Nếu repetitions >= 2: interval mới = round(interval_hiệu_dụng * EF_mới).
     *   + Khoảng cách mới luôn lớn hơn khoảng cách cũ ít nhất 1 ngày.
     */
    private fun evaluateGraduatedReviewCard(
        card: Card,
        snapshot: CardScheduleSnapshot,
        quality: ReviewQuality,
        firstSeenDay: Long,
        currentLearningDay: Long,
        nowMillis: Long
    ): ReviewComputationResult {
        val newEf = calculateNewEaseFactor(card.easeFactor, quality)

        if (quality == ReviewQuality.AGAIN) {
            val updated = card.copy(
                state = CardState.LEARNING,
                easeFactor = newEf,
                repetitions = 0,
                interval = 0,
                lapses = card.lapses + 1,
                dueAtMillis = nowMillis + STEP_AGAIN_MINUTES * MILLIS_PER_MINUTE,
                dueDay = null,
                firstSeenDay = firstSeenDay,
                lastReviewedAtMillis = nowMillis,
                updatedAtMillis = nowMillis,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            return ReviewComputationResult(
                updatedCard = updated,
                snapshotBeforeReview = snapshot,
                shouldRequeueInSameSession = true,
                nextDuePreviewLabelVi = "1 phút"
            )
        }

        // Hỗ trợ trường hợp Ôn trễ hạn (Overdue Review - Mục 7.4)
        val overdueDays = if (card.dueDay != null && currentLearningDay > card.dueDay) {
            (currentLearningDay - card.dueDay).toInt()
        } else {
            0
        }
        val effectiveBaseInterval = max(1, card.interval + overdueDays)

        val rawNextInterval = if (card.repetitions == 1) {
            if (overdueDays == 0) 6 else max(6, (effectiveBaseInterval * newEf).roundToInt())
        } else {
            (effectiveBaseInterval * newEf).roundToInt()
        }

        val finalInterval = max(card.interval + 1, rawNextInterval)
        val updated = card.copy(
            state = CardState.REVIEW,
            easeFactor = newEf,
            repetitions = card.repetitions + 1,
            interval = finalInterval,
            dueDay = currentLearningDay + finalInterval,
            dueAtMillis = null,
            firstSeenDay = firstSeenDay,
            lastReviewedAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        return ReviewComputationResult(
            updatedCard = updated,
            snapshotBeforeReview = snapshot,
            shouldRequeueInSameSession = false,
            nextDuePreviewLabelVi = "$finalInterval ngày"
        )
    }

    /**
     * Tính nhãn thời gian dự kiến hiển thị trên 4 nút (Mục 6.6 & Mục 7.6).
     * Dùng chính quy tắc cập nhật thật để đảm bảo khớp 100%.
     */
    fun previewButtonIntervals(
        card: Card,
        currentLearningDay: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): Map<ReviewQuality, String> {
        return ReviewQuality.entries.associateWith { quality ->
            evaluateReview(card, quality, currentLearningDay, nowMillis).nextDuePreviewLabelVi
        }
    }
}
