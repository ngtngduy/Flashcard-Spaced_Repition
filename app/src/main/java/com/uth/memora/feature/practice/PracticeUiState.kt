package com.uth.memora.feature.practice

import com.uth.memora.core.domain.SentenceLocalValidator
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.UserPreferences

/**
 * Trạng thái giao diện màn hình Luyện đặt câu với Chatbot AI (Figma 2061:119 & 2212:109 · Mục 6.7, 6.8, 8.2 & 9.5).
 */
data class PracticeUiState(
    /** `true` nếu chưa đăng nhập hoặc đang ở chế độ khách (`currentUser == null || currentUser.isAnonymous` - Mục 6.7 & 13). */
    val isGuest: Boolean = true,

    /** Danh sách tối đa 5 thẻ mục tiêu trong buổi luyện hiện tại (Mục 6.7). */
    val targetCards: List<Card> = emptyList(),

    /** Vị trí thẻ đang chọn trong [targetCards]. */
    val selectedCardIndex: Int = 0,

    /** Nội dung câu tiếng Anh người học đang nhập hoặc nói qua Micro (Mục 6.7). */
    val inputText: String = "",

    /** Thông báo lỗi kiểm tra cục bộ từ `SentenceLocalValidator` trước khi gửi (Mục 6.8). */
    val validationError: String? = null,

    /** Số lượt chấm câu AI còn lại trong ngày học hôm nay (tối đa 20 câu/ngày - Mục 6.7 & 11). */
    val remainingDailyQuota: Int = UserPreferences.DEFAULT_AI_SENTENCE_LIMIT,

    /** Lịch sử các câu đã đặt cho thẻ đang chọn, sắp xếp mới nhất trước (Mục 6.7 & 8.2). */
    val history: List<PracticeSentence> = emptyList(),

    /** Đang gửi câu lên AI hoặc lưu vào hàng đợi chấm câu (khóa nút gửi để tránh gửi trùng - Mục 6.7 & 8.2). */
    val isSubmitting: Boolean = false,

    /** Thông báo lỗi thân thiện bằng tiếng Việt (mất mạng, 429, JSON sai định dạng, hết hạn mức... - Mục 9.5). */
    val errorMessage: String? = null,

    /** Cờ tải danh sách thẻ gợi ý ban đầu hoặc khi đổi bộ từ. */
    val isLoading: Boolean = false,

    /** Mã bộ từ đang lọc cho buổi luyện (`null` = gợi ý trên toàn bộ thẻ đang học - Mục 6.7). */
    val selectedDeckId: String? = null,

    /** Mã câu đang thực hiện "Chấm lại" khi ở trạng thái `GRADE_ERROR` (Mục 8.2). */
    val retryingSentenceId: String? = null,

    /** Mã câu đang được lưu làm ví dụ riêng cho thẻ (Mục 6.7 · Figma 2212:91). */
    val savingExampleSentenceId: String? = null,

    /** Tập hợp mã câu đã lưu thành công làm ví dụ riêng của thẻ trong phiên. */
    val savedExampleSentenceIds: Set<String> = emptySet(),

    /** Thông báo phản hồi tích cực bằng tiếng Việt (ví dụ: đã lưu câu sửa, đã xếp hàng chờ chấm offline). */
    val feedbackMessage: String? = null,

    /** `true` khi câu nhận diện từ giọng nói có độ tin cậy thấp (< 0.7f), nhắc người học rà lại trước khi gửi (Mục 6.7). */
    val isLowConfidenceVoiceInput: Boolean = false
) {
    /** Thẻ từ vựng mục tiêu hiện tại đang được chọn để đặt câu (Mục 6.7). */
    val selectedCard: Card?
        get() = targetCards.getOrNull(selectedCardIndex)

    /** Câu luyện tập gần nhất của thẻ đang chọn để hiển thị ở khung kết quả chính (Figma 2061:119). */
    val latestSentence: PracticeSentence?
        get() = history.firstOrNull()

    /** Tổng hạn mức chấm câu AI tối đa mỗi ngày học (20 câu/ngày - Mục 11). */
    val maxDailyQuota: Int
        get() = PracticeSentence.MAX_DAILY_QUOTA

    /** Số lượt chấm câu AI đã sử dụng hôm nay (phục vụ hiển thị "Hạn mức chấm hôm nay: x/20 câu" - Figma 2212:88). */
    val usedDailyQuota: Int
        get() = (maxDailyQuota - remainingDailyQuota).coerceIn(0, maxDailyQuota)

    /** Hiển thị `AiResultShimmerSkeleton` khi đang gửi hoặc có câu ở trạng thái `GRADING` (Figma 2212:109 · Mục 6.7 & 8.2). */
    val isGradingShimmerVisible: Boolean
        get() = isSubmitting || history.any { it.status == SentenceStatus.GRADING }

    /** `true` nếu câu đang nhập trùng hệt với câu đang ở trạng thái `PENDING_GRADE` hoặc `GRADING` trong lịch sử (Mục 6.7 & 11). */
    val isDuplicateInFlight: Boolean
        get() {
            val cleanInput = inputText.trim()
            if (cleanInput.isEmpty()) return false
            return history.any { existing ->
                existing.sentence.trim().equals(cleanInput, ignoreCase = true) &&
                    (existing.status == SentenceStatus.PENDING_GRADE ||
                        existing.status == SentenceStatus.GRADING)
            }
        }

    /** `true` khi câu nhập đã vượt qua kiểm tra cục bộ `SentenceLocalValidator.validate()` (Mục 6.8). */
    val isInputValid: Boolean
        get() {
            val target = selectedCard ?: return false
            if (inputText.isBlank()) return false
            return validationError == null &&
                SentenceLocalValidator.validate(target.word, inputText).isValid
        }

    /** `true` khi đã dùng hết 20 lượt chấm AI trong ngày học hôm nay (Mục 6.7, 9.5 & 11). */
    val isQuotaExhausted: Boolean
        get() = remainingDailyQuota <= 0

    /** Nút Gửi sáng khi còn hạn mức hôm nay, câu hợp lệ cục bộ, không đang gửi và không trùng câu đang chờ/đang chấm (Mục 4, 6.7, 6.8 & 11). */
    val canSubmit: Boolean
        get() = !isQuotaExhausted &&
            selectedCard != null &&
            isInputValid &&
            !isSubmitting &&
            !isDuplicateInFlight
}
