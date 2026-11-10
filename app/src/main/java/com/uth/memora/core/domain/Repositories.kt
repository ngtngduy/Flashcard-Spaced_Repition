package com.uth.memora.core.domain

import com.uth.memora.core.model.AiEvaluationResult
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyStatistics
import kotlinx.coroutines.flow.Flow

/**
 * ============================================================================
 * HỢP ĐỒNG GIAO TIẾP CHUNG (CONTRACTS / INTERFACES) CHO CẢ 3 THÀNH VIÊN
 * ============================================================================
 * QUY TẮC NHÓM:
 * - Không ai tự ý đổi tên hàm hoặc kiểu trả về trong file này mà không báo nhóm.
 * - Thành viên 1: Hiện thực bằng Room Database + Sm2Engine trong package `core/database`.
 * - Thành viên 2: Gọi các hàm trong Interface này từ ViewModel & Compose UI (dùng sẵn FakeMemoraRepository khi chưa ghép DB).
 * - Thành viên 3: Hiện thực `AiGradingService`, `CloudSyncManager` và WorkManager trong package `integration/`.
 */

interface DeckRepository {
    fun observeActiveDecks(): Flow<List<Deck>>
    fun observePrebuiltDecks(): Flow<List<Deck>>
    suspend fun getDeckById(deckId: String): Deck?
    suspend fun createDeck(name: String, description: String): Result<Deck>
    suspend fun updateDeck(deckId: String, name: String, description: String): Result<Deck>
    suspend fun softDeleteDeck(deckId: String): Result<Unit>
    suspend fun importPrebuiltDeck(prebuiltDeckId: String): Result<Deck>
}

interface CardRepository {
    fun observeCardsByDeck(deckId: String): Flow<List<Card>>
    fun observeAllActiveCards(): Flow<List<Card>>
    suspend fun getCardById(cardId: String): Card?
    suspend fun searchCards(deckId: String?, query: String): List<Card>
    suspend fun addCard(
        deckId: String,
        word: String,
        meaning: String,
        phonetic: String = "",
        partOfSpeech: PartOfSpeech? = null,
        example: String = "",
        localImagePath: String? = null,
        allowDuplicateWord: Boolean = false
    ): Result<Card>
    suspend fun updateCardContent(
        cardId: String,
        deckId: String,
        word: String,
        meaning: String,
        phonetic: String,
        partOfSpeech: PartOfSpeech?,
        example: String,
        localImagePath: String?
    ): Result<Card>
    suspend fun softDeleteCard(cardId: String): Result<Unit>
    suspend fun resetCardProgress(cardId: String): Result<Card>
    suspend fun resetDeckProgress(deckId: String): Result<Unit>
}

interface StudySessionRepository {
    /**
     * Lấy hàng đợi "Học từ mới" theo thứ tự tạo cũ trước, giới hạn bởi hạn mức thẻ mới còn lại hôm nay (Mục 6.5 & 7.6).
     */
    suspend fun getNewCardsQueue(deckId: String? = null): List<Card>

    /**
     * Lấy hàng đợi "Ôn tập hôm nay": Thẻ đang học đến hạn trước, sau đó Thẻ đã học theo dueDay cũ nhất trước (Mục 6.6 & 7.6).
     */
    suspend fun getDueReviewQueue(deckId: String? = null): List<Card>

    /**
     * Ghi nhận một lượt đánh giá thẻ bằng 4 nút (Chưa nhớ, Khó, Nhớ tốt, Dễ) và tính lịch SM-2 ngay lập tức.
     */
    suspend fun submitCardReview(
        cardId: String,
        quality: ReviewQuality,
        durationValidSeconds: Int = 5
    ): Result<Card>

    /**
     * Hoàn tác lượt đánh giá gần nhất của phiên hiện tại (Mục 9.1).
     */
    suspend fun undoLastReview(): Result<Card>

    /**
     * Xem trước mốc thời gian hiển thị trên 4 nút của một thẻ.
     */
    fun getButtonPreviewLabels(card: Card): Map<ReviewQuality, String>
}

interface PracticeSentenceRepository {
    fun observeSentencesByCard(cardId: String): Flow<List<PracticeSentence>>
    fun observeRecentSentences(): Flow<List<PracticeSentence>>

    /**
     * Chọn danh sách tối đa 5 từ mục tiêu cho một buổi luyện đặt câu (Mục 6.7).
     */
    suspend fun getSuggestedPracticeCards(deckId: String? = null, limit: Int = 5): List<Card>

    /**
     * Gửi một câu luyện tập:
     * - Kiểm tra cục bộ trước (Mục 6.8).
     * - Nếu có mạng: chuyển sang GRADING (hiện Shimmer Loading) -> gọi AI -> lưu GRADED.
     * - Nếu mất mạng: lưu ở trạng thái PENDING_GRADE (Chờ chấm) để WorkManager gửi sau.
     */
    suspend fun submitPracticeSentence(
        cardId: String,
        sentence: String,
        isOnline: Boolean = true
    ): Result<PracticeSentence>

    /**
     * Bấm "Chấm lại" cho một câu đang ở trạng thái Lỗi chấm (Mục 8.2).
     */
    suspend fun retryFailedSentence(sentenceId: String): Result<PracticeSentence>
}

interface StatisticsRepository {
    fun observeStatistics(): Flow<StudyStatistics>
}

/**
 * Dịch vụ gọi Gemini AI để chấm điểm và sửa câu (Dành cho Thành viên 3 hiện thực ở `integration/ai`).
 */
interface AiGradingService {
    suspend fun evaluateSentence(
        targetWord: String,
        meaningVi: String,
        userSentence: String
    ): Result<AiEvaluationResult>
}

/**
 * Dịch vụ đồng bộ đám mây Firebase Firestore & Gộp dữ liệu khách (Dành cho Thành viên 3 hiện thực ở `integration/sync`).
 */
interface CloudSyncManager {
    suspend fun syncPendingChanges(): Result<Int>
    suspend fun mergeGuestDataIntoAccount(accountId: String, discardGuestData: Boolean): Result<Unit>
}
