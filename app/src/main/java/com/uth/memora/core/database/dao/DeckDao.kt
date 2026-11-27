package com.uth.memora.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.uth.memora.core.database.entity.DeckEntity
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO thao tác trên bảng `decks` trong Room Database (Mục 5, Mục 6.3 & Mục 10.1).
 *
 * Mọi truy vấn hiển thị cho người dùng luôn lọc `isDeleted = 0`.
 */
@Dao
interface DeckDao {

    @Query(
        """
        SELECT * FROM decks
        WHERE ownerId = :ownerId AND isDeleted = 0
        ORDER BY updatedAtMillis DESC
        """
    )
    fun observeActiveDecks(ownerId: String): Flow<List<DeckEntity>>

    @Query(
        """
        SELECT * FROM decks
        WHERE ownerId = :prebuiltOwnerId AND isDeleted = 0
        ORDER BY createdAtMillis ASC
        """
    )
    fun observePrebuiltDecks(prebuiltOwnerId: String = PREBUILT_OWNER_ID): Flow<List<DeckEntity>>

    @Query(
        """
        SELECT * FROM decks
        WHERE id = :deckId AND isDeleted = 0
        LIMIT 1
        """
    )
    suspend fun getDeckById(deckId: String): DeckEntity?

    @Query(
        """
        SELECT * FROM decks
        WHERE id = :deckId
        LIMIT 1
        """
    )
    suspend fun getDeckByIdIncludingDeleted(deckId: String): DeckEntity?

    @Query(
        """
        SELECT * FROM decks
        WHERE ownerId = :ownerId AND isDeleted = 0 AND LOWER(name) = LOWER(:name)
        LIMIT 1
        """
    )
    suspend fun getDeckByName(ownerId: String, name: String): DeckEntity?

    @Query(
        """
        SELECT * FROM decks
        WHERE ownerId = :ownerId AND isDeleted = 0 AND sourcePrebuiltId = :sourcePrebuiltId
        LIMIT 1
        """
    )
    suspend fun getDeckBySourcePrebuiltId(ownerId: String, sourcePrebuiltId: String): DeckEntity?

    @Query(
        """
        SELECT COUNT(*) FROM decks
        WHERE ownerId = :ownerId AND isDeleted = 0
        """
    )
    suspend fun countActiveDecks(ownerId: String): Int

    @Upsert
    suspend fun upsertDeck(deck: DeckEntity)

    @Upsert
    suspend fun upsertDecks(decks: List<DeckEntity>)

    @Query(
        """
        UPDATE decks
        SET isDeleted = 1,
            updatedAtMillis = :updatedAtMillis,
            syncStatus = :syncStatus
        WHERE id = :deckId
        """
    )
    suspend fun softDeleteDeck(
        deckId: String,
        updatedAtMillis: Long,
        syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
    )

    @Query("DELETE FROM decks WHERE ownerId = :ownerId")
    suspend fun deleteAllByOwner(ownerId: String)

    @Query(
        """
        DELETE FROM decks
        WHERE isDeleted = 1
          AND syncStatus = 'SYNCED'
          AND updatedAtMillis < :cutoffMillis
        """
    )
    suspend fun purgeSyncedDeletedBefore(cutoffMillis: Long)

    // ---------------- Đồng bộ đám mây (Mục 8.3 & 10.1) ----------------

    @Query(
        """
        SELECT * FROM decks
        WHERE ownerId = :ownerId AND syncStatus = 'PENDING_SYNC'
        ORDER BY updatedAtMillis ASC
        """
    )
    suspend fun getPendingSyncDecks(ownerId: String): List<DeckEntity>

    @Query(
        """
        UPDATE decks
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
        SELECT COUNT(*) FROM decks
        WHERE ownerId = :ownerId AND syncStatus = 'PENDING_SYNC'
        """
    )
    fun countPendingSync(ownerId: String): Flow<Int>

    @Query(
        """
        UPDATE decks
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

    companion object {
        const val PREBUILT_OWNER_ID = "prebuilt_catalog"
    }
}
