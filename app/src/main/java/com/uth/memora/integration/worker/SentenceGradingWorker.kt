package com.uth.memora.integration.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: HÀNG ĐỢI CHẤM CÂU NGOẠI TUYẾN (Mục 6.8 & 9.5)
 * ============================================================================
 * Tác vụ nền WorkManager tự động chạy khi thiết bị có mạng trở lại.
 * Áp dụng chiến lược Exponential Backoff (15s -> 60s -> 5m) tối đa 3 lần thử.
 */
class SentenceGradingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            // Quá 3 lần thử không thành công -> đánh dấu câu sang trạng thái GRADE_ERROR (Mục 8.2)
            return Result.failure()
        }

        // TODO (Thành viên 3): Lấy các câu PENDING_GRADE từ Database, gửi lên GeminiAiGradingService
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "memora_offline_sentence_grading_work"
        const val MAX_RETRY_ATTEMPTS = 3

        /**
         * Đăng ký hàng đợi chờ mạng để tự động chấm các câu đang ở trạng thái Chờ chấm.
         */
        fun enqueueWhenNetworkAvailable(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SentenceGradingWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15L,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
