package com.uth.memora.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.SyncStatus

/**
 * Bảng lưu trữ thẻ từ vựng (Flashcard) trong Room Database (Mục 5, Mục 7.1, Mục 7.6 & Mục 10.1).
 *
 * Khai báo 3 chỉ mục (deckId), (ownerId, dueDay), (ownerId, state) giúp truy vấn hàng đợi
 * thẻ mới và thẻ đến hạn ôn (LEARNING / REVIEW) nhanh ngay cả khi bộ từ đạt tối đa 2000 thẻ.
 */
@Entity(
    tableName = "cards",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deckId"]),
        Index(value = ["ownerId", "dueDay"]),
        Index(value = ["ownerId", "state"])
    ]
)
data class CardEntity(
    @PrimaryKey
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

    // Thông tin lịch ôn thuật toán SM-2 (Mục 7.1)
    val state: CardState = CardState.NEW,
    val easeFactor: Double = Card.INITIAL_EASE_FACTOR,
    val repetitions: Int = 0,
    val interval: Int = 0,
    val lapses: Int = 0,
    val dueDay: Long? = null,
    val dueAtMillis: Long? = null,
    val firstSeenDay: Long? = null,
    val lastReviewedAtMillis: Long? = null,

    // Metadata đồng bộ & xóa mềm (Mục 10.1)
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)
