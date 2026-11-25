package com.uth.memora.core.data

import androidx.room.withTransaction
import com.uth.memora.core.database.MemoraDatabase
import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.ReviewLogDao
import com.uth.memora.core.database.mapper.toDomain
import com.uth.memora.core.database.mapper.toEntity
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.StudySessionRepository
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.sm2.LearningDayCalculator
import com.uth.memora.core.sm2.Sm2Engine

/**
 * Hiện thực [StudySessionRepository] trên Room Database (Mục 6.5, 6.6, 7.1–7.6, 9.1 & 10.2).
 */
class RoomStudySessionRepository(
    private val database: MemoraDatabase? = null,
    private val cardDao: CardDao,
    private val reviewLogDao: ReviewLogDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository? = null,
    private val ownerIdProvider: suspend () -> String = {
        authRepository?.getCurrentUser()?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID
    },
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val learningDayProvider: () -> Long = {
        LearningDayCalculator.toLearningDay(timeProvider())
    },
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
        val db = database
        if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }
) : StudySessionRepository {

    override suspend fun getNewCardsQueue(deckId: String?): List<Card> {
        val ownerId = ownerIdProvider()
        val todayLearningDay = learningDayProvider()
        val normalizedDeckId = normalizeDeckId(deckId)

        val newCardsLearnedToday = cardDao.countNewCardsLearnedOnDay(
            ownerId = ownerId,
            learningDay = todayLearningDay
        )
        val dailyLimit = preferencesRepository.getUserPreferences().dailyNewCardsLimit
        val remainingQuota = (dailyLimit - newCardsLearnedToday).coerceAtLeast(0)
        if (remainingQuota <= 0) {
            return emptyList()
        }

        return cardDao.getNewCardsQueue(
            ownerId = ownerId,
            deckId = normalizedDeckId,
            limit = remainingQuota
        ).map { it.toDomain() }
    }

    override suspend fun markNewCardDisplayed(cardId: String): Result<Card> = runCatching {
        val cleanCardId = cardId.trim()
        val existingEntity = cardDao.getCardById(cleanCardId)
            ?: throw NoSuchElementException("Không tìm thấy thẻ.")

        val currentCard = existingEntity.toDomain()
        val updatedCard = Sm2Engine.markCardDisplayedFirstTime(
            card = currentCard,
            currentLearningDay = learningDayProvider(),
            nowMillis = timeProvider()
        )
        if (updatedCard != currentCard) {
            cardDao.upsertCard(updatedCard.toEntity())
        }
        updatedCard
    }

    override suspend fun getDueReviewQueue(deckId: String?): List<Card> {
        val ownerId = ownerIdProvider()
        val nowMillis = timeProvider()
        val todayLearningDay = learningDayProvider()
        val normalizedDeckId = normalizeDeckId(deckId)

        val dueLearningCards = cardDao.getDueLearningCards(
            ownerId = ownerId,
            deckId = normalizedDeckId,
            nowMillis = nowMillis
        ).map { it.toDomain() }

        val dueReviewCards = cardDao.getDueReviewCards(
            ownerId = ownerId,
            deckId = normalizedDeckId,
            currentLearningDay = todayLearningDay
        ).map { it.toDomain() }
            .groupBy { it.dueDay ?: Long.MAX_VALUE }
            .toSortedMap()
            .values
            .flatMap { sameDayCards -> sameDayCards.shuffled() }

        return dueLearningCards + dueReviewCards
    }

    override suspend fun submitCardReview(
        cardId: String,
        quality: ReviewQuality,
        durationValidSeconds: Int
    ): Result<Card> = runCatching {
        val cleanCardId = cardId.trim()
        val nowMillis = timeProvider()
        val todayLearningDay = learningDayProvider()

        var updatedCard: Card? = null
        transactionRunner {
            val existingEntity = cardDao.getCardById(cleanCardId)
                ?: throw NoSuchElementException("Không tìm thấy thẻ.")
            val currentCard = existingEntity.toDomain()

            val computation = Sm2Engine.evaluateReview(
                card = currentCard,
                quality = quality,
                currentLearningDay = todayLearningDay,
                nowMillis = nowMillis
            )

            cardDao.upsertCard(computation.updatedCard.toEntity())
            updatedCard = computation.updatedCard
        }

        updatedCard ?: throw IllegalStateException("Không thể cập nhật tiến độ thẻ.")
    }

    override suspend fun undoLastReview(): Result<Card> =
        Result.failure(UnsupportedOperationException("Chưa cài đặt undoLastReview"))

    override fun getButtonPreviewLabels(card: Card): Map<ReviewQuality, String> = emptyMap()

    override suspend fun getTomorrowDueCount(deckId: String?): Int {
        val ownerId = ownerIdProvider()
        val tomorrowDay = learningDayProvider() + 1L
        val normalizedDeckId = normalizeDeckId(deckId)
        return cardDao.countTomorrowDueCards(
            ownerId = ownerId,
            deckId = normalizedDeckId,
            tomorrowDay = tomorrowDay
        )
    }

    private fun normalizeDeckId(deckId: String?): String? {
        val clean = deckId?.trim()
        return if (clean.isNullOrEmpty() || clean.equals("all", ignoreCase = true)) {
            null
        } else {
            clean
        }
    }
}
