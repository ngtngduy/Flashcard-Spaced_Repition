package com.uth.memora.core.data

import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.database.entity.DeckEntity
import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AutoFillCardData
import com.uth.memora.core.fake.FakeUserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.SyncStatus
import com.uth.memora.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử toàn bộ RoomDeckRepository và RoomCardRepository (M1-E1..E7).
 */
class RoomDeckAndCardRepositoryTest {

    private class InMemoryDeckDao : DeckDao {
        val state = MutableStateFlow<List<DeckEntity>>(emptyList())

        override fun observeActiveDecks(ownerId: String): Flow<List<DeckEntity>> =
            state.map { list ->
                list.filter { it.ownerId == ownerId && !it.isDeleted }
                    .sortedByDescending { it.updatedAtMillis }
            }

        override fun observePrebuiltDecks(prebuiltOwnerId: String): Flow<List<DeckEntity>> =
            state.map { list ->
                list.filter { it.ownerId == prebuiltOwnerId && !it.isDeleted }
            }

        override suspend fun getDeckById(deckId: String): DeckEntity? =
            state.value.firstOrNull { it.id == deckId && !it.isDeleted }

        override suspend fun getDeckByIdIncludingDeleted(deckId: String): DeckEntity? =
            state.value.firstOrNull { it.id == deckId }

        override suspend fun getDeckByName(ownerId: String, name: String): DeckEntity? =
            state.value.firstOrNull {
                it.ownerId == ownerId && !it.isDeleted && it.name.equals(name, ignoreCase = true)
            }

        override suspend fun getDeckBySourcePrebuiltId(
            ownerId: String,
            sourcePrebuiltId: String
        ): DeckEntity? = state.value.firstOrNull {
            it.ownerId == ownerId && !it.isDeleted && it.sourcePrebuiltId == sourcePrebuiltId
        }

        override suspend fun countActiveDecks(ownerId: String): Int =
            state.value.count { it.ownerId == ownerId && !it.isDeleted }

        override suspend fun upsertDeck(deck: DeckEntity) {
            state.value = state.value.filterNot { it.id == deck.id } + deck
        }

        override suspend fun upsertDecks(decks: List<DeckEntity>) {
            val ids = decks.map { it.id }.toSet()
            state.value = state.value.filterNot { it.id in ids } + decks
        }

        override suspend fun softDeleteDeck(
            deckId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {
            state.value = state.value.map {
                if (it.id == deckId) {
                    it.copy(isDeleted = true, updatedAtMillis = updatedAtMillis, syncStatus = syncStatus)
                } else {
                    it
                }
            }
        }

        override suspend fun deleteAllByOwner(ownerId: String) {
            state.value = state.value.filterNot { it.ownerId == ownerId }
        }

        override suspend fun purgeSyncedDeletedBefore(cutoffMillis: Long) {
            state.value = state.value.filterNot {
                it.isDeleted && it.syncStatus == SyncStatus.SYNCED && it.updatedAtMillis < cutoffMillis
            }
        }

        override suspend fun getPendingSyncDecks(ownerId: String): List<DeckEntity> =
            state.value.filter { it.ownerId == ownerId && it.syncStatus == SyncStatus.PENDING_SYNC }

        override suspend fun updateSyncStatus(
            id: String,
            syncStatus: SyncStatus,
            updatedAtMillis: Long
        ) {
            state.value = state.value.map {
                if (it.id == id) it.copy(syncStatus = syncStatus, updatedAtMillis = updatedAtMillis) else it
            }
        }

        override fun countPendingSync(ownerId: String): Flow<Int> =
            state.map { list -> list.count { it.ownerId == ownerId && it.syncStatus == SyncStatus.PENDING_SYNC } }

        override suspend fun reassignOwner(
            fromOwnerId: String,
            toOwnerId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {
            state.value = state.value.map {
                if (it.ownerId == fromOwnerId) {
                    it.copy(ownerId = toOwnerId, updatedAtMillis = updatedAtMillis, syncStatus = syncStatus)
                } else {
                    it
                }
            }
        }
    }

    private class InMemoryCardDao : CardDao {
        val state = MutableStateFlow<List<CardEntity>>(emptyList())

        override fun observeCardsByDeck(deckId: String): Flow<List<CardEntity>> =
            state.map { list ->
                list.filter { it.deckId == deckId && !it.isDeleted }
                    .sortedByDescending { it.createdAtMillis }
            }

        override fun observeAllActiveCards(ownerId: String): Flow<List<CardEntity>> =
            state.map { list ->
                list.filter { it.ownerId == ownerId && !it.isDeleted }
                    .sortedByDescending { it.updatedAtMillis }
            }

        override suspend fun getAllActiveCards(ownerId: String): List<CardEntity> =
            state.value.filter { it.ownerId == ownerId && !it.isDeleted }

        override suspend fun getCardById(cardId: String): CardEntity? =
            state.value.firstOrNull { it.id == cardId && !it.isDeleted }

        override suspend fun getCardByIdIncludingDeleted(cardId: String): CardEntity? =
            state.value.firstOrNull { it.id == cardId }

        override suspend fun getCardByWordInDeck(deckId: String, word: String): CardEntity? =
            state.value.firstOrNull {
                it.deckId == deckId && !it.isDeleted && it.word.equals(word, ignoreCase = true)
            }

        override suspend fun countActiveCardsInDeck(deckId: String): Int =
            state.value.count { it.deckId == deckId && !it.isDeleted }

        override suspend fun upsertCard(card: CardEntity) {
            state.value = state.value.filterNot { it.id == card.id } + card
        }

        override suspend fun upsertCards(cards: List<CardEntity>) {
            val ids = cards.map { it.id }.toSet()
            state.value = state.value.filterNot { it.id in ids } + cards
        }

        override suspend fun softDeleteCard(
            cardId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {
            state.value = state.value.map {
                if (it.id == cardId) {
                    it.copy(isDeleted = true, updatedAtMillis = updatedAtMillis, syncStatus = syncStatus)
                } else {
                    it
                }
            }
        }

        override suspend fun softDeleteCardsByDeck(
            deckId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {
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
        ) {
            this.state.value = this.state.value.map {
                if (it.id == cardId && !it.isDeleted) {
                    it.copy(
                        state = state,
                        easeFactor = easeFactor,
                        repetitions = 0,
                        interval = 0,
                        lapses = 0,
                        dueDay = null,
                        dueAtMillis = null,
                        firstSeenDay = null,
                        lastReviewedAtMillis = null,
                        updatedAtMillis = updatedAtMillis,
                        syncStatus = syncStatus
                    )
                } else {
                    it
                }
            }
        }

        override suspend fun resetDeckProgress(
            deckId: String,
            updatedAtMillis: Long,
            easeFactor: Double,
            state: CardState,
            syncStatus: SyncStatus
        ) {
            this.state.value = this.state.value.map {
                if (it.deckId == deckId && !it.isDeleted) {
                    it.copy(
                        state = state,
                        easeFactor = easeFactor,
                        repetitions = 0,
                        interval = 0,
                        lapses = 0,
                        dueDay = null,
                        dueAtMillis = null,
                        firstSeenDay = null,
                        lastReviewedAtMillis = null,
                        updatedAtMillis = updatedAtMillis,
                        syncStatus = syncStatus
                    )
                } else {
                    it
                }
            }
        }

        override suspend fun countNewCardsLearnedOnDay(ownerId: String, learningDay: Long): Int =
            state.value.count { it.ownerId == ownerId && !it.isDeleted && it.firstSeenDay == learningDay }

        override suspend fun getNewCardsQueue(
            ownerId: String,
            deckId: String?,
            limit: Int
        ): List<CardEntity> = state.value
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
        ): List<CardEntity> = emptyList()

        override suspend fun getDueReviewCards(
            ownerId: String,
            deckId: String?,
            currentLearningDay: Long
        ): List<CardEntity> = emptyList()

        override suspend fun countTomorrowDueCards(
            ownerId: String,
            deckId: String?,
            tomorrowDay: Long
        ): Int = 0

        override suspend fun deleteAllByOwner(ownerId: String) {
            state.value = state.value.filterNot { it.ownerId == ownerId }
        }

        override suspend fun purgeSyncedDeletedBefore(cutoffMillis: Long) {}

        override suspend fun getPendingSyncCards(ownerId: String): List<CardEntity> = emptyList()

        override suspend fun updateSyncStatus(
            id: String,
            syncStatus: SyncStatus,
            updatedAtMillis: Long
        ) {}

        override fun countPendingSync(ownerId: String): Flow<Int> = state.map { 0 }

        override suspend fun reassignOwner(
            fromOwnerId: String,
            toOwnerId: String,
            updatedAtMillis: Long,
            syncStatus: SyncStatus
        ) {}
    }

    @Test
    fun deckCrud_enforcesValidationDuplicateCheckAndSoftDeletesCardsCascading() = runTest {
        val deckDao = InMemoryDeckDao()
        val cardDao = InMemoryCardDao()
        val deckRepo = RoomDeckRepository(
            database = null,
            deckDao = deckDao,
            cardDao = cardDao
        )
        val prefsRepo = FakeUserPreferencesRepository()
        val cardRepo = RoomCardRepository(
            cardDao = cardDao,
            deckDao = deckDao,
            preferencesRepository = prefsRepo,
            aiAutoFillService = object : AiCardAutoFillService {
                override suspend fun autoFillCard(word: String): Result<AutoFillCardData> =
                    Result.failure(Exception("unused"))
            }
        )

        // Tên rỗng hoặc quá 50 ký tự bị từ chối
        assertTrue(deckRepo.createDeck("   ", "mô tả").isFailure)
        assertTrue(deckRepo.createDeck("a".repeat(51), "mô tả").isFailure)

        // Tạo bộ hợp lệ
        val deck = deckRepo.createDeck("  Động từ Bất quy tắc  ", "Bộ từ cơ bản").getOrThrow()
        assertEquals("Động từ Bất quy tắc", deck.name)
        assertEquals(SyncStatus.PENDING_SYNC, deck.syncStatus)

        // Chặn trùng tên không phân biệt hoa thường tiếng Việt
        assertTrue(deckRepo.createDeck("động từ bất quy tắc", "trùng tên").isFailure)

        // Thêm thẻ vào bộ từ
        val card = cardRepo.addCard(
            deckId = deck.id,
            word = "resilient",
            meaning = "kiên cường"
        ).getOrThrow()
        assertNotNull(cardRepo.getCardById(card.id))

        // Xóa mềm bộ từ phải xóa mềm luôn cả thẻ bên trong
        assertTrue(deckRepo.softDeleteDeck(deck.id).isSuccess)
        assertNull(deckRepo.getDeckById(deck.id))
        assertNull(cardRepo.getCardById(card.id))
        assertTrue(deckRepo.observeActiveDecks().first().isEmpty())
    }

    @Test
    fun cardRepository_handlesDuplicateWordSearchResetAndAiAutoFillQuotas() = runTest {
        val deckDao = InMemoryDeckDao()
        val cardDao = InMemoryCardDao()
        val deckRepo = RoomDeckRepository(database = null, deckDao = deckDao, cardDao = cardDao)
        val prefsRepo = FakeUserPreferencesRepository(
            UserPreferences(aiAutoFillRemainingToday = 1)
        )
        var shouldFailAi = false
        val aiService = object : AiCardAutoFillService {
            override suspend fun autoFillCard(word: String): Result<AutoFillCardData> {
                return if (shouldFailAi) {
                    Result.failure(IllegalStateException("Mất kết nối mạng."))
                } else {
                    Result.success(
                        AutoFillCardData(
                            word = word,
                            phonetic = "/rɪˈzɪl.jənt/",
                            partOfSpeech = PartOfSpeech.ADJECTIVE,
                            meaning = "kiên cường, bền bỉ",
                            example = "She remained resilient."
                        )
                    )
                }
            }
        }
        val cardRepo = RoomCardRepository(
            cardDao = cardDao,
            deckDao = deckDao,
            preferencesRepository = prefsRepo,
            aiAutoFillService = aiService
        )

        val deck = deckRepo.createDeck("IELTS Academic", "").getOrThrow()
        val card1 = cardRepo.addCard(
            deckId = deck.id,
            word = "Resilient",
            meaning = "Kiên cường, phục hồi nhanh"
        ).getOrThrow()

        // Thêm trùng từ khi allowDuplicateWord = false -> trả failure
        val dupFail = cardRepo.addCard(
            deckId = deck.id,
            word = "resilient",
            meaning = "Nghĩa khác",
            allowDuplicateWord = false
        )
        assertTrue(dupFail.isFailure)

        // Khi allowDuplicateWord = true -> cho phép lưu
        val dupAllowed = cardRepo.addCard(
            deckId = deck.id,
            word = "resilient",
            meaning = "Đàn hồi",
            allowDuplicateWord = true
        )
        assertTrue(dupAllowed.isSuccess)

        // Tìm kiếm tiếng Việt không dấu ("kien cuong" khớp "Kiên cường")
        val searchResult = cardRepo.searchCards(deck.id, "kien cuong")
        assertEquals(1, searchResult.size)
        assertEquals(card1.id, searchResult.first().id)

        // Đặt lại tiến độ thẻ
        cardDao.upsertCard(
            cardDao.getCardById(card1.id)!!.copy(
                state = CardState.REVIEW,
                interval = 15,
                repetitions = 4,
                easeFactor = 2.8,
                dueDay = 20_500L
            )
        )
        val resetCard = cardRepo.resetCardProgress(card1.id).getOrThrow()
        assertEquals(CardState.NEW, resetCard.state)
        assertEquals(0, resetCard.interval)
        assertEquals(0, resetCard.repetitions)
        assertEquals(Card.INITIAL_EASE_FACTOR, resetCard.easeFactor, 0.001)
        assertNull(resetCard.dueDay)

        // Khi AI lỗi mạng -> không bị trừ mất lượt Auto-Fill
        shouldFailAi = true
        assertTrue(cardRepo.autoFillCardContent("ability").isFailure)
        assertEquals(1, prefsRepo.getUserPreferences().aiAutoFillRemainingToday)

        // Khi AI thành công -> trừ 1 lượt (1 -> 0)
        shouldFailAi = false
        val autoFilled = cardRepo.autoFillCardContent("resilient").getOrThrow()
        assertEquals(PartOfSpeech.ADJECTIVE, autoFilled.partOfSpeech)
        assertEquals(0, prefsRepo.getUserPreferences().aiAutoFillRemainingToday)

        // Hết lượt (0) -> lần gọi tiếp theo bị chặn ngay
        assertTrue(cardRepo.autoFillCardContent("serendipity").isFailure)
    }
}
