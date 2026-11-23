package com.uth.memora.integration.ai

/**
 * Xây dựng prompt chấm điểm câu luyện tập tiếng Anh gửi lên Gemini AI (Mục 6.7 & 9.5).
 *
 * Schema JSON trả về khớp 100% 6 khóa snake_case trong [GeminiAiGradingService.buildGradingPrompt]
 * từ Sprint 0 để ánh xạ trực tiếp sang [com.uth.memora.core.model.AiEvaluationResult].
 */
object GradingPrompt {

    const val KEY_SCORE: String = "score"
    const val KEY_CEFR_LEVEL: String = "cefr_level"
    const val KEY_FEEDBACK_VI: String = "feedback_vi"
    const val KEY_GRAMMAR_ANALYSIS: String = "grammar_analysis"
    const val KEY_CORRECTED_SENTENCE: String = "corrected_sentence"
    const val KEY_NATURAL_ALTERNATIVE: String = "natural_alternative"

    val VALID_CEFR_LEVELS: Set<String> = setOf("A1", "A2", "B1", "B2", "C1", "C2")

    /**
     * Tạo prompt chấm câu yêu cầu Gemini trả về duy nhất JSON thuần gồm 6 khóa chuẩn (Mục 6.7).
     */
    fun build(
        targetWord: String,
        meaningVi: String,
        userSentence: String
    ): String {
        val cleanWord = targetWord.trim()
        val cleanMeaning = meaningVi.trim()
        val cleanSentence = userSentence.trim()

        return """
            Bạn là trợ lý chấm điểm câu tiếng Anh của ứng dụng học từ vựng Memora.
            Từ mục tiêu: "$cleanWord" (Nghĩa: $cleanMeaning).
            Câu người học viết: "$cleanSentence".

            Hãy chấm điểm câu trên theo tiêu chí ngữ pháp, ngữ nghĩa và mức độ tự nhiên khi dùng từ mục tiêu.
            Trả về DUY NHẤT một chuỗi JSON thuần hợp lệ (không kèm markdown, không giải thích ngoài JSON) với đúng 6 khóa sau:
            - "$KEY_SCORE": số thực từ 0.0 đến 10.0.
            - "$KEY_CEFR_LEVEL": đúng một trong các giá trị "A1", "A2", "B1", "B2", "C1", "C2".
            - "$KEY_FEEDBACK_VI": nhận xét ngắn gọn bằng tiếng Việt về ngữ pháp và cách dùng từ mục tiêu.
            - "$KEY_GRAMMAR_ANALYSIS": mảng chuỗi tiếng Việt phân tích từng điểm ngữ pháp nổi bật hoặc lỗi cần sửa.
            - "$KEY_CORRECTED_SENTENCE": câu tiếng Anh đã sửa chuẩn ngữ pháp (giữ nguyên ý của người học).
            - "$KEY_NATURAL_ALTERNATIVE": câu tiếng Anh diễn đạt tự nhiên hơn theo văn phong người bản xứ có dùng từ mục tiêu.

            Ví dụ cấu trúc JSON hợp lệ:
            {
              "$KEY_SCORE": 8.5,
              "$KEY_CEFR_LEVEL": "B1",
              "$KEY_FEEDBACK_VI": "Nhận xét ngắn gọn bằng tiếng Việt về ngữ pháp và cách dùng từ mục tiêu.",
              "$KEY_GRAMMAR_ANALYSIS": ["Điểm ngữ pháp 1", "Điểm ngữ pháp 2"],
              "$KEY_CORRECTED_SENTENCE": "Câu đã sửa chuẩn ngữ pháp tiếng Anh.",
              "$KEY_NATURAL_ALTERNATIVE": "Câu diễn đạt tự nhiên hơn theo văn phong người bản xứ."
            }
        """.trimIndent()
    }
}
