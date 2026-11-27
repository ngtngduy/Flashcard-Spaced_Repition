package com.uth.memora.integration.sync

import androidx.room.withTransaction
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.uth.memora.core.database.MemoraDatabase
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.database.mapper.toDomain
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.CloudSyncManager
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.ReviewLog
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: ĐỒNG BỘ ĐÁM MÂY CLOUD FIRESTORE (Mục 8.3 & Mục 10.1)
 * ============================================================================
 * Hiện thực [CloudSyncManager] sử dụng Cloud Firestore + Firebase Storage theo đường dẫn:
 * - `users/{uid}/decks/{id}`
 * - `users/{uid}/cards/{id}`
 * - `users/{uid}/reviewLogs/{id}`
 * - `users/{uid}/practiceSentences/{id}`
 *
 * Hiện thực đẩy (Push) theo lô (`WriteBatch` tối đa 450 bản ghi/lô với `SetOptions.merge()`)
 * cho cả 4 bảng `Deck`, `Card`, `ReviewLog` và `PracticeSentence` đang có `syncStatus == PENDING_SYNC`,
 * sau đó cập nhật `SyncStatus.SYNCED` trong Room Database trong transaction mà vẫn giữ nguyên
 * `updatedAtMillis` gốc để bảo toàn Last-Write-Wins. Tài khoản Khách (`isAnonymous == true`)
 * được bỏ qua push/pull cho đến khi đăng nhập tài khoản chính thức.
 */
class FirestoreCloudSyncManager(
    private val authRepository: AuthRepository,
    private val database: MemoraDatabase,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    @Suppress("unused")
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : CloudSyncManager {

    private val syncMutex = Mutex()

    /**
     * Đồng bộ các thay đổi đang chờ (`PENDING_SYNC`) của tài khoản chính thức lên Cloud Firestore (Mục 8.3 & Mục 10.1).
     *
     * Thứ tự thực hiện:
     * 1. Nếu chưa đăng nhập hoặc đang ở chế độ Khách (`isAnonymous == true`) -> bỏ qua push/pull, trả `Result.success(0)`.
     * 2. Đẩy toàn bộ `Deck` đang `PENDING_SYNC` trước để bảo đảm quan hệ tham chiếu bộ -> thẻ.
     * 3. Đẩy toàn bộ `Card` đang `PENDING_SYNC` (bao gồm cả thẻ xóa mềm `isDeleted = true`).
     * 4. Đẩy toàn bộ `ReviewLog` (lịch sử ôn tập SM-2 & cờ `isUndone`) đang `PENDING_SYNC`.
     * 5. Đẩy toàn bộ `PracticeSentence` (lịch sử đặt câu & kết quả chấm AI) đang `PENDING_SYNC`.
     * 6. Cập nhật mốc `lastSyncedAtMillis` vào DataStore sau khi đồng bộ thành công.
     *
     * @return [Result.success] kèm tổng số bản ghi đã đẩy thành công, hoặc [Result.failure] với thông báo tiếng Việt.
     */
    override suspend fun syncPendingChanges(): Result<Int> = syncMutex.withLock {
        withContext(ioDispatcher) {
            try {
                // Tài khoản Khách (isAnonymous == true) hoặc chưa đăng nhập -> bỏ qua push/pull (Mục 6.2 & 10.1)
                val uid = resolveAuthenticatedUid()
                    ?: return@withContext Result.success(0)

                // Đẩy theo thứ tự tham chiếu: Deck -> Card -> ReviewLog -> PracticeSentence (Mục 8.3 & 10.1)
                val pushedDecksCount = pushPendingDecks(uid)
                val pushedCardsCount = pushPendingCards(uid)
                val pushedReviewLogsCount = pushPendingReviewLogs(uid)
                val pushedPracticeSentencesCount = pushPendingPracticeSentences(uid)
                val totalSyncedCount = pushedDecksCount +
                    pushedCardsCount +
                    pushedReviewLogsCount +
                    pushedPracticeSentencesCount

                userPreferencesRepository.setLastSyncedAtMillis(nowProvider())
                Result.success(totalSyncedCount)
            } catch (e: CancellationException) {
                // Không nuốt tín hiệu hủy coroutine có chủ đích từ ViewModel / Worker
                throw e
            } catch (e: Exception) {
                Result.failure(mapSyncException(e))
            }
        }
    }

    /**
     * Gộp hoặc bỏ dữ liệu chế độ khách vào tài khoản đăng nhập (Mục 6.2 & Mục 10.1).
     * Sẽ được triển khai chi tiết ở bước gộp dữ liệu khách tiếp theo.
     */
    override suspend fun mergeGuestDataIntoAccount(
        accountId: String,
        discardGuestData: Boolean
    ): Result<Unit> {
        return Result.success(Unit)
    }

    /**
     * Đẩy các [Deck] có `syncStatus == PENDING_SYNC` lên `users/{uid}/decks/{id}` theo lô (Mục 8.3 & 10.1).
     * Chỉ đánh dấu `SYNCED` trong Room sau khi batch commit Firestore thành công.
     */
    internal suspend fun pushPendingDecks(uid: String): Int {
        val deckDao = database.deckDao()
        val pendingDecks = deckDao.getPendingSyncDecks(uid).map { it.toDomain() }
        if (pendingDecks.isEmpty()) return 0

        val decksCollection = userDocument(uid).collection(FirestoreCollections.DECKS)
        var syncedCount = 0

        for (chunk in pendingDecks.chunked(MAX_FIRESTORE_BATCH_SIZE)) {
            val batch = firestore.batch()
            for (deck in chunk) {
                val docRef = decksCollection.document(deck.id)
                val payload = deck.copy(syncStatus = SyncStatus.SYNCED).toFirestoreMap()
                batch.set(docRef, payload, SetOptions.merge())
            }
            batch.commit().await()

            markDecksSyncedInRoom(chunk)
            syncedCount += chunk.size
        }

        return syncedCount
    }

    /**
     * Đẩy các [Card] có `syncStatus == PENDING_SYNC` lên `users/{uid}/cards/{id}` theo lô (Mục 8.3 & 10.1).
     * Chỉ đánh dấu `SYNCED` trong Room sau khi batch commit Firestore thành công.
     */
    internal suspend fun pushPendingCards(uid: String): Int {
        val cardDao = database.cardDao()
        val pendingCards = cardDao.getPendingSyncCards(uid).map { it.toDomain() }
        if (pendingCards.isEmpty()) return 0

        val cardsCollection = userDocument(uid).collection(FirestoreCollections.CARDS)
        var syncedCount = 0

        for (chunk in pendingCards.chunked(MAX_FIRESTORE_BATCH_SIZE)) {
            val batch = firestore.batch()
            for (card in chunk) {
                val docRef = cardsCollection.document(card.id)
                val payload = card.copy(syncStatus = SyncStatus.SYNCED).toFirestoreMap()
                batch.set(docRef, payload, SetOptions.merge())
            }
            batch.commit().await()

            markCardsSyncedInRoom(chunk)
            syncedCount += chunk.size
        }

        return syncedCount
    }

    /**
     * Đẩy các [ReviewLog] có `syncStatus == PENDING_SYNC` lên `users/{uid}/reviewLogs/{id}` theo lô (Mục 8.3, 10.1 & 10.2).
     * Bảo toàn đầy đủ `snapshotBeforeReview`, `isUndone`, `reviewedAtMillis` và `updatedAtMillis`.
     */
    internal suspend fun pushPendingReviewLogs(uid: String): Int {
        val reviewLogDao = database.reviewLogDao()
        val pendingLogs = reviewLogDao.getPendingSyncReviewLogs(uid).map { it.toDomain() }
        if (pendingLogs.isEmpty()) return 0

        val reviewLogsCollection = userDocument(uid).collection(FirestoreCollections.REVIEW_LOGS)
        var syncedCount = 0

        for (chunk in pendingLogs.chunked(MAX_FIRESTORE_BATCH_SIZE)) {
            val batch = firestore.batch()
            for (log in chunk) {
                val docRef = reviewLogsCollection.document(log.id)
                val payload = log.copy(syncStatus = SyncStatus.SYNCED).toFirestoreMap()
                batch.set(docRef, payload, SetOptions.merge())
            }
            batch.commit().await()

            markReviewLogsSyncedInRoom(chunk)
            syncedCount += chunk.size
        }

        return syncedCount
    }

    /**
     * Đẩy các [PracticeSentence] có `syncStatus == PENDING_SYNC` lên `users/{uid}/practiceSentences/{id}` theo lô (Mục 6.7, 8.2 & 10.1).
     * Bảo toàn trạng thái chấm AI (`status`), điểm số, nhận xét tiếng Việt, phân tích ngữ pháp và câu sửa.
     */
    internal suspend fun pushPendingPracticeSentences(uid: String): Int {
        val practiceSentenceDao = database.practiceSentenceDao()
        val pendingSentences = practiceSentenceDao.getPendingSyncSentences(uid).map { it.toDomain() }
        if (pendingSentences.isEmpty()) return 0

        val sentencesCollection = userDocument(uid).collection(FirestoreCollections.PRACTICE_SENTENCES)
        var syncedCount = 0

        for (chunk in pendingSentences.chunked(MAX_FIRESTORE_BATCH_SIZE)) {
            val batch = firestore.batch()
            for (sentence in chunk) {
                val docRef = sentencesCollection.document(sentence.id)
                val payload = sentence.copy(syncStatus = SyncStatus.SYNCED).toFirestoreMap()
                batch.set(docRef, payload, SetOptions.merge())
            }
            batch.commit().await()

            markPracticeSentencesSyncedInRoom(chunk)
            syncedCount += chunk.size
        }

        return syncedCount
    }

    /**
     * Cập nhật `syncStatus = SYNCED` cho lô [Deck] trong Room, giữ nguyên `updatedAtMillis` gốc
     * và bỏ qua nếu người dùng vừa sửa bản ghi cục bộ trong lúc đang tải lên (Mục 10.1).
     */
    private suspend fun markDecksSyncedInRoom(pushedDecks: List<Deck>) {
        val deckDao = database.deckDao()
        database.withTransaction {
            for (deck in pushedDecks) {
                val current = deckDao.getDeckByIdIncludingDeleted(deck.id) ?: continue
                if (
                    current.syncStatus == SyncStatus.PENDING_SYNC &&
                    current.updatedAtMillis == deck.updatedAtMillis
                ) {
                    deckDao.updateSyncStatus(
                        id = deck.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAtMillis = deck.updatedAtMillis
                    )
                }
            }
        }
    }

    /**
     * Cập nhật `syncStatus = SYNCED` cho lô [Card] trong Room, giữ nguyên `updatedAtMillis` gốc
     * và bỏ qua nếu người dùng vừa sửa bản ghi cục bộ trong lúc đang tải lên (Mục 10.1).
     */
    private suspend fun markCardsSyncedInRoom(pushedCards: List<Card>) {
        val cardDao = database.cardDao()
        database.withTransaction {
            for (card in pushedCards) {
                val current = cardDao.getCardByIdIncludingDeleted(card.id) ?: continue
                if (
                    current.syncStatus == SyncStatus.PENDING_SYNC &&
                    current.updatedAtMillis == card.updatedAtMillis
                ) {
                    cardDao.updateSyncStatus(
                        id = card.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAtMillis = card.updatedAtMillis
                    )
                }
            }
        }
    }

    /**
     * Cập nhật `syncStatus = SYNCED` cho lô [ReviewLog] trong Room, giữ nguyên `updatedAtMillis` gốc
     * và không ghi đè nếu lượt ôn vừa bị Hoàn tác (`isUndone`) trong lúc đang đẩy lên đám mây (Mục 9.1 & 10.1).
     */
    private suspend fun markReviewLogsSyncedInRoom(pushedLogs: List<ReviewLog>) {
        val reviewLogDao = database.reviewLogDao()
        database.withTransaction {
            for (log in pushedLogs) {
                val current = reviewLogDao.getLogById(log.id) ?: continue
                if (
                    current.syncStatus == SyncStatus.PENDING_SYNC &&
                    current.updatedAtMillis == log.updatedAtMillis &&
                    current.isUndone == log.isUndone
                ) {
                    reviewLogDao.updateSyncStatus(
                        id = log.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAtMillis = log.updatedAtMillis
                    )
                }
            }
        }
    }

    /**
     * Cập nhật `syncStatus = SYNCED` cho lô [PracticeSentence] trong Room, giữ nguyên `updatedAtMillis` gốc
     * và không ghi đè nếu AI vừa chấm xong kết quả mới trong lúc đang đẩy lên đám mây (Mục 8.2 & 10.1).
     */
    private suspend fun markPracticeSentencesSyncedInRoom(pushedSentences: List<PracticeSentence>) {
        val practiceSentenceDao = database.practiceSentenceDao()
        database.withTransaction {
            for (sentence in pushedSentences) {
                val current = practiceSentenceDao.getSentenceById(sentence.id) ?: continue
                if (
                    current.syncStatus == SyncStatus.PENDING_SYNC &&
                    current.updatedAtMillis == sentence.updatedAtMillis &&
                    current.status == sentence.status
                ) {
                    practiceSentenceDao.updateSyncStatus(
                        id = sentence.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAtMillis = sentence.updatedAtMillis
                    )
                }
            }
        }
    }

    /**
     * Lấy `uid` hợp lệ của tài khoản đã đăng nhập (loại trừ chế độ khách và bộ từ mẫu) (Mục 6.2 & 10.1).
     */
    private suspend fun resolveAuthenticatedUid(): String? {
        val user = authRepository.getCurrentUser() ?: return null
        if (user.isAnonymous) return null
        val uid = user.uid.trim()
        if (uid.isEmpty() || uid == Deck.GUEST_OWNER_ID || uid == DeckDao.PREBUILT_OWNER_ID) {
            return null
        }
        return uid
    }

    private fun userDocument(uid: String) =
        firestore.collection(FirestoreCollections.USERS).document(uid)

    companion object {
        /**
         * Ngưỡng ghi an toàn cho mỗi `WriteBatch` Firestore (giới hạn tối đa của Firestore là 500 thao tác/lô) (Mục 10.1).
         */
        const val MAX_FIRESTORE_BATCH_SIZE: Int = 450

        const val ERROR_NOT_AUTHENTICATED_VI: String =
            "Vui lòng đăng nhập tài khoản để đồng bộ dữ liệu lên đám mây."
        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng hoặc máy chủ đồng bộ không phản hồi. Dữ liệu cục bộ vẫn an toàn và sẽ đồng bộ lại sau."
        const val ERROR_RATE_LIMIT_VI: String =
            "Hệ thống đồng bộ đang quá tải hoặc vượt giới hạn yêu cầu (429). Vui lòng thử lại sau ít phút."
        const val ERROR_PERMISSION_VI: String =
            "Phiên đăng nhập đã hết hạn hoặc bạn không có quyền đồng bộ dữ liệu. Vui lòng đăng nhập lại."
        const val ERROR_INVALID_FORMAT_VI: String =
            "Dữ liệu đồng bộ không đúng định dạng. Vui lòng thử lại sau."
        const val ERROR_SYNC_UNKNOWN_VI: String =
            "Đồng bộ dữ liệu lên đám mây chưa thành công. Vui lòng thử lại sau."

        /**
         * Phân loại ngoại lệ Firestore/mạng/429/định dạng dữ liệu sang thông báo tiếng Việt thân thiện (Mục 8.3 & 10.1).
         */
        internal fun mapSyncException(throwable: Throwable): Exception {
            return when (throwable) {
                is FirebaseFirestoreException -> when (throwable.code) {
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                        Exception(ERROR_RATE_LIMIT_VI, throwable)

                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        Exception(ERROR_NETWORK_VI, throwable)

                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        Exception(ERROR_PERMISSION_VI, throwable)

                    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                    FirebaseFirestoreException.Code.DATA_LOSS,
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        Exception(ERROR_INVALID_FORMAT_VI, throwable)

                    else -> mapGenericSyncException(throwable)
                }

                is FirebaseTooManyRequestsException ->
                    Exception(ERROR_RATE_LIMIT_VI, throwable)

                is FirebaseNetworkException ->
                    Exception(ERROR_NETWORK_VI, throwable)

                else -> mapGenericSyncException(throwable)
            }
        }

        private fun mapGenericSyncException(throwable: Throwable): Exception {
            val causeChain = generateSequence(throwable) { it.cause }.toList()
            val combinedMessage = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            if (
                combinedMessage.contains("429") ||
                combinedMessage.contains("resource_exhausted") ||
                combinedMessage.contains("too_many_requests") ||
                combinedMessage.contains("too many requests") ||
                combinedMessage.contains("quota")
            ) {
                return Exception(ERROR_RATE_LIMIT_VI, throwable)
            }

            if (
                causeChain.any { it is IOException } ||
                combinedMessage.contains("network") ||
                combinedMessage.contains("unavailable") ||
                combinedMessage.contains("unable to resolve host") ||
                combinedMessage.contains("failed to connect") ||
                combinedMessage.contains("timeout") ||
                combinedMessage.contains("timed out")
            ) {
                return Exception(ERROR_NETWORK_VI, throwable)
            }

            if (
                combinedMessage.contains("json") ||
                combinedMessage.contains("malformed") ||
                combinedMessage.contains("invalid_argument") ||
                combinedMessage.contains("format")
            ) {
                return Exception(ERROR_INVALID_FORMAT_VI, throwable)
            }

            return Exception(ERROR_SYNC_UNKNOWN_VI, throwable)
        }
    }
}
