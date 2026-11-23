package com.uth.memora.integration.ai

import com.uth.memora.core.model.AiEvaluationResult

/**
 * Ngoại lệ đánh dấu phản hồi JSON từ Gemini AI sai định dạng hoặc thiếu trường bắt buộc (Mục 6.7 & 9.5).
 */
class AiJsonParseException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Bộ phân tích JSON thuần Kotlin chuẩn RFC 8259 (không phụ thuộc `org.json` hay thư viện ngoài)
 * để chuyển phản hồi từ Gemini AI thành [AiEvaluationResult], chạy ổn định cả trên JVM Unit Test
 * lẫn thiết bị Android (Mục 6.7 & 9.5).
 *
 * Hỗ trợ:
 * - Gỡ vỏ bọc markdown (ví dụ ```json ... ```) hoặc văn bản thừa bao quanh JSON.
 * - Đọc ưu tiên khóa `snake_case` theo [GradingPrompt] và fallback sang `camelCase`.
 * - Giới hạn điểm số `score` trong đoạn `0f..10f`.
 */
object AiJsonParser {

    private const val MIN_SCORE: Float = 0f
    private const val MAX_SCORE: Float = 10f
    private const val MAX_NESTING_DEPTH: Int = 32

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
            PureJsonReader(cleanedJson).parseRootObject()
        } catch (e: AiJsonParseException) {
            return Result.failure(e)
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

    private fun Map<String, Any?>.optScore(key: String): Float? {
        val value = this[key] ?: return null
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        } ?: return null

        if (!parsed.isFinite()) return null
        return parsed.toFloat()
    }

    private fun Map<String, Any?>.optNonBlankString(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] as? String ?: continue
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) return trimmed
        }
        return null
    }

    private fun Map<String, Any?>.optStringList(vararg keys: String): List<String> {
        for (key in keys) {
            val value = this[key] ?: continue
            when (value) {
                is List<*> -> {
                    return value.mapNotNull { item ->
                        (item as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    }
                }
                is String -> {
                    val trimmed = value.trim()
                    if (trimmed.isNotEmpty()) return listOf(trimmed)
                }
            }
        }
        return emptyList()
    }

    /**
     * Bộ đọc JSON đệ quy thuần Kotlin theo chuẩn RFC 8259 (tương tự cơ chế tự parse của Converters.kt).
     */
    private class PureJsonReader(private val source: String) {
        private var index: Int = 0
        private var depth: Int = 0

        fun parseRootObject(): Map<String, Any?> {
            skipWhitespace()
            val obj = parseObject()
            skipWhitespace()
            if (index != source.length) {
                throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }
            return obj
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (index >= source.length) {
                throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }
            return when (source[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> {
                    consumeLiteral("true")
                    true
                }
                'f' -> {
                    consumeLiteral("false")
                    false
                }
                'n' -> {
                    consumeLiteral("null")
                    null
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }
        }

        private fun parseObject(): Map<String, Any?> {
            enterNestedContainer()
            try {
                expectChar('{')
                skipWhitespace()

                val map = LinkedHashMap<String, Any?>()
                if (peekChar() == '}') {
                    index++
                    return map
                }

                while (true) {
                    skipWhitespace()
                    if (peekChar() != '"') {
                        throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                    }
                    val key = parseString()
                    skipWhitespace()
                    expectChar(':')
                    val value = parseValue()
                    map[key] = value
                    skipWhitespace()

                    when (peekChar()) {
                        ',' -> index++
                        '}' -> {
                            index++
                            return map
                        }
                        else -> throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                    }
                }
            } finally {
                depth--
            }
        }

        private fun parseArray(): List<Any?> {
            enterNestedContainer()
            try {
                expectChar('[')
                skipWhitespace()

                val list = mutableListOf<Any?>()
                if (peekChar() == ']') {
                    index++
                    return list
                }

                while (true) {
                    list.add(parseValue())
                    skipWhitespace()

                    when (peekChar()) {
                        ',' -> index++
                        ']' -> {
                            index++
                            return list
                        }
                        else -> throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                    }
                }
            } finally {
                depth--
            }
        }

        private fun parseString(): String {
            expectChar('"')
            val builder = StringBuilder()

            while (index < source.length) {
                val ch = source[index++]
                when {
                    ch == '"' -> return builder.toString()
                    ch == '\\' -> {
                        if (index >= source.length) {
                            throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                        }
                        when (source[index++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (index + 4 > source.length) {
                                    throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                                }
                                val hex = source.substring(index, index + 4)
                                val codePoint = hex.toIntOrNull(16)
                                    ?: throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                                builder.append(codePoint.toChar())
                                index += 4
                            }
                            else -> throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                        }
                    }
                    ch.code < 0x20 -> throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                    else -> builder.append(ch)
                }
            }

            throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
        }

        private fun parseNumber(): Double {
            val start = index
            if (peekChar() == '-') {
                index++
            }
            if (index >= source.length) {
                throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }

            // Phần nguyên: 0 hoặc [1-9][0-9]*
            when (source[index]) {
                '0' -> index++
                in '1'..'9' -> {
                    while (index < source.length && source[index] in '0'..'9') {
                        index++
                    }
                }
                else -> throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }

            // Phần thập phân (tùy chọn)
            if (peekChar() == '.') {
                index++
                if (index >= source.length || source[index] !in '0'..'9') {
                    throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                }
                while (index < source.length && source[index] in '0'..'9') {
                    index++
                }
            }

            // Phần mũ khoa học e/E (tùy chọn)
            val expChar = peekChar()
            if (expChar == 'e' || expChar == 'E') {
                index++
                val sign = peekChar()
                if (sign == '+' || sign == '-') {
                    index++
                }
                if (index >= source.length || source[index] !in '0'..'9') {
                    throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
                }
                while (index < source.length && source[index] in '0'..'9') {
                    index++
                }
            }

            val rawNumber = source.substring(start, index)
            val parsed = rawNumber.toDoubleOrNull()
                ?: throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            if (!parsed.isFinite()) {
                throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }
            return parsed
        }

        private fun consumeLiteral(literal: String) {
            if (!source.startsWith(literal, startIndex = index)) {
                throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }
            index += literal.length
        }

        private fun expectChar(expected: Char) {
            if (index >= source.length || source[index] != expected) {
                throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }
            index++
        }

        private fun peekChar(): Char? = if (index < source.length) source[index] else null

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) {
                index++
            }
        }

        private fun enterNestedContainer() {
            depth++
            if (depth > MAX_NESTING_DEPTH) {
                throw AiJsonParseException(ERROR_INVALID_FORMAT_VI)
            }
        }
    }
}
