package com.uth.memora.integration.sync

import android.content.Context
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
import com.uth.memora.core.database.mapper.toEntity
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
 * Hiện thực đồng bộ 2 chiều (Pull → Push → Dọn xóa mềm 30 ngày) cho cả 4 bảng `Deck`, `Card`,
 * `ReviewLog` và `PracticeSentence`:
 * - Pull (Last-Write-Wins): đọc mốc `lastSyncedAtMillis` (mặc định `0L`), truy vấn thay đổi mới hơn
 *   từ Firestore (`whereGreaterThan("updatedAtMillis", lastSyncedAtMillis)`), chỉ ghi đè vào Room khi
 *   chưa có bản cục bộ, hoặc `remote.updatedAtMillis > local.updatedAtMillis`, hoặc bản cục bộ đã
 *   `SYNCED`; giữ nguyên `isDeleted = true` nếu bản thắng là bản xóa mềm.
 * - Push: đẩy theo lô (`WriteBatch` tối đa 450 bản ghi/lô với `SetOptions.merge()`) các bản ghi
 *   còn `syncStatus == PENDING_SYNC` (bao gồm các bản cục bộ thắng LWW), sau đó cập nhật `SYNCED`
 *   trong Room mà vẫn giữ nguyên `updatedAtMillis` gốc.
 * - Dọn rác 30 ngày (Mục 10.1): sau khi đồng bộ xong, xóa vĩnh viễn khỏi Room các bản ghi xóa mềm
 *   (`isDeleted == true && syncStatus == SYNCED`) có `updatedAtMillis` cũ hơn 30 ngày.
 * - Tài khoản Khách (`isAnonymous == true`) được bỏ qua push/pull cho đến khi đăng nhập tài khoản chính thức.
 */
class FirestoreCloudSyncManager(
    private val authRepository: AuthRepository,
    private val database: MemoraDatabase,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    @Suppress("unused")
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val userPreferencesRepository: UserPreferencesRepository,
    private val imageUploader: ImageUploader? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : CloudSyncManager {

    /**
     * Hàm khởi tạo tiện lợi nhận [Context] để tự tạo [ImageUploader] dùng chung [FirebaseStorage] (Mục 10.1 & 11).
     */
    constructor(
        context: Context,
        authRepository: AuthRepository,
        database: MemoraDatabase,
        firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
        storage: FirebaseStorage = FirebaseStorage.getInstance(),
        userPreferencesRepository: UserPreferencesRepository
    ) : this(
        authRepository = authRepository,
        database = database,
        firestore = firestore,
        storage = storage,
        userPreferencesRepository = userPreferencesRepository,
        imageUploader = ImageUploader(context = context, storage = storage)
    )

    private val syncMutex = Mutex()

    /**
     * Lưu tạm `ownerId` của phiên Khách ẩn danh Firebase (`isAnonymous == true`) ngay trước khi
     * đăng nhập sang tài khoản chính thức để gộp/xóa chính xác dữ liệu cục bộ (Mục 6.2 & 10.1).
     */
    @Volatile
    private var pendingGuestOwnerId: String? = null

    /**
     * Ghi nhận `ownerId` của tài khoản Khách trước khi phiên xác thực chuyển sang tài khoản chính thức (Mục 6.2 & 10.1).
     */
    fun rememberGuestOwnerId(guestOwnerId: String?) {
        val cleanId = guestOwnerId?.trim()?.takeIf {
            it.isNotEmpty() && it != DeckDao.PREBUILT_OWNER_ID
        }
        pendingGuestOwnerId = cleanId
    }

    /**
     * Kiểm tra tài khoản chính thức [accountId] đã có sẵn dữ liệu học tập trên Cloud Firestore hay chưa
     * (dùng để quyết định bật hộp thoại `MergeGuestDialog` Figma 2196:16 khi chuyển từ Khách sang đăng nhập) (Mục 6.2 & 10.1).
     */
    suspend fun hasRemoteCloudData(accountId: String): Boolean = withContext(ioDispatcher) {
        val cleanAccountId = accountId.trim()
        if (
            cleanAccountId.isEmpty() ||
            cleanAccountId == Deck.GUEST_OWNER_ID ||
            cleanAccountId == DeckDao.PREBUILT_OWNER_ID
        ) {
            return@withContext false
        }

        val userDoc = userDocument(cleanAccountId)
        val hasDecks = !userDoc.collection(FirestoreCollections.DECKS)
            .limit(1)
            .get()
            .await()
            .isEmpty
        if (hasDecks) return@withContext true

        !userDoc.collection(FirestoreCollections.CARDS)
            .limit(1)
            .get()
            .await()
            .isEmpty
    }

    /**
     * Đồng bộ 2 chiều (Pull → Push → Dọn xóa mềm 30 ngày) dữ liệu học tập của tài khoản chính thức
     * với Cloud Firestore (Mục 8.3 & Mục 10.1).
     *
     * Thứ tự thực hiện (Pull trước Push để giải quyết xung đột Last-Write-Wins chính xác, tránh đẩy bản cũ đè bản mới trên server):
     * 1. Nếu chưa đăng nhập hoặc đang ở chế độ Khách (`isAnonymous == true`) -> bỏ qua push/pull, trả `Result.success(0)`.
     * 2. Đọc mốc `lastSyncedAtMillis` từ DataStore (mặc định `0L` cho lần đầu đăng nhập trên thiết bị mới).
     * 3. Pull thay đổi mới hơn `lastSyncedAtMillis` từ Firestore về Room theo thứ tự khóa ngoại
     *    `Deck` -> `Card` -> `ReviewLog` -> `PracticeSentence`, áp dụng Last-Write-Wins.
     * 4. Push các bản ghi vẫn còn `PENDING_SYNC` (bản mới tạo hoặc bản cục bộ thắng LWW) theo thứ tự
     *    `Deck` -> `Card` -> `ReviewLog` -> `PracticeSentence`.
     * 5. Dọn vĩnh viễn khỏi Room các bản ghi xóa mềm đã đồng bộ quá 30 ngày (Mục 10.1).
     * 6. Lưu mốc `lastSyncedAtMillis = now` vào `UserPreferencesRepository` sau khi hoàn tất.
     *
     * @return [Result.success] kèm tổng số bản ghi đã tải về và đẩy lên thành công, hoặc [Result.failure] với thông báo tiếng Việt.
     */
    override suspend fun syncPendingChanges(): Result<Int> = syncMutex.withLock {
        withContext(ioDispatcher) {
            try {
                // Tài khoản Khách (isAnonymous == true) hoặc chưa đăng nhập -> bỏ qua push/pull (Mục 6.2 & 10.1)
                val uid = resolveAuthenticatedUid()
                    ?: return@withContext Result.success(0)

                val lastSyncedAtMillis = userPreferencesRepository
                    .getUserPreferences()
                    .lastSyncedAtMillis ?: 0L

                val totalSyncedCount = runTwoWaySyncUnlocked(
                    uid = uid,
                    sinceMillis = lastSyncedAtMillis
                )
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
     * Gộp hoặc xóa dữ liệu chế độ Khách cục bộ khi chuyển sang tài khoản chính thức [accountId] (Mục 6.2 & Mục 10.1 · Figma 2196:16).
     *
     * - `discardGuestData = false` ("Gộp vào tài khoản"): chuyển `ownerId` của toàn bộ dữ liệu Khách
     *   (`guest_local` và/hoặc UID ẩn danh trước đó) sang [accountId] với `SyncStatus.PENDING_SYNC`,
     *   sau đó thực hiện đồng bộ 2 chiều đầy đủ (Pull toàn bộ dữ liệu Cloud về + Push dữ liệu Khách lên Firestore).
     * - `discardGuestData = true` ("Bỏ dữ liệu khách"): xóa toàn bộ dữ liệu Khách khỏi 4 bảng Room
     *   (giữ nguyên bộ từ mẫu `prebuilt_catalog`), sau đó kéo toàn bộ dữ liệu trên Cloud của [accountId] về máy.
     */
    override suspend fun mergeGuestDataIntoAccount(
        accountId: String,
        discardGuestData: Boolean
    ): Result<Unit> = syncMutex.withLock {
        withContext(ioDispatcher) {
            val cleanAccountId = accountId.trim()
            if (
                cleanAccountId.isEmpty() ||
                cleanAccountId == Deck.GUEST_OWNER_ID ||
                cleanAccountId == DeckDao.PREBUILT_OWNER_ID
            ) {
                return@withContext Result.failure(
                    IllegalArgumentException(ERROR_NOT_AUTHENTICATED_VI)
                )
            }

            try {
                val guestOwnerIds = collectGuestOwnerIdsForMerge(cleanAccountId)
                val now = nowProvider()

                if (discardGuestData) {
                    // Xóa sạch dữ liệu Khách trong 4 bảng Room theo đúng thứ tự con -> cha (Mục 6.2 & 10.1)
                    database.withTransaction {
                        for (guestOwnerId in guestOwnerIds) {
                            database.practiceSentenceDao().deleteAllByOwner(guestOwnerId)
                            database.reviewLogDao().deleteAllByOwner(guestOwnerId)
                            database.cardDao().deleteAllByOwner(guestOwnerId)
                            database.deckDao().deleteAllByOwner(guestOwnerId)
                        }
                    }
                } else {
                    // Gộp dữ liệu Khách: chuyển ownerId sang tài khoản chính thức & đặt PENDING_SYNC để đẩy lên Cloud (Mục 6.2 & 10.1)
                    database.withTransaction {
                        for (guestOwnerId in guestOwnerIds) {
                            database.deckDao().reassignOwner(
                                fromOwnerId = guestOwnerId,
                                toOwnerId = cleanAccountId,
                                updatedAtMillis = now,
                                syncStatus = SyncStatus.PENDING_SYNC
                            )
                            database.cardDao().reassignOwner(
                                fromOwnerId = guestOwnerId,
                                toOwnerId = cleanAccountId,
                                updatedAtMillis = now,
                                syncStatus = SyncStatus.PENDING_SYNC
                            )
                            database.reviewLogDao().reassignOwner(
                                fromOwnerId = guestOwnerId,
                                toOwnerId = cleanAccountId,
                                updatedAtMillis = now,
                                syncStatus = SyncStatus.PENDING_SYNC
                            )
                            database.practiceSentenceDao().reassignOwner(
                                fromOwnerId = guestOwnerId,
                                toOwnerId = cleanAccountId,
                                updatedAtMillis = now,
                                syncStatus = SyncStatus.PENDING_SYNC
                            )
                        }
                    }
                }

                // Chạy đồng bộ 2 chiều đầy đủ (sinceMillis = 0L) để tải toàn bộ dữ liệu Cloud và đẩy dữ liệu vừa gộp lên (Mục 10.1)
                runTwoWaySyncUnlocked(
                    uid = cleanAccountId,
                    sinceMillis = 0L
                )

                pendingGuestOwnerId = null
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(mapSyncException(e))
            }
        }
    }

    /**
     * Thực thi chu trình đồng bộ 2 chiều Pull → Push → Dọn xóa mềm 30 ngày → Lưu `lastSyncedAtMillis`
     * (chỉ gọi bên trong `syncMutex.withLock`) (Mục 8.3 & Mục 10.1).
     */
    private suspend fun runTwoWaySyncUnlocked(
        uid: String,
        sinceMillis: Long
    ): Int {
        // 1. Pull trước để so sánh Last-Write-Wins với bản cục bộ trước khi đẩy lên (Mục 8.3 & 10.1)
        val pulledDecksCount = pullRemoteDecks(uid, sinceMillis)
        val pulledCardsCount = pullRemoteCards(uid, sinceMillis)
        val pulledReviewLogsCount = pullRemoteReviewLogs(uid, sinceMillis)
        val pulledPracticeSentencesCount = pullRemotePracticeSentences(uid, sinceMillis)
        val totalPulledCount = pulledDecksCount +
            pulledCardsCount +
            pulledReviewLogsCount +
            pulledPracticeSentencesCount

        // 2. Push các thay đổi cục bộ còn PENDING_SYNC (gồm bản cục bộ thắng LWW hoặc vừa gộp từ Khách) (Mục 8.3 & 10.1)
        val pushedDecksCount = pushPendingDecks(uid)
        val pushedCardsCount = pushPendingCards(uid)
        val pushedReviewLogsCount = pushPendingReviewLogs(uid)
        val pushedPracticeSentencesCount = pushPendingPracticeSentences(uid)
        val totalPushedCount = pushedDecksCount +
            pushedCardsCount +
            pushedReviewLogsCount +
            pushedPracticeSentencesCount

        // 3. Dọn vĩnh viễn bản ghi xóa mềm đã SYNCED cũ hơn 30 ngày khỏi Room (Mục 10.1)
        val now = nowProvider()
        purgeExpiredSoftDeletedRecords(now)

        // 4. Ghi nhận mốc đồng bộ mới nhất sau khi hoàn tất cả Pull, Push & Dọn rác (Mục 10.1 & 13)
        userPreferencesRepository.setLastSyncedAtMillis(now)

        return totalPulledCount + totalPushedCount
    }

    /**
     * Tập hợp danh sách `ownerId` chế độ Khách cần gộp/xóa (luôn bao gồm `guest_local` và UID ẩn danh trước đó nếu có),
     * đảm bảo không bao giờ đụng vào [targetAccountId] hoặc bộ từ mẫu `prebuilt_catalog` (Mục 6.2 & 10.1).
     */
    private suspend fun collectGuestOwnerIdsForMerge(targetAccountId: String): Set<String> {
        val candidates = LinkedHashSet<String>()
        candidates.add(Deck.GUEST_OWNER_ID)

        pendingGuestOwnerId
            ?.takeIf { it.isNotBlank() && it != targetAccountId && it != DeckDao.PREBUILT_OWNER_ID }
            ?.let { candidates.add(it) }

        val currentUser = runCatching { authRepository.getCurrentUser() }.getOrNull()
        if (currentUser != null && currentUser.isAnonymous) {
            val anonUid = currentUser.uid.trim()
            if (anonUid.isNotEmpty() && anonUid != targetAccountId && anonUid != DeckDao.PREBUILT_OWNER_ID) {
                candidates.add(anonUid)
            }
        }
        return candidates
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
     * Đẩy các [Card] có `syncStatus == PENDING_SYNC` lên `users/{uid}/cards/{id}` theo lô (Mục 8.3, 10.1 & 11).
     *
     * Trước khi đẩy từng thẻ lên Firestore, nếu thẻ chưa bị xóa và có `localImagePath != null` nhưng
     * `cloudImageUrl == null`, sẽ gọi [ImageUploader] nén <= 500 KB (max 1280px), tải lên
     * `users/{uid}/card_images/{cardId}.jpg` và lưu `cloudImageUrl` vào `CardEntity` trước khi ghi Firestore.
     * Chỉ đánh dấu `SYNCED` trong Room sau khi batch commit Firestore thành công.
     */
    internal suspend fun pushPendingCards(uid: String): Int {
        val cardDao = database.cardDao()
        val pendingCards = cardDao.getPendingSyncCards(uid).map { it.toDomain() }
        if (pendingCards.isEmpty()) return 0

        val cardsCollection = userDocument(uid).collection(FirestoreCollections.CARDS)
        var syncedCount = 0

        for (chunk in pendingCards.chunked(MAX_FIRESTORE_BATCH_SIZE)) {
            // Tải ảnh minh họa (nếu có) lên Firebase Storage và cập nhật cloudImageUrl vào Room trước khi push (Mục 10.1 & 11)
            val preparedChunk = ArrayList<Card>(chunk.size)
            for (card in chunk) {
                preparedChunk.add(uploadCardImageIfNeeded(uid, card))
            }

            val batch = firestore.batch()
            for (card in preparedChunk) {
                val docRef = cardsCollection.document(card.id)
                val payload = card.copy(syncStatus = SyncStatus.SYNCED).toFirestoreMap()
                batch.set(docRef, payload, SetOptions.merge())
            }
            batch.commit().await()

            markCardsSyncedInRoom(preparedChunk)
            syncedCount += preparedChunk.size
        }

        return syncedCount
    }

    /**
     * Khi thẻ chưa bị xóa có `localImagePath != null` và `cloudImageUrl == null`, nén & tải ảnh lên
     * Firebase Storage, cập nhật `cloudImageUrl` vào `CardEntity` cục bộ rồi trả về [Card] đã cập nhật (Mục 10.1 & 11).
     */
    private suspend fun uploadCardImageIfNeeded(uid: String, card: Card): Card {
        val uploader = imageUploader ?: return card
        val localPath = card.localImagePath?.takeIf { it.isNotBlank() } ?: return card
        if (card.isDeleted || !card.cloudImageUrl.isNullOrBlank()) return card

        val uploadResult = uploader.uploadCardImage(
            uid = uid,
            cardId = card.id,
            localImagePath = localPath
        )

        return uploadResult.fold(
            onSuccess = { downloadUrl ->
                val cardDao = database.cardDao()
                database.withTransaction {
                    val current = cardDao.getCardByIdIncludingDeleted(card.id)
                    if (current != null && current.localImagePath == card.localImagePath) {
                        cardDao.upsertCard(current.copy(cloudImageUrl = downloadUrl))
                    }
                }
                card.copy(cloudImageUrl = downloadUrl)
            },
            onFailure = { error ->
                // Nếu tệp ảnh cục bộ không còn tồn tại/bị thu hồi quyền -> bỏ qua ảnh để không chặn đồng bộ nội dung thẻ
                if (error is ImageUploader.LocalImageUnavailableException) {
                    card
                } else {
                    // Lỗi mạng / 429 / Storage tạm thời -> dừng để giữ PENDING_SYNC và tự thử lại ở phiên đồng bộ sau
                    throw error
                }
            }
        )
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

    // ============================================================================
    // BƯỚC PULL TỪ CLOUD FIRESTORE VỀ ROOM DATABASE (Mục 8.3 & Mục 10.1)
    // ============================================================================

    /**
     * Kéo các [Deck] có `updatedAtMillis > lastSyncedAtMillis` từ `users/{uid}/decks` về Room (Mục 8.3 & 10.1).
     * Chèn mới hoặc ghi đè theo Last-Write-Wins dựa trên `updatedAtMillis` (kể cả bộ từ xóa mềm `isDeleted = true`).
     */
    internal suspend fun pullRemoteDecks(uid: String, lastSyncedAtMillis: Long): Int {
        val snapshot = userDocument(uid)
            .collection(FirestoreCollections.DECKS)
            .whereGreaterThan(FirestoreFields.UPDATED_AT_MILLIS, lastSyncedAtMillis)
            .get()
            .await()

        val remoteDecks = snapshot.documents
            .mapNotNull { it.toDeck(fallbackOwnerId = uid) }
            .map { it.copy(ownerId = uid, syncStatus = SyncStatus.SYNCED) }
        if (remoteDecks.isEmpty()) return 0

        val deckDao = database.deckDao()
        var appliedCount = 0

        database.withTransaction {
            val decksToUpsert = ArrayList<Deck>(remoteDecks.size)
            for (remoteDeck in remoteDecks) {
                val localDeck = deckDao.getDeckByIdIncludingDeleted(remoteDeck.id)
                val remoteWins = shouldApplyRemoteRecord(
                    localUpdatedAtMillis = localDeck?.updatedAtMillis,
                    localSyncStatus = localDeck?.syncStatus,
                    remoteUpdatedAtMillis = remoteDeck.updatedAtMillis
                )
                if (remoteWins) {
                    // Giữ nguyên trạng thái xóa mềm isDeleted = true nếu bản thắng có isDeleted = true (Mục 10.1)
                    val candidateEntity = remoteDeck.toEntity()
                    if (localDeck == null || candidateEntity != localDeck) {
                        decksToUpsert.add(remoteDeck)
                    }
                }
            }
            if (decksToUpsert.isNotEmpty()) {
                deckDao.upsertDecks(decksToUpsert.map { it.toEntity() })
                appliedCount = decksToUpsert.size
            }
        }

        return appliedCount
    }

    /**
     * Kéo các [Card] có `updatedAtMillis > lastSyncedAtMillis` từ `users/{uid}/cards` về Room (Mục 7.1, 8.3 & 10.1).
     * Bảo đảm bộ từ cha (`deckId`) tồn tại trước khi chèn để không vi phạm khóa ngoại, áp dụng Last-Write-Wins
     * (bảo toàn `isDeleted = true` nếu bản thắng là bản xóa mềm) và giữ lại `localImagePath` cục bộ nếu `cloudImageUrl` không đổi.
     */
    internal suspend fun pullRemoteCards(uid: String, lastSyncedAtMillis: Long): Int {
        val snapshot = userDocument(uid)
            .collection(FirestoreCollections.CARDS)
            .whereGreaterThan(FirestoreFields.UPDATED_AT_MILLIS, lastSyncedAtMillis)
            .get()
            .await()

        val remoteCards = snapshot.documents
            .mapNotNull { it.toCard(fallbackOwnerId = uid) }
            .filter { it.deckId.isNotBlank() }
            .map { it.copy(ownerId = uid, syncStatus = SyncStatus.SYNCED) }
        if (remoteCards.isEmpty()) return 0

        // Kiểm tra các deckId chưa có cục bộ (trường hợp lệch đồng hồ trong kéo tăng dần) để đảm bảo khóa ngoại Room
        val deckDao = database.deckDao()
        val missingParentDecks = fetchMissingParentDecks(
            uid = uid,
            deckIds = remoteCards.mapTo(LinkedHashSet()) { it.deckId }
        )

        val cardDao = database.cardDao()
        var appliedCount = 0

        database.withTransaction {
            if (missingParentDecks.isNotEmpty()) {
                deckDao.upsertDecks(missingParentDecks.map { it.toEntity() })
            }

            val cardsToUpsert = ArrayList<Card>(remoteCards.size)
            for (remoteCard in remoteCards) {
                if (deckDao.getDeckByIdIncludingDeleted(remoteCard.deckId) == null) {
                    continue
                }
                val localCard = cardDao.getCardByIdIncludingDeleted(remoteCard.id)
                val remoteWins = shouldApplyRemoteRecord(
                    localUpdatedAtMillis = localCard?.updatedAtMillis,
                    localSyncStatus = localCard?.syncStatus,
                    remoteUpdatedAtMillis = remoteCard.updatedAtMillis
                )
                if (remoteWins) {
                    // Giữ lại đường dẫn ảnh cục bộ trên máy này nếu ảnh đám mây không thay đổi (Mục 10.1)
                    val preservedLocalImagePath = if (
                        localCard != null &&
                        localCard.cloudImageUrl == remoteCard.cloudImageUrl
                    ) {
                        localCard.localImagePath
                    } else {
                        null
                    }
                    // Giữ nguyên trạng thái xóa mềm isDeleted = true nếu bản thắng có isDeleted = true (Mục 10.1)
                    val resolvedCard = remoteCard.copy(localImagePath = preservedLocalImagePath)
                    if (localCard == null || resolvedCard.toEntity() != localCard) {
                        cardsToUpsert.add(resolvedCard)
                    }
                }
            }
            if (cardsToUpsert.isNotEmpty()) {
                cardDao.upsertCards(cardsToUpsert.map { it.toEntity() })
                appliedCount = cardsToUpsert.size
            }
        }

        return appliedCount
    }

    /**
     * Kéo các [ReviewLog] có `updatedAtMillis > lastSyncedAtMillis` từ `users/{uid}/reviewLogs` về Room (Mục 9.1, 10.1 & 10.2).
     * Truy vấn theo `updatedAtMillis` (luôn >= `reviewedAtMillis`) giúp tải đầy đủ cả lượt ôn mới lẫn lượt vừa Hoàn tác (`isUndone`).
     */
    internal suspend fun pullRemoteReviewLogs(uid: String, lastSyncedAtMillis: Long): Int {
        val snapshot = userDocument(uid)
            .collection(FirestoreCollections.REVIEW_LOGS)
            .whereGreaterThan(FirestoreFields.UPDATED_AT_MILLIS, lastSyncedAtMillis)
            .get()
            .await()

        val remoteLogs = snapshot.documents
            .mapNotNull { it.toReviewLog(fallbackOwnerId = uid) }
            .map { it.copy(ownerId = uid, syncStatus = SyncStatus.SYNCED) }
        if (remoteLogs.isEmpty()) return 0

        val reviewLogDao = database.reviewLogDao()
        var appliedCount = 0

        database.withTransaction {
            val logsToUpsert = ArrayList<ReviewLog>(remoteLogs.size)
            for (remoteLog in remoteLogs) {
                val localLog = reviewLogDao.getLogById(remoteLog.id)
                val remoteTimestamp = maxOf(remoteLog.updatedAtMillis, remoteLog.reviewedAtMillis)
                val localTimestamp = localLog?.let { maxOf(it.updatedAtMillis, it.reviewedAtMillis) }
                val shouldApplyUndo = localLog != null &&
                    remoteLog.isUndone &&
                    !localLog.isUndone &&
                    remoteTimestamp >= (localTimestamp ?: 0L)
                val remoteWins = shouldApplyRemoteRecord(
                    localUpdatedAtMillis = localTimestamp,
                    localSyncStatus = localLog?.syncStatus,
                    remoteUpdatedAtMillis = remoteTimestamp
                ) || shouldApplyUndo

                if (remoteWins && (localLog == null || remoteLog.toEntity() != localLog)) {
                    logsToUpsert.add(remoteLog)
                }
            }
            if (logsToUpsert.isNotEmpty()) {
                reviewLogDao.upsertLogs(logsToUpsert.map { it.toEntity() })
                appliedCount = logsToUpsert.size
            }
        }

        return appliedCount
    }

    /**
     * Kéo các [PracticeSentence] có `updatedAtMillis > lastSyncedAtMillis` từ `users/{uid}/practiceSentences` về Room (Mục 6.7, 8.2 & 10.1).
     */
    internal suspend fun pullRemotePracticeSentences(uid: String, lastSyncedAtMillis: Long): Int {
        val snapshot = userDocument(uid)
            .collection(FirestoreCollections.PRACTICE_SENTENCES)
            .whereGreaterThan(FirestoreFields.UPDATED_AT_MILLIS, lastSyncedAtMillis)
            .get()
            .await()

        val remoteSentences = snapshot.documents
            .mapNotNull { it.toPracticeSentence(fallbackOwnerId = uid) }
            .map { it.copy(ownerId = uid, syncStatus = SyncStatus.SYNCED) }
        if (remoteSentences.isEmpty()) return 0

        val practiceSentenceDao = database.practiceSentenceDao()
        var appliedCount = 0

        database.withTransaction {
            val sentencesToUpsert = ArrayList<PracticeSentence>(remoteSentences.size)
            for (remoteSentence in remoteSentences) {
                val localSentence = practiceSentenceDao.getSentenceById(remoteSentence.id)
                val remoteWins = shouldApplyRemoteRecord(
                    localUpdatedAtMillis = localSentence?.updatedAtMillis,
                    localSyncStatus = localSentence?.syncStatus,
                    remoteUpdatedAtMillis = remoteSentence.updatedAtMillis
                )
                if (remoteWins && (localSentence == null || remoteSentence.toEntity() != localSentence)) {
                    sentencesToUpsert.add(remoteSentence)
                }
            }
            if (sentencesToUpsert.isNotEmpty()) {
                practiceSentenceDao.upsertSentences(sentencesToUpsert.map { it.toEntity() })
                appliedCount = sentencesToUpsert.size
            }
        }

        return appliedCount
    }

    /**
     * Dọn vĩnh viễn khỏi Room các bản ghi đã xóa mềm (`isDeleted == true && syncStatus == SYNCED`)
     * có `updatedAtMillis` cũ hơn 30 ngày sau khi hoàn tất đồng bộ (Mục 10.1).
     * Xóa bảng con `cards` trước rồi mới xóa bảng cha `decks` trong cùng transaction.
     */
    internal suspend fun purgeExpiredSoftDeletedRecords(nowMillis: Long = nowProvider()) {
        val cutoffMillis = (nowMillis - SOFT_DELETE_RETENTION_MILLIS).coerceAtLeast(0L)
        database.withTransaction {
            database.cardDao().purgeSyncedDeletedBefore(cutoffMillis)
            database.deckDao().purgeSyncedDeletedBefore(cutoffMillis)
        }
    }

    /**
     * Tải bổ sung các [Deck] cha còn thiếu cục bộ trước khi chèn [Card] nhằm tránh lỗi khóa ngoại Room (Mục 10.1).
     */
    private suspend fun fetchMissingParentDecks(
        uid: String,
        deckIds: Set<String>
    ): List<Deck> {
        val deckDao = database.deckDao()
        val missingIds = deckIds.filter { deckDao.getDeckByIdIncludingDeleted(it) == null }
        if (missingIds.isEmpty()) return emptyList()

        val decksCollection = userDocument(uid).collection(FirestoreCollections.DECKS)
        val fetched = ArrayList<Deck>(missingIds.size)
        for (deckId in missingIds) {
            val deck = decksCollection
                .document(deckId)
                .get()
                .await()
                .toDeck(fallbackOwnerId = uid)
                ?.copy(ownerId = uid, syncStatus = SyncStatus.SYNCED)
            if (deck != null) {
                fetched.add(deck)
            }
        }
        return fetched
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

        /** Thời hạn giữ bản ghi xóa mềm đã đồng bộ trong Room trước khi dọn vĩnh viễn: 30 ngày (Mục 10.1). */
        const val SOFT_DELETE_RETENTION_DAYS: Long = 30L
        const val SOFT_DELETE_RETENTION_MILLIS: Long =
            SOFT_DELETE_RETENTION_DAYS * 24L * 60L * 60L * 1000L

        /**
         * Quyết định Last-Write-Wins khi ghi đè bản ghi từ Firestore vào Room (Mục 8.3 & Mục 10.1):
         * - Chưa có bản cục bộ (`localUpdatedAtMillis == null`) -> ghi bản đám mây.
         * - Có bản cục bộ -> chỉ ghi đè khi `remoteUpdatedAtMillis > localUpdatedAtMillis`
         *   hoặc bản cục bộ đã ở trạng thái `SyncStatus.SYNCED` (không có thay đổi cục bộ chưa đẩy).
         */
        internal fun shouldApplyRemoteRecord(
            localUpdatedAtMillis: Long?,
            localSyncStatus: SyncStatus?,
            remoteUpdatedAtMillis: Long
        ): Boolean {
            if (localUpdatedAtMillis == null || localSyncStatus == null) return true
            return remoteUpdatedAtMillis > localUpdatedAtMillis ||
                localSyncStatus == SyncStatus.SYNCED
        }

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
                is ImageUploader.ImageUploadException -> throwable

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
