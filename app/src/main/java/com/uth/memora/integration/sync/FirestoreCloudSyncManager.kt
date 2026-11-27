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
 * Bước G2 hiện thực đẩy (Push) theo lô (`WriteBatch` tối đa 450 bản ghi/lô với `SetOptions.merge()`)
 * cho `Deck` và `Card` đang có `syncStatus == PENDING_SYNC`, sau đó cập nhật `SyncStatus.SYNCED`
 * trong Room Database trong transaction mà vẫn giữ nguyên `updatedAtMillis` gốc để bảo toàn LWW.
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
     * Đồng bộ các thay đổi đang chờ (`PENDING_SYNC`) của tài khoản hiện tại lên Cloud Firestore (Mục 8.3 & Mục 10.1).
     *
     * Thứ tự thực hiện:
     * 1. Xác thực người dùng đã đăng nhập tài khoản chính thức (không đồng bộ ở chế độ khách).
     * 2. Đẩy toàn bộ `Deck` đang `PENDING_SYNC` trước để bảo đảm quan hệ tham chiếu bộ -> thẻ.
     * 3. Đẩy toàn bộ `Card` đang `PENDING_SYNC` (bao gồm cả thẻ xóa mềm `isDeleted = true`).
     * 4. Cập nhật mốc `lastSyncedAtMillis` vào DataStore sau khi đồng bộ thành công.
     *
     * @return [Result.success] kèm tổng số bản ghi đã đẩy thành công, hoặc [Result.failure] với thông báo tiếng Việt.
     */
    override suspend fun syncPendingChanges(): Result<Int> = syncMutex.withLock {
        withContext(ioDispatcher) {
            try {
                val uid = resolveAuthenticatedUid()
                    ?: return@withContext Result.failure(
                        IllegalStateException(ERROR_NOT_AUTHENTICATED_VI)
                    )

                // Đẩy Deck trước rồi mới đẩy Card để giữ nhất quán dữ liệu bộ từ - thẻ (Mục 8.3 & 10.1)
                val pushedDecksCount = pushPendingDecks(uid)
                val pushedCardsCount = pushPendingCards(uid)
                val totalSyncedCount = pushedDecksCount + pushedCardsCount

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
