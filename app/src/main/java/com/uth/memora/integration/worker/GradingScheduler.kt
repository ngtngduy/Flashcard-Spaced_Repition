package com.uth.memora.integration.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Bộ lập lịch WorkManager cho hàng đợi chấm câu ngoại tuyến (Mục 6.8 & 8.2).
 * Đảm bảo mỗi câu đặt ở trạng thái PENDING_GRADE chỉ có một tác vụ duy nhất
 * chạy khi thiết bị có kết nối mạng, hỗ trợ hẹn trễ cho lịch thử lại.
 */
class GradingScheduler(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Đưa một câu đặt vào hàng đợi chấm AI khi có mạng (Mục 8.2).
     *
     * @param sentenceId Mã định danh câu cần chấm.
     * @param initialDelaySeconds Thời gian chờ trước khi chạy (giây), dùng cho lịch lùi thử lại.
     * @param replaceExisting `true` để thay thế tác vụ cũ (khi thử lại), `false` để giữ tác vụ đang chờ.
     */
    fun enqueueSentence(
        sentenceId: String,
        initialDelaySeconds: Long = 0L,
        replaceExisting: Boolean = false
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val requestBuilder = OneTimeWorkRequestBuilder<SentenceGradingWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_SENTENCE_ID to sentenceId))

        if (initialDelaySeconds > 0L) {
            requestBuilder.setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)
        }

        val workPolicy = if (replaceExisting) {
            ExistingWorkPolicy.REPLACE
        } else {
            ExistingWorkPolicy.KEEP
        }

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueWorkName(sentenceId),
            workPolicy,
            requestBuilder.build()
        )
    }

    companion object {
        const val KEY_SENTENCE_ID = "sentence_id"
        private const val WORK_NAME_PREFIX = "grade_sentence_"

        fun uniqueWorkName(sentenceId: String): String = "$WORK_NAME_PREFIX$sentenceId"
    }
}
