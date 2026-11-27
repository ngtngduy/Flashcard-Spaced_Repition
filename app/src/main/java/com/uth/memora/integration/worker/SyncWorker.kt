package com.uth.memora.integration.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import com.uth.memora.MemoraApplication
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.model.Deck
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: TÁC VỤ NỀN ĐỒNG BỘ ĐÁM MÂY (Mục 8.3 & Mục 10.1)
 * ============================================================================
 * Tác vụ nền WorkManager tự động chạy khi thiết bị có kết nối mạng (`NetworkType.CONNECTED`):
 * - Bỏ qua an toàn nếu chưa đăng nhập hoặc đang ở chế độ Khách (`isAnonymous == true`).
 * - Gọi `container.cloudSyncManager.syncPendingChanges()` để chạy chu trình đồng bộ 2 chiều
 *   (Pull LWW → Push 4 bảng + tải ảnh Firebase Storage → Dọn bản ghi xóa mềm quá 30 ngày).
 * - Tự động `Result.retry()` theo `BackoffPolicy.EXPONENTIAL` cho các lỗi mạng / 429 tạm thời
 *   (tối đa 3 lần thử) và trả `Result.failure()` cho lỗi không thể tự phục hồi.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MemoraApplication)?.container
            ?: return Result.failure()

        // Bỏ qua đồng bộ nếu chưa đăng nhập hoặc đang ở chế độ Khách ẩn danh (Mục 6.2 & 10.1)
        val currentUser = try {
            container.authRepository.getCurrentUser()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return handleSyncFailure(e)
        }

        if (
            currentUser == null ||
            currentUser.isAnonymous ||
            currentUser.uid.isBlank() ||
            currentUser.uid == Deck.GUEST_OWNER_ID ||
            currentUser.uid == DeckDao.PREBUILT_OWNER_ID
        ) {
            return Result.success(workDataOf(KEY_SYNCED_COUNT to 0))
        }

        // Gọi CloudSyncManager hiện thực đồng bộ 2 chiều với Firestore & Firebase Storage (Mục 8.3 & 10.1)
        val syncResult = try {
            container.cloudSyncManager.syncPendingChanges()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            kotlin.Result.failure(e)
        }

        return syncResult.fold(
            onSuccess = { syncedCount ->
                Result.success(workDataOf(KEY_SYNCED_COUNT to syncedCount))
            },
            onFailure = { error ->
                handleSyncFailure(error)
            }
        )
    }

    private fun handleSyncFailure(error: Throwable): Result {
        if (error is CancellationException) throw error
        val shouldRetry = isRetryableSyncError(error) && runAttemptCount < MAX_RETRY_ATTEMPTS
        return if (shouldRetry) {
            Result.retry()
        } else {
            val friendlyMessage = error.message
                ?.takeIf { it.isNotBlank() }
                ?: ERROR_BACKGROUND_SYNC_FAILED_VI
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to friendlyMessage))
        }
    }

    companion object {
        const val KEY_SYNCED_COUNT: String = "synced_count"
        const val KEY_ERROR_MESSAGE: String = "sync_error_message"
        const val MAX_RETRY_ATTEMPTS: Int = 3

        const val ERROR_BACKGROUND_SYNC_FAILED_VI: String =
            "Đồng bộ nền chưa thành công. Dữ liệu cục bộ vẫn an toàn và sẽ đồng bộ lại khi có mạng."

        /**
         * Phân biệt lỗi tạm thời nên thử lại (mất mạng, timeout, 429/quota) với lỗi cố định
         * (hết phiên đăng nhập, thiếu quyền, sai định dạng) để tránh vòng lặp tốn pin (Mục 8.3 & 10.1).
         */
        internal fun isRetryableSyncError(throwable: Throwable): Boolean {
            val causeChain = generateSequence(throwable) { it.cause }.toList()
            for (cause in causeChain) {
                when (cause) {
                    is FirebaseFirestoreException -> return when (cause.code) {
                        FirebaseFirestoreException.Code.UNAVAILABLE,
                        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                        FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
                        FirebaseFirestoreException.Code.ABORTED,
                        FirebaseFirestoreException.Code.INTERNAL -> true

                        else -> false
                    }

                    is StorageException -> return when (cause.errorCode) {
                        StorageException.ERROR_RETRY_LIMIT_EXCEEDED,
                        StorageException.ERROR_QUOTA_EXCEEDED -> true

                        else -> false
                    }

                    is FirebaseNetworkException,
                    is FirebaseTooManyRequestsException,
                    is IOException -> return true
                }
            }

            val combinedMessage = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            return combinedMessage.contains("429") ||
                combinedMessage.contains("quá tải") ||
                combinedMessage.contains("vượt giới hạn") ||
                combinedMessage.contains("kết nối mạng") ||
                combinedMessage.contains("network") ||
                combinedMessage.contains("unavailable") ||
                combinedMessage.contains("timeout") ||
                combinedMessage.contains("timed out")
        }
    }
}

/**
 * Bộ lập lịch WorkManager cho đồng bộ đám mây (tức thì & định kỳ) (Mục 8.3 & Mục 10.1).
 *
 * Dùng để kích hoạt mỗi khi:
 * - Người dùng vừa đăng nhập tài khoản chính thức thành công.
 * - Người dùng bấm "Đồng bộ ngay" ở màn Cài đặt.
 * - Mở ứng dụng khi có kết nối mạng hoặc khi thiết bị có mạng trở lại.
 */
object SyncScheduler {

    const val UNIQUE_IMMEDIATE_SYNC_WORK_NAME: String = "memora_immediate_cloud_sync"
    const val UNIQUE_PERIODIC_SYNC_WORK_NAME: String = "memora_periodic_cloud_sync"

    const val TAG_IMMEDIATE_SYNC: String = "memora_sync_immediate"
    const val TAG_PERIODIC_SYNC: String = "memora_sync_periodic"

    const val INITIAL_BACKOFF_SECONDS: Long = 30L
    const val MIN_PERIODIC_INTERVAL_MINUTES: Long = 15L
    const val DEFAULT_PERIODIC_INTERVAL_MINUTES: Long = 60L

    /**
     * Đưa tác vụ đồng bộ đám mây tức thì vào WorkManager với điều kiện có mạng (`NetworkType.CONNECTED`) (Mục 8.3 & 10.1).
     *
     * @param context Context bất kỳ (sẽ tự dùng `applicationContext`).
     * @param replaceExisting `true` để thay thế tác vụ đang chờ (ví dụ khi người dùng bấm "Đồng bộ ngay"
     * hoặc vừa đăng nhập xong), `false` để giữ nguyên tác vụ đang xếp hàng tránh tạo trùng.
     */
    fun enqueueImmediateSync(
        context: Context,
        replaceExisting: Boolean = false
    ) {
        val workPolicy = if (replaceExisting) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_IMMEDIATE_SYNC_WORK_NAME,
            workPolicy,
            buildImmediateSyncRequest()
        )
    }

    /**
     * Đăng ký lịch đồng bộ định kỳ khi có kết nối mạng (`NetworkType.CONNECTED`) (Mục 8.3 & 10.1).
     * Tối thiểu 15 phút theo giới hạn WorkManager; mặc định mỗi 60 phút.
     */
    fun schedulePeriodicSync(
        context: Context,
        repeatIntervalMinutes: Long = DEFAULT_PERIODIC_INTERVAL_MINUTES
    ) {
        val request = buildPeriodicSyncRequest(repeatIntervalMinutes)
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Hủy toàn bộ tác vụ đồng bộ đám mây đang xếp hàng/định kỳ (dùng khi người dùng đăng xuất) (Mục 6.2 & 10.1).
     */
    fun cancelAllSyncWork(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(UNIQUE_IMMEDIATE_SYNC_WORK_NAME)
        workManager.cancelUniqueWork(UNIQUE_PERIODIC_SYNC_WORK_NAME)
    }

    internal fun buildNetworkConstraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    internal fun buildImmediateSyncRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(buildNetworkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_IMMEDIATE_SYNC)
            .build()

    internal fun buildPeriodicSyncRequest(
        repeatIntervalMinutes: Long = DEFAULT_PERIODIC_INTERVAL_MINUTES
    ): PeriodicWorkRequest {
        val safeIntervalMinutes = repeatIntervalMinutes.coerceAtLeast(MIN_PERIODIC_INTERVAL_MINUTES)
        return PeriodicWorkRequestBuilder<SyncWorker>(safeIntervalMinutes, TimeUnit.MINUTES)
            .setConstraints(buildNetworkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                INITIAL_BACKOFF_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_PERIODIC_SYNC)
            .build()
    }
}
