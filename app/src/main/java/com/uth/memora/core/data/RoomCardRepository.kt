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
import com.uth.memora.core.domain.VietnameseTextNormalizer
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val consumeQuotaOnSuccess: Boolean = true,
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
            if (ownerId == DeckDao.PREBUILT_OWNER_ID) {
                cardDao.observeAllActiveCards(ownerId).map { entities ->
                    entities.map { it.toDomain() }
                }
            } else {
                combine(
                    cardDao.observeAllActiveCards(ownerId),
                    cardDao.observeAllActiveCards(DeckDao.PREBUILT_OWNER_ID)
                ) { userEntities, prebuiltEntities ->
                    (userEntities + prebuiltEntities).map { it.toDomain() }
                }
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

    override suspend fun searchCards(deckId: String?, query: String): List<Card> {
        val cleanDeckId = deckId?.trim()?.takeIf { it.isNotEmpty() }
        val activeCards = if (cleanDeckId != null) {
            cardDao.observeCardsByDeck(cleanDeckId).first().map { it.toDomain() }
        } else {
            cardDao.getAllActiveCards(ownerIdProvider()).map { it.toDomain() }
        }

        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return activeCards

        return activeCards.filter { card ->
            VietnameseTextNormalizer.matchesQuery(card.word, cleanQuery) ||
                VietnameseTextNormalizer.matchesQuery(card.meaning, cleanQuery)
        }
    }

    override suspend fun updateCardContent(
        cardId: String,
        deckId: String,
        word: String,
        meaning: String,
        phonetic: String,
        partOfSpeech: PartOfSpeech?,
        example: String,
        localImagePath: String?
    ): Result<Card> = runCatching {
        val cleanCardId = cardId.trim()
        val existingEntity = cardDao.getCardById(cleanCardId)
            ?: throw NoSuchElementException("Không tìm thấy thẻ.")

        val cleanDeckId = deckId.trim()
        val targetDeck = deckDao.getDeckById(cleanDeckId)
            ?: throw NoSuchElementException("Không tìm thấy bộ từ vựng.")

        if (cleanDeckId != existingEntity.deckId) {
            val targetDeckCount = cardDao.countActiveCardsInDeck(cleanDeckId)
            if (targetDeckCount >= Deck.MAX_CARDS_PER_DECK) {
                throw IllegalStateException(
                    "Bộ từ đích đã đạt giới hạn tối đa ${Deck.MAX_CARDS_PER_DECK} thẻ."
                )
            }
        }

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

        val normalizedLocalImage = localImagePath?.takeIf { it.isNotBlank() }
        val preservedCloudImageUrl = if (normalizedLocalImage == existingEntity.localImagePath) {
            existingEntity.cloudImageUrl
        } else {
            null
        }

        val updatedCard = existingEntity.toDomain().copy(
            deckId = cleanDeckId,
            ownerId = targetDeck.ownerId,
            word = cleanWord,
            meaning = cleanMeaning,
            phonetic = cleanPhonetic,
            partOfSpeech = partOfSpeech,
            example = cleanExample,
            localImagePath = normalizedLocalImage,
            cloudImageUrl = preservedCloudImageUrl,
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_SYNC
        )

        cardDao.upsertCard(updatedCard.toEntity())
        updatedCard
    }

    override suspend fun softDeleteCard(cardId: String): Result<Unit> = runCatching {
        val cleanCardId = cardId.trim()
        cardDao.getCardById(cleanCardId)
            ?: throw NoSuchElementException("Không tìm thấy thẻ.")

        cardDao.softDeleteCard(
            cardId = cleanCardId,
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
    }

    override suspend fun resetCardProgress(cardId: String): Result<Card> = runCatching {
        val cleanCardId = cardId.trim()
        val existingEntity = cardDao.getCardById(cleanCardId)
            ?: throw NoSuchElementException("Không tìm thấy thẻ.")

        val now = System.currentTimeMillis()
        cardDao.resetCardProgress(
            cardId = cleanCardId,
            updatedAtMillis = now,
            easeFactor = Card.INITIAL_EASE_FACTOR,
            state = CardState.NEW,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        existingEntity.toDomain().copy(
            state = CardState.NEW,
            easeFactor = Card.INITIAL_EASE_FACTOR,
            repetitions = 0,
            interval = 0,
            lapses = 0,
            dueDay = null,
            dueAtMillis = null,
            firstSeenDay = null,
            lastReviewedAtMillis = null,
            updatedAtMillis = now,
            syncStatus = SyncStatus.PENDING_SYNC
        )
    }

    override suspend fun resetDeckProgress(deckId: String): Result<Unit> = runCatching {
        val cleanDeckId = deckId.trim()
        deckDao.getDeckById(cleanDeckId)
            ?: throw NoSuchElementException("Không tìm thấy bộ từ vựng.")

        cardDao.resetDeckProgress(
            deckId = cleanDeckId,
            updatedAtMillis = System.currentTimeMillis(),
            easeFactor = Card.INITIAL_EASE_FACTOR,
            state = CardState.NEW,
            syncStatus = SyncStatus.PENDING_SYNC
        )
    }

    override suspend fun autoFillCardContent(word: String): Result<AutoFillCardData> {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("Vui lòng nhập từ vựng trước khi gọi AI gợi ý.")
            )
        }
        if (cleanWord.length > Card.MAX_WORD_LENGTH) {
            return Result.failure(
                IllegalArgumentException("Từ hoặc cụm từ tối đa ${Card.MAX_WORD_LENGTH} ký tự.")
            )
        }

        val currentPrefs = runCatching { preferencesRepository.getUserPreferences() }.getOrNull()
        if (currentPrefs != null && currentPrefs.aiAutoFillRemainingToday <= 0) {
            return Result.failure(
                IllegalStateException(
                    "Bạn đã dùng hết 30 lượt AI Auto-Fill hôm nay (tự hồi lúc 4:00 sáng)."
                )
            )
        }

        val aiResult = try {
            aiAutoFillService.autoFillCard(cleanWord)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

        return aiResult.fold(
            onSuccess = { data ->
                if (consumeQuotaOnSuccess) {
                    val quotaResult = preferencesRepository.consumeAutoFillQuota()
                    if (quotaResult.isFailure) {
                        return Result.failure(
                            quotaResult.exceptionOrNull()
                                ?: IllegalStateException(
                                    "Bạn đã dùng hết 30 lượt AI Auto-Fill hôm nay (tự hồi lúc 4:00 sáng)."
                                )
                        )
                    }
                }
                Result.success(
                    data.copy(
                        word = data.word.trim().ifEmpty { cleanWord }.take(Card.MAX_WORD_LENGTH),
                        phonetic = data.phonetic.trim().take(Card.MAX_PHONETIC_LENGTH),
                        meaning = data.meaning.trim().take(Card.MAX_MEANING_LENGTH),
                        example = data.example.trim().take(Card.MAX_EXAMPLE_LENGTH)
                    )
                )
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

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
