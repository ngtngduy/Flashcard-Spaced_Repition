package com.uth.memora.core.data

import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.database.mapper.toDomain
import com.uth.memora.core.database.mapper.toEntity
import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.AutoFillCardData
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Hiện thực [CardRepository] trên Room Database (Mục 5, Mục 6.3, Mục 6.4, Mục 10.1 & Mục 11).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomCardRepository(
    private val cardDao: CardDao,
    private val deckDao: DeckDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val aiAutoFillService: AiCardAutoFillService,
    private val authRepository: AuthRepository? = null,
    private val ownerIdProvider: suspend () -> String = {
        authRepository?.getCurrentUser()?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID
    }
) : CardRepository {

    override fun observeCardsByDeck(deckId: String): Flow<List<Card>> =
        cardDao.observeCardsByDeck(deckId.trim()).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeAllActiveCards(): Flow<List<Card>> {
        val ownerFlow: Flow<String> = authRepository
            ?.observeCurrentUser()
            ?.map { user -> user?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID }
            ?.distinctUntilChanged()
            ?: flow { emit(ownerIdProvider()) }

        return ownerFlow.flatMapLatest { ownerId ->
            cardDao.observeAllActiveCards(ownerId).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    override suspend fun getCardById(cardId: String): Card? {
        val cleanId = cardId.trim()
        if (cleanId.isEmpty()) return null
        return cardDao.getCardById(cleanId)?.toDomain()
    }

    override suspend fun addCard(
        deckId: String,
        word: String,
        meaning: String,
        phonetic: String,
        partOfSpeech: PartOfSpeech?,
        example: String,
        localImagePath: String?,
        allowDuplicateWord: Boolean
    ): Result<Card> = runCatching {
        val cleanDeckId = deckId.trim()
        val deckEntity = deckDao.getDeckById(cleanDeckId)
            ?: throw NoSuchElementException("Không tìm thấy bộ từ vựng.")

        val cleanWord = word.trim()
        val cleanMeaning = meaning.trim()
        val cleanPhonetic = phonetic.trim()
        val cleanExample = example.trim()
        validateCardFields(
            word = cleanWord,
            meaning = cleanMeaning,
            phonetic = cleanPhonetic,
            example = cleanExample
        )

        val currentCardCount = cardDao.countActiveCardsInDeck(cleanDeckId)
        if (currentCardCount >= Deck.MAX_CARDS_PER_DECK) {
            throw IllegalStateException(
                "Bộ từ này đã đạt giới hạn tối đa ${Deck.MAX_CARDS_PER_DECK} thẻ."
            )
        }

        if (!allowDuplicateWord) {
            val existingDuplicate = cardDao.getCardByWordInDeck(cleanDeckId, cleanWord) != null ||
                cardDao.observeCardsByDeck(cleanDeckId).first().any { existing ->
                    existing.word.trim().equals(cleanWord, ignoreCase = true)
                }
            if (existingDuplicate) {
                throw IllegalStateException("Từ \"$cleanWord\" đã có trong bộ từ này.")
            }
        }

        val now = System.currentTimeMillis()
        val newCard = Card(
            id = UUID.randomUUID().toString(),
            deckId = cleanDeckId,
            ownerId = deckEntity.ownerId,
            word = cleanWord,
            meaning = cleanMeaning,
            phonetic = cleanPhonetic,
            partOfSpeech = partOfSpeech,
            example = cleanExample,
            localImagePath = localImagePath?.takeIf { it.isNotBlank() },
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
            createdAtMillis = now,
            updatedAtMillis = now,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        cardDao.upsertCard(newCard.toEntity())
        newCard
    }

    override suspend fun searchCards(deckId: String?, query: String): List<Card> = emptyList()

    override suspend fun updateCardContent(
        cardId: String,
        deckId: String,
        word: String,
        meaning: String,
        phonetic: String,
        partOfSpeech: PartOfSpeech?,
        example: String,
        localImagePath: String?
    ): Result<Card> = Result.failure(UnsupportedOperationException("Chưa cài đặt updateCardContent"))

    override suspend fun softDeleteCard(cardId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Chưa cài đặt softDeleteCard"))

    override suspend fun resetCardProgress(cardId: String): Result<Card> =
        Result.failure(UnsupportedOperationException("Chưa cài đặt resetCardProgress"))

    override suspend fun resetDeckProgress(deckId: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Chưa cài đặt resetDeckProgress"))

    override suspend fun autoFillCardContent(word: String): Result<AutoFillCardData> =
        Result.failure(UnsupportedOperationException("Chưa cài đặt autoFillCardContent"))

    private fun validateCardFields(
        word: String,
        meaning: String,
        phonetic: String,
        example: String
    ) {
        if (word.isEmpty() || word.length > Card.MAX_WORD_LENGTH) {
            throw IllegalArgumentException(
                "Từ vựng phải từ 1 đến ${Card.MAX_WORD_LENGTH} ký tự."
            )
        }
        if (meaning.isEmpty() || meaning.length > Card.MAX_MEANING_LENGTH) {
            throw IllegalArgumentException(
                "Nghĩa phải từ 1 đến ${Card.MAX_MEANING_LENGTH} ký tự."
            )
        }
        if (phonetic.length > Card.MAX_PHONETIC_LENGTH) {
            throw IllegalArgumentException(
                "Phiên âm IPA tối đa ${Card.MAX_PHONETIC_LENGTH} ký tự."
            )
        }
        if (example.length > Card.MAX_EXAMPLE_LENGTH) {
            throw IllegalArgumentException(
                "Câu ví dụ tối đa ${Card.MAX_EXAMPLE_LENGTH} ký tự."
            )
        }
    }
}
