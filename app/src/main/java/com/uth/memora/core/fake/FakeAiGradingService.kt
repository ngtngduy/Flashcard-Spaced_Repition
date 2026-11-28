package com.uth.memora.core.fake

import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.model.AiEvaluationResult
import kotlinx.coroutines.delay

/**
 * Dịch vụ chấm câu tiếng Anh dự phòng thông minh (Offline / Fallback Grammar & Vocabulary Evaluator).
 * Phân tích trực tiếp câu tiếng Anh của người học (kiểm tra mạo từ a/an, chia động từ, từ loại,
 * cấu trúc câu với từ mục tiêu), đưa ra bản sửa lỗi chính xác (`correctedSentence`), nhận xét
 * tiếng Việt chi tiết và câu diễn đạt tự nhiên (`naturalAlternative`).
 */
class FakeAiGradingService(
    private val simulatedDelayMillis: Long = 600L
) : AiGradingService {

    override suspend fun evaluateSentence(
        targetWord: String,
        meaningVi: String,
        userSentence: String
    ): Result<AiEvaluationResult> {
        if (simulatedDelayMillis > 0L) {
            delay(simulatedDelayMillis)
        }

        val trimmed = userSentence.trim()
        val cleanTarget = targetWord.trim()
        val grammarNotes = mutableListOf<String>()
        var corrected = trimmed
        var penalty = 0f

        // 1. Kiểm tra mạo từ "a" đứng trước nguyên âm (VD: "a important" -> "an important")
        val aBeforeVowelRegex = Regex("""\b([Aa])\s+([aeiouAEIOU][a-zA-Z]+)\b""")
        if (aBeforeVowelRegex.containsMatchIn(corrected)) {
            val match = aBeforeVowelRegex.find(corrected)
            val wrongPhrase = match?.value.orEmpty()
            corrected = aBeforeVowelRegex.replace(corrected) { m ->
                val article = if (m.groupValues[1] == "A") "An" else "an"
                "$article ${m.groupValues[2]}"
            }
            grammarNotes.add("Mạo từ: Dùng \"an\" thay vì \"a\" trước từ bắt đầu bằng nguyên âm (sửa \"$wrongPhrase\").")
            penalty += 0.8f
        }

        // 2. Kiểm tra cụm "We are live once" -> "We only live once"
        val weAreLiveOnceRegex = Regex("""\b[Ww]e\s+are\s+live\s+once\b""")
        if (weAreLiveOnceRegex.containsMatchIn(corrected)) {
            corrected = weAreLiveOnceRegex.replace(corrected) { m ->
                if (m.value.first().isUpperCase()) "We only live once" else "we only live once"
            }
            grammarNotes.add("Thì hiện tại đơn: Động từ thường \"live\" không đi kèm động từ to-be \"are\" (sửa \"We are live once\" thành \"We only live once\").")
            penalty += 0.9f
        }

        // 3. Kiểm tra "so make everyone ... is" -> danh động từ (Gerund) làm chủ ngữ "so making everyone ... is"
        val soMakeSubjectRegex = Regex("""\bso\s+make\s+([^,.]+?)\s+is\b""", RegexOption.IGNORE_CASE)
        if (soMakeSubjectRegex.containsMatchIn(corrected)) {
            corrected = soMakeSubjectRegex.replace(corrected) { m ->
                "so making ${m.groupValues[1]} is"
            }
            grammarNotes.add("Cấu trúc chủ ngữ: Khi động từ làm chủ ngữ đứng trước \"is\", cần chia ở dạng V-ing (\"making ... is\" thay vì \"make ... is\").")
            penalty += 0.8f
        }

        // 4. Kiểm tra nhầm từ loại động từ "achieve" ở vị trí danh từ sau tính từ ("important achieve" -> "important achievement")
        val adjAchieveRegex = Regex("""\b(important|great|big|major|significant)\s+achieve\b""", RegexOption.IGNORE_CASE)
        if (adjAchieveRegex.containsMatchIn(corrected)) {
            corrected = adjAchieveRegex.replace(corrected) { m ->
                "${m.groupValues[1]} achievement"
            }
            grammarNotes.add("Từ loại (Word Form): Sau tính từ cần một danh từ, nên đổi động từ \"achieve\" thành danh từ \"achievement\".")
            penalty += 0.8f
        }

        // 5. Kiểm tra đại từ nhân xưng "i" viết thường
        val lowerIRegex = Regex("""\bi\b""")
        if (lowerIRegex.containsMatchIn(corrected)) {
            corrected = lowerIRegex.replace(corrected, "I")
            grammarNotes.add("Chính tả: Đại từ nhân xưng \"I\" luôn phải viết hoa trong tiếng Anh.")
            penalty += 0.5f
        }

        // 6. Kiểm tra hòa hợp chủ ngữ - động từ cơ bản (He/She/It + V nguyên mẫu phổ biến)
        val thirdPersonVerbRegex = Regex("""\b([Hh]e|[Ss]he|[Ii]t)\s+(go|have|do|make|need|want|like|work|study|learn)\b""")
        if (thirdPersonVerbRegex.containsMatchIn(corrected)) {
            corrected = thirdPersonVerbRegex.replace(corrected) { m ->
                val subj = m.groupValues[1]
                val verb = m.groupValues[2].lowercase()
                val conjugated = when (verb) {
                    "have" -> "has"
                    "go" -> "goes"
                    "do" -> "does"
                    "study" -> "studies"
                    else -> "${verb}s"
                }
                "$subj $conjugated"
            }
            grammarNotes.add("Hòa hợp chủ ngữ – động từ: Chủ ngữ ngôi thứ 3 số ít (He/She/It) cần chia động từ thêm -s/-es ở thì hiện tại đơn.")
            penalty += 0.8f
        }

        val wordCount = trimmed.split(Regex("""\s+""")).count { it.isNotBlank() }
        val baseScore = when {
            wordCount >= 12 -> 9.2f
            wordCount >= 8 -> 8.8f
            else -> 8.4f
        }
        val finalScore = ((baseScore - penalty) * 10f).toInt().toFloat() / 10f
            .coerceIn(5.5f, 9.8f)

        val cefrLevel = when {
            finalScore >= 9.0f && wordCount >= 10 -> "B2"
            finalScore >= 8.0f -> "B2"
            finalScore >= 7.0f -> "B1"
            else -> "A2"
        }

        if (grammarNotes.isEmpty()) {
            grammarNotes.add("Cấu trúc ngữ pháp và cách chia thì trong câu chính xác, mạch lạc.")
            grammarNotes.add("Từ mục tiêu \"$cleanTarget\" được sử dụng đúng ngữ cảnh và đúng chức năng từ loại.")
        } else {
            grammarNotes.add("Đã sử dụng từ mục tiêu \"$cleanTarget\" ($meaningVi) trong câu, hãy lưu ý các điểm ngữ pháp đã chỉnh sửa ở trên để câu chuẩn xác hơn.")
        }

        val feedbackVi = if (penalty > 0f) {
            "Câu của bạn có ý tưởng rất hay và đã đưa được từ \"$cleanTarget\" vào ngữ cảnh. Mình đã chỉ ra và sửa lại ${grammarNotes.size - 1} điểm ngữ pháp/từ loại bên dưới để câu tiếng Anh tự nhiên và chuẩn xác hơn nhé!"
        } else {
            "Câu của bạn sử dụng từ \"$cleanTarget\" ($meaningVi) rất tự nhiên, đúng ngữ pháp và truyền đạt ý rõ ràng!"
        }

        val naturalAlternative = buildNaturalAlternative(cleanTarget, corrected)

        return Result.success(
            AiEvaluationResult(
                score = finalScore.coerceIn(5.0f, 10.0f),
                cefrLevel = cefrLevel,
                feedbackVi = feedbackVi,
                grammarAnalysis = grammarNotes,
                correctedSentence = corrected,
                naturalAlternative = naturalAlternative
            )
        )
    }

    private fun buildNaturalAlternative(targetWord: String, correctedSentence: String): String {
        val lower = targetWord.lowercase()
        return when (lower) {
            "reliable" -> "We only live once, so building a reputation as a reliable person is a truly important achievement."
            "approach" -> "Adopting a practical approach allows our team to solve complex problems efficiently."
            "achieve" -> "With consistent daily effort, you can achieve even your most ambitious goals."
            "benefit" -> "One major benefit of spaced repetition is long-term vocabulary retention."
            "challenge" -> "Overcoming a difficult challenge helps you grow stronger and more confident."
            "common" -> "It is very common for language learners to practice with flashcards every day."
            "study" -> "Setting aside thirty minutes to study each morning makes a remarkable difference."
            else -> if (correctedSentence.length > 15) {
                "In everyday communication, using \"$targetWord\" in context—such as: \"$correctedSentence\"—helps express your idea clearly."
            } else {
                "Mastering how to use \"$targetWord\" naturally in daily conversation will significantly boost your fluency."
            }
        }
    }
}
