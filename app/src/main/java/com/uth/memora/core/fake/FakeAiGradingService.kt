package com.uth.memora.core.fake

import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.model.AiEvaluationResult
import kotlinx.coroutines.delay

/**
 * Bản giả lập dịch vụ AI dùng trong quá trình phát triển giao diện & kiểm thử offline.
 * Có delay 2.2 giây để Thành viên 2 kiểm tra trực quan hiệu ứng Skeleton Shimmer Loading (Mục 6.7).
 */
class FakeAiGradingService(
    private val simulatedDelayMillis: Long = 2200L
) : AiGradingService {

    override suspend fun evaluateSentence(
        targetWord: String,
        meaningVi: String,
        userSentence: String
    ): Result<AiEvaluationResult> {
        delay(simulatedDelayMillis)

        val trimmed = userSentence.trim()
        return Result.success(
            AiEvaluationResult(
                score = 8.5f,
                cefrLevel = "B2",
                feedbackVi = "Câu của bạn sử dụng từ \"$targetWord\" ($meaningVi) đúng ngữ cảnh và cấu trúc ngữ pháp rõ ràng. Có thể tinh chỉnh nhẹ để câu văn tự nhiên hơn như người bản xứ.",
                grammarAnalysis = listOf(
                    "Cấu trúc mệnh đề chính được chia thì chính xác.",
                    "Từ mục tiêu \"$targetWord\" đặt đúng vị trí ngữ pháp trong câu."
                ),
                correctedSentence = trimmed,
                naturalAlternative = "Despite the unexpected challenges, the team remained remarkably $targetWord throughout the project."
            )
        )
    }
}
