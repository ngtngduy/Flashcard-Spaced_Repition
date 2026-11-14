package com.uth.memora.core.fake

import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AutoFillCardData
import com.uth.memora.core.model.PartOfSpeech

/**
 * Bản giả lập dịch vụ AI Auto-Fill điền nhanh thẻ học (In-Memory).
 */
class FakeAiCardAutoFillService : AiCardAutoFillService {
    override suspend fun autoFillCard(word: String): Result<AutoFillCardData> {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Từ vựng không được để trống."))
        }
        val sample = AutoFillCardData(
            word = trimmed,
            phonetic = "/${trimmed.lowercase()}/",
            partOfSpeech = PartOfSpeech.NOUN,
            meaning = "Nghĩa mẫu của từ $trimmed (gợi ý bởi AI)",
            example = "This is an example sentence demonstrating the word $trimmed."
        )
        return Result.success(sample)
    }
}
