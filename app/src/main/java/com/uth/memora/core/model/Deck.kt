package com.uth.memora.core.model

/**
 * Bộ từ vựng (Deck - Mục 5 & Mục 11).
 *
 * Quy định:
 * - Tên bộ: 1–50 ký tự, không trùng trong cùng người dùng (không phân biệt hoa thường).
 * - Mô tả bộ: tối đa 200 ký tự.
 * - Tối đa 100 bộ mỗi người, 2000 thẻ mỗi bộ.
 */
data class Deck(
    val id: String,
    val ownerId: String = GUEST_OWNER_ID,
    val name: String,
    val description: String = "",
    val sourcePrebuiltId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
) {
    companion object {
        const val GUEST_OWNER_ID = "guest_local"
        const val MAX_NAME_LENGTH = 50
        const val MAX_DESCRIPTION_LENGTH = 200
        const val MAX_DECKS_PER_USER = 100
        const val MAX_CARDS_PER_DECK = 2000
    }
}
