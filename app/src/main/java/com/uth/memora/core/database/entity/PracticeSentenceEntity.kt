package com.uth.memora.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.SyncStatus

/**
 * Bảng lưu câu luyện tập đặt câu và kết quả chấm điểm từ AI Gemini (Mục 6.7, 6.8, 8.2 & 10.1).
 *
 * Chỉ mục (cardId), (status), (ownerId, learningDay) hỗ trợ tải lịch sử câu theo thẻ,
 * quét hàng đợi PENDING_GRADE/GRADING khi có mạng và đếm hạn mức 20 câu/ngày.
 */
@Entity(
    tableName = "practice_sentences",
    indices = [
        Index(value = ["cardId"]),
        Index(value = ["status"]),
        Index(value = ["ownerId", "learningDay"])
    ]
)
data class PracticeSentenceEntity(
    @PrimaryKey
    val id: String,
    val cardId: String,
    val ownerId: String = Deck.GUEST_OWNER_ID,
    val targetWord: String,
    val sentence: String,
    val status: SentenceStatus = SentenceStatus.PENDING_GRADE,

    // Kết quả chấm điểm từ AI Gemini (Mục 6.7)
    val score: Float? = null,
    val cefrLevel: String? = null,
    val feedbackVi: String? = null,
    val grammarAnalysis: List<String> = emptyList(),
    val correctedSentence: String? = null,
    val naturalAlternative: String? = null,
    val errorMessage: String? = null,

    val retryCount: Int = 0,
    val learningDay: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
)
