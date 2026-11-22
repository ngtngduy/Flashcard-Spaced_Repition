package com.uth.memora.core.database.mapper

import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.database.entity.DeckEntity
import com.uth.memora.core.database.entity.PracticeSentenceEntity
import com.uth.memora.core.database.entity.ReviewLogEntity
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.ReviewLog

/**
 * Các hàm ánh xạ 2 chiều giữa tầng Room Entity và tầng Domain Model (Mục 5 & Mục 10.1).
 *
 * Bảo toàn đầy đủ 100% trường dữ liệu, thông tin SM-2, snapshot Undo và metadata đồng bộ.
 */

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    sourcePrebuiltId = sourcePrebuiltId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    isDeleted = isDeleted,
    syncStatus = syncStatus
)

fun DeckEntity.toDomain(): Deck = Deck(
    id = id,
    ownerId = ownerId,
    name = name,
    description = description,
    sourcePrebuiltId = sourcePrebuiltId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    isDeleted = isDeleted,
    syncStatus = syncStatus
)

fun Card.toEntity(): CardEntity = CardEntity(
    id = id,
    deckId = deckId,
    ownerId = ownerId,
    word = word,
    meaning = meaning,
    phonetic = phonetic,
    partOfSpeech = partOfSpeech,
    example = example,
    localImagePath = localImagePath,
    cloudImageUrl = cloudImageUrl,
    state = state,
    easeFactor = easeFactor,
    repetitions = repetitions,
    interval = interval,
    lapses = lapses,
    dueDay = dueDay,
    dueAtMillis = dueAtMillis,
    firstSeenDay = firstSeenDay,
    lastReviewedAtMillis = lastReviewedAtMillis,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    isDeleted = isDeleted,
    syncStatus = syncStatus
)

fun CardEntity.toDomain(): Card = Card(
    id = id,
    deckId = deckId,
    ownerId = ownerId,
    word = word,
    meaning = meaning,
    phonetic = phonetic,
    partOfSpeech = partOfSpeech,
    example = example,
    localImagePath = localImagePath,
    cloudImageUrl = cloudImageUrl,
    state = state,
    easeFactor = easeFactor,
    repetitions = repetitions,
    interval = interval,
    lapses = lapses,
    dueDay = dueDay,
    dueAtMillis = dueAtMillis,
    firstSeenDay = firstSeenDay,
    lastReviewedAtMillis = lastReviewedAtMillis,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    isDeleted = isDeleted,
    syncStatus = syncStatus
)

fun ReviewLog.toEntity(): ReviewLogEntity = ReviewLogEntity(
    id = id,
    cardId = cardId,
    deckId = deckId,
    ownerId = ownerId,
    quality = quality,
    learningDay = learningDay,
    reviewedAtMillis = reviewedAtMillis,
    durationValidSeconds = durationValidSeconds,
    snapshotBeforeReview = snapshotBeforeReview,
    isUndone = isUndone,
    updatedAtMillis = updatedAtMillis,
    syncStatus = syncStatus
)

fun ReviewLogEntity.toDomain(): ReviewLog = ReviewLog(
    id = id,
    cardId = cardId,
    deckId = deckId,
    ownerId = ownerId,
    quality = quality,
    learningDay = learningDay,
    reviewedAtMillis = reviewedAtMillis,
    durationValidSeconds = durationValidSeconds,
    snapshotBeforeReview = snapshotBeforeReview,
    isUndone = isUndone,
    updatedAtMillis = updatedAtMillis,
    syncStatus = syncStatus
)

fun PracticeSentence.toEntity(): PracticeSentenceEntity = PracticeSentenceEntity(
    id = id,
    cardId = cardId,
    ownerId = ownerId,
    targetWord = targetWord,
    sentence = sentence,
    status = status,
    score = score,
    cefrLevel = cefrLevel,
    feedbackVi = feedbackVi,
    grammarAnalysis = grammarAnalysis,
    correctedSentence = correctedSentence,
    naturalAlternative = naturalAlternative,
    errorMessage = errorMessage,
    retryCount = retryCount,
    learningDay = learningDay,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    syncStatus = syncStatus
)

fun PracticeSentenceEntity.toDomain(): PracticeSentence = PracticeSentence(
    id = id,
    cardId = cardId,
    ownerId = ownerId,
    targetWord = targetWord,
    sentence = sentence,
    status = status,
    score = score,
    cefrLevel = cefrLevel,
    feedbackVi = feedbackVi,
    grammarAnalysis = grammarAnalysis,
    correctedSentence = correctedSentence,
    naturalAlternative = naturalAlternative,
    errorMessage = errorMessage,
    retryCount = retryCount,
    learningDay = learningDay,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    syncStatus = syncStatus
)
