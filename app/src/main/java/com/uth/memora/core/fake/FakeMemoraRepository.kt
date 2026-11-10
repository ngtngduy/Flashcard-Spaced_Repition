package com.uth.memora.core.fake

import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.domain.PracticeSentenceRepository
import com.uth.memora.core.domain.SentenceLocalValidator
import com.uth.memora.core.domain.StatisticsRepository
import com.uth.memora.core.domain.StudySessionRepository
import com.uth.memora.core.domain.VietnameseTextNormalizer
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.model.ReviewLog
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.StudyStatistics
import com.uth.memora.core.model.SyncStatus
import com.uth.memora.core.sm2.LearningDayCalculator
import com.uth.memora.core.sm2.Sm2Engine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Kho dữ liệu giả lập (In-Memory Fake Repository) cài sẵn dữ liệu mẫu và kết nối trực tiếp với Sm2Engine.
 *
 * Giúp Thành viên 2 (Compose UI) và Thành viên 3 (AI/Cloud) clone repo về là chạy app,
 * bấm lật thẻ, chấm điểm câu với hiệu ứng Shimmer được ngay lập tức mà không cần chờ Room DB hoàn tất.
 */
class FakeMemoraRepository(
    private val aiGradingService: AiGradingService = FakeAiGradingService()
) : DeckRepository,
    CardRepository,
    StudySessionRepository,
    PracticeSentenceRepository,
    StatisticsRepository {

    private val todayLearningDay: Long
        get() = LearningDayCalculator.toLearningDay()

    private val _decks = MutableStateFlow(createInitialDecks())
    private val _prebuiltDecks = MutableStateFlow(createPrebuiltDecks())
    private val _cards = MutableStateFlow(createInitialCards(LearningDayCalculator.toLearningDay()))
    private val _reviewLogs = MutableStateFlow<List<ReviewLog>>(emptyList())
    private val _sentences = MutableStateFlow<List<PracticeSentence>>(emptyList())

    // ---------------- DeckRepository ----------------

    override fun observeActiveDecks(): Flow<List<Deck>> =
        _decks.map { list -> list.filter { !it.isDeleted } }

    override fun observePrebuiltDecks(): Flow<List<Deck>> = _prebuiltDecks

    override suspend fun getDeckById(deckId: String): Deck? =
        _decks.value.firstOrNull { it.id == deckId && !it.isDeleted }

    override suspend fun createDeck(name: String, description: String): Result<Deck> {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > Deck.MAX_NAME_LENGTH) {
            return Result.failure(IllegalArgumentException("Tên bộ từ phải từ 1 đến ${Deck.MAX_NAME_LENGTH} ký tự."))
        }
        val duplicate = _decks.value.any { !it.isDeleted && it.name.equals(cleanName, ignoreCase = true) }
        if (duplicate) {
            return Result.failure(IllegalArgumentException("Tên bộ từ \"$cleanName\" đã tồn tại."))
        }
        val newDeck = Deck(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            description = description.trim().take(Deck.MAX_DESCRIPTION_LENGTH)
        )
        _decks.value = _decks.value + newDeck
        return Result.success(newDeck)
    }

    override suspend fun updateDeck(deckId: String, name: String, description: String): Result<Deck> {
        val deck = getDeckById(deckId) ?: return Result.failure(NoSuchElementException("Không tìm thấy bộ từ."))
        val updated = deck.copy(
            name = name.trim().take(Deck.MAX_NAME_LENGTH),
            description = description.trim().take(Deck.MAX_DESCRIPTION_LENGTH),
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        _decks.value = _decks.value.map { if (it.id == deckId) updated else it }
        return Result.success(updated)
    }

    override suspend fun softDeleteDeck(deckId: String): Result<Unit> {
        val now = System.currentTimeMillis()
        _decks.value = _decks.value.map {
            if (it.id == deckId) it.copy(isDeleted = true, updatedAtMillis = now, syncStatus = SyncStatus.PENDING_SYNC)
            else it
        }
        _cards.value = _cards.value.map {
            if (it.deckId == deckId) it.copy(isDeleted = true, updatedAtMillis = now, syncStatus = SyncStatus.PENDING_SYNC)
            else it
        }
        return Result.success(Unit)
    }

    override suspend fun importPrebuiltDeck(prebuiltDeckId: String): Result<Deck> {
        val alreadyImported = _decks.value.firstOrNull { !it.isDeleted && it.sourcePrebuiltId == prebuiltDeckId }
        if (alreadyImported != null) {
            return Result.success(alreadyImported)
        }
        val prebuilt = _prebuiltDecks.value.firstOrNull { it.id == prebuiltDeckId }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy bộ từ có sẵn."))
        val copiedDeck = Deck(
            id = UUID.randomUUID().toString(),
            name = prebuilt.name,
            description = prebuilt.description,
            sourcePrebuiltId = prebuilt.id
        )
        _decks.value = _decks.value + copiedDeck
        return Result.success(copiedDeck)
    }

    // ---------------- CardRepository ----------------

    override fun observeCardsByDeck(deckId: String): Flow<List<Card>> =
        _cards.map { list -> list.filter { !it.isDeleted && it.deckId == deckId } }

    override fun observeAllActiveCards(): Flow<List<Card>> =
        _cards.map { list -> list.filter { !it.isDeleted } }

    override suspend fun getCardById(cardId: String): Card? =
        _cards.value.firstOrNull { it.id == cardId && !it.isDeleted }

    override suspend fun searchCards(deckId: String?, query: String): List<Card> {
        return _cards.value.filter { card ->
            !card.isDeleted &&
                (deckId == null || card.deckId == deckId) &&
                (VietnameseTextNormalizer.matchesQuery(card.word, query) ||
                    VietnameseTextNormalizer.matchesQuery(card.meaning, query))
        }
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
    ): Result<Card> {
        val cleanWord = word.trim()
        val cleanMeaning = meaning.trim()
        if (cleanWord.isEmpty() || cleanWord.length > Card.MAX_WORD_LENGTH) {
            return Result.failure(IllegalArgumentException("Từ vựng phải từ 1 đến ${Card.MAX_WORD_LENGTH} ký tự."))
        }
        if (cleanMeaning.isEmpty() || cleanMeaning.length > Card.MAX_MEANING_LENGTH) {
            return Result.failure(IllegalArgumentException("Nghĩa phải từ 1 đến ${Card.MAX_MEANING_LENGTH} ký tự."))
        }
        if (!allowDuplicateWord) {
            val duplicate = _cards.value.any {
                !it.isDeleted && it.deckId == deckId && it.word.equals(cleanWord, ignoreCase = true)
            }
            if (duplicate) {
                return Result.failure(IllegalStateException("Từ \"$cleanWord\" đã có trong bộ từ này."))
            }
        }
        val newCard = Card(
            id = UUID.randomUUID().toString(),
            deckId = deckId,
            word = cleanWord,
            meaning = cleanMeaning,
            phonetic = phonetic.trim().take(Card.MAX_PHONETIC_LENGTH),
            partOfSpeech = partOfSpeech,
            example = example.trim().take(Card.MAX_EXAMPLE_LENGTH),
            localImagePath = localImagePath,
            state = CardState.NEW
        )
        _cards.value = _cards.value + newCard
        return Result.success(newCard)
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
    ): Result<Card> {
        val card = getCardById(cardId) ?: return Result.failure(NoSuchElementException("Không tìm thấy thẻ."))
        val updated = card.copy(
            deckId = deckId,
            word = word.trim().take(Card.MAX_WORD_LENGTH),
            meaning = meaning.trim().take(Card.MAX_MEANING_LENGTH),
            phonetic = phonetic.trim().take(Card.MAX_PHONETIC_LENGTH),
            partOfSpeech = partOfSpeech,
            example = example.trim().take(Card.MAX_EXAMPLE_LENGTH),
            localImagePath = localImagePath,
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        _cards.value = _cards.value.map { if (it.id == cardId) updated else it }
        return Result.success(updated)
    }

    override suspend fun softDeleteCard(cardId: String): Result<Unit> {
        val now = System.currentTimeMillis()
        _cards.value = _cards.value.map {
            if (it.id == cardId) it.copy(isDeleted = true, updatedAtMillis = now, syncStatus = SyncStatus.PENDING_SYNC)
            else it
        }
        return Result.success(Unit)
    }

    override suspend fun resetCardProgress(cardId: String): Result<Card> {
        val card = getCardById(cardId) ?: return Result.failure(NoSuchElementException("Không tìm thấy thẻ."))
        val reset = card.copy(
            state = CardState.NEW,
            easeFactor = Card.INITIAL_EASE_FACTOR,
            repetitions = 0,
            interval = 0,
            lapses = 0,
            dueDay = null,
            dueAtMillis = null,
            firstSeenDay = null,
            lastReviewedAtMillis = null,
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        _cards.value = _cards.value.map { if (it.id == cardId) reset else it }
        return Result.success(reset)
    }

    override suspend fun resetDeckProgress(deckId: String): Result<Unit> {
        val now = System.currentTimeMillis()
        _cards.value = _cards.value.map { card ->
            if (card.deckId == deckId && !card.isDeleted) {
                card.copy(
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
            } else card
        }
        return Result.success(Unit)
    }

    // ---------------- StudySessionRepository ----------------

    override suspend fun getNewCardsQueue(deckId: String?): List<Card> {
        val today = todayLearningDay
        val newLearnedToday = _cards.value.count { !it.isDeleted && it.firstSeenDay == today }
        val remainingQuota = (20 - newLearnedToday).coerceAtLeast(0)
        return _cards.value
            .filter { !it.isDeleted && it.state == CardState.NEW && (deckId == null || it.deckId == deckId) }
            .sortedBy { it.createdAtMillis }
            .take(remainingQuota)
    }

    override suspend fun getDueReviewQueue(deckId: String?): List<Card> {
        val today = todayLearningDay
        val now = System.currentTimeMillis()
        val active = _cards.value.filter { !it.isDeleted && (deckId == null || it.deckId == deckId) }

        val learningDue = active
            .filter { it.state == CardState.LEARNING && (it.dueAtMillis ?: 0L) <= now }
            .sortedBy { it.dueAtMillis ?: 0L }

        val reviewDue = active
            .filter { it.state == CardState.REVIEW && (it.dueDay ?: Long.MAX_VALUE) <= today }
            .groupBy { it.dueDay ?: Long.MAX_VALUE }
            .toSortedMap()
            .values
            .flatMap { sameDayCards -> sameDayCards.shuffled() }

        return learningDue + reviewDue
    }

    override suspend fun submitCardReview(
        cardId: String,
        quality: ReviewQuality,
        durationValidSeconds: Int
    ): Result<Card> {
        val card = getCardById(cardId) ?: return Result.failure(NoSuchElementException("Không tìm thấy thẻ."))
        val now = System.currentTimeMillis()
        val today = todayLearningDay

        val result = Sm2Engine.evaluateReview(
            card = card,
            quality = quality,
            currentLearningDay = today,
            nowMillis = now
        )

        val log = ReviewLog(
            id = UUID.randomUUID().toString(),
            cardId = card.id,
            deckId = card.deckId,
            quality = quality,
            learningDay = today,
            reviewedAtMillis = now,
            durationValidSeconds = durationValidSeconds.coerceIn(1, ReviewLog.MAX_SECONDS_PER_REVIEW),
            snapshotBeforeReview = result.snapshotBeforeReview
        )

        _cards.value = _cards.value.map { if (it.id == cardId) result.updatedCard else it }
        _reviewLogs.value = _reviewLogs.value + log
        return Result.success(result.updatedCard)
    }

    override suspend fun undoLastReview(): Result<Card> {
        val lastLog = _reviewLogs.value.lastOrNull { !it.isUndone }
            ?: return Result.failure(IllegalStateException("Không có lượt đánh giá nào để hoàn tác."))

        val card = getCardById(lastLog.cardId)
            ?: return Result.failure(NoSuchElementException("Không tìm thấy thẻ cần hoàn tác."))

        val restoredCard = Sm2Engine.restoreFromSnapshot(card, lastLog.snapshotBeforeReview)
        _cards.value = _cards.value.map { if (it.id == card.id) restoredCard else it }
        _reviewLogs.value = _reviewLogs.value.map {
            if (it.id == lastLog.id) it.copy(isUndone = true, updatedAtMillis = System.currentTimeMillis())
            else it
        }
        return Result.success(restoredCard)
    }

    override fun getButtonPreviewLabels(card: Card): Map<ReviewQuality, String> {
        return Sm2Engine.previewButtonIntervals(card, todayLearningDay)
    }

    // ---------------- PracticeSentenceRepository ----------------

    override fun observeSentencesByCard(cardId: String): Flow<List<PracticeSentence>> =
        _sentences.map { list -> list.filter { it.cardId == cardId }.sortedByDescending { it.createdAtMillis } }

    override fun observeRecentSentences(): Flow<List<PracticeSentence>> =
        _sentences.map { list -> list.sortedByDescending { it.createdAtMillis } }

    override suspend fun getSuggestedPracticeCards(deckId: String?, limit: Int): List<Card> {
        val active = _cards.value.filter { !it.isDeleted && (deckId == null || it.deckId == deckId) }
        return active
            .sortedWith(
                compareBy<Card> {
                    when (it.state) {
                        CardState.LEARNING -> 0
                        CardState.REVIEW -> 1
                        CardState.NEW -> 2
                    }
                }.thenBy { it.dueDay ?: Long.MAX_VALUE }
            )
            .take(limit.coerceAtMost(PracticeSentence.MAX_WORDS_PER_SESSION))
    }

    override suspend fun submitPracticeSentence(
        cardId: String,
        sentence: String,
        isOnline: Boolean
    ): Result<PracticeSentence> {
        val card = getCardById(cardId) ?: return Result.failure(NoSuchElementException("Không tìm thấy thẻ."))
        val validation = SentenceLocalValidator.validate(card.word, sentence)
        if (!validation.isValid) {
            return Result.failure(IllegalArgumentException(validation.errorMessageVi ?: "Câu không hợp lệ."))
        }

        val cleanSentence = sentence.trim()
        // Không tạo bản ghi mới nếu đang Chờ chấm hoặc Đang chấm cùng một câu (Mục 11)
        val duplicateInFlight = _sentences.value.firstOrNull {
            it.cardId == cardId &&
                it.sentence.equals(cleanSentence, ignoreCase = true) &&
                (it.status == SentenceStatus.PENDING_GRADE || it.status == SentenceStatus.GRADING)
        }
        if (duplicateInFlight != null) {
            return Result.success(duplicateInFlight)
        }

        val now = System.currentTimeMillis()
        val initialStatus = if (isOnline) SentenceStatus.GRADING else SentenceStatus.PENDING_GRADE
        val record = PracticeSentence(
            id = UUID.randomUUID().toString(),
            cardId = card.id,
            targetWord = card.word,
            sentence = cleanSentence,
            status = initialStatus,
            learningDay = todayLearningDay,
            createdAtMillis = now,
            updatedAtMillis = now
        )
        _sentences.value = _sentences.value + record

        if (!isOnline) {
            return Result.success(record)
        }

        val aiResult = aiGradingService.evaluateSentence(card.word, card.meaning, cleanSentence)
        val finalRecord = aiResult.fold(
            onSuccess = { eval ->
                record.copy(
                    status = SentenceStatus.GRADED,
                    score = eval.score,
                    cefrLevel = eval.cefrLevel,
                    feedbackVi = eval.feedbackVi,
                    grammarAnalysis = eval.grammarAnalysis,
                    correctedSentence = eval.correctedSentence,
                    naturalAlternative = eval.naturalAlternative,
                    updatedAtMillis = System.currentTimeMillis()
                )
            },
            onFailure = { err ->
                record.copy(
                    status = SentenceStatus.GRADE_ERROR,
                    errorMessage = err.message ?: "Lỗi khi chấm câu với AI",
                    retryCount = 1,
                    updatedAtMillis = System.currentTimeMillis()
                )
            }
        )
        _sentences.value = _sentences.value.map { if (it.id == record.id) finalRecord else it }
        return Result.success(finalRecord)
    }

    override suspend fun retryFailedSentence(sentenceId: String): Result<PracticeSentence> {
        val existing = _sentences.value.firstOrNull { it.id == sentenceId }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy câu cần chấm lại."))
        val card = getCardById(existing.cardId)
        return submitPracticeSentence(
            cardId = existing.cardId,
            sentence = existing.sentence,
            isOnline = card != null
        )
    }

    // ---------------- StatisticsRepository ----------------

    override fun observeStatistics(): Flow<StudyStatistics> =
        combine(_cards, _reviewLogs) { cards, logs ->
            val today = todayLearningDay
            val now = System.currentTimeMillis()
            val activeCards = cards.filter { !it.isDeleted }
            val validLogs = logs.filter { !it.isUndone }

            val newToday = activeCards.count { it.firstSeenDay == today }
            val reviewsToday = validLogs.count { it.learningDay == today }
            val dueCount = activeCards.count { it.isDue(today, now) }
            val distribution = RetentionStage.entries.associateWith { stage ->
                activeCards.count { it.retentionStage == stage }
            }
            val masteredCount = distribution[RetentionStage.LEVEL_5_MASTERED] ?: 0
            val totalSeconds = validLogs.sumOf { it.durationValidSeconds.toLong() }

            val recentLogs = validLogs.filter { it.learningDay >= today - 7 }
            val retentionRate = if (recentLogs.isEmpty()) {
                92f
            } else {
                val passed = recentLogs.count { it.quality != ReviewQuality.AGAIN }
                (passed * 100f) / recentLogs.size
            }

            StudyStatistics(
                newCardsLearnedToday = newToday,
                dailyNewCardLimit = 20,
                reviewsToday = reviewsToday,
                dueCardsCount = dueCount,
                remainingNewCardsToday = (20 - newToday).coerceAtLeast(0),
                currentStreakDays = maxOf(3, if (reviewsToday > 0) 4 else 3),
                totalStudyTimeSeconds = totalSeconds + 420L,
                masteredCardsCount = masteredCount,
                totalActiveCardsCount = activeCards.size,
                stageDistribution = distribution,
                retentionRatePercent = retentionRate
            )
        }

    companion object {
        private fun createInitialDecks(): List<Deck> = listOf(
            Deck(
                id = "deck_everyday",
                name = "Everyday English",
                description = "Từ vựng giao tiếp và đọc báo thông dụng hằng ngày"
            ),
            Deck(
                id = "deck_work",
                name = "Work & Productivity",
                description = "Từ vựng công sở, thuyết trình và làm việc nhóm"
            )
        )

        private fun createPrebuiltDecks(): List<Deck> = listOf(
            Deck(
                id = "prebuilt_tech",
                name = "Technology & Innovation",
                description = "35 từ vựng chủ đề công nghệ phần mềm và trí tuệ nhân tạo"
            ),
            Deck(
                id = "prebuilt_travel",
                name = "Travel & Culture",
                description = "40 từ vựng thiết yếu khi du lịch và giao lưu văn hóa"
            )
        )

        private fun createInitialCards(today: Long): List<Card> {
            val now = System.currentTimeMillis()
            return listOf(
                Card(
                    id = "card_1",
                    deckId = "deck_everyday",
                    word = "resilient",
                    phonetic = "/rɪˈzɪliənt/",
                    partOfSpeech = PartOfSpeech.ADJECTIVE,
                    meaning = "kiên cường, có khả năng phục hồi nhanh sau khó khăn",
                    example = "Children are often remarkably resilient in the face of change.",
                    state = CardState.REVIEW,
                    easeFactor = 2.5,
                    repetitions = 2,
                    interval = 6,
                    dueDay = today, // Đến hạn ôn hôm nay (Cấp 3 – Ghi nhớ trung hạn)
                    firstSeenDay = today - 7
                ),
                Card(
                    id = "card_2",
                    deckId = "deck_everyday",
                    word = "meticulous",
                    phonetic = "/məˈtɪkjələs/",
                    partOfSpeech = PartOfSpeech.ADJECTIVE,
                    meaning = "tỉ mỉ, kỹ lưỡng đến từng chi tiết nhỏ",
                    example = "She kept meticulous records of every experiment.",
                    state = CardState.LEARNING,
                    easeFactor = 2.5,
                    repetitions = 0,
                    interval = 0,
                    dueAtMillis = now - 60_000L, // Đến hạn ôn ngay (Cấp 1 – Khởi động)
                    firstSeenDay = today
                ),
                Card(
                    id = "card_3",
                    deckId = "deck_everyday",
                    word = "ubiquitous",
                    phonetic = "/juːˈbɪkwɪtəs/",
                    partOfSpeech = PartOfSpeech.ADJECTIVE,
                    meaning = "có mặt ở khắp mọi nơi, phổ biến rộng rãi",
                    example = "Smartphones have become ubiquitous in modern daily life.",
                    state = CardState.NEW // Thẻ mới (Cấp 0 – Chưa học)
                ),
                Card(
                    id = "card_4",
                    deckId = "deck_work",
                    word = "pragmatic",
                    phonetic = "/præɡˈmætɪk/",
                    partOfSpeech = PartOfSpeech.ADJECTIVE,
                    meaning = "thực tế, thực dụng, ưu tiên giải pháp khả thi",
                    example = "We need a pragmatic approach to solve this scheduling conflict.",
                    state = CardState.REVIEW,
                    easeFactor = 2.6,
                    repetitions = 4,
                    interval = 25,
                    dueDay = today + 5, // Cấp 5 – Thành thạo (interval >= 21 ngày)
                    firstSeenDay = today - 30
                ),
                Card(
                    id = "card_5",
                    deckId = "deck_work",
                    word = "eloquent",
                    phonetic = "/ˈeləkwənt/",
                    partOfSpeech = PartOfSpeech.ADJECTIVE,
                    meaning = "hùng hồn, có khả năng diễn đạt thuyết phục",
                    example = "She gave an eloquent presentation on sustainable urban design.",
                    state = CardState.REVIEW,
                    easeFactor = 2.5,
                    repetitions = 1,
                    interval = 2,
                    dueDay = today, // Đến hạn ôn hôm nay (Cấp 2 – Ghi nhớ ngắn hạn)
                    firstSeenDay = today - 2
                )
            )
        }
    }
}
