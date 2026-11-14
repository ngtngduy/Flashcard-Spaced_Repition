package com.uth.memora.core.domain

import com.uth.memora.core.model.AiEvaluationResult
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyStatistics
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.core.model.UserAccount
import com.uth.memora.core.model.UserPreferences
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

    /**
     * Tự động gợi ý điền nhanh thông tin thẻ mới bằng AI (Mục 6.3 & 11).
     * Điền: Phiên âm IPA, Loại từ, Nghĩa tiếng Việt và Câu ví dụ.
     */
    suspend fun autoFillCardContent(word: String): Result<AutoFillCardData>
}

data class AutoFillCardData(
    val word: String,
    val phonetic: String,
    val partOfSpeech: PartOfSpeech?,
    val meaning: String,
    val example: String
)

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

    /**
     * Đếm số thẻ dự kiến đến hạn vào ngày học kế tiếp (Màn Tổng kết phiên - Figma 2199:19).
     */
    suspend fun getTomorrowDueCount(deckId: String? = null): Int
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

/**
 * Quản lý cài đặt giao diện, hạn mức học và streak người dùng (Mục 11 & 13).
 */
interface UserPreferencesRepository {
    fun observeUserPreferences(): Flow<UserPreferences>
    suspend fun getUserPreferences(): UserPreferences
    suspend fun setThemeMode(mode: ThemeMode): Result<Unit>
    suspend fun setDailyNewCardsLimit(limit: Int): Result<Unit>
    suspend fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int): Result<Unit>
    suspend fun setSoundEnabled(enabled: Boolean): Result<Unit>
    suspend fun setOnboardingCompleted(completed: Boolean): Result<Unit>
    suspend fun updateStreak(streak: Int, isFrozen: Boolean): Result<Unit>
    suspend fun consumeAutoFillQuota(): Result<Int>
    suspend fun consumeSentenceGradingQuota(): Result<Int>
    suspend fun resetDailyQuotasIfNeeded(currentEpochDay: Long): Result<Unit>
}

/**
 * Quản lý xác thực người dùng: Email/Mật khẩu, Google & Chế độ khách (Mục 6.2 & 13).
 */
interface AuthRepository {
    fun observeCurrentUser(): Flow<UserAccount?>
    suspend fun getCurrentUser(): UserAccount?
    suspend fun signInWithEmail(email: String, password: String): Result<UserAccount>
    suspend fun registerWithEmail(email: String, password: String, displayName: String): Result<UserAccount>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<UserAccount>
    suspend fun signInAnonymously(): Result<UserAccount>
    suspend fun signOut(): Result<Unit>
}

/**
 * Dịch vụ AI gợi ý điền nhanh thông tin thẻ học (Thành viên 3 hiện thực trong `integration/ai`).
 */
interface AiCardAutoFillService {
    suspend fun autoFillCard(word: String): Result<AutoFillCardData>
}

/**
 * Tóm tắt trạng thái đồng bộ đám mây (Hiển thị ở Cài đặt - Figma 13:2).
 */
data class CloudSyncSummary(
    val isSyncing: Boolean = false,
    val pendingChangesCount: Int = 0,
    val lastSyncedAtMillis: Long? = null,
    val syncErrorMessage: String? = null
)

/**
 * Cung cấp luồng trạng thái đồng bộ và cho phép người dùng bấm "Đồng bộ ngay" (Mục 13).
 */
interface SyncStatusProvider {
    fun observeSyncSummary(): Flow<CloudSyncSummary>
    suspend fun triggerImmediateSync(): Result<Int>
}
