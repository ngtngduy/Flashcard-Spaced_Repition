package com.uth.memora.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.SyncStatus

/**
 * Bảng lưu trữ bộ từ vựng trong Room Database (Mục 5 & Mục 10.1).
 *
 * Hỗ trợ kiến trúc offline-first với khóa chính UUID, xóa mềm (isDeleted)
 * và cờ trạng thái đồng bộ (syncStatus).
 */
@Entity(
    tableName = "decks",
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["ownerId", "isDeleted"])
    ]
)
data class DeckEntity(
    @PrimaryKey
    val id: String,
    val ownerId: String = Deck.GUEST_OWNER_ID,
    val name: String,
    val description: String = "",
    val sourcePrebuiltId: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)
