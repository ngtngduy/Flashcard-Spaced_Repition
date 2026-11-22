package com.uth.memora.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.uth.memora.core.model.CardScheduleSnapshot
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SyncStatus

/**
 * Bảng lưu lịch sử từng lượt đánh giá thẻ (append-only - Mục 5, Mục 9.1 & Mục 10.2).
 *
 * Dùng @Embedded(prefix = "snapshot_") để lưu toàn bộ bản chụp trạng thái SM-2 trước đánh giá
 * phục vụ tính năng Hoàn tác (Undo) lượt vừa chấm trong vòng 5 giây.
 */
@Entity(
    tableName = "review_logs",
    indices = [
        Index(value = ["ownerId", "learningDay"]),
        Index(value = ["cardId", "reviewedAtMillis"])
    ]
)
data class ReviewLogEntity(
    @PrimaryKey
    val id: String,
    val cardId: String,
    val deckId: String,
    val ownerId: String = Deck.GUEST_OWNER_ID,
    val quality: ReviewQuality,
    val learningDay: Long,
    val reviewedAtMillis: Long,
    val durationValidSeconds: Int = 5,
    @Embedded(prefix = "snapshot_")
    val snapshotBeforeReview: CardScheduleSnapshot,
    val isUndone: Boolean = false,
    val updatedAtMillis: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)
