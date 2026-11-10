package com.uth.memora.core.model

/**
 * Thẻ từ vựng (Flashcard - Mục 5, Mục 7.1 & Mục 11).
 *
 * Quy định:
 * - word: 1–60 ký tự
 * - meaning: 1–300 ký tự
 * - example: tối đa 300 ký tự (không bắt buộc)
 * - phonetic: tối đa 60 ký tự (không bắt buộc)
 * - easeFactor: khởi tạo 2.5, tối thiểu 1.3
 */
data class Card(
    val id: String,
    val deckId: String,
    val ownerId: String = Deck.GUEST_OWNER_ID,
    val word: String,
    val meaning: String,
    val phonetic: String = "",
    val partOfSpeech: PartOfSpeech? = null,
    val example: String = "",
    val localImagePath: String? = null,
    val cloudImageUrl: String? = null,

    // Thông tin lịch ôn SM-2 (Mục 7.1)
    val state: CardState = CardState.NEW,
    val easeFactor: Double = INITIAL_EASE_FACTOR,
    val repetitions: Int = 0,
    val interval: Int = 0,
    val lapses: Int = 0,
    val dueDay: Long? = null,
    val dueAtMillis: Long? = null,
    val firstSeenDay: Long? = null,
    val lastReviewedAtMillis: Long? = null,

    // Metadata đồng bộ & xóa mềm
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
) {
    /**
     * Cấp độ ghi nhớ (Cấp 0 đến Cấp 5 - Mục 7.5), tính tự động từ state và interval.
     */
    val retentionStage: RetentionStage
        get() = RetentionStage.fromCard(state, interval)

    /**
     * Kiểm tra thẻ có đến hạn ôn trong ngày học hiện tại hay không (Mục 7.6).
     */
    fun isDue(currentLearningDay: Long, nowMillis: Long): Boolean {
        if (isDeleted) return false
        return when (state) {
            CardState.NEW -> false
            CardState.LEARNING -> (dueAtMillis ?: 0L) <= nowMillis
            CardState.REVIEW -> (dueDay ?: Long.MAX_VALUE) <= currentLearningDay
        }
    }

    companion object {
        const val INITIAL_EASE_FACTOR = 2.5
        const val MIN_EASE_FACTOR = 1.3
        const val MAX_WORD_LENGTH = 60
        const val MAX_MEANING_LENGTH = 300
        const val MAX_EXAMPLE_LENGTH = 300
        const val MAX_PHONETIC_LENGTH = 60
    }
}

/**
 * Bản chụp thông tin lịch ôn của thẻ trước khi đánh giá, dùng để Hoàn tác (Undo - Mục 9.1).
 */
data class CardScheduleSnapshot(
    val state: CardState,
    val easeFactor: Double,
    val repetitions: Int,
    val interval: Int,
    val lapses: Int,
    val dueDay: Long?,
    val dueAtMillis: Long?,
    val firstSeenDay: Long?,
    val lastReviewedAtMillis: Long?
)
