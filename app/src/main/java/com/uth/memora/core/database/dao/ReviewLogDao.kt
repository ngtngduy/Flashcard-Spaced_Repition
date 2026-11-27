package com.uth.memora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.uth.memora.core.database.entity.ReviewLogEntity
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO thao tác trên bảng `review_logs` (append-only - Mục 5, Mục 9.1 & Mục 10.2).
 *
 * Chỉ lọc bản ghi hợp lệ (`isUndone = 0`) cho thống kê và tính năng Hoàn tác lượt gần nhất.
 */
@Dao
interface ReviewLogDao {

    @Upsert
    suspend fun insertLog(log: ReviewLogEntity)

    @Upsert
    suspend fun upsertLogs(logs: List<ReviewLogEntity>)

    @Query("SELECT * FROM review_logs WHERE id = :logId LIMIT 1")
    suspend fun getLogById(logId: String): ReviewLogEntity?

    /**
     * Lấy lượt đánh giá gần nhất chưa bị hoàn tác để phục vụ nút Undo trong 5 giây (Mục 9.1).
     */
    @Query(
        """
        SELECT * FROM review_logs
        WHERE ownerId = :ownerId AND isUndone = 0
        ORDER BY reviewedAtMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLatestActiveLog(ownerId: String): ReviewLogEntity?

    @Query(
        """
        UPDATE review_logs
        SET isUndone = 1,
            updatedAtMillis = :updatedAtMillis,
            syncStatus = :syncStatus
        WHERE id = :logId
        """
    )
    suspend fun markLogUndone(
        logId: String,
        updatedAtMillis: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    /**
     * Quan sát lịch sử đánh giá hợp lệ từ một ngày học trở đi (phục vụ tỷ lệ nhớ 7 ngày - Mục 10.2).
     */
    @Query(
        """
        SELECT * FROM review_logs
        WHERE ownerId = :ownerId
          AND isUndone = 0
          AND learningDay >= :fromLearningDay
        ORDER BY reviewedAtMillis DESC
        """
    )
    fun observeLogsFromDay(ownerId: String, fromLearningDay: Long): Flow<List<ReviewLogEntity>>

    @Query(
        """
        SELECT * FROM review_logs
        WHERE ownerId = :ownerId AND isUndone = 0
        ORDER BY reviewedAtMillis DESC
        """
    )
    fun observeAllValidLogs(ownerId: String): Flow<List<ReviewLogEntity>>

    @Query("DELETE FROM review_logs WHERE ownerId = :ownerId")
    suspend fun deleteAllByOwner(ownerId: String)

    // ---------------- Đồng bộ đám mây (Mục 8.3 & 10.1) ----------------

    @Query(
        """
        SELECT * FROM review_logs
        WHERE ownerId = :ownerId AND syncStatus = 'PENDING_SYNC'
        ORDER BY updatedAtMillis ASC
        """
    )
    suspend fun getPendingSyncReviewLogs(ownerId: String): List<ReviewLogEntity>

    @Query(
        """
        UPDATE review_logs
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
        SELECT COUNT(*) FROM review_logs
        WHERE ownerId = :ownerId AND syncStatus = 'PENDING_SYNC'
        """
    )
    fun countPendingSync(ownerId: String): Flow<Int>

    @Query(
        """
        UPDATE review_logs
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
