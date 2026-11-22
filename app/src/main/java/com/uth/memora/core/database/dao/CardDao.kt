package com.uth.memora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO thao tác trên bảng `cards` trong Room Database (Mục 5, Mục 6.4, Mục 7.6 & Mục 10.1).
 *
 * Mọi truy vấn hiển thị và hàng đợi học luôn lọc `isDeleted = 0`.
 */
@Dao
interface CardDao {

    @Query(
        """
        SELECT * FROM cards
        WHERE deckId = :deckId AND isDeleted = 0
        ORDER BY createdAtMillis DESC
        """
    )
    fun observeCardsByDeck(deckId: String): Flow<List<CardEntity>>

    @Query(
        """
        SELECT * FROM cards
        WHERE ownerId = :ownerId AND isDeleted = 0
        ORDER BY updatedAtMillis DESC
        """
    )
    fun observeAllActiveCards(ownerId: String): Flow<List<CardEntity>>

    @Query(
        """
        SELECT * FROM cards
        WHERE ownerId = :ownerId AND isDeleted = 0
        ORDER BY updatedAtMillis DESC
        """
    )
    suspend fun getAllActiveCards(ownerId: String): List<CardEntity>

    @Query(
        """
        SELECT * FROM cards
        WHERE id = :cardId AND isDeleted = 0
        LIMIT 1
        """
    )
    suspend fun getCardById(cardId: String): CardEntity?

    @Query(
        """
        SELECT * FROM cards
        WHERE id = :cardId
        LIMIT 1
        """
    )
    suspend fun getCardByIdIncludingDeleted(cardId: String): CardEntity?

    @Query(
        """
        SELECT * FROM cards
        WHERE deckId = :deckId AND isDeleted = 0 AND LOWER(word) = LOWER(:word)
        LIMIT 1
        """
    )
    suspend fun getCardByWordInDeck(deckId: String, word: String): CardEntity?

    @Query(
        """
        SELECT COUNT(*) FROM cards
        WHERE deckId = :deckId AND isDeleted = 0
        """
    )
    suspend fun countActiveCardsInDeck(deckId: String): Int

    @Upsert
    suspend fun upsertCard(card: CardEntity)

    @Upsert
    suspend fun upsertCards(cards: List<CardEntity>)

    @Query(
        """
        UPDATE cards
        SET isDeleted = 1,
            updatedAtMillis = :updatedAtMillis,
            syncStatus = :syncStatus
        WHERE id = :cardId
        """
    )
    suspend fun softDeleteCard(
        cardId: String,
        updatedAtMillis: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    @Query(
        """
        UPDATE cards
        SET isDeleted = 1,
            updatedAtMillis = :updatedAtMillis,
            syncStatus = :syncStatus
        WHERE deckId = :deckId AND isDeleted = 0
        """
    )
    suspend fun softDeleteCardsByDeck(
        deckId: String,
        updatedAtMillis: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    @Query(
        """
        UPDATE cards
        SET state = :state,
            easeFactor = :easeFactor,
            repetitions = 0,
            interval = 0,
            lapses = 0,
            dueDay = NULL,
            dueAtMillis = NULL,
            firstSeenDay = NULL,
            lastReviewedAtMillis = NULL,
            updatedAtMillis = :updatedAtMillis,
            syncStatus = :syncStatus
        WHERE id = :cardId AND isDeleted = 0
        """
    )
    suspend fun resetCardProgress(
        cardId: String,
        updatedAtMillis: Long,
        easeFactor: Double = Card.INITIAL_EASE_FACTOR,
        state: CardState = CardState.NEW,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    @Query(
        """
        UPDATE cards
        SET state = :state,
            easeFactor = :easeFactor,
            repetitions = 0,
            interval = 0,
            lapses = 0,
            dueDay = NULL,
            dueAtMillis = NULL,
            firstSeenDay = NULL,
            lastReviewedAtMillis = NULL,
            updatedAtMillis = :updatedAtMillis,
            syncStatus = :syncStatus
        WHERE deckId = :deckId AND isDeleted = 0
        """
    )
    suspend fun resetDeckProgress(
        deckId: String,
        updatedAtMillis: Long,
        easeFactor: Double = Card.INITIAL_EASE_FACTOR,
        state: CardState = CardState.NEW,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    /**
     * Đếm số thẻ mới đã hiển thị lần đầu trong ngày học hiện tại (Mục 6.5 & 7.6).
     */
    @Query(
        """
        SELECT COUNT(*) FROM cards
        WHERE ownerId = :ownerId
          AND isDeleted = 0
          AND firstSeenDay = :learningDay
        """
    )
    suspend fun countNewCardsLearnedOnDay(ownerId: String, learningDay: Long): Int

    /**
     * Hàng đợi thẻ mới: lấy các thẻ state = NEW chưa xóa, ưu tiên tạo cũ trước (Mục 7.6).
     */
    @Query(
        """
        SELECT * FROM cards
        WHERE ownerId = :ownerId
          AND isDeleted = 0
          AND (:deckId IS NULL OR deckId = :deckId)
          AND state = 'NEW'
        ORDER BY createdAtMillis ASC
        LIMIT :limit
        """
    )
    suspend fun getNewCardsQueue(
        ownerId: String,
        deckId: String?,
        limit: Int
    ): List<CardEntity>

    /**
     * Hàng đợi thẻ đang học (LEARNING) đến hạn theo bước phút, sắp xếp đến hạn sớm nhất trước (Mục 7.6).
     */
    @Query(
        """
        SELECT * FROM cards
        WHERE ownerId = :ownerId
          AND isDeleted = 0
          AND (:deckId IS NULL OR deckId = :deckId)
          AND state = 'LEARNING'
          AND dueAtMillis IS NOT NULL
          AND dueAtMillis <= :nowMillis
        ORDER BY dueAtMillis ASC
        """
    )
    suspend fun getDueLearningCards(
        ownerId: String,
        deckId: String?,
        nowMillis: Long
    ): List<CardEntity>

    /**
     * Hàng đợi thẻ đã học (REVIEW) đến hạn hoặc quá hạn, ưu tiên dueDay cũ nhất trước (Mục 7.6).
     */
    @Query(
        """
        SELECT * FROM cards
        WHERE ownerId = :ownerId
          AND isDeleted = 0
          AND (:deckId IS NULL OR deckId = :deckId)
          AND state = 'REVIEW'
          AND dueDay IS NOT NULL
          AND dueDay <= :currentLearningDay
        ORDER BY dueDay ASC, updatedAtMillis ASC
        """
    )
    suspend fun getDueReviewCards(
        ownerId: String,
        deckId: String?,
        currentLearningDay: Long
    ): List<CardEntity>

    /**
     * Đếm số thẻ REVIEW sẽ đến hạn vào ngày học kế tiếp cho màn Tổng kết phiên (Mục 6.6).
     */
    @Query(
        """
        SELECT COUNT(*) FROM cards
        WHERE ownerId = :ownerId
          AND isDeleted = 0
          AND (:deckId IS NULL OR deckId = :deckId)
          AND state = 'REVIEW'
          AND dueDay = :tomorrowDay
        """
    )
    suspend fun countTomorrowDueCards(
        ownerId: String,
        deckId: String?,
        tomorrowDay: Long
    ): Int

    @Query("DELETE FROM cards WHERE ownerId = :ownerId")
    suspend fun deleteAllByOwner(ownerId: String)

    @Query(
        """
        DELETE FROM cards
        WHERE isDeleted = 1
          AND syncStatus = 'SYNCED'
          AND updatedAtMillis < :cutoffMillis
        """
    )
    suspend fun purgeSyncedDeletedBefore(cutoffMillis: Long)
}
