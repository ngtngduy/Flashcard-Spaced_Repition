package com.uth.memora.integration.sync

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.uth.memora.core.database.MemoraDatabase
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.CloudSyncManager
import com.uth.memora.core.domain.CloudSyncSummary
import com.uth.memora.core.domain.SyncStatusProvider
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.UserAccount
import com.uth.memora.integration.worker.SyncScheduler
import com.uth.memora.integration.worker.SyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: BỘ CUNG CẤP TRẠNG THÁI ĐỒNG BỘ (Mục 8.3, 10.1 & 13)
 * ============================================================================
 * Hiện thực [SyncStatusProvider] phát ra luồng [Flow]<[CloudSyncSummary]> thời gian thực cho
 * màn Cài đặt (M1-I · Figma 13:2) bằng cách kết hợp:
 * - Tổng số bản ghi `PENDING_SYNC` thời gian thực từ cả 4 Room DAO (`decks`, `cards`, `review_logs`, `practice_sentences`).
 * - Trạng thái đang đồng bộ và thông báo lỗi tiếng Việt gần nhất (từ đồng bộ thủ công lẫn `SyncWorker`).
 * - Thời điểm đồng bộ thành công gần nhất `lastSyncedAtMillis` từ [UserPreferencesRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusProviderImpl(
    private val database: MemoraDatabase,
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cloudSyncManager: CloudSyncManager,
    private val workManager: WorkManager? = null,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : SyncStatusProvider {

    /**
     * Hàm khởi tạo tiện lợi nhận [Context] để tự động theo dõi thêm trạng thái chạy nền của [SyncWorker] (Mục 8.3 & 13).
     */
    constructor(
        context: Context,
        database: MemoraDatabase,
        authRepository: AuthRepository,
        userPreferencesRepository: UserPreferencesRepository,
        cloudSyncManager: CloudSyncManager
    ) : this(
        database = database,
        authRepository = authRepository,
        userPreferencesRepository = userPreferencesRepository,
        cloudSyncManager = cloudSyncManager,
        workManager = WorkManager.getInstance(context.applicationContext)
    )

    private val triggerMutex = Mutex()
    private val isManualSyncingState = MutableStateFlow(false)
    private val scopedManualErrorState = MutableStateFlow<ScopedSyncError?>(null)
    private val dismissedFailedWorkIdsState = MutableStateFlow<Set<UUID>>(emptySet())

    @Volatile
    private var latestFailedWorkIds: Set<UUID> = emptySet()

    /**
     * Quan sát tóm tắt trạng thái đồng bộ đám mây theo thời gian thực cho màn Cài đặt (Mục 8.3, 10.1 & 13).
     * Khi người dùng tạo/sửa/xóa thẻ offline -> `pendingChangesCount` tăng ngay lập tức;
     * khi đồng bộ xong -> `pendingChangesCount` về 0, `lastSyncedAtMillis` cập nhật và huy hiệu về "Đã đồng bộ".
     */
    override fun observeSyncSummary(): Flow<CloudSyncSummary> {
        val ownerIdFlow = authRepository
            .observeCurrentUser()
            .map { user -> resolveOwnerId(user) }
            .distinctUntilChanged()

        val pendingCountByOwnerFlow = ownerIdFlow.flatMapLatest { ownerId ->
            combine(
                database.deckDao().countPendingSync(ownerId),
                database.cardDao().countPendingSync(ownerId),
                database.reviewLogDao().countPendingSync(ownerId),
                database.practiceSentenceDao().countPendingSync(ownerId)
            ) { pendingDecks, pendingCards, pendingLogs, pendingSentences ->
                ownerId to (pendingDecks + pendingCards + pendingLogs + pendingSentences).coerceAtLeast(0)
            }
        }

        val lastSyncedAtFlow = userPreferencesRepository
            .observeUserPreferences()
            .map { prefs -> prefs.lastSyncedAtMillis }
            .distinctUntilChanged()

        val runtimeStateFlow = combine(
            isManualSyncingState,
            scopedManualErrorState,
            observeBackgroundWorkerState(),
            dismissedFailedWorkIdsState
        ) { isManualSyncing, manualError, workerSnapshot, dismissedFailedIds ->
            latestFailedWorkIds = workerSnapshot.failedWorkIds
            val activeWorkerError = workerSnapshot.errorMessage
                ?.takeIf { workerSnapshot.latestFailedWorkId !in dismissedFailedIds }

            RuntimeSyncState(
                isSyncing = isManualSyncing || workerSnapshot.isRunning,
                manualError = manualError,
                workerError = activeWorkerError
            )
        }

        return combine(
            pendingCountByOwnerFlow,
            lastSyncedAtFlow,
            runtimeStateFlow
        ) { (ownerId, pendingCount), lastSyncedAtMillis, runtime ->
            val ownerScopedManualError = runtime.manualError
                ?.takeIf { it.ownerId == ownerId }
                ?.message
            val effectiveError = if (runtime.isSyncing) {
                null
            } else {
                ownerScopedManualError ?: runtime.workerError
            }

            CloudSyncSummary(
                isSyncing = runtime.isSyncing,
                pendingChangesCount = pendingCount,
                lastSyncedAtMillis = lastSyncedAtMillis,
                syncErrorMessage = effectiveError
            )
        }.distinctUntilChanged()
    }

    /**
     * Kích hoạt đồng bộ ngay lập tức (khi bấm nút "Đồng bộ ngay" trên màn Cài đặt) (Mục 8.3 & 13).
     * Cập nhật tức thì cờ `isSyncing`, xóa lỗi cũ và ghi nhận thông báo tiếng Việt nếu xảy ra lỗi mạng/429.
     */
    override suspend fun triggerImmediateSync(): Result<Int> = triggerMutex.withLock {
        val currentUser = try {
            authRepository.getCurrentUser()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
        val ownerId = resolveOwnerId(currentUser)

        isManualSyncingState.value = true
        scopedManualErrorState.value = null
        dismissCurrentFailedWorkerErrors()

        return try {
            val result = try {
                cloudSyncManager.syncPendingChanges()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(FirestoreCloudSyncManager.mapSyncException(e))
            }

            result.onSuccess {
                scopedManualErrorState.value = null
                dismissCurrentFailedWorkerErrors()
                if (currentUser != null && !currentUser.isAnonymous) {
                    userPreferencesRepository.setLastSyncedAtMillis(nowProvider())
                }
            }.onFailure { error ->
                val message = error.message
                    ?.takeIf { it.isNotBlank() }
                    ?: FirestoreCloudSyncManager.ERROR_SYNC_UNKNOWN_VI
                scopedManualErrorState.value = ScopedSyncError(
                    ownerId = ownerId,
                    message = message
                )
            }
            result
        } finally {
            isManualSyncingState.value = false
        }
    }

    /**
     * Xóa trạng thái lỗi đồng bộ gần nhất trên UI (ví dụ khi người dùng bỏ qua thông báo lỗi) (Mục 13).
     */
    fun clearSyncError() {
        scopedManualErrorState.value = null
        dismissCurrentFailedWorkerErrors()
    }

    /**
     * Quan sát trạng thái chạy/lỗi của [SyncWorker] (cả tức thì lẫn định kỳ) nếu có [WorkManager] (Mục 8.3).
     */
    private fun observeBackgroundWorkerState(): Flow<WorkerSyncSnapshot> {
        val wm = workManager ?: return flowOf(WorkerSyncSnapshot())
        return combine(
            wm.getWorkInfosForUniqueWorkFlow(SyncScheduler.UNIQUE_IMMEDIATE_SYNC_WORK_NAME),
            wm.getWorkInfosForUniqueWorkFlow(SyncScheduler.UNIQUE_PERIODIC_SYNC_WORK_NAME)
        ) { immediateInfos, periodicInfos ->
            summarizeWorkInfos(immediateInfos + periodicInfos)
        }.distinctUntilChanged()
    }

    private fun dismissCurrentFailedWorkerErrors() {
        val snapshotIds = latestFailedWorkIds
        if (snapshotIds.isNotEmpty()) {
            dismissedFailedWorkIdsState.update { existing -> existing + snapshotIds }
        }
    }

    private data class ScopedSyncError(
        val ownerId: String,
        val message: String
    )

    private data class RuntimeSyncState(
        val isSyncing: Boolean,
        val manualError: ScopedSyncError?,
        val workerError: String?
    )

    internal data class WorkerSyncSnapshot(
        val isRunning: Boolean = false,
        val errorMessage: String? = null,
        val latestFailedWorkId: UUID? = null,
        val failedWorkIds: Set<UUID> = emptySet()
    )

    companion object {
        /**
         * Xác định `ownerId` dùng để đếm bản ghi `PENDING_SYNC` trong Room Database (Mục 6.2 & 10.1).
         */
        internal fun resolveOwnerId(user: UserAccount?): String =
            user?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID

        /**
         * Tổng hợp danh sách [WorkInfo] của [SyncWorker] thành trạng thái đang chạy / lỗi gần nhất (Mục 8.3).
         */
        internal fun summarizeWorkInfos(workInfos: List<WorkInfo>): WorkerSyncSnapshot {
            if (workInfos.isEmpty()) return WorkerSyncSnapshot()

            val isRunning = workInfos.any { it.state == WorkInfo.State.RUNNING }
            val failedInfos = workInfos.filter { it.state == WorkInfo.State.FAILED }
            val latestFailed = failedInfos.lastOrNull()
            val errorMessage = if (isRunning || latestFailed == null) {
                null
            } else {
                latestFailed.outputData
                    .getString(SyncWorker.KEY_ERROR_MESSAGE)
                    ?.takeIf { it.isNotBlank() }
                    ?: SyncWorker.ERROR_BACKGROUND_SYNC_FAILED_VI
            }

            return WorkerSyncSnapshot(
                isRunning = isRunning,
                errorMessage = errorMessage,
                latestFailedWorkId = latestFailed?.id,
                failedWorkIds = failedInfos.mapTo(LinkedHashSet()) { it.id }
            )
        }
    }
}
