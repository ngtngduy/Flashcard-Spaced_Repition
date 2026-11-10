package com.uth.memora.integration.ai

import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.model.AiEvaluationResult

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: TÍCH HỢP GEMINI AI SDK (Mục 6.7 & 9.5)
 * ============================================================================
 * Đã chuẩn bị sẵn System Prompt chuẩn theo đúng schema JSON đặc tả.
 * Thành viên 3 chỉ cần khởi tạo `GenerativeModel` của Google AI SDK và parse JSON.
 */
class GeminiAiGradingService(
    private val apiKey: String = ""
) : AiGradingService {

    override suspend fun evaluateSentence(
        targetWord: String,
        meaningVi: String,
        userSentence: String
    ): Result<AiEvaluationResult> {
        // TODO (Thành viên 3): Gọi GenerativeModel("gemini-2.5-flash", apiKey) với buildPrompt(...)
        return Result.failure(NotImplementedError("Thành viên 3 sẽ hiện thực kết nối Gemini SDK tại đây."))
    }

    companion object {
        fun buildGradingPrompt(
            targetWord: String,
            meaningVi: String,
            userSentence: String
        ): String = """
            Bạn là trợ lý chấm điểm câu tiếng Anh của ứng dụng học từ vựng Memora.
            Từ mục tiêu: "$targetWord" (Nghĩa: $meaningVi).
            Câu người học viết: "$userSentence".

            Hãy chấm điểm và trả về DUY NHẤT một chuỗi JSON hợp lệ theo đúng cấu trúc sau (không kèm markdown):
            {
              "score": 8.5,
              "cefr_level": "B1",
              "feedback_vi": "Nhận xét ngắn gọn bằng tiếng Việt về ngữ pháp và cách dùng từ mục tiêu.",
              "grammar_analysis": ["Điểm ngữ pháp 1", "Điểm ngữ pháp 2"],
              "corrected_sentence": "Câu đã sửa chuẩn ngữ pháp tiếng Anh.",
              "natural_alternative": "Câu diễn đạt tự nhiên hơn theo văn phong người bản xứ."
            }
        """.trimIndent()
    }
}
