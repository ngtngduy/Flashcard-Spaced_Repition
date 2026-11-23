package com.uth.memora.integration.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test cho [AiJsonParser] kiểm tra parse phản hồi JSON từ Gemini AI (Mục 6.7 & 9.5).
 */
class AiJsonParserTest {

    @Test
    fun parseEvaluationResult_validSixFieldSnakeCaseJson_returnsExpectedResult() {
        val rawJson = """
            {
              "score": 8.5,
              "cefr_level": "B2",
              "feedback_vi": "Câu dùng từ mục tiêu chính xác, cấu trúc ngữ pháp rõ ràng.",
              "grammar_analysis": [
                "Chia thì quá khứ đơn chính xác với ngữ cảnh.",
                "Đặt tính từ resilient đúng vị trí sau động từ nối remained."
              ],
              "corrected_sentence": "The team remained resilient despite heavy pressure.",
              "natural_alternative": "Even under intense pressure, the team stayed remarkably resilient."
            }
        """.trimIndent()

        val result = AiJsonParser.parseEvaluationResult(rawJson)

        assertTrue("Expected success for valid 6-field JSON", result.isSuccess)
        val evaluation = result.getOrThrow()
        assertEquals(8.5f, evaluation.score, 0.001f)
        assertEquals("B2", evaluation.cefrLevel)
        assertEquals(
            "Câu dùng từ mục tiêu chính xác, cấu trúc ngữ pháp rõ ràng.",
            evaluation.feedbackVi
        )
        assertEquals(
            listOf(
                "Chia thì quá khứ đơn chính xác với ngữ cảnh.",
                "Đặt tính từ resilient đúng vị trí sau động từ nối remained."
            ),
            evaluation.grammarAnalysis
        )
        assertEquals(
            "The team remained resilient despite heavy pressure.",
            evaluation.correctedSentence
        )
        assertEquals(
            "Even under intense pressure, the team stayed remarkably resilient.",
            evaluation.naturalAlternative
        )
    }

    @Test
    fun parseEvaluationResult_markdownCodeBlockWrappedJson_stripsFenceAndParsesSuccessfully() {
        val markdownWrapped = """
            Dưới đây là kết quả chấm điểm:
            ```json
            {
              "score": 9.0,
              "cefr_level": "c1",
              "feedback_vi": "Diễn đạt mạch lạc và tự nhiên.",
              "grammar_analysis": ["Mệnh đề trạng ngữ dùng chuẩn xác."],
              "corrected_sentence": "She remained resilient throughout the crisis.",
              "natural_alternative": "She showed remarkable resilience throughout the crisis."
            }
            ```
        """.trimIndent()

        val result = AiJsonParser.parseEvaluationResult(markdownWrapped)

        assertTrue("Expected success for markdown-wrapped JSON", result.isSuccess)
        val evaluation = result.getOrThrow()
        assertEquals(9.0f, evaluation.score, 0.001f)
        assertEquals("C1", evaluation.cefrLevel)
        assertEquals("Diễn đạt mạch lạc và tự nhiên.", evaluation.feedbackVi)
        assertEquals(listOf("Mệnh đề trạng ngữ dùng chuẩn xác."), evaluation.grammarAnalysis)
        assertEquals("She remained resilient throughout the crisis.", evaluation.correctedSentence)
        assertEquals(
            "She showed remarkable resilience throughout the crisis.",
            evaluation.naturalAlternative
        )
    }

    @Test
    fun parseEvaluationResult_missingGrammarAnalysis_returnsEmptyListSafely() {
        val missingGrammarJson = """
            {
              "score": 7.5,
              "cefr_level": "B1",
              "feedback_vi": "Câu đúng ý nghĩa cơ bản.",
              "corrected_sentence": "He is a resilient leader.",
              "natural_alternative": "He proves to be a truly resilient leader."
            }
        """.trimIndent()

        val result = AiJsonParser.parseEvaluationResult(missingGrammarJson)

        assertTrue("Expected success when grammar_analysis is omitted", result.isSuccess)
        val evaluation = result.getOrThrow()
        assertEquals(7.5f, evaluation.score, 0.001f)
        assertEquals("B1", evaluation.cefrLevel)
        assertEquals(emptyList<String>(), evaluation.grammarAnalysis)

        val nullGrammarJson = """
            {
              "score": 7.5,
              "cefrLevel": "B1",
              "feedbackVi": "Câu đúng ý nghĩa cơ bản.",
              "grammarAnalysis": null,
              "correctedSentence": "He is a resilient leader.",
              "naturalAlternative": "He proves to be a truly resilient leader."
            }
        """.trimIndent()

        val nullResult = AiJsonParser.parseEvaluationResult(nullGrammarJson)
        assertTrue("Expected success when grammarAnalysis is null", nullResult.isSuccess)
        assertEquals(emptyList<String>(), nullResult.getOrThrow().grammarAnalysis)
    }

    @Test
    fun parseEvaluationResult_camelCaseKeysAndOutOfRangeScore_clampsScoreIntoZeroToTen() {
        val highScoreJson = """
            {
              "score": 12.8,
              "cefrLevel": "C2",
              "feedbackVi": "Xuất sắc.",
              "grammarAnalysis": ["Không có lỗi ngữ pháp."],
              "correctedSentence": "A resilient mindset helps us grow.",
              "naturalAlternative": "Cultivating a resilient mindset fuels continuous growth."
            }
        """.trimIndent()

        val highResult = AiJsonParser.parseEvaluationResult(highScoreJson)
        assertTrue(highResult.isSuccess)
        assertEquals(10.0f, highResult.getOrThrow().score, 0.001f)

        val negativeScoreJson = """
            {
              "score": -2.5,
              "cefrLevel": "A1",
              "feedbackVi": "Cần sửa lại cấu trúc câu.",
              "grammarAnalysis": ["Thiếu động từ chính."],
              "correctedSentence": "She is resilient.",
              "naturalAlternative": "She is very resilient."
            }
        """.trimIndent()

        val lowResult = AiJsonParser.parseEvaluationResult(negativeScoreJson)
        assertTrue(lowResult.isSuccess)
        assertEquals(0.0f, lowResult.getOrThrow().score, 0.001f)
    }

    @Test
    fun parseEvaluationResult_malformedOrIncompleteJson_returnsFailure() {
        val invalidInputs = listOf(
            null,
            "",
            "   ",
            "Xin lỗi, không thể chấm câu này.",
            """{"score": 8.5, "cefr_level": "B1""",
            "{}",
            """{"score": "not-a-number", "cefr_level": "B1", "feedback_vi": "Ok", "corrected_sentence": "A.", "natural_alternative": "B."}""",
            """{"score": 8.0, "cefr_level": "B1", "corrected_sentence": "A.", "natural_alternative": "B."}"""
        )

        invalidInputs.forEach { raw ->
            val result = AiJsonParser.parseEvaluationResult(raw)
            assertTrue("Expected failure for invalid input: $raw", result.isFailure)
            val error = result.exceptionOrNull()
            assertTrue(
                "Expected AiJsonParseException for input: $raw",
                error is AiJsonParseException
            )
            assertNotNull(error?.message)
            assertTrue(error!!.message!!.isNotBlank())
        }
    }
}
