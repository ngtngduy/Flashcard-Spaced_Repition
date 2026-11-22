package com.uth.memora.core.database

import com.uth.memora.core.database.converter.Converters
import com.uth.memora.core.database.mapper.toDomain
import com.uth.memora.core.database.mapper.toEntity
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardScheduleSnapshot
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.ReviewLog
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kiểm thử chuyển đổi 2 chiều Entity <-> Domain và bộ TypeConverter của Room (M1-A7).
 */
class MapperTest {

    private val converters = Converters()

    @Test
    fun deckRoundTrip_preservesAllFieldsAndNullables() {
        val prebuiltDeck = Deck(
            id = "deck-001",
            ownerId = "user-uid-123",
            name = "Oxford 3000 cơ bản",
            description = "Bộ từ vựng cốt lõi cho người mới bắt đầu",
            sourcePrebuiltId = "prebuilt_oxford_3000",
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_000_123_456L,
            isDeleted = true,
            syncStatus = SyncStatus.SYNCED
        )
        assertEquals(prebuiltDeck, prebuiltDeck.toEntity().toDomain())

        val customDeck = Deck(
            id = "deck-002",
            ownerId = Deck.GUEST_OWNER_ID,
            name = "Từ vựng TOEIC",
            description = "",
            sourcePrebuiltId = null,
            createdAtMillis = 1_700_000_200_000L,
            updatedAtMillis = 1_700_000_200_000L,
            isDeleted = false,
            syncStatus = SyncStatus.SYNC_ERROR
        )
        assertEquals(customDeck, customDeck.toEntity().toDomain())
    }

    @Test
    fun cardRoundTrip_preservesPopulatedAndNullableFields() {
        val fullCard = Card(
            id = "card-001",
            deckId = "deck-001",
            ownerId = "user-uid-123",
            word = "resilient",
            meaning = "kiên cường, có khả năng phục hồi nhanh",
            phonetic = "/rɪˈzɪl.jənt/",
            partOfSpeech = PartOfSpeech.ADJECTIVE,
            example = "She stayed resilient despite repeated setbacks.",
            localImagePath = "/data/user/0/com.uth.memora/files/img_1.jpg",
            cloudImageUrl = "https://firebasestorage.googleapis.com/card-001.jpg",
            state = CardState.REVIEW,
            easeFactor = 2.36,
            repetitions = 4,
            interval = 14,
            lapses = 1,
            dueDay = 20_415L,
            dueAtMillis = 1_700_500_000_000L,
            firstSeenDay = 20_400L,
            lastReviewedAtMillis = 1_700_400_000_000L,
            createdAtMillis = 1_700_000_000_000L,
            updatedAtMillis = 1_700_400_000_500L,
            isDeleted = false,
            syncStatus = SyncStatus.SYNCED
        )
        assertEquals(fullCard, fullCard.toEntity().toDomain())

        val minimalNewCard = Card(
            id = "card-002",
            deckId = "deck-001",
            ownerId = Deck.GUEST_OWNER_ID,
            word = "serendipity",
            meaning = "sự tình cờ may mắn",
            phonetic = "",
            partOfSpeech = null,
            example = "",
            localImagePath = null,
            cloudImageUrl = null,
            state = CardState.NEW,
            easeFactor = Card.INITIAL_EASE_FACTOR,
            repetitions = 0,
            interval = 0,
            lapses = 0,
            dueDay = null,
            dueAtMillis = null,
            firstSeenDay = null,
            lastReviewedAtMillis = null,
            createdAtMillis = 1_700_100_000_000L,
            updatedAtMillis = 1_700_100_000_000L,
            isDeleted = true,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        assertEquals(minimalNewCard, minimalNewCard.toEntity().toDomain())
    }

    @Test
    fun reviewLogRoundTrip_preservesEmbeddedSnapshotAndUndoFlag() {
        val reviewLog = ReviewLog(
            id = "log-001",
            cardId = "card-001",
            deckId = "deck-001",
            ownerId = "user-uid-123",
            quality = ReviewQuality.HARD,
            learningDay = 20_401L,
            reviewedAtMillis = 1_700_400_000_000L,
            durationValidSeconds = ReviewLog.MAX_SECONDS_PER_REVIEW,
            snapshotBeforeReview = CardScheduleSnapshot(
                state = CardState.LEARNING,
                easeFactor = 2.5,
                repetitions = 1,
                interval = 1,
                lapses = 0,
                dueDay = 20_401L,
                dueAtMillis = 1_700_399_400_000L,
                firstSeenDay = 20_400L,
                lastReviewedAtMillis = 1_700_398_800_000L
            ),
            isUndone = true,
            updatedAtMillis = 1_700_400_005_000L,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        assertEquals(reviewLog, reviewLog.toEntity().toDomain())

        val firstReviewLog = ReviewLog(
            id = "log-002",
            cardId = "card-002",
            deckId = "deck-001",
            ownerId = Deck.GUEST_OWNER_ID,
            quality = ReviewQuality.AGAIN,
            learningDay = 20_401L,
            reviewedAtMillis = 1_700_400_100_000L,
            durationValidSeconds = 7,
            snapshotBeforeReview = CardScheduleSnapshot(
                state = CardState.NEW,
                easeFactor = Card.INITIAL_EASE_FACTOR,
                repetitions = 0,
                interval = 0,
                lapses = 0,
                dueDay = null,
                dueAtMillis = null,
                firstSeenDay = null,
                lastReviewedAtMillis = null
            ),
            isUndone = false,
            updatedAtMillis = 1_700_400_100_000L,
            syncStatus = SyncStatus.SYNCED
        )
        assertEquals(firstReviewLog, firstReviewLog.toEntity().toDomain())
    }

    @Test
    fun practiceSentenceRoundTrip_preservesSpecialCharactersAndNullableResults() {
        val gradedSentence = PracticeSentence(
            id = "sent-001",
            cardId = "card-001",
            ownerId = "user-uid-123",
            targetWord = "resilient",
            sentence = "The team remained resilient, even under heavy pressure.",
            status = SentenceStatus.GRADED,
            score = 8.5f,
            cefrLevel = "B2",
            feedbackVi = "Câu dùng đúng nghĩa \"resilient\", cấu trúc tự nhiên.",
            grammarAnalysis = listOf(
                "Mệnh đề chính đúng thì quá khứ đơn, dùng \"remained\" tự nhiên.",
                "Gợi ý bổ sung:\nCó thể rút gọn \"even under\" -> \"under\" cho gọn.",
                "Kiểm tra ký tự đặc biệt: dấu phẩy, ngoặc [vuông], gạch chéo \\ và tab\tok."
            ),
            correctedSentence = "The team remained resilient even under heavy pressure.",
            naturalAlternative = "Even under intense pressure, the team stayed remarkably resilient.",
            errorMessage = null,
            retryCount = 1,
            learningDay = 20_401L,
            createdAtMillis = 1_700_400_000_000L,
            updatedAtMillis = 1_700_400_010_000L,
            syncStatus = SyncStatus.SYNCED
        )

        val entity = gradedSentence.toEntity()
        val encodedGrammar = converters.fromStringList(entity.grammarAnalysis)
        val decodedEntity = entity.copy(grammarAnalysis = converters.toStringList(encodedGrammar))
        assertEquals(gradedSentence, decodedEntity.toDomain())

        val errorSentence = PracticeSentence(
            id = "sent-002",
            cardId = "card-002",
            ownerId = Deck.GUEST_OWNER_ID,
            targetWord = "serendipity",
            sentence = "Finding this café was pure serendipity.",
            status = SentenceStatus.GRADE_ERROR,
            score = null,
            cefrLevel = null,
            feedbackVi = null,
            grammarAnalysis = emptyList(),
            correctedSentence = null,
            naturalAlternative = null,
            errorMessage = "Không thể kết nối dịch vụ AI sau 3 lần thử.",
            retryCount = PracticeSentence.MAX_RETRY_ATTEMPTS,
            learningDay = 20_401L,
            createdAtMillis = 1_700_400_200_000L,
            updatedAtMillis = 1_700_400_500_000L,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        assertEquals(errorSentence, errorSentence.toEntity().toDomain())
    }

    @Test
    fun converters_roundTripAllEnumsAndHandleFallbacksSafely() {
        CardState.entries.forEach { state ->
            assertEquals(state, converters.toCardState(converters.fromCardState(state)))
        }
        assertEquals(CardState.NEW, converters.toCardState(null))
        assertEquals(CardState.NEW, converters.toCardState("UNKNOWN_STATE"))

        ReviewQuality.entries.forEach { quality ->
            assertEquals(quality, converters.toReviewQuality(converters.fromReviewQuality(quality)))
        }
        assertEquals(ReviewQuality.GOOD, converters.toReviewQuality(""))
        assertEquals(ReviewQuality.GOOD, converters.toReviewQuality("INVALID"))

        PartOfSpeech.entries.forEach { pos ->
            assertEquals(pos, converters.toPartOfSpeech(converters.fromPartOfSpeech(pos)))
        }
        assertNull(converters.fromPartOfSpeech(null))
        assertNull(converters.toPartOfSpeech(null))
        assertNull(converters.toPartOfSpeech("   "))
        assertNull(converters.toPartOfSpeech("NON_EXISTENT"))

        SyncStatus.entries.forEach { status ->
            assertEquals(status, converters.toSyncStatus(converters.fromSyncStatus(status)))
        }
        assertEquals(SyncStatus.PENDING_SYNC, converters.toSyncStatus(null))
        assertEquals(SyncStatus.PENDING_SYNC, converters.toSyncStatus("BAD"))

        SentenceStatus.entries.forEach { status ->
            assertEquals(status, converters.toSentenceStatus(converters.fromSentenceStatus(status)))
        }
        assertEquals(SentenceStatus.PENDING_GRADE, converters.toSentenceStatus(""))
        assertEquals(SentenceStatus.PENDING_GRADE, converters.toSentenceStatus("BAD"))
    }

    @Test
    fun converters_stringListPreservesEdgeCasesAndRejectsCorruptInputSafely() {
        assertEquals("[]", converters.fromStringList(null))
        assertEquals("[]", converters.fromStringList(emptyList()))
        assertEquals(emptyList<String>(), converters.toStringList(null))
        assertEquals(emptyList<String>(), converters.toStringList(""))
        assertEquals(emptyList<String>(), converters.toStringList("[]"))
        assertEquals(emptyList<String>(), converters.toStringList("   [   ]  "))

        val trickyItems = listOf(
            "",
            "Simple item",
            "Có dấu phẩy, chấm phẩy; hai chấm:",
            "Dòng 1\nDòng 2\r\nDòng 3\tTab",
            "\"Trích dẫn\" và dấu gạch \\ ngược \\\"kép\\\"",
            "Ký tự JSON [{\"k\": \"v\"}] và tiếng Việt Ắ Ặ Ử Ỹ",
            "Control char \u0007 và form-feed \u000C và backspace \b"
        )
        val serialized = converters.fromStringList(trickyItems)
        assertEquals(trickyItems, converters.toStringList(serialized))

        // Hỗ trợ chuỗi JSON có escape \uXXXX và \/
        assertEquals(listOf("A/B", "€"), converters.toStringList("""["A\/B", "\u20AC"]"""))

        // Dữ liệu hỏng phải trả về danh sách rỗng, không ném ngoại lệ
        val corruptedInputs = listOf(
            "not-a-json-array",
            "[\"unterminated",
            "[\"trailing-comma\",]",
            "[\"invalid-escape\\x\"]",
            "[\"short-unicode\\u12\"]",
            "[123, 456]"
        )
        corruptedInputs.forEach { bad ->
            assertEquals("Failed for input: $bad", emptyList<String>(), converters.toStringList(bad))
        }
    }
}
