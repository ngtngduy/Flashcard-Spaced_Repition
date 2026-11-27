package com.uth.memora.core.data

import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.database.dao.ReviewLogDao
import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.database.entity.DeckEntity
import com.uth.memora.core.database.entity.ReviewLogEntity
import com.uth.memora.core.database.mapper.toEntity
import com.uth.memora.core.fake.FakeUserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardScheduleSnapshot
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bộ kiểm thử [RoomStatisticsRepository], [PrebuiltDeckSeeder] và [RoomDeckRepository.importPrebuiltDeck] (M1-G).
 */
class RoomStatisticsAndPrebuiltTest {

    @Test
    fun `RoomStatisticsRepository computes 7-day retention, 6-stage distribution and streak freeze accurately`() = runTest {
        val ownerId = Deck.GUEST_OWNER_ID
        val todayDay = 20_000L
        val nowMillis = 1_730_000_000_000L

        val cardDao = StatsInMemoryCardDao()
        val reviewLogDao = StatsInMemoryReviewLogDao()
        val prefsRepo = FakeUserPreferencesRepository()

        val statsRepo = RoomStatisticsRepository(
            reviewLogDao = reviewLogDao,
            cardDao = cardDao,
            preferencesRepository = prefsRepo,
            ownerIdProvider = { ownerId },
            clockMillisProvider = { nowMillis },
            learningDayProvider = { todayDay }
        )

        // 1. Khi mới cài app (chưa có thẻ & log): không chia cho 0, đủ 6 cấp RetentionStage = 0
        val initialStats = statsRepo.observeStatistics().first()
        assertEquals(0, initialStats.reviewsLast7DaysCount)
        assertEquals(0f, initialStats.retentionRatePercent, 0.001f)
        assertEquals(6, initialStats.stageDistribution.size)
        RetentionStage.entries.forEach { stage ->
            assertEquals(0, initialStats.stageDistribution[stage])
        }
        assertEquals(0, initialStats.masteredCardsCount)
        assertEquals(0L, initialStats.totalStudyTimeSeconds)
        assertEquals(0, initialStats.remainingNewCardsToday)

        // 2. Chèn 6 thẻ thuộc đủ 6 cấp độ RetentionStage (Cấp 0 -> Cấp 5)
        val cards = listOf(
            Card(
                id = "c0",
                deckId = "d1",
                ownerId = ownerId,
                word = "novice",
                meaning = "người mới",
                state = CardState.NEW,
                interval = 0
            ),
            Card(
                id = "c1",
                deckId = "d1",
                ownerId = ownerId,
                word = "spark",
                meaning = "tia lửa",
                state = CardState.LEARNING,
                interval = 0,
                firstSeenDay = todayDay,
                dueAtMillis = nowMillis - 1_000L
            ),
            Card(
                id = "c2",
                deckId = "d1",
                ownerId = ownerId,
                word = "short",
                meaning = "ngắn hạn",
                state = CardState.REVIEW,
                interval = 2,
                dueDay = todayDay
            ),
            Card(
                id = "c3",
                deckId = "d1",
                ownerId = ownerId,
                word = "medium",
                meaning = "trung hạn",
                state = CardState.REVIEW,
                interval = 5,
                dueDay = todayDay + 2
            ),
            Card(
                id = "c4",
                deckId = "d1",
                ownerId = ownerId,
                word = "long",
                meaning = "dài hạn",
                state = CardState.REVIEW,
                interval = 14,
                dueDay = todayDay + 5
            ),
            Card(
                id = "c5",
                deckId = "d1",
                ownerId = ownerId,
                word = "mastered",
                meaning = "thành thạo",
                state = CardState.REVIEW,
                interval = 25,
                dueDay = todayDay + 10
            )
        )
        cardDao.upsertCards(cards.map { it.toEntity() })

        // 3. Chèn ReviewLog: 4 lượt hợp lệ trong 7 ngày gần nhất (3 nhớ, 1 quên = 75%),
        // 1 lượt đã hoàn tác (isUndone = true) và 1 lượt quá 7 ngày (todayDay - 7)
        val logs = listOf(
            createTestLog("l1", ownerId, todayDay, ReviewQuality.GOOD, durationSeconds = 15, isUndone = false),
            createTestLog("l2", ownerId, todayDay - 1, ReviewQuality.EASY, durationSeconds = 20, isUndone = false),
            createTestLog("l3", ownerId, todayDay - 3, ReviewQuality.HARD, durationSeconds = 25, isUndone = false),
            createTestLog("l4", ownerId, todayDay - 6, ReviewQuality.AGAIN, durationSeconds = 10, isUndone = false),
            createTestLog("l_undone", ownerId, todayDay, ReviewQuality.AGAIN, durationSeconds = 40, isUndone = true),
            createTestLog("l_old", ownerId, todayDay - 7, ReviewQuality.AGAIN, durationSeconds = 30, isUndone = false)
        )
        reviewLogDao.upsertLogs(logs)

        // Giả lập người học lỡ 1 ngày hôm qua (lastStudyDay = todayDay - 2) và có Khiên bảo vệ
        prefsRepo.updateStreak(
            streak = 5,
            isFrozen = true,
            lastStudyLearningDay = todayDay - 2,
            lastFreezeUsedDay = null
        )

        val updatedStats = statsRepo.observeStatistics().first()
        assertEquals(6, updatedStats.totalActiveCardsCount)
        assertEquals(1, updatedStats.masteredCardsCount)
        RetentionStage.entries.forEach { stage ->
            assertEquals(1, updatedStats.stageDistribution[stage])
        }
        assertEquals(4, updatedStats.reviewsLast7DaysCount)
        assertEquals(75f, updatedStats.retentionRatePercent, 0.001f)
        // Tổng thời gian học tính mọi log hợp lệ (không tính l_undone): 15 + 20 + 25 + 10 + 30 = 100s
        assertEquals(100L, updatedStats.totalStudyTimeSeconds)
        assertEquals(1, updatedStats.newCardsLearnedToday)
        assertEquals(1, updatedStats.remainingNewCardsToday)
        assertEquals(1, updatedStats.reviewsToday)
        assertEquals(2, updatedStats.dueCardsCount)

        // Kiểm tra Khiên bảo vệ chuỗi đã kích hoạt hôm nay
        assertEquals(5, updatedStats.currentStreakDays)
        assertFalse(updatedStats.streakFreezeAvailable)
        assertTrue(updatedStats.isStreakProtectedToday)
        assertEquals(30, updatedStats.nextFreezeAvailableInDays)
    }

    @Test
    fun `PrebuiltDeckSeeder seeds 2 starter decks from assets json and importPrebuiltDeck clones once`() = runTest {
        val deckDao = StatsInMemoryDeckDao()
        val cardDao = StatsInMemoryCardDao()

        val seeder = PrebuiltDeckSeeder(
            deckDao = deckDao,
            cardDao = cardDao
        )
        val deckRepo = RoomDeckRepository(
            deckDao = deckDao,
            cardDao = cardDao,
            prebuiltDeckSeeder = seeder,
            ownerIdProvider = { "user_test" }
        )

        // 1. Seed từ file thật app/src/main/assets/prebuilt_decks.json
        val seededCount = seeder.seedIfNeeded()
        assertEquals(2, seededCount)

        val prebuiltDecks = deckRepo.observePrebuiltDecks().first()
        assertEquals(2, prebuiltDecks.size)
        val oxford = prebuiltDecks.first { it.id == "prebuilt_oxford_3000" }
        val ielts = prebuiltDecks.first { it.id == "prebuilt_ielts_core" }
        assertEquals("Oxford 3000 cơ bản", oxford.name)
        assertEquals("IELTS cơ bản", ielts.name)
        assertEquals(20, cardDao.countActiveCardsInDeck(oxford.id))
        assertEquals(20, cardDao.countActiveCardsInDeck(ielts.id))

        // Gọi seedIfNeeded lần 2 phải idempotent (vẫn 20 thẻ mỗi bộ)
        seeder.seedIfNeeded()
        assertEquals(20, cardDao.countActiveCardsInDeck(oxford.id))
        assertEquals(20, cardDao.countActiveCardsInDeck(ielts.id))

        // 2. Nhập bộ Oxford sang bộ cá nhân
        val importedFirst = deckRepo.importPrebuiltDeck("prebuilt_oxford_3000").getOrThrow()
        assertNotEquals("prebuilt_oxford_3000", importedFirst.id)
        assertEquals("user_test", importedFirst.ownerId)
        assertEquals("prebuilt_oxford_3000", importedFirst.sourcePrebuiltId)
        assertEquals(SyncStatus.PENDING_SYNC, importedFirst.syncStatus)
        assertEquals(20, cardDao.countActiveCardsInDeck(importedFirst.id))

        // 3. Nhập lần 2 cùng bộ Oxford phải trả về đúng bộ đã nhập, không nhân bản thêm (Mục 6.4)
        val importedSecond = deckRepo.importPrebuiltDeck("prebuilt_oxford_3000").getOrThrow()
        assertEquals(importedFirst.id, importedSecond.id)
        assertEquals(1, deckRepo.observeActiveDecks().first().size)

        // 4. Xóa mềm bộ cá nhân đã nhập không làm ảnh hưởng bộ mẫu gốc
        deckRepo.softDeleteDeck(importedFirst.id).getOrThrow()
        assertEquals(20, cardDao.countActiveCardsInDeck("prebuilt_oxford_3000"))
    }

    private fun createTestLog(
        id: String,
        ownerId: String,
        learningDay: Long,
        quality: ReviewQuality,
        durationSeconds: Int,
        isUndone: Boolean
    ): ReviewLogEntity = ReviewLogEntity(
        id = id,
        cardId = "c2",
        deckId = "d1",
        ownerId = ownerId,
        quality = quality,
        learningDay = learningDay,
        reviewedAtMillis = 1_730_000_000_000L,
        durationValidSeconds = durationSeconds,
        snapshotBeforeReview = CardScheduleSnapshot(
            state = CardState.REVIEW,
            easeFactor = 2.5,
            repetitions = 1,
            interval = 2,
            lapses = 0,
            dueDay = learningDay,
            dueAtMillis = null,
            firstSeenDay = learningDay - 2,
            lastReviewedAtMillis = 1_729_000_000_000L
        ),
        isUndone = isUndone,
        updatedAtMillis = 1_730_000_000_000L,
        syncStatus = SyncStatus.PENDING_SYNC
    )
}

private class StatsInMemoryDeckDao : DeckDao {
    private val state = MutableStateFlow<List<DeckEntity>>(emptyList())

    override fun observeActiveDecks(ownerId: String): Flow<List<DeckEntity>> =
        state.map { list -> list.filter { it.ownerId == ownerId && !it.isDeleted } }

    override fun observePrebuiltDecks(prebuiltOwnerId: String): Flow<List<DeckEntity>> =
        state.map { list -> list.filter { it.ownerId == prebuiltOwnerId && !it.isDeleted } }

    override suspend fun getDeckById(deckId: String): DeckEntity? =
        state.value.firstOrNull { it.id == deckId && !it.isDeleted }

    override suspend fun getDeckByIdIncludingDeleted(deckId: String): DeckEntity? =
        state.value.firstOrNull { it.id == deckId }

    override suspend fun getDeckByName(ownerId: String, name: String): DeckEntity? =
        state.value.firstOrNull { it.ownerId == ownerId && !it.isDeleted && it.name.equals(name, ignoreCase = true) }

    override suspend fun getDeckBySourcePrebuiltId(ownerId: String, sourcePrebuiltId: String): DeckEntity? =
        state.value.firstOrNull { it.ownerId == ownerId && !it.isDeleted && it.sourcePrebuiltId == sourcePrebuiltId }

    override suspend fun countActiveDecks(ownerId: String): Int =
        state.value.count { it.ownerId == ownerId && !it.isDeleted }

    override suspend fun upsertDeck(deck: DeckEntity) {
        state.value = state.value.filterNot { it.id == deck.id } + deck
    }

    override suspend fun upsertDecks(decks: List<DeckEntity>) {
        val ids = decks.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in ids } + decks
    }

    override suspend fun softDeleteDeck(deckId: String, updatedAtMillis: Long, syncStatus: SyncStatus) {
        state.value = state.value.map {
            if (it.id == deckId) it.copy(isDeleted = true, updatedAtMillis = updatedAtMillis, syncStatus = syncStatus) else it
        }
    }

    override suspend fun deleteAllByOwner(ownerId: String) {
        state.value = state.value.filterNot { it.ownerId == ownerId }
    }

    override suspend fun purgeSyncedDeletedBefore(cutoffMillis: Long) = Unit

    override suspend fun getPendingSyncDecks(ownerId: String): List<DeckEntity> = emptyList()

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus, updatedAtMillis: Long) = Unit

    override fun countPendingSync(ownerId: String): Flow<Int> = MutableStateFlow(0)

    override suspend fun reassignOwner(fromOwnerId: String, toOwnerId: String, updatedAtMillis: Long, syncStatus: SyncStatus) = Unit
}

private class StatsInMemoryCardDao : CardDao {
    private val state = MutableStateFlow<List<CardEntity>>(emptyList())

    override fun observeCardsByDeck(deckId: String): Flow<List<CardEntity>> =
        state.map { list -> list.filter { it.deckId == deckId && !it.isDeleted } }

    override fun observeAllActiveCards(ownerId: String): Flow<List<CardEntity>> =
        state.map { list -> list.filter { it.ownerId == ownerId && !it.isDeleted } }

    override suspend fun getAllActiveCards(ownerId: String): List<CardEntity> =
        state.value.filter { it.ownerId == ownerId && !it.isDeleted }

    override suspend fun getCardById(cardId: String): CardEntity? =
        state.value.firstOrNull { it.id == cardId && !it.isDeleted }

    override suspend fun getCardByIdIncludingDeleted(cardId: String): CardEntity? =
        state.value.firstOrNull { it.id == cardId }

    override suspend fun getCardByWordInDeck(deckId: String, word: String): CardEntity? =
        state.value.firstOrNull { it.deckId == deckId && !it.isDeleted && it.word.equals(word, ignoreCase = true) }

    override suspend fun countActiveCardsInDeck(deckId: String): Int =
        state.value.count { it.deckId == deckId && !it.isDeleted }

    override suspend fun upsertCard(card: CardEntity) {
        state.value = state.value.filterNot { it.id == card.id } + card
    }

    override suspend fun upsertCards(cards: List<CardEntity>) {
        val ids = cards.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in ids } + cards
    }

    override suspend fun softDeleteCard(cardId: String, updatedAtMillis: Long, syncStatus: SyncStatus) = Unit

    override suspend fun softDeleteCardsByDeck(deckId: String, updatedAtMillis: Long, syncStatus: SyncStatus) {
        state.value = state.value.map {
            if (it.deckId == deckId && !it.isDeleted) {
                it.copy(isDeleted = true, updatedAtMillis = updatedAtMillis, syncStatus = syncStatus)
            } else {
                it
            }
        }
    }

    override suspend fun resetCardProgress(
        cardId: String,
        updatedAtMillis: Long,
        easeFactor: Double,
        state: CardState,
        syncStatus: SyncStatus
    ) = Unit

    override suspend fun resetDeckProgress(
        deckId: String,
        updatedAtMillis: Long,
        easeFactor: Double,
        state: CardState,
        syncStatus: SyncStatus
    ) = Unit

    override suspend fun countNewCardsLearnedOnDay(ownerId: String, learningDay: Long): Int = 0

    override suspend fun getNewCardsQueue(ownerId: String, deckId: String?, limit: Int): List<CardEntity> = emptyList()

    override suspend fun getDueLearningCards(ownerId: String, deckId: String?, nowMillis: Long): List<CardEntity> = emptyList()

    override suspend fun getDueReviewCards(ownerId: String, deckId: String?, currentLearningDay: Long): List<CardEntity> = emptyList()

    override suspend fun countTomorrowDueCards(ownerId: String, deckId: String?, tomorrowDay: Long): Int = 0

    override suspend fun deleteAllByOwner(ownerId: String) = Unit

    override suspend fun purgeSyncedDeletedBefore(cutoffMillis: Long) = Unit

    override suspend fun getPendingSyncCards(ownerId: String): List<CardEntity> = emptyList()

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus, updatedAtMillis: Long) = Unit

    override fun countPendingSync(ownerId: String): Flow<Int> = MutableStateFlow(0)

    override suspend fun reassignOwner(fromOwnerId: String, toOwnerId: String, updatedAtMillis: Long, syncStatus: SyncStatus) = Unit
}

private class StatsInMemoryReviewLogDao : ReviewLogDao {
    private val state = MutableStateFlow<List<ReviewLogEntity>>(emptyList())

    override suspend fun insertLog(log: ReviewLogEntity) {
        state.value = state.value.filterNot { it.id == log.id } + log
    }

    override suspend fun upsertLogs(logs: List<ReviewLogEntity>) {
        val ids = logs.map { it.id }.toSet()
        state.value = state.value.filterNot { it.id in ids } + logs
    }

    override suspend fun getLogById(logId: String): ReviewLogEntity? =
        state.value.firstOrNull { it.id == logId }

    override suspend fun getLatestActiveLog(ownerId: String): ReviewLogEntity? =
        state.value.filter { it.ownerId == ownerId && !it.isUndone }.maxByOrNull { it.reviewedAtMillis }

    override suspend fun markLogUndone(logId: String, updatedAtMillis: Long, syncStatus: SyncStatus) {
        state.value = state.value.map {
            if (it.id == logId) it.copy(isUndone = true, updatedAtMillis = updatedAtMillis, syncStatus = syncStatus) else it
        }
    }

    override fun observeLogsFromDay(ownerId: String, fromLearningDay: Long): Flow<List<ReviewLogEntity>> =
        state.map { list -> list.filter { it.ownerId == ownerId && !it.isUndone && it.learningDay >= fromLearningDay } }

    override fun observeAllValidLogs(ownerId: String): Flow<List<ReviewLogEntity>> =
        state.map { list -> list.filter { it.ownerId == ownerId && !it.isUndone } }

    override suspend fun deleteAllByOwner(ownerId: String) {
        state.value = state.value.filterNot { it.ownerId == ownerId }
    }

    override suspend fun getPendingSyncReviewLogs(ownerId: String): List<ReviewLogEntity> = emptyList()

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus, updatedAtMillis: Long) = Unit

    override fun countPendingSync(ownerId: String): Flow<Int> = MutableStateFlow(0)

    override suspend fun reassignOwner(fromOwnerId: String, toOwnerId: String, updatedAtMillis: Long, syncStatus: SyncStatus) = Unit
}
