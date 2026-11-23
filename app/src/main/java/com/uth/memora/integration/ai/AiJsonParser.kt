package com.uth.memora.integration.ai

import com.uth.memora.core.model.AiEvaluationResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Ngoại lệ đánh dấu phản hồi JSON từ Gemini AI sai định dạng hoặc thiếu trường bắt buộc (Mục 6.7 & 9.5).
 */
class AiJsonParseException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Bộ phân tích JSON an toàn dùng `org.json.JSONObject` có sẵn trong Android SDK
 * để chuyển phản hồi từ Gemini AI thành [AiEvaluationResult] (Mục 6.7 & 9.5).
 *
 * Hỗ trợ:
 * - Gỡ vỏ bọc markdown (ví dụ ```json ... ```) hoặc văn bản thừa bao quanh JSON.
 * - Đọc ưu tiên khóa `snake_case` theo [GradingPrompt] và fallback sang `camelCase`.
 * - Giới hạn điểm số `score` trong đoạn `0f..10f`.
 */
object AiJsonParser {

    private const val MIN_SCORE: Float = 0f
    private const val MAX_SCORE: Float = 10f

    private const val FALLBACK_KEY_CEFR_LEVEL: String = "cefrLevel"
    private const val FALLBACK_KEY_FEEDBACK_VI: String = "feedbackVi"
    private const val FALLBACK_KEY_GRAMMAR_ANALYSIS: String = "grammarAnalysis"
    private const val FALLBACK_KEY_CORRECTED_SENTENCE: String = "correctedSentence"
    private const val FALLBACK_KEY_NATURAL_ALTERNATIVE: String = "naturalAlternative"

    private const val ERROR_INVALID_FORMAT_VI: String =
        "Phản hồi AI không đúng định dạng JSON chuẩn. Vui lòng thử chấm lại."

    private val MARKDOWN_FENCE_REGEX = Regex(
        pattern = "```(?:json)?\\s*([\\s\\S]*?)\\s*```",
        option = RegexOption.IGNORE_CASE
    )

    private val CEFR_EXTRACTION_REGEX = Regex("\\b(A1|A2|B1|B2|C1|C2)\\b")

    /**
     * Phân tích chuỗi phản hồi thô từ Gemini AI thành [AiEvaluationResult] (Mục 6.7).
     */
    fun parseEvaluationResult(rawResponse: String?): Result<AiEvaluationResult> {
        if (rawResponse.isNullOrBlank()) {
            return Result.failure(
                AiJsonParseException("AI trả về nội dung trống. Vui lòng thử chấm lại.")
            )
        }

        val cleanedJson = extractJsonObject(rawResponse)
        if (cleanedJson.isBlank()) {
            return Result.failure(AiJsonParseException(ERROR_INVALID_FORMAT_VI))
        }

        val jsonObject = try {
            JSONObject(cleanedJson)
        } catch (e: JSONException) {
            return Result.failure(AiJsonParseException(ERROR_INVALID_FORMAT_VI, e))
        }

        val score = jsonObject.optScore(GradingPrompt.KEY_SCORE)
            ?: return Result.failure(
                AiJsonParseException("Phản hồi AI thiếu hoặc sai định dạng điểm số.")
            )

        val rawCefrLevel = jsonObject.optNonBlankString(
            GradingPrompt.KEY_CEFR_LEVEL,
            FALLBACK_KEY_CEFR_LEVEL
        ) ?: return Result.failure(
            AiJsonParseException("Phản hồi AI thiếu trình độ CEFR.")
        )
        val cefrLevel = normalizeCefrLevel(rawCefrLevel)
            ?: return Result.failure(
                AiJsonParseException("Phản hồi AI trả về trình độ CEFR không hợp lệ.")
            )

        val feedbackVi = jsonObject.optNonBlankString(
            GradingPrompt.KEY_FEEDBACK_VI,
            FALLBACK_KEY_FEEDBACK_VI
        ) ?: return Result.failure(
            AiJsonParseException("Phản hồi AI thiếu nhận xét tiếng Việt.")
        )

        val grammarAnalysis = jsonObject.optStringList(
            GradingPrompt.KEY_GRAMMAR_ANALYSIS,
            FALLBACK_KEY_GRAMMAR_ANALYSIS
        )

        val correctedSentence = jsonObject.optNonBlankString(
            GradingPrompt.KEY_CORRECTED_SENTENCE,
            FALLBACK_KEY_CORRECTED_SENTENCE
        ) ?: return Result.failure(
            AiJsonParseException("Phản hồi AI thiếu câu đã sửa ngữ pháp.")
        )

        val naturalAlternative = jsonObject.optNonBlankString(
            GradingPrompt.KEY_NATURAL_ALTERNATIVE,
            FALLBACK_KEY_NATURAL_ALTERNATIVE
        ) ?: return Result.failure(
            AiJsonParseException("Phản hồi AI thiếu câu diễn đạt tự nhiên.")
        )

        return Result.success(
            AiEvaluationResult(
                score = score.coerceIn(MIN_SCORE, MAX_SCORE),
                cefrLevel = cefrLevel,
                feedbackVi = feedbackVi,
                grammarAnalysis = grammarAnalysis,
                correctedSentence = correctedSentence,
                naturalAlternative = naturalAlternative
            )
        )
    }

    /**
     * Tách phần JSON object hợp lệ từ phản hồi thô (gỡ vỏ bọc markdown code fence nếu có).
     */
    internal fun extractJsonObject(rawResponse: String): String {
        var text = rawResponse.trim().removePrefix("\uFEFF").trim()

        // Ưu tiên bóc nội dung bên trong khối ```json ... ``` hoặc ``` ... ```
        val fenceMatch = MARKDOWN_FENCE_REGEX.find(text)
        if (fenceMatch != null) {
            text = fenceMatch.groupValues[1].trim()
        } else if (text.startsWith("`") && text.endsWith("`") && text.length >= 2) {
            text = text.removePrefix("`").removeSuffix("`").trim()
        }

        // Bóc bỏ phần chữ thừa trước dấu '{' đầu tiên hoặc sau dấu '}' cuối cùng
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace == -1 || lastBrace <= firstBrace) {
            return ""
        }
        return text.substring(firstBrace, lastBrace + 1).trim()
    }

    private fun normalizeCefrLevel(rawLevel: String): String? {
        val upper = rawLevel.trim().uppercase()
        if (upper in GradingPrompt.VALID_CEFR_LEVELS) {
            return upper
        }
        return CEFR_EXTRACTION_REGEX.find(upper)?.groupValues?.get(1)
    }

    private fun JSONObject.optScore(key: String): Float? {
        if (!has(key) || isNull(key)) return null
        val parsed = when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        } ?: return null

        if (!parsed.isFinite()) return null
        return parsed.toFloat()
    }

    private fun JSONObject.optNonBlankString(vararg keys: String): String? {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = opt(key)
            if (value is String) {
                val trimmed = value.trim()
                if (trimmed.isNotEmpty()) return trimmed
            }
        }
        return null
    }

    private fun JSONObject.optStringList(vararg keys: String): List<String> {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            when (val value = opt(key)) {
                is JSONArray -> {
                    val result = mutableListOf<String>()
                    for (index in 0 until value.length()) {
                        if (!value.isNull(index)) {
                            val item = value.optString(index, "").trim()
                            if (item.isNotEmpty()) {
                                result.add(item)
                            }
                        }
                    }
                    return result
                }
                is String -> {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty()) return listOf(trimmed)
                }
            }
        }
        return emptyList()
    }
}
