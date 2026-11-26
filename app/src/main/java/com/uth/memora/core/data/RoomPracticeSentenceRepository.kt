package com.uth.memora.core.data

import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.PracticeSentenceDao
import com.uth.memora.core.database.mapper.toDomain
import com.uth.memora.core.database.mapper.toEntity
import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.PracticeSentenceRepository
import com.uth.memora.core.domain.SentenceLocalValidator
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.SyncStatus
import com.uth.memora.core.sm2.LearningDayCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Hiện thực [PracticeSentenceRepository] trên Room Database (Mục 6.7, 6.8, 8.2, 10.1 & 11).
 *
 * Hỗ trợ kiểm tra cục bộ tức thì, chống gửi trùng câu đang chấm, kiểm soát hạn mức 20 câu/ngày,
 * chấm câu trực tuyến kèm Shimmer Loading hoặc lưu hàng đợi ngoại tuyến cho [SentenceGradingWorker],
 * và lưu câu đã sửa từ AI làm câu ví dụ cho thẻ gốc.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomPracticeSentenceRepository(
    private val practiceSentenceDao: PracticeSentenceDao,
    private val cardDao: CardDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val aiGradingService: AiGradingService,
    private val authRepository: AuthRepository? = null,
    private val consumeQuotaOnOnlineSuccess: Boolean = true,
    private val onOfflineSentenceEnqueued: ((String) -> Unit)? = null,
    private val ownerIdProvider: suspend () -> String = {
        authRepository?.getCurrentUser()?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID
    },
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val learningDayProvider: () -> Long = {
        LearningDayCalculator.toLearningDay(timeProvider())
    }
) : PracticeSentenceRepository {

    override fun observeSentencesByCard(cardId: String): Flow<List<PracticeSentence>> =
        practiceSentenceDao.observeSentencesByCard(cardId.trim()).map { list ->
            list.map { it.toDomain() }
        }

    override fun observeRecentSentences(): Flow<List<PracticeSentence>> {
        val ownerFlow: Flow<String> = authRepository
            ?.observeCurrentUser()
            ?.map { user -> user?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID }
            ?.distinctUntilChanged()
            ?: flow { emit(ownerIdProvider()) }

        return ownerFlow.flatMapLatest { ownerId ->
            practiceSentenceDao.observeRecentSentences(ownerId).map { list ->
                list.map { it.toDomain() }
            }
        }
    }

    override suspend fun getPendingSentences(): List<PracticeSentence> =
        practiceSentenceDao.getAllPendingSentences().map { it.toDomain() }

    override suspend fun getSuggestedPracticeCards(
        deckId: String?,
        limit: Int
    ): List<Card> {
        val cleanDeckId = deckId?.trim()?.takeIf {
            it.isNotEmpty() && !it.equals("all", ignoreCase = true)
        }
        val activeCards = if (cleanDeckId != null) {
            cardDao.observeCardsByDeck(cleanDeckId).first().map { it.toDomain() }
        } else {
            cardDao.getAllActiveCards(ownerIdProvider()).map { it.toDomain() }
        }

        val effectiveLimit = limit.coerceIn(1, PracticeSentence.MAX_WORDS_PER_SESSION)
        return activeCards
            .sortedWith(
                compareBy<Card> {
                    when (it.state) {
                        CardState.LEARNING -> 0
                        CardState.REVIEW -> 1
                        CardState.NEW -> 2
                    }
                }
                    .thenByDescending { it.lapses }
                    .thenBy { it.easeFactor }
                    .thenBy { it.dueDay ?: Long.MAX_VALUE }
            )
            .take(effectiveLimit)
    }

    override suspend fun submitPracticeSentence(
        cardId: String,
        sentence: String,
        isOnline: Boolean
    ): Result<PracticeSentence> {
        val cleanCardId = cardId.trim()
        val cardEntity = cardDao.getCardById(cleanCardId)
            ?: return Result.failure(NoSuchElementException("Không tìm thấy thẻ mục tiêu."))

        val validation = SentenceLocalValidator.validate(cardEntity.word, sentence)
        if (!validation.isValid) {
            return Result.failure(
                IllegalArgumentException(validation.errorMessageVi ?: "Câu đặt chưa hợp lệ.")
            )
        }

        val cleanSentence = sentence.trim()
        val existingSentences = practiceSentenceDao.observeSentencesByCard(cleanCardId).first()
        val duplicateInFlight = existingSentences.firstOrNull { existing ->
            existing.sentence.trim().equals(cleanSentence, ignoreCase = true) &&
                (existing.status == SentenceStatus.PENDING_GRADE ||
                    existing.status == SentenceStatus.GRADING)
        }
        if (duplicateInFlight != null) {
            return Result.success(duplicateInFlight.toDomain())
        }

        val prefs = preferencesRepository.getUserPreferences()
        if (prefs.aiSentenceGradingRemainingToday <= 0) {
            return Result.failure(
                IllegalStateException(
                    "Bạn đã dùng hết 20 lượt chấm câu AI hôm nay (tự hồi lúc 4:00 sáng)."
                )
            )
        }

        val nowMillis = timeProvider()
        val todayLearningDay = learningDayProvider()
        val initialStatus = if (isOnline) {
            SentenceStatus.GRADING
        } else {
            SentenceStatus.PENDING_GRADE
        }

        val initialRecord = PracticeSentence(
            id = UUID.randomUUID().toString(),
            cardId = cardEntity.id,
            ownerId = cardEntity.ownerId,
            targetWord = cardEntity.word,
            sentence = cleanSentence,
            status = initialStatus,
            learningDay = todayLearningDay,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        practiceSentenceDao.upsertSentence(initialRecord.toEntity())

        if (!isOnline) {
            onOfflineSentenceEnqueued?.invoke(initialRecord.id)
            return Result.success(initialRecord)
        }

        val aiResult = try {
            aiGradingService.evaluateSentence(
                targetWord = cardEntity.word,
                meaningVi = cardEntity.meaning,
                userSentence = cleanSentence
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

        val finalRecord = aiResult.fold(
            onSuccess = { evaluation ->
                if (consumeQuotaOnOnlineSuccess) {
                    preferencesRepository.consumeSentenceGradingQuota()
                }
                initialRecord.copy(
                    status = SentenceStatus.GRADED,
                    score = evaluation.score,
                    cefrLevel = evaluation.cefrLevel,
                    feedbackVi = evaluation.feedbackVi,
                    grammarAnalysis = evaluation.grammarAnalysis,
                    correctedSentence = evaluation.correctedSentence,
                    naturalAlternative = evaluation.naturalAlternative,
                    errorMessage = null,
                    updatedAtMillis = timeProvider(),
                    syncStatus = SyncStatus.PENDING_SYNC
                )
            },
            onFailure = { error ->
                initialRecord.copy(
                    status = SentenceStatus.GRADE_ERROR,
                    errorMessage = error.message ?: "Chưa thể chấm câu lúc này, bạn bấm Chấm lại nhé.",
                    retryCount = 1,
                    updatedAtMillis = timeProvider(),
                    syncStatus = SyncStatus.PENDING_SYNC
                )
            }
        )

        practiceSentenceDao.upsertSentence(finalRecord.toEntity())
        return Result.success(finalRecord)
    }

    override suspend fun saveAiGradingResult(
        sentenceId: String,
        status: SentenceStatus,
        score: Float?,
        cefrLevel: String?,
        feedbackVi: String?,
        grammarAnalysis: List<String>,
        correctedSentence: String?,
        naturalAlternative: String?,
        errorMessage: String?,
        retryCount: Int?
    ): Result<PracticeSentence> = runCatching {
        val cleanId = sentenceId.trim()
        val existingEntity = practiceSentenceDao.getSentenceById(cleanId)
            ?: throw NoSuchElementException("Không tìm thấy câu luyện tập.")

        val existing = existingEntity.toDomain()
        val updated = existing.copy(
            status = status,
            score = score ?: existing.score,
            cefrLevel = cefrLevel ?: existing.cefrLevel,
            feedbackVi = feedbackVi ?: existing.feedbackVi,
            grammarAnalysis = grammarAnalysis.ifEmpty { existing.grammarAnalysis },
            correctedSentence = correctedSentence ?: existing.correctedSentence,
            naturalAlternative = naturalAlternative ?: existing.naturalAlternative,
            errorMessage = errorMessage,
            retryCount = retryCount ?: existing.retryCount,
            updatedAtMillis = timeProvider(),
            syncStatus = SyncStatus.PENDING_SYNC
        )

        practiceSentenceDao.upsertSentence(updated.toEntity())
        updated
    }

    override suspend fun saveCorrectedSentenceAsExample(sentenceId: String): Result<Card> = runCatching {
        val cleanId = sentenceId.trim()
        val sentenceEntity = practiceSentenceDao.getSentenceById(cleanId)
            ?: throw NoSuchElementException("Không tìm thấy câu luyện tập.")

        val corrected = sentenceEntity.correctedSentence?.trim().orEmpty()
        if (corrected.isEmpty()) {
            throw IllegalStateException("Câu chưa có bản sửa từ AI để lưu làm ví dụ.")
        }

        val cardEntity = cardDao.getCardById(sentenceEntity.cardId)
            ?: throw NoSuchElementException("Không tìm thấy thẻ gốc.")

        val updatedCard = cardEntity.toDomain().copy(
            example = corrected.take(Card.MAX_EXAMPLE_LENGTH),
            updatedAtMillis = timeProvider(),
            syncStatus = SyncStatus.PENDING_SYNC
        )

        cardDao.upsertCard(updatedCard.toEntity())
        updatedCard
    }

    override suspend fun retryFailedSentence(sentenceId: String): Result<PracticeSentence> = runCatching {
        val cleanId = sentenceId.trim()
        val existingEntity = practiceSentenceDao.getSentenceById(cleanId)
            ?: throw NoSuchElementException("Không tìm thấy câu luyện tập.")

        val prefs = preferencesRepository.getUserPreferences()
        if (prefs.aiSentenceGradingRemainingToday <= 0) {
            throw IllegalStateException(
                "Bạn đã dùng hết 20 lượt chấm câu AI hôm nay (tự hồi lúc 4:00 sáng)."
            )
        }

        val resetRecord = existingEntity.toDomain().copy(
            status = SentenceStatus.PENDING_GRADE,
            errorMessage = null,
            retryCount = 0,
            updatedAtMillis = timeProvider(),
            syncStatus = SyncStatus.PENDING_SYNC
        )

        practiceSentenceDao.upsertSentence(resetRecord.toEntity())
        onOfflineSentenceEnqueued?.invoke(resetRecord.id)
        resetRecord
    }
}
