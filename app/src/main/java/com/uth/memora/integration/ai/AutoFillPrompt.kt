package com.uth.memora.integration.ai

/**
 * Xây dựng prompt gợi ý tự điền thông tin thẻ từ vựng tiếng Anh gửi lên Gemini AI (Mục 6.3 & 11).
 *
 * Schema JSON trả về gồm 5 khóa chuẩn ánh xạ trực tiếp sang
 * [com.uth.memora.core.domain.AutoFillCardData] và [com.uth.memora.core.model.PartOfSpeech].
 */
object AutoFillPrompt {

    const val KEY_WORD: String = "word"
    const val KEY_PHONETIC: String = "phonetic"
    const val KEY_PART_OF_SPEECH: String = "partOfSpeech"
    const val KEY_MEANING: String = "meaning"
    const val KEY_EXAMPLE: String = "example"

    val VALID_PARTS_OF_SPEECH: Set<String> = setOf(
        "NOUN",
        "VERB",
        "ADJECTIVE",
        "ADVERB",
        "PREPOSITION",
        "CONJUNCTION",
        "OTHER"
    )

    /**
     * Tạo prompt yêu cầu Gemini trả về duy nhất JSON thuần gồm 5 khóa
     * gợi ý tự điền thẻ học cho từ tiếng Anh đầu vào (Mục 6.3 & 11).
     */
    fun build(word: String): String {
        val cleanWord = word.trim()
        val allowedPosList = VALID_PARTS_OF_SPEECH.joinToString(separator = ", ") { "\"$it\"" }

        return """
            Bạn là trợ lý từ điển Anh-Việt của ứng dụng học từ vựng Memora.
            Từ tiếng Anh đầu vào: "$cleanWord".

            Hãy gợi ý thông tin thẻ học chuẩn xác cho từ trên.
            Trả về DUY NHẤT một chuỗi JSON thuần hợp lệ (không kèm markdown, không giải thích ngoài JSON) với đúng 5 khóa sau:
            - "$KEY_WORD": từ tiếng Anh đã chuẩn hóa chính tả (sửa lỗi gõ nhẹ nếu cần, giữ dạng nguyên thể phổ biến).
            - "$KEY_PHONETIC": phiên âm IPA chuẩn tiếng Anh Mỹ (US), đặt trong dấu gạch chéo (ví dụ: "/əˈbɪl.ə.ti/").
            - "$KEY_PART_OF_SPEECH": đúng một trong 7 giá trị sau: $allowedPosList.
            - "$KEY_MEANING": nghĩa tiếng Việt ngắn gọn, phổ biến nhất của từ.
            - "$KEY_EXAMPLE": 1 câu ví dụ tiếng Anh tự nhiên, rõ ngữ cảnh có chứa từ đó.

            Ví dụ cấu trúc JSON hợp lệ:
            {
              "$KEY_WORD": "ability",
              "$KEY_PHONETIC": "/əˈbɪl.ə.ti/",
              "$KEY_PART_OF_SPEECH": "NOUN",
              "$KEY_MEANING": "khả năng, năng lực",
              "$KEY_EXAMPLE": "She has the ability to solve complex problems quickly."
            }
        """.trimIndent()
    }
}
