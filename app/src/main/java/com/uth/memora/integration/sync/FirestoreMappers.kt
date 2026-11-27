package com.uth.memora.integration.sync

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
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

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: ÁNH XẠ CLOUD FIRESTORE (Mục 5, Mục 8.3 & Mục 10.1)
 * ============================================================================
 * Chuyển đổi 2 chiều giữa 4 domain model ([Deck], [Card], [ReviewLog], [PracticeSentence])
 * và bản ghi Cloud Firestore theo đường dẫn:
 * - `users/{uid}/decks`
 * - `users/{uid}/cards`
 * - `users/{uid}/reviewLogs`
 * - `users/{uid}/practiceSentences`
 *
 * Bảo toàn đầy đủ trường enum theo `name`, các mốc thời gian `Long` phục vụ Last-Write-Wins
 * (`updatedAtMillis`, `reviewedAtMillis`, `dueDay`, `createdAtMillis`), cờ xóa mềm `isDeleted`
 * và URL ảnh đám mây `cloudImageUrl`. Mọi trường null/thiếu đều có giá trị mặc định an toàn.
 */

/** Tên các collection đồng bộ trên Cloud Firestore (Mục 8.3 & Mục 10.1). */
object FirestoreCollections {
    const val USERS = "users"
    const val DECKS = "decks"
    const val CARDS = "cards"
    const val REVIEW_LOGS = "reviewLogs"
    const val PRACTICE_SENTENCES = "practiceSentences"
}

/** Tên trường chuẩn hóa dùng chung khi ghi/đọc document trên Cloud Firestore (Mục 5 & Mục 10.1). */
object FirestoreFields {
    // Trường chung
    const val ID = "id"
    const val OWNER_ID = "ownerId"
    const val CREATED_AT_MILLIS = "createdAtMillis"
    const val UPDATED_AT_MILLIS = "updatedAtMillis"
    const val IS_DELETED = "isDeleted"
    const val SYNC_STATUS = "syncStatus"

    // Trường Deck (Mục 5)
    const val NAME = "name"
    const val DESCRIPTION = "description"
    const val SOURCE_PREBUILT_ID = "sourcePrebuiltId"

    // Trường Card & SM-2 (Mục 5 & Mục 7.1)
    const val DECK_ID = "deckId"
    const val WORD = "word"
    const val MEANING = "meaning"
    const val PHONETIC = "phonetic"
    const val PART_OF_SPEECH = "partOfSpeech"
    const val EXAMPLE = "example"
    const val CLOUD_IMAGE_URL = "cloudImageUrl"
    const val STATE = "state"
    const val EASE_FACTOR = "easeFactor"
    const val REPETITIONS = "repetitions"
    const val INTERVAL = "interval"
    const val LAPSES = "lapses"
    const val DUE_DAY = "dueDay"
    const val DUE_AT_MILLIS = "dueAtMillis"
    const val FIRST_SEEN_DAY = "firstSeenDay"
    const val LAST_REVIEWED_AT_MILLIS = "lastReviewedAtMillis"

    // Trường ReviewLog (Mục 5, Mục 9.1 & Mục 10.2)
    const val CARD_ID = "cardId"
    const val QUALITY = "quality"
    const val LEARNING_DAY = "learningDay"
    const val REVIEWED_AT_MILLIS = "reviewedAtMillis"
    const val DURATION_VALID_SECONDS = "durationValidSeconds"
    const val SNAPSHOT_BEFORE_REVIEW = "snapshotBeforeReview"
    const val IS_UNDONE = "isUndone"

    // Trường PracticeSentence (Mục 6.7 & Mục 8.2)
    const val TARGET_WORD = "targetWord"
    const val SENTENCE = "sentence"
    const val STATUS = "status"
    const val SCORE = "score"
    const val CEFR_LEVEL = "cefrLevel"
    const val FEEDBACK_VI = "feedbackVi"
    const val GRAMMAR_ANALYSIS = "grammarAnalysis"
    const val CORRECTED_SENTENCE = "correctedSentence"
    const val NATURAL_ALTERNATIVE = "naturalAlternative"
    const val ERROR_MESSAGE = "errorMessage"
    const val RETRY_COUNT = "retryCount"
}

private const val DEFAULT_REVIEW_DURATION_SECONDS = 5

// ============================================================================
// 1. DECK <-> FIRESTORE (Mục 5, Mục 8.3 & Mục 10.1)
// ============================================================================

/**
 * Chuyển đổi [Deck] sang [Map] để ghi lên `users/{uid}/decks/{deckId}` (Mục 8.3 & Mục 10.1).
 * Bảo toàn cờ xóa mềm `isDeleted`, các mốc thời gian `Long` và enum `syncStatus.name`.
 */
fun Deck.toFirestoreMap(): Map<String, Any?> = linkedMapOf(
    FirestoreFields.ID to id,
    FirestoreFields.OWNER_ID to ownerId,
    FirestoreFields.NAME to name,
    FirestoreFields.DESCRIPTION to description,
    FirestoreFields.SOURCE_PREBUILT_ID to sourcePrebuiltId,
    FirestoreFields.CREATED_AT_MILLIS to createdAtMillis,
    FirestoreFields.UPDATED_AT_MILLIS to updatedAtMillis,
    FirestoreFields.IS_DELETED to isDeleted,
    FirestoreFields.SYNC_STATUS to syncStatus.name
)

/**
 * Đọc [Deck] từ [DocumentSnapshot] của Cloud Firestore, trả về `null` nếu document không tồn tại (Mục 8.3 & Mục 10.1).
 */
fun DocumentSnapshot.toDeck(
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID
): Deck? {
    if (!exists()) return null
    val rawData = data ?: return null
    val resolvedId = resolveDocumentId(rawData) ?: return null
    return rawData.toDeck(
        documentId = resolvedId,
        fallbackOwnerId = fallbackOwnerId
    )
}

/**
 * Chuyển đổi [Map] dữ liệu Firestore sang [Deck] với giá trị mặc định an toàn khi thiếu/null trường (Mục 8.3 & Mục 10.1).
 */
fun Map<String, Any?>.toDeck(
    documentId: String = "",
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID
): Deck {
    val resolvedId = documentId.takeIf { it.isNotBlank() }
        ?: getStringOrDefault(FirestoreFields.ID, "")
    val createdAt = getLongOrDefault(FirestoreFields.CREATED_AT_MILLIS, 0L)
    val updatedAt = getLongOrNull(FirestoreFields.UPDATED_AT_MILLIS) ?: createdAt

    return Deck(
        id = resolvedId,
        ownerId = getNonBlankStringOrDefault(FirestoreFields.OWNER_ID, fallbackOwnerId),
        name = getStringOrDefault(FirestoreFields.NAME, ""),
        description = getStringOrDefault(FirestoreFields.DESCRIPTION, ""),
        sourcePrebuiltId = getStringOrNull(FirestoreFields.SOURCE_PREBUILT_ID)?.takeIf { it.isNotBlank() },
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        isDeleted = getBooleanOrDefault(FirestoreFields.IS_DELETED, false),
        syncStatus = parseSyncStatus(
            raw = getStringOrNull(FirestoreFields.SYNC_STATUS),
            default = SyncStatus.SYNCED
        )
    )
}

// ============================================================================
// 2. CARD <-> FIRESTORE (Mục 5, Mục 7.1, Mục 8.3 & Mục 10.1)
// ============================================================================

/**
 * Chuyển đổi [Card] sang [Map] để ghi lên `users/{uid}/cards/{cardId}` (Mục 7.1, Mục 8.3 & Mục 10.1).
 *
 * Lưu ý: Không đẩy `localImagePath` (đường dẫn tệp riêng từng thiết bị) lên đám mây;
 * chỉ đồng bộ `cloudImageUrl` sau khi tải ảnh lên Firebase Storage để tránh lỗi hiển thị đa thiết bị.
 */
fun Card.toFirestoreMap(): Map<String, Any?> = linkedMapOf(
    FirestoreFields.ID to id,
    FirestoreFields.DECK_ID to deckId,
    FirestoreFields.OWNER_ID to ownerId,
    FirestoreFields.WORD to word,
    FirestoreFields.MEANING to meaning,
    FirestoreFields.PHONETIC to phonetic,
    FirestoreFields.PART_OF_SPEECH to partOfSpeech?.name,
    FirestoreFields.EXAMPLE to example,
    FirestoreFields.CLOUD_IMAGE_URL to cloudImageUrl,
    FirestoreFields.STATE to state.name,
    FirestoreFields.EASE_FACTOR to easeFactor,
    FirestoreFields.REPETITIONS to repetitions,
    FirestoreFields.INTERVAL to interval,
    FirestoreFields.LAPSES to lapses,
    FirestoreFields.DUE_DAY to dueDay,
    FirestoreFields.DUE_AT_MILLIS to dueAtMillis,
    FirestoreFields.FIRST_SEEN_DAY to firstSeenDay,
    FirestoreFields.LAST_REVIEWED_AT_MILLIS to lastReviewedAtMillis,
    FirestoreFields.CREATED_AT_MILLIS to createdAtMillis,
    FirestoreFields.UPDATED_AT_MILLIS to updatedAtMillis,
    FirestoreFields.IS_DELETED to isDeleted,
    FirestoreFields.SYNC_STATUS to syncStatus.name
)

/**
 * Đọc [Card] từ [DocumentSnapshot] của Cloud Firestore, trả về `null` nếu document không tồn tại (Mục 7.1 & Mục 10.1).
 */
fun DocumentSnapshot.toCard(
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID,
    localImagePath: String? = null
): Card? {
    if (!exists()) return null
    val rawData = data ?: return null
    val resolvedId = resolveDocumentId(rawData) ?: return null
    return rawData.toCard(
        documentId = resolvedId,
        fallbackOwnerId = fallbackOwnerId,
        localImagePath = localImagePath
    )
}

/**
 * Chuyển đổi [Map] dữ liệu Firestore sang [Card], bảo toàn lịch SM-2, enum, mốc thời gian,
 * `isDeleted` và `cloudImageUrl` với giá trị mặc định an toàn (Mục 7.1, Mục 8.3 & Mục 10.1).
 */
fun Map<String, Any?>.toCard(
    documentId: String = "",
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID,
    localImagePath: String? = null
): Card {
    val resolvedId = documentId.takeIf { it.isNotBlank() }
        ?: getStringOrDefault(FirestoreFields.ID, "")
    val createdAt = getLongOrDefault(FirestoreFields.CREATED_AT_MILLIS, 0L)
    val updatedAt = getLongOrNull(FirestoreFields.UPDATED_AT_MILLIS) ?: createdAt

    return Card(
        id = resolvedId,
        deckId = getStringOrDefault(FirestoreFields.DECK_ID, ""),
        ownerId = getNonBlankStringOrDefault(FirestoreFields.OWNER_ID, fallbackOwnerId),
        word = getStringOrDefault(FirestoreFields.WORD, ""),
        meaning = getStringOrDefault(FirestoreFields.MEANING, ""),
        phonetic = getStringOrDefault(FirestoreFields.PHONETIC, ""),
        partOfSpeech = parsePartOfSpeech(getStringOrNull(FirestoreFields.PART_OF_SPEECH)),
        example = getStringOrDefault(FirestoreFields.EXAMPLE, ""),
        localImagePath = localImagePath,
        cloudImageUrl = getStringOrNull(FirestoreFields.CLOUD_IMAGE_URL)?.takeIf { it.isNotBlank() },
        state = parseCardState(getStringOrNull(FirestoreFields.STATE)),
        easeFactor = getDoubleOrDefault(FirestoreFields.EASE_FACTOR, Card.INITIAL_EASE_FACTOR),
        repetitions = getIntOrDefault(FirestoreFields.REPETITIONS, 0).coerceAtLeast(0),
        interval = getIntOrDefault(FirestoreFields.INTERVAL, 0).coerceAtLeast(0),
        lapses = getIntOrDefault(FirestoreFields.LAPSES, 0).coerceAtLeast(0),
        dueDay = getLongOrNull(FirestoreFields.DUE_DAY),
        dueAtMillis = getLongOrNull(FirestoreFields.DUE_AT_MILLIS),
        firstSeenDay = getLongOrNull(FirestoreFields.FIRST_SEEN_DAY),
        lastReviewedAtMillis = getLongOrNull(FirestoreFields.LAST_REVIEWED_AT_MILLIS),
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        isDeleted = getBooleanOrDefault(FirestoreFields.IS_DELETED, false),
        syncStatus = parseSyncStatus(
            raw = getStringOrNull(FirestoreFields.SYNC_STATUS),
            default = SyncStatus.SYNCED
        )
    )
}

// ============================================================================
// 3. REVIEW LOG & SNAPSHOT <-> FIRESTORE (Mục 5, Mục 9.1, Mục 10.1 & Mục 10.2)
// ============================================================================

/**
 * Chuyển đổi [CardScheduleSnapshot] sang [Map] lồng nhau trong document [ReviewLog] (Mục 9.1 & Mục 10.1).
 */
fun CardScheduleSnapshot.toFirestoreMap(): Map<String, Any?> = linkedMapOf(
    FirestoreFields.STATE to state.name,
    FirestoreFields.EASE_FACTOR to easeFactor,
    FirestoreFields.REPETITIONS to repetitions,
    FirestoreFields.INTERVAL to interval,
    FirestoreFields.LAPSES to lapses,
    FirestoreFields.DUE_DAY to dueDay,
    FirestoreFields.DUE_AT_MILLIS to dueAtMillis,
    FirestoreFields.FIRST_SEEN_DAY to firstSeenDay,
    FirestoreFields.LAST_REVIEWED_AT_MILLIS to lastReviewedAtMillis
)

/**
 * Đọc [CardScheduleSnapshot] từ [Map] lồng nhau, tự điền mặc định thẻ mới nếu null/thiếu trường (Mục 9.1).
 */
fun Map<String, Any?>?.toCardScheduleSnapshot(): CardScheduleSnapshot {
    if (this == null) return defaultCardScheduleSnapshot()
    return CardScheduleSnapshot(
        state = parseCardState(getStringOrNull(FirestoreFields.STATE)),
        easeFactor = getDoubleOrDefault(FirestoreFields.EASE_FACTOR, Card.INITIAL_EASE_FACTOR),
        repetitions = getIntOrDefault(FirestoreFields.REPETITIONS, 0).coerceAtLeast(0),
        interval = getIntOrDefault(FirestoreFields.INTERVAL, 0).coerceAtLeast(0),
        lapses = getIntOrDefault(FirestoreFields.LAPSES, 0).coerceAtLeast(0),
        dueDay = getLongOrNull(FirestoreFields.DUE_DAY),
        dueAtMillis = getLongOrNull(FirestoreFields.DUE_AT_MILLIS),
        firstSeenDay = getLongOrNull(FirestoreFields.FIRST_SEEN_DAY),
        lastReviewedAtMillis = getLongOrNull(FirestoreFields.LAST_REVIEWED_AT_MILLIS)
    )
}

/**
 * Chuyển đổi [ReviewLog] sang [Map] để ghi lên `users/{uid}/reviewLogs/{logId}` (Mục 9.1, Mục 10.1 & Mục 10.2).
 */
fun ReviewLog.toFirestoreMap(): Map<String, Any?> = linkedMapOf(
    FirestoreFields.ID to id,
    FirestoreFields.CARD_ID to cardId,
    FirestoreFields.DECK_ID to deckId,
    FirestoreFields.OWNER_ID to ownerId,
    FirestoreFields.QUALITY to quality.name,
    FirestoreFields.LEARNING_DAY to learningDay,
    FirestoreFields.REVIEWED_AT_MILLIS to reviewedAtMillis,
    FirestoreFields.DURATION_VALID_SECONDS to durationValidSeconds,
    FirestoreFields.SNAPSHOT_BEFORE_REVIEW to snapshotBeforeReview.toFirestoreMap(),
    FirestoreFields.IS_UNDONE to isUndone,
    FirestoreFields.UPDATED_AT_MILLIS to updatedAtMillis,
    FirestoreFields.SYNC_STATUS to syncStatus.name
)

/**
 * Đọc [ReviewLog] từ [DocumentSnapshot] của Cloud Firestore, trả về `null` nếu document không tồn tại (Mục 10.1 & Mục 10.2).
 */
fun DocumentSnapshot.toReviewLog(
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID
): ReviewLog? {
    if (!exists()) return null
    val rawData = data ?: return null
    val resolvedId = resolveDocumentId(rawData) ?: return null
    return rawData.toReviewLog(
        documentId = resolvedId,
        fallbackOwnerId = fallbackOwnerId
    )
}

/**
 * Chuyển đổi [Map] dữ liệu Firestore sang [ReviewLog] với giá trị mặc định an toàn (Mục 9.1, Mục 10.1 & Mục 10.2).
 */
fun Map<String, Any?>.toReviewLog(
    documentId: String = "",
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID
): ReviewLog {
    val resolvedId = documentId.takeIf { it.isNotBlank() }
        ?: getStringOrDefault(FirestoreFields.ID, "")
    val reviewedAt = getLongOrDefault(FirestoreFields.REVIEWED_AT_MILLIS, 0L)
    val updatedAt = getLongOrNull(FirestoreFields.UPDATED_AT_MILLIS) ?: reviewedAt

    return ReviewLog(
        id = resolvedId,
        cardId = getStringOrDefault(FirestoreFields.CARD_ID, ""),
        deckId = getStringOrDefault(FirestoreFields.DECK_ID, ""),
        ownerId = getNonBlankStringOrDefault(FirestoreFields.OWNER_ID, fallbackOwnerId),
        quality = parseReviewQuality(this[FirestoreFields.QUALITY]),
        learningDay = getLongOrDefault(FirestoreFields.LEARNING_DAY, 0L),
        reviewedAtMillis = reviewedAt,
        durationValidSeconds = getIntOrDefault(
            key = FirestoreFields.DURATION_VALID_SECONDS,
            default = DEFAULT_REVIEW_DURATION_SECONDS
        ).coerceAtLeast(0),
        snapshotBeforeReview = getNestedMapOrNull(FirestoreFields.SNAPSHOT_BEFORE_REVIEW)
            .toCardScheduleSnapshot(),
        isUndone = getBooleanOrDefault(FirestoreFields.IS_UNDONE, false),
        updatedAtMillis = updatedAt,
        syncStatus = parseSyncStatus(
            raw = getStringOrNull(FirestoreFields.SYNC_STATUS),
            default = SyncStatus.SYNCED
        )
    )
}

// ============================================================================
// 4. PRACTICE SENTENCE <-> FIRESTORE (Mục 6.7, Mục 8.2, Mục 8.3 & Mục 10.1)
// ============================================================================

/**
 * Chuyển đổi [PracticeSentence] sang [Map] để ghi lên `users/{uid}/practiceSentences/{sentenceId}` (Mục 6.7 & Mục 10.1).
 */
fun PracticeSentence.toFirestoreMap(): Map<String, Any?> = linkedMapOf(
    FirestoreFields.ID to id,
    FirestoreFields.CARD_ID to cardId,
    FirestoreFields.OWNER_ID to ownerId,
    FirestoreFields.TARGET_WORD to targetWord,
    FirestoreFields.SENTENCE to sentence,
    FirestoreFields.STATUS to status.name,
    FirestoreFields.SCORE to score?.toDouble(),
    FirestoreFields.CEFR_LEVEL to cefrLevel,
    FirestoreFields.FEEDBACK_VI to feedbackVi,
    FirestoreFields.GRAMMAR_ANALYSIS to grammarAnalysis,
    FirestoreFields.CORRECTED_SENTENCE to correctedSentence,
    FirestoreFields.NATURAL_ALTERNATIVE to naturalAlternative,
    FirestoreFields.ERROR_MESSAGE to errorMessage,
    FirestoreFields.RETRY_COUNT to retryCount,
    FirestoreFields.LEARNING_DAY to learningDay,
    FirestoreFields.CREATED_AT_MILLIS to createdAtMillis,
    FirestoreFields.UPDATED_AT_MILLIS to updatedAtMillis,
    FirestoreFields.SYNC_STATUS to syncStatus.name
)

/**
 * Đọc [PracticeSentence] từ [DocumentSnapshot] của Cloud Firestore, trả về `null` nếu document không tồn tại (Mục 6.7 & Mục 10.1).
 */
fun DocumentSnapshot.toPracticeSentence(
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID
): PracticeSentence? {
    if (!exists()) return null
    val rawData = data ?: return null
    val resolvedId = resolveDocumentId(rawData) ?: return null
    return rawData.toPracticeSentence(
        documentId = resolvedId,
        fallbackOwnerId = fallbackOwnerId
    )
}

/**
 * Chuyển đổi [Map] dữ liệu Firestore sang [PracticeSentence] với giá trị mặc định an toàn (Mục 6.7, Mục 8.2 & Mục 10.1).
 */
fun Map<String, Any?>.toPracticeSentence(
    documentId: String = "",
    fallbackOwnerId: String = Deck.GUEST_OWNER_ID
): PracticeSentence {
    val resolvedId = documentId.takeIf { it.isNotBlank() }
        ?: getStringOrDefault(FirestoreFields.ID, "")
    val createdAt = getLongOrDefault(FirestoreFields.CREATED_AT_MILLIS, 0L)
    val updatedAt = getLongOrNull(FirestoreFields.UPDATED_AT_MILLIS) ?: createdAt

    return PracticeSentence(
        id = resolvedId,
        cardId = getStringOrDefault(FirestoreFields.CARD_ID, ""),
        ownerId = getNonBlankStringOrDefault(FirestoreFields.OWNER_ID, fallbackOwnerId),
        targetWord = getStringOrDefault(FirestoreFields.TARGET_WORD, ""),
        sentence = getStringOrDefault(FirestoreFields.SENTENCE, ""),
        status = parseSentenceStatus(getStringOrNull(FirestoreFields.STATUS)),
        score = getFloatOrNull(FirestoreFields.SCORE),
        cefrLevel = getStringOrNull(FirestoreFields.CEFR_LEVEL),
        feedbackVi = getStringOrNull(FirestoreFields.FEEDBACK_VI),
        grammarAnalysis = getStringListOrEmpty(FirestoreFields.GRAMMAR_ANALYSIS),
        correctedSentence = getStringOrNull(FirestoreFields.CORRECTED_SENTENCE),
        naturalAlternative = getStringOrNull(FirestoreFields.NATURAL_ALTERNATIVE),
        errorMessage = getStringOrNull(FirestoreFields.ERROR_MESSAGE),
        retryCount = getIntOrDefault(FirestoreFields.RETRY_COUNT, 0).coerceAtLeast(0),
        learningDay = getLongOrDefault(FirestoreFields.LEARNING_DAY, 0L),
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        syncStatus = parseSyncStatus(
            raw = getStringOrNull(FirestoreFields.SYNC_STATUS),
            default = SyncStatus.SYNCED
        )
    )
}

// ============================================================================
// HÀM PHỤ TRỢ ĐỌC AN TOÀN ENUM & KIỂU DỮ LIỆU FIRESTORE (Mục 10.1)
// ============================================================================

private fun DocumentSnapshot.resolveDocumentId(rawData: Map<String, Any?>): String? {
    return id.takeIf { it.isNotBlank() }
        ?: rawData.getStringOrNull(FirestoreFields.ID)?.takeIf { it.isNotBlank() }
}

private fun defaultCardScheduleSnapshot(): CardScheduleSnapshot = CardScheduleSnapshot(
    state = CardState.NEW,
    easeFactor = Card.INITIAL_EASE_FACTOR,
    repetitions = 0,
    interval = 0,
    lapses = 0,
    dueDay = null,
    dueAtMillis = null,
    firstSeenDay = null,
    lastReviewedAtMillis = null
)

private fun parsePartOfSpeech(raw: String?): PartOfSpeech? {
    val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return PartOfSpeech.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
}

private fun parseCardState(raw: String?): CardState {
    val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return CardState.NEW
    return CardState.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        ?: CardState.NEW
}

private fun parseReviewQuality(raw: Any?): ReviewQuality = when (raw) {
    is String -> {
        val normalized = raw.trim()
        ReviewQuality.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
            ?: ReviewQuality.GOOD
    }
    is Number -> ReviewQuality.fromQ(raw.toInt())
    else -> ReviewQuality.GOOD
}

private fun parseSentenceStatus(raw: String?): SentenceStatus {
    val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return SentenceStatus.PENDING_GRADE
    return SentenceStatus.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        ?: SentenceStatus.PENDING_GRADE
}

private fun parseSyncStatus(raw: String?, default: SyncStatus): SyncStatus {
    val normalized = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return default
    return SyncStatus.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        ?: default
}

private fun Map<String, Any?>.getStringOrNull(key: String): String? =
    this[key] as? String

private fun Map<String, Any?>.getStringOrDefault(key: String, default: String): String =
    (this[key] as? String) ?: default

private fun Map<String, Any?>.getNonBlankStringOrDefault(key: String, default: String): String =
    (this[key] as? String)?.takeIf { it.isNotBlank() } ?: default

private fun Map<String, Any?>.getBooleanOrDefault(key: String, default: Boolean): Boolean =
    (this[key] as? Boolean) ?: default

private fun Map<String, Any?>.getIntOrDefault(key: String, default: Int): Int =
    (this[key] as? Number)?.toInt() ?: default

private fun Map<String, Any?>.getLongOrNull(key: String): Long? = when (val raw = this[key]) {
    is Number -> raw.toLong()
    is Timestamp -> raw.seconds * 1_000L + raw.nanoseconds / 1_000_000L
    is String -> raw.toLongOrNull()
    else -> null
}

private fun Map<String, Any?>.getLongOrDefault(key: String, default: Long): Long =
    getLongOrNull(key) ?: default

private fun Map<String, Any?>.getDoubleOrDefault(key: String, default: Double): Double =
    (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() } ?: default

private fun Map<String, Any?>.getFloatOrNull(key: String): Float? =
    (this[key] as? Number)?.toFloat()?.takeIf { it.isFinite() }

private fun Map<String, Any?>.getStringListOrEmpty(key: String): List<String> {
    val rawList = this[key] as? List<*> ?: return emptyList()
    return rawList.mapNotNull { it as? String }
}

private fun Map<String, Any?>.getNestedMapOrNull(key: String): Map<String, Any?>? {
    val rawMap = this[key] as? Map<*, *> ?: return null
    val result = LinkedHashMap<String, Any?>(rawMap.size)
    rawMap.forEach { (k, v) ->
        if (k is String) {
            result[k] = v
        }
    }
    return result
}
