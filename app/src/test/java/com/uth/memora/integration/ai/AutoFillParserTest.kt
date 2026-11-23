package com.uth.memora.integration.ai

import com.uth.memora.core.model.PartOfSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test cho [AiJsonParser.parseAutoFillJson] và [AiJsonParser.normalizePartOfSpeech]
 * trong luồng AI gợi ý tự điền thông tin thẻ học (Mục 6.3, 9.5 & 11).
 */
class AutoFillParserTest {

    @Test
    fun parseAutoFillJson_validFiveFieldJson_returnsExpectedCardData() {
        val rawJson = """
            {
              "word": "ability",
              "phonetic": "/əˈbɪl.ə.ti/",
              "partOfSpeech": "NOUN",
              "meaning": "khả năng, năng lực",
              "example": "She has the ability to solve complex problems quickly."
            }
        """.trimIndent()

        val result = AiJsonParser.parseAutoFillJson(
            rawJson = rawJson,
            fallbackWord = "abilty"
        )

        assertTrue("Expected success for valid 5-field JSON", result.isSuccess)
        val cardData = result.getOrThrow()
        assertEquals("ability", cardData.word)
        assertEquals("/əˈbɪl.ə.ti/", cardData.phonetic)
        assertEquals(PartOfSpeech.NOUN, cardData.partOfSpeech)
        assertEquals("khả năng, năng lực", cardData.meaning)
        assertEquals(
            "She has the ability to solve complex problems quickly.",
            cardData.example
        )
    }

    @Test
    fun parseAutoFillJson_markdownFenceWrappedJson_stripsFenceAndParsesSuccessfully() {
        val markdownWrapped = """
            Dưới đây là gợi ý thông tin thẻ từ vựng cho bạn:
            ```json
            {
              "word": "resilient",
              "phonetic": "/rɪˈzɪl.jənt/",
              "partOfSpeech": "ADJECTIVE",
              "meaning": "kiên cường, bền bỉ",
              "example": "Small businesses proved remarkably resilient during the downturn."
            }
            ```
        """.trimIndent()

        val result = AiJsonParser.parseAutoFillJson(
            rawJson = markdownWrapped,
            fallbackWord = "resilient"
        )

        assertTrue("Expected success for markdown-wrapped JSON", result.isSuccess)
        val cardData = result.getOrThrow()
        assertEquals("resilient", cardData.word)
        assertEquals("/rɪˈzɪl.jənt/", cardData.phonetic)
        assertEquals(PartOfSpeech.ADJECTIVE, cardData.partOfSpeech)
        assertEquals("kiên cường, bền bỉ", cardData.meaning)
        assertEquals(
            "Small businesses proved remarkably resilient during the downturn.",
            cardData.example
        )
    }

    @Test
    fun parseAutoFillJson_missingWordUsesFallbackAndNormalizesPartOfSpeechVariants() {
        val nounVariantJson = """
            {
              "phonetic": "/ˈhæb.ɪt/",
              "part_of_speech": "n.",
              "meaning": "thói quen",
              "example": "Reading every night is a great habit."
            }
        """.trimIndent()

        val nounResult = AiJsonParser.parseAutoFillJson(nounVariantJson, fallbackWord = "  habit  ")
        assertTrue(nounResult.isSuccess)
        val nounCard = nounResult.getOrThrow()
        assertEquals("habit", nounCard.word)
        assertEquals(PartOfSpeech.NOUN, nounCard.partOfSpeech)

        val adjVariantJson = """
            {
              "word": "vivid",
              "phonetic": "/ˈvɪv.ɪd/",
              "partOfSpeech": "adj",
              "meaning": "sinh động, sống động",
              "example": "He has vivid memories of his childhood."
            }
        """.trimIndent()

        val adjResult = AiJsonParser.parseAutoFillJson(adjVariantJson, fallbackWord = "vivid")
        assertTrue(adjResult.isSuccess)
        assertEquals(PartOfSpeech.ADJECTIVE, adjResult.getOrThrow().partOfSpeech)

        val verbVariantJson = """
            {
              "word": "adapt",
              "phonetic": "/əˈdæpt/",
              "partOfSpeech": "Động từ",
              "meaning": "thích nghi",
              "example": "Teams must adapt quickly to changing requirements."
            }
        """.trimIndent()

        val verbResult = AiJsonParser.parseAutoFillJson(verbVariantJson, fallbackWord = "adapt")
        assertTrue(verbResult.isSuccess)
        assertEquals(PartOfSpeech.VERB, verbResult.getOrThrow().partOfSpeech)
    }

    @Test
    fun normalizePartOfSpeech_knownEnglishAbbreviationAndVietnameseVariants_mapsCorrectly() {
        // Biến thể danh từ
        assertEquals(PartOfSpeech.NOUN, AiJsonParser.normalizePartOfSpeech("noun"))
        assertEquals(PartOfSpeech.NOUN, AiJsonParser.normalizePartOfSpeech("NOUN"))
        assertEquals(PartOfSpeech.NOUN, AiJsonParser.normalizePartOfSpeech("n."))
        assertEquals(PartOfSpeech.NOUN, AiJsonParser.normalizePartOfSpeech(" (N.) "))
        assertEquals(PartOfSpeech.NOUN, AiJsonParser.normalizePartOfSpeech("danh từ"))
        assertEquals(PartOfSpeech.NOUN, AiJsonParser.normalizePartOfSpeech("Danh Từ"))

        // Biến thể động từ
        assertEquals(PartOfSpeech.VERB, AiJsonParser.normalizePartOfSpeech("verb"))
        assertEquals(PartOfSpeech.VERB, AiJsonParser.normalizePartOfSpeech("v."))
        assertEquals(PartOfSpeech.VERB, AiJsonParser.normalizePartOfSpeech("Động từ"))
        assertEquals(PartOfSpeech.VERB, AiJsonParser.normalizePartOfSpeech("ĐỘNG TỪ"))
        assertEquals(PartOfSpeech.VERB, AiJsonParser.normalizePartOfSpeech("phrasal verb"))

        // Biến thể tính từ
        assertEquals(PartOfSpeech.ADJECTIVE, AiJsonParser.normalizePartOfSpeech("adj"))
        assertEquals(PartOfSpeech.ADJECTIVE, AiJsonParser.normalizePartOfSpeech("ADJ."))
        assertEquals(PartOfSpeech.ADJECTIVE, AiJsonParser.normalizePartOfSpeech("adjective"))
        assertEquals(PartOfSpeech.ADJECTIVE, AiJsonParser.normalizePartOfSpeech("tính từ"))

        // Biến thể trạng từ
        assertEquals(PartOfSpeech.ADVERB, AiJsonParser.normalizePartOfSpeech("adv"))
        assertEquals(PartOfSpeech.ADVERB, AiJsonParser.normalizePartOfSpeech("adv."))
        assertEquals(PartOfSpeech.ADVERB, AiJsonParser.normalizePartOfSpeech("adverb"))
        assertEquals(PartOfSpeech.ADVERB, AiJsonParser.normalizePartOfSpeech("trạng từ"))

        // Biến thể giới từ và liên từ
        assertEquals(PartOfSpeech.PREPOSITION, AiJsonParser.normalizePartOfSpeech("prep"))
        assertEquals(PartOfSpeech.PREPOSITION, AiJsonParser.normalizePartOfSpeech("preposition"))
        assertEquals(PartOfSpeech.PREPOSITION, AiJsonParser.normalizePartOfSpeech("giới từ"))
        assertEquals(PartOfSpeech.CONJUNCTION, AiJsonParser.normalizePartOfSpeech("conj"))
        assertEquals(PartOfSpeech.CONJUNCTION, AiJsonParser.normalizePartOfSpeech("conjunction"))
        assertEquals(PartOfSpeech.CONJUNCTION, AiJsonParser.normalizePartOfSpeech("liên từ"))
    }

    @Test
    fun normalizePartOfSpeech_unknownOrBlankValues_defaultsToOther() {
        val unknownValues = listOf(
            null,
            "",
            "   ",
            "other",
            "OTHER",
            "khác",
            "pronoun",
            "interjection",
            "idiom",
            "slang",
            "không xác định",
            "123"
        )

        unknownValues.forEach { raw ->
            assertEquals(
                "Expected PartOfSpeech.OTHER for input: $raw",
                PartOfSpeech.OTHER,
                AiJsonParser.normalizePartOfSpeech(raw)
            )
        }
    }

    @Test
    fun parseAutoFillJson_malformedOrIncompleteJson_returnsFailure() {
        val invalidInputs = listOf(
            null,
            "",
            "   ",
            "Xin lỗi, tôi không tìm thấy từ này.",
            """{"word": "ability", "meaning": "khả năng""",
            """{word: "ability", meaning: "khả năng"}""",
            "{}",
            """{"word": "ability", "phonetic": "/əˈbɪl.ə.ti/", "partOfSpeech": "NOUN"}"""
        )

        invalidInputs.forEach { raw ->
            val result = AiJsonParser.parseAutoFillJson(rawJson = raw, fallbackWord = "ability")
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
