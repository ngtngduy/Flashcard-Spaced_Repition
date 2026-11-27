package com.uth.memora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.uth.memora.core.database.entity.PracticeSentenceEntity
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO thao tác trên bảng `practice_sentences` phục vụ Chatbot AI & hàng đợi chấm offline (Mục 6.7, 6.8 & 8.2).
 */
@Dao
interface PracticeSentenceDao {

    @Query(
        """
        SELECT * FROM practice_sentences
        WHERE cardId = :cardId
        ORDER BY createdAtMillis DESC
        """
    )
    fun observeSentencesByCard(cardId: String): Flow<List<PracticeSentenceEntity>>

    @Query(
        """
        SELECT * FROM practice_sentences
        WHERE ownerId = :ownerId
        ORDER BY createdAtMillis DESC
        LIMIT :limit
        """
    )
    fun observeRecentSentences(
        ownerId: String,
        limit: Int = 50
    ): Flow<List<PracticeSentenceEntity>>

    @Query("SELECT * FROM practice_sentences WHERE id = :id LIMIT 1")
    suspend fun getSentenceById(id: String): PracticeSentenceEntity?

    @Upsert
    suspend fun upsertSentence(sentence: PracticeSentenceEntity)

    @Upsert
    suspend fun upsertSentences(sentences: List<PracticeSentenceEntity>)

    /**
     * Lấy danh sách câu đang chờ chấm hoặc đang chấm dở của người dùng (Mục 8.2).
     */
    @Query(
        """
        SELECT * FROM practice_sentences
        WHERE ownerId = :ownerId
          AND status IN ('PENDING_GRADE', 'GRADING')
        ORDER BY createdAtMillis ASC
        """
    )
    suspend fun getPendingOrErrorSentences(ownerId: String): List<PracticeSentenceEntity>

    /**
     * Lấy toàn bộ câu ở trạng thái PENDING_GRADE để SentenceGradingWorker xử lý lại khi có mạng (Mục 8.2).
     */
    @Query(
        """
        SELECT * FROM practice_sentences
        WHERE status = 'PENDING_GRADE'
        ORDER BY createdAtMillis ASC
        """
    )
    suspend fun getAllPendingSentences(): List<PracticeSentenceEntity>

    /**
     * Đếm số câu đã đặt trong một ngày học để kiểm soát hạn mức 20 câu/ngày (Mục 6.7 & 11).
     */
    @Query(
        """
        SELECT COUNT(*) FROM practice_sentences
        WHERE ownerId = :ownerId AND learningDay = :learningDay
        """
    )
    suspend fun countSentencesOnDay(ownerId: String, learningDay: Long): Int

    @Query("DELETE FROM practice_sentences WHERE ownerId = :ownerId")
    suspend fun deleteAllByOwner(ownerId: String)

    // ---------------- Đồng bộ đám mây (Mục 8.3 & 10.1) ----------------

    @Query(
        """
        SELECT * FROM practice_sentences
        WHERE ownerId = :ownerId AND syncStatus = 'PENDING_SYNC'
        ORDER BY updatedAtMillis ASC
        """
    )
    suspend fun getPendingSyncSentences(ownerId: String): List<PracticeSentenceEntity>

    @Query(
        """
        UPDATE practice_sentences
        SET syncStatus = :syncStatus,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :id
        """
    )
    suspend fun updateSyncStatus(
        id: String,
        syncStatus: SyncStatus,
        updatedAtMillis: Long
    )

    @Query(
        """
        SELECT COUNT(*) FROM practice_sentences
        WHERE ownerId = :ownerId AND syncStatus = 'PENDING_SYNC'
        """
    )
    fun countPendingSync(ownerId: String): Flow<Int>

    @Query(
        """
        UPDATE practice_sentences
        SET ownerId = :toOwnerId,
            updatedAtMillis = :updatedAtMillis,
            syncStatus = :syncStatus
        WHERE ownerId = :fromOwnerId
        """
    )
    suspend fun reassignOwner(
        fromOwnerId: String,
        toOwnerId: String,
        updatedAtMillis: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )
}
