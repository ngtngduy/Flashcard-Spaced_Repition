package com.uth.memora.core.model

/**
 * Một câu luyện tập đặt câu với từ mục tiêu và kết quả chấm từ AI (Mục 6.7, 6.8, 8.2 & 11).
 *
 * Quy định:
 * - Câu từ 3 từ đến 300 ký tự, bắt đầu bằng chữ hoa, kết thúc bằng dấu câu, chứa từ mục tiêu.
 * - Hạn mức: 20 câu mỗi ngày mỗi tài khoản; mỗi buổi luyện tối đa 5 từ.
 * - Thử lại tối đa 3 lần (Exponential Backoff: 15s, 60s, 5m) trước khi chuyển sang GRADE_ERROR.
 */
data class PracticeSentence(
    val id: String,
    val cardId: String,
    val ownerId: String = Deck.GUEST_OWNER_ID,
    val targetWord: String,
    val sentence: String,
    val status: SentenceStatus = SentenceStatus.PENDING_GRADE,

    // Kết quả chấm điểm từ AI theo schema chuẩn (Mục 6.7)
    val score: Float? = null,
    val cefrLevel: String? = null,
    val feedbackVi: String? = null,
    val grammarAnalysis: List<String> = emptyList(),
    val correctedSentence: String? = null,
    val naturalAlternative: String? = null,
    val errorMessage: String? = null,

    val retryCount: Int = 0,
    val learningDay: Long,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val syncStatus: SyncStatus = SyncStatus.PENDING_SYNC
) {
    companion object {
        const val MIN_WORDS = 3
        const val MAX_SENTENCE_LENGTH = 300
        const val MAX_DAILY_QUOTA = 20
        const val MAX_WORDS_PER_SESSION = 5
        const val MAX_RETRY_ATTEMPTS = 3
    }
}

/**
 * Cấu trúc dữ liệu trả về từ AI Gemini khi chấm câu (Mục 6.7).
 */
data class AiEvaluationResult(
    val score: Float,
    val cefrLevel: String,
    val feedbackVi: String,
    val grammarAnalysis: List<String>,
    val correctedSentence: String,
    val naturalAlternative: String
)
