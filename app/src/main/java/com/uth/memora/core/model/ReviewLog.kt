package com.uth.memora.core.model

/**
 * Lịch sử một lượt đánh giá thẻ (Mục 5, Mục 9.1 & Mục 10.2).
 *
 * Chỉ thêm mới (append-only), không sửa hoặc xóa.
 * Riêng cờ isUndone được bật nếu người học bấm Hoàn tác lượt gần nhất.
 */
data class ReviewLog(
    val id: String,
    val cardId: String,
    val deckId: String,
    val ownerId: String = Deck.GUEST_OWNER_ID,
    val quality: ReviewQuality,
    val learningDay: Long,
    val reviewedAtMillis: Long = System.currentTimeMillis(),
    /** Thời gian suy nghĩ của lượt này, tối đa 60 giây để không tính lúc bỏ máy (Mục 10.2) */
    val durationValidSeconds: Int = 5,
    val snapshotBeforeReview: CardScheduleSnapshot,
    val isUndone: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
) {
    companion object {
        const val MAX_SECONDS_PER_REVIEW = 60
    }
}
