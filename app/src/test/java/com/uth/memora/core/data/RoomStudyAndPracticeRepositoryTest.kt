package com.uth.memora.core.data

import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.PracticeSentenceDao
import com.uth.memora.core.database.dao.ReviewLogDao
import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.database.entity.PracticeSentenceEntity
import com.uth.memora.core.database.entity.ReviewLogEntity
import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.fake.FakeUserPreferencesRepository
import com.uth.memora.core.model.AiEvaluationResult
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.SyncStatus
import com.uth.memora.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử RoomStudySessionRepository và RoomPracticeSentenceRepository (M1-F1..F7).
 */
class RoomStudyAndPracticeRepositoryTest {

    private class FakeCardDao : CardDao {
        val cards = MutableStateFlow<List<CardEntity>>(emptyList())

        override fun observeCardsByDeck(deckId: String): Flow<List<CardEntity>> =
            cards.map { list -> list.filter { it.deckId == deckId && !it.isDeleted } }

        override fun observeAllActiveCards(ownerId: String): Flow<List<CardEntity>> =
            cards.map { list -> list.filter { it.ownerId == ownerId && !it.isDeleted } }

        override suspend fun getAllActiveCards(ownerId: String): List<CardEntity> =
            cards.value.filter { it.ownerId == ownerId && !it.isDeleted }

        override suspend fun getCardById(cardId: String): CardEntity? =
            cards.value.firstOrNull { it.id == cardId && !it.isDeleted }

        override suspend fun getCardByIdIncludingDeleted(cardId: String): CardEntity? =
            cards.value.firstOrNull { it.id == cardId }

        override suspend fun getCardByWordInDeck(deckId: String, word: String): CardEntity? =
            cards.value.firstOrNull {
                it.deckId == deckId && !it.isDeleted && it.word.equals(word, ignoreCase = true)
            }

        override suspend fun countActiveCardsInDeck(deckId: String): Int =
            cards.value.count { it.deckId == deckId && !it.isDeleted }

        override suspend fun upsertCard(card: CardEntity) {
            cards.value = cards.value.filterNot { it.id == card.id } + card
        }

        override suspend fun upsertCards(cards: List<CardEntity>) {
            val ids = cards.map { it.id }.toSet()
            this.cards.value = this.cards.value.filterNot { it.id in ids } + cards
        }

        override suspend fun softDeleteCard(
            cardId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {}

        override suspend fun softDeleteCardsByDeck(
            deckId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {}

        override suspend fun resetCardProgress(
            cardId: String,
            updatedAtMillis: Long,
            easeFactor: Double,
            state: CardState,
            syncStatus: SyncStatus
        ) {}

        override suspend fun resetDeckProgress(
            deckId: String,
            updatedAtMillis: Long,
            easeFactor: Double,
            state: CardState,
            syncStatus: SyncStatus
        ) {}

        override suspend fun countNewCardsLearnedOnDay(ownerId: String, learningDay: Long): Int =
            cards.value.count {
                it.ownerId == ownerId && !it.isDeleted && it.firstSeenDay == learningDay
            }

        override suspend fun getNewCardsQueue(
            ownerId: String,
            deckId: String?,
            limit: Int
        ): List<CardEntity> = cards.value
            .filter {
                it.ownerId == ownerId && !it.isDeleted &&
                    (deckId == null || it.deckId == deckId) && it.state == CardState.NEW
            }
            .sortedBy { it.createdAtMillis }
            .take(limit)

        override suspend fun getDueLearningCards(
            ownerId: String,
            deckId: String?,
            nowMillis: Long
        ): List<CardEntity> = cards.value
            .filter {
                it.ownerId == ownerId && !it.isDeleted &&
                    (deckId == null || it.deckId == deckId) &&
                    it.state == CardState.LEARNING &&
                    it.dueAtMillis != null && it.dueAtMillis <= nowMillis
            }
            .sortedBy { it.dueAtMillis }

        override suspend fun getDueReviewCards(
            ownerId: String,
            deckId: String?,
            currentLearningDay: Long
        ): List<CardEntity> = cards.value
            .filter {
                it.ownerId == ownerId && !it.isDeleted &&
                    (deckId == null || it.deckId == deckId) &&
                    it.state == CardState.REVIEW &&
                    it.dueDay != null && it.dueDay <= currentLearningDay
            }
            .sortedWith(compareBy<CardEntity> { it.dueDay }.thenBy { it.updatedAtMillis })

        override suspend fun countTomorrowDueCards(
            ownerId: String,
            deckId: String?,
            tomorrowDay: Long
        ): Int = cards.value.count {
            it.ownerId == ownerId && !it.isDeleted &&
                (deckId == null || it.deckId == deckId) &&
                it.state == CardState.REVIEW && it.dueDay == tomorrowDay
        }

        override suspend fun deleteAllByOwner(ownerId: String) {}
        override suspend fun purgeSyncedDeletedBefore(cutoffMillis: Long) {}
        override suspend fun getPendingSyncCards(ownerId: String): List<CardEntity> = emptyList()
        override suspend fun updateSyncStatus(
            id: String,
            syncStatus: SyncStatus,
            updatedAtMillis: Long
        ) {}
        override fun countPendingSync(ownerId: String): Flow<Int> = cards.map { 0 }
        override suspend fun reassignOwner(
            fromOwnerId: String,
            toOwnerId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {}
    }

    private class FakeReviewLogDao : ReviewLogDao {
        val logs = MutableStateFlow<List<ReviewLogEntity>>(emptyList())

        override suspend fun insertLog(log: ReviewLogEntity) {
            logs.value = logs.value.filterNot { it.id == log.id } + log
        }

        override suspend fun upsertLogs(logs: List<ReviewLogEntity>) {
            val ids = logs.map { it.id }.toSet()
            this.logs.value = this.logs.value.filterNot { it.id in ids } + logs
        }

        override suspend fun getLogById(logId: String): ReviewLogEntity? =
            logs.value.firstOrNull { it.id == logId }

        override suspend fun getLatestActiveLog(ownerId: String): ReviewLogEntity? =
            logs.value
                .filter { it.ownerId == ownerId && !it.isUndone }
                .maxByOrNull { it.reviewedAtMillis }

        override suspend fun markLogUndone(
            logId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {
            logs.value = logs.value.map {
                if (it.id == logId) {
                    it.copy(isUndone = true, updatedAtMillis = updatedAtMillis, syncStatus = syncStatus)
                } else {
                    it
                }
            }
        }

        override fun observeLogsFromDay(
            ownerId: String,
            fromLearningDay: Long
        ): Flow<List<ReviewLogEntity>> = logs.map { list ->
            list.filter { it.ownerId == ownerId && !it.isUndone && it.learningDay >= fromLearningDay }
        }

        override fun observeAllValidLogs(ownerId: String): Flow<List<ReviewLogEntity>> =
            logs.map { list -> list.filter { it.ownerId == ownerId && !it.isUndone } }

        override suspend fun deleteAllByOwner(ownerId: String) {}
        override suspend fun getPendingSyncReviewLogs(ownerId: String): List<ReviewLogEntity> = emptyList()
        override suspend fun updateSyncStatus(
            id: String,
            syncStatus: SyncStatus,
            updatedAtMillis: Long
        ) {}
        override fun countPendingSync(ownerId: String): Flow<Int> = logs.map { 0 }
        override suspend fun reassignOwner(
            fromOwnerId: String,
            toOwnerId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {}
    }

    private class FakePracticeSentenceDao : PracticeSentenceDao {
        val sentences = MutableStateFlow<List<PracticeSentenceEntity>>(emptyList())

        override fun observeSentencesByCard(cardId: String): Flow<List<PracticeSentenceEntity>> =
            sentences.map { list ->
                list.filter { it.cardId == cardId }.sortedByDescending { it.createdAtMillis }
            }

        override fun observeRecentSentences(
            ownerId: String,
            limit: Int
        ): Flow<List<PracticeSentenceEntity>> = sentences.map { list ->
            list.filter { it.ownerId == ownerId }.sortedByDescending { it.createdAtMillis }.take(limit)
        }

        override suspend fun getSentenceById(id: String): PracticeSentenceEntity? =
            sentences.value.firstOrNull { it.id == id }

        override suspend fun upsertSentence(sentence: PracticeSentenceEntity) {
            sentences.value = sentences.value.filterNot { it.id == sentence.id } + sentence
        }

        override suspend fun upsertSentences(sentences: List<PracticeSentenceEntity>) {
            val ids = sentences.map { it.id }.toSet()
            this.sentences.value = this.sentences.value.filterNot { it.id in ids } + sentences
        }

        override suspend fun getPendingOrErrorSentences(ownerId: String): List<PracticeSentenceEntity> =
            sentences.value.filter {
                it.ownerId == ownerId &&
                    (it.status == SentenceStatus.PENDING_GRADE || it.status == SentenceStatus.GRADING)
            }

        override suspend fun getAllPendingSentences(): List<PracticeSentenceEntity> =
            sentences.value.filter { it.status == SentenceStatus.PENDING_GRADE }

        override suspend fun countSentencesOnDay(ownerId: String, learningDay: Long): Int =
            sentences.value.count { it.ownerId == ownerId && it.learningDay == learningDay }

        override suspend fun deleteAllByOwner(ownerId: String) {}
        override suspend fun getPendingSyncSentences(ownerId: String): List<PracticeSentenceEntity> = emptyList()
        override suspend fun updateSyncStatus(
            id: String,
            syncStatus: SyncStatus,
            updatedAtMillis: Long
        ) {}
        override fun countPendingSync(ownerId: String): Flow<Int> = sentences.map { 0 }
        override suspend fun reassignOwner(
            fromOwnerId: String,
            toOwnerId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {}
    }

    @Test
    fun studySession_handlesQueuesSubmitStreakUndoAndDuplicateTapGuard() = runTest {
        val cardDao = FakeCardDao()
        val reviewLogDao = FakeReviewLogDao()
        val prefsRepo = FakeUserPreferencesRepository(
            UserPreferences(dailyNewCardsLimit = 5, currentStreak = 3, lastStudyLearningDay = 20_399L)
        )
        var nowMillis = 1_700_000_000_000L
        val todayDay = 20_400L

        val studyRepo = RoomStudySessionRepository(
            database = null,
            cardDao = cardDao,
            reviewLogDao = reviewLogDao,
            preferencesRepository = prefsRepo,
            timeProvider = { nowMillis },
            learningDayProvider = { todayDay }
        )

        cardDao.upsertCards(
            listOf(
                CardEntity(
                    id = "c-new",
                    deckId = "d1",
                    ownerId = Deck.GUEST_OWNER_ID,
                    word = "resilient",
                    meaning = "kiên cường",
                    state = CardState.NEW,
                    createdAtMillis = 100L,
                    updatedAtMillis = 100L
                ),
                CardEntity(
                    id = "c-learn",
                    deckId = "d1",
                    ownerId = Deck.GUEST_OWNER_ID,
                    word = "eloquent",
                    meaning = "hùng biện",
                    state = CardState.LEARNING,
                    dueAtMillis = nowMillis - 1000L,
                    firstSeenDay = todayDay,
                    createdAtMillis = 200L,
                    updatedAtMillis = 200L
                ),
                CardEntity(
                    id = "c-rev",
                    deckId = "d1",
                    ownerId = Deck.GUEST_OWNER_ID,
                    word = "pragmatic",
                    meaning = "thực tế",
                    state = CardState.REVIEW,
                    interval = 4,
                    repetitions = 2,
                    dueDay = todayDay,
                    firstSeenDay = todayDay - 5,
                    createdAtMillis = 300L,
                    updatedAtMillis = 300L
                )
            )
        )

        // Đã có 1 thẻ firstSeenDay = todayDay -> hạn mức thẻ mới còn 5 - 1 = 4, trả về c-new
        val newQueue = studyRepo.getNewCardsQueue("d1")
        assertEquals(listOf("c-new"), newQueue.map { it.id })

        // Hàng đợi ôn tập trước khi mở thẻ mới: LEARNING ("c-learn") đứng trước REVIEW ("c-rev")
        val dueQueue = studyRepo.getDueReviewQueue("d1")
        assertEquals(listOf("c-learn", "c-rev"), dueQueue.map { it.id })

        // Đánh dấu hiển thị lần đầu cho c-new -> chuyển sang LEARNING và gán firstSeenDay = todayDay (Mục 6.5)
        val marked = studyRepo.markNewCardDisplayed("c-new").getOrThrow()
        assertEquals(todayDay, marked.firstSeenDay)
        assertEquals(CardState.LEARNING, marked.state)

        // Chấm c-new bằng GOOD -> chuyển sang REVIEW, dueDay = todayDay + 1, tăng streak 3 -> 4
        val reviewed = studyRepo.submitCardReview("c-new", ReviewQuality.GOOD, durationValidSeconds = 120).getOrThrow()
        assertEquals(CardState.REVIEW, reviewed.state)
        assertEquals(todayDay + 1L, reviewed.dueDay)
        assertEquals(4, prefsRepo.getUserPreferences().currentStreak)
        assertEquals(1, reviewLogDao.logs.value.size)
        assertEquals(ReviewLogMax60, reviewLogDao.logs.value.first().durationValidSeconds)
        assertEquals(1, studyRepo.getTomorrowDueCount("d1"))

        // Chống bấm đúp < 800ms trên cùng thẻ c-new -> không tạo log thứ 2
        nowMillis += 300L
        studyRepo.submitCardReview("c-new", ReviewQuality.GOOD, durationValidSeconds = 5).getOrThrow()
        assertEquals(1, reviewLogDao.logs.value.size)

        // Hoàn tác lượt vừa chấm -> thẻ c-new trở về trạng thái LEARNING trước khi bấm đánh giá, log isUndone = true
        nowMillis += 1000L
        val restored = studyRepo.undoLastReview().getOrThrow()
        assertEquals(CardState.LEARNING, restored.state)
        assertTrue(reviewLogDao.logs.value.first().isUndone)
        assertEquals(4, studyRepo.getButtonPreviewLabels(restored).size)
    }

    @Test
    fun practiceSentence_handlesValidationDeduplicationGradingAndSaveExample() = runTest {
        val cardDao = FakeCardDao()
        val sentenceDao = FakePracticeSentenceDao()
        val prefsRepo = FakeUserPreferencesRepository(
            UserPreferences(aiSentenceGradingRemainingToday = 2)
        )
        val aiService = object : AiGradingService {
            override suspend fun evaluateSentence(
                targetWord: String,
                meaningVi: String,
                userSentence: String
            ): Result<AiEvaluationResult> = Result.success(
                AiEvaluationResult(
                    score = 9.0f,
                    cefrLevel = "B2",
                    feedbackVi = "Câu viết rất tốt.",
                    grammarAnalysis = listOf("Đúng cấu trúc."),
                    correctedSentence = "She remained resilient during the crisis.",
                    naturalAlternative = "Throughout the crisis, she stayed resilient."
                )
            )
        }

        val practiceRepo = RoomPracticeSentenceRepository(
            practiceSentenceDao = sentenceDao,
            cardDao = cardDao,
            preferencesRepository = prefsRepo,
            aiGradingService = aiService
        )

        cardDao.upsertCard(
            CardEntity(
                id = "c1",
                deckId = "d1",
                ownerId = Deck.GUEST_OWNER_ID,
                word = "resilient",
                meaning = "kiên cường",
                createdAtMillis = 100L,
                updatedAtMillis = 100L
            )
        )

        // Câu thiếu từ mục tiêu bị chặn bởi SentenceLocalValidator
        assertTrue(practiceRepo.submitPracticeSentence("c1", "This sentence has no target.", isOnline = true).isFailure)

        // Khi mất mạng (isOnline = false) -> lưu PENDING_GRADE và chống tạo trùng nếu gửi lặp
        val offline1 = practiceRepo.submitPracticeSentence(
            cardId = "c1",
            sentence = "She is resilient in difficult times.",
            isOnline = false
        ).getOrThrow()
        assertEquals(SentenceStatus.PENDING_GRADE, offline1.status)

        val offlineDup = practiceRepo.submitPracticeSentence(
            cardId = "c1",
            sentence = "She is resilient in difficult times.",
            isOnline = false
        ).getOrThrow()
        assertEquals(offline1.id, offlineDup.id)
        assertEquals(1, sentenceDao.sentences.value.size)

        // Khi có mạng (isOnline = true) -> chấm điểm GRADED và trừ 1 lượt quota (2 -> 1)
        val graded = practiceRepo.submitPracticeSentence(
            cardId = "c1",
            sentence = "She remained resilient during the storm.",
            isOnline = true
        ).getOrThrow()
        assertEquals(SentenceStatus.GRADED, graded.status)
        assertEquals(1, prefsRepo.getUserPreferences().aiSentenceGradingRemainingToday)

        // Lưu câu đã sửa từ AI làm câu ví dụ cho thẻ gốc (Mục 6.7)
        val updatedCard = practiceRepo.saveCorrectedSentenceAsExample(graded.id).getOrThrow()
        assertEquals("She remained resilient during the crisis.", updatedCard.example)
        assertNotNull(cardDao.getCardById("c1"))
    }

    companion object {
        private const val ReviewLogMax60 = 60
    }
}
