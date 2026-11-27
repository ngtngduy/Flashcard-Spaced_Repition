package com.uth.memora.integration.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.uth.memora.MainActivity
import com.uth.memora.MemoraApplication
import com.uth.memora.core.sm2.LearningDayCalculator
import kotlinx.coroutines.CancellationException

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: THÔNG BÁO NHẮC HỌC HẰNG NGÀY (Mục 6.10 & Mục 10.3)
 * ============================================================================
 * Tác vụ nền WorkManager chịu trách nhiệm hiển thị thông báo nhắc ôn tập hằng ngày:
 * - Khởi tạo kênh thông báo `NotificationChannel` (Android 8+) tên "Nhắc học Memora".
 * - Kiểm tra an toàn quyền `POST_NOTIFICATIONS` trên Android 13+ (chưa cấp quyền thì bỏ qua, không crash).
 * - Kiểm tra ngày học nhắc gần nhất trong preferences riêng của module để bảo đảm tối đa 1 thông báo/ngày (Mục 6.10).
 * - Truy vấn cả thẻ đến hạn (`getDueReviewQueue(null)`) và thẻ mới còn lại hôm nay (`getNewCardsQueue(null)`);
 *   nếu cả hai đều `<= 0` thì bỏ qua để không làm phiền người học đã hoàn thành mục tiêu hôm nay.
 * - Đính kèm `PendingIntent` mở thẳng `MainActivity` vào màn Ôn tập thẻ đến hạn (`StudyMode.DUE_REVIEW`).
 */
class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MemoraApplication)?.container
            ?: return Result.failure()

        // Tôn trọng công tắc bật/tắt nhắc học trong Cài đặt người dùng (Mục 6.10 & 10.3)
        val isReminderEnabled = try {
            container.userPreferencesRepository.getUserPreferences().isReminderEnabled
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return retryOrFail()
        }

        if (!isReminderEnabled) {
            return Result.success(
                workDataOf(
                    KEY_NOTIFICATION_SHOWN to false,
                    KEY_SKIP_REASON to SKIP_REASON_REMINDER_DISABLED
                )
            )
        }

        // Bảo đảm tối đa 1 thông báo mỗi ngày học (mốc 04:00 sáng theo LearningDayCalculator - Mục 6.10)
        val todayLearningDay = LearningDayCalculator.toLearningDay()
        val ignoreDailyLimit = inputData.getBoolean(KEY_IGNORE_DAILY_LIMIT_FOR_TEST, false)
        val lastRemindedLearningDay = getLastRemindedLearningDay(applicationContext)
        if (!ignoreDailyLimit && hasAlreadyRemindedToday(lastRemindedLearningDay, todayLearningDay)) {
            return Result.success(
                workDataOf(
                    KEY_NOTIFICATION_SHOWN to false,
                    KEY_LAST_REMINDED_LEARNING_DAY to todayLearningDay,
                    KEY_SKIP_REASON to SKIP_REASON_ALREADY_REMINDED_TODAY
                )
            )
        }

        // Tạo sẵn NotificationChannel trên Android 8+ (idempotent, an toàn khi chưa có quyền) (Mục 10.3)
        ensureNotificationChannel(applicationContext)

        // Kiểm tra quyền thông báo trên Android 13+ và trạng thái bật thông báo hệ thống (Mục 6.10 & 10.3)
        val hasRuntimePermission = hasNotificationPermission(applicationContext)
        val notificationsEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        val permissionSkipReason = evaluateNotificationSkipReason(
            sdkInt = Build.VERSION.SDK_INT,
            isPostNotificationsGranted = hasRuntimePermission,
            areNotificationsEnabled = notificationsEnabled
        )
        if (permissionSkipReason != null) {
            return Result.success(
                workDataOf(
                    KEY_NOTIFICATION_SHOWN to false,
                    KEY_SKIP_REASON to permissionSkipReason
                )
            )
        }

        // Truy vấn cả thẻ đến hạn ôn tập và thẻ mới còn lại trong hạn mức hôm nay (Mục 6.5, 6.6 & 6.10)
        val (dueCardsCount, remainingNewCardsToday) = try {
            val dueCount = container.studySessionRepository.getDueReviewQueue(deckId = null).size
            val newCardsCount = container.studySessionRepository.getNewCardsQueue(deckId = null).size
            dueCount to newCardsCount
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return retryOrFail()
        }

        // Không bắn thông báo nếu người học đã hoàn thành hết thẻ cần ôn và thẻ mới hôm nay (Mục 6.10)
        if (!shouldNotifyForCardCounts(dueCardsCount, remainingNewCardsToday)) {
            return Result.success(
                workDataOf(
                    KEY_NOTIFICATION_SHOWN to false,
                    KEY_DUE_CARDS_COUNT to dueCardsCount,
                    KEY_NEW_CARDS_COUNT to remainingNewCardsToday,
                    KEY_SKIP_REASON to SKIP_REASON_DAILY_GOAL_COMPLETED
                )
            )
        }

        val notification = buildReminderNotification(
            context = applicationContext,
            dueCardsCount = dueCardsCount,
            remainingNewCardsToday = remainingNewCardsToday
        )

        val posted = postNotificationSafely(
            context = applicationContext,
            notification = notification
        )

        return if (posted) {
            // Lưu mốc ngày học vừa nhắc để tránh bắn trùng thông báo lần thứ 2 trong cùng ngày (Mục 6.10)
            saveLastRemindedLearningDay(applicationContext, todayLearningDay)
            Result.success(
                workDataOf(
                    KEY_NOTIFICATION_SHOWN to true,
                    KEY_DUE_CARDS_COUNT to dueCardsCount,
                    KEY_NEW_CARDS_COUNT to remainingNewCardsToday,
                    KEY_LAST_REMINDED_LEARNING_DAY to todayLearningDay
                )
            )
        } else {
            Result.success(
                workDataOf(
                    KEY_NOTIFICATION_SHOWN to false,
                    KEY_SKIP_REASON to SKIP_REASON_PERMISSION_DENIED
                )
            )
        }
    }

    private fun retryOrFail(): Result {
        return if (shouldRetry(runAttemptCount)) Result.retry() else Result.failure()
    }

    companion object {
        const val CHANNEL_ID: String = "memora_daily_reminder_channel"
        const val CHANNEL_NAME: String = "Nhắc học Memora"
        const val CHANNEL_DESCRIPTION: String =
            "Thông báo nhắc ôn tập các thẻ từ vựng đến hạn hằng ngày cùng Memora."

        const val NOTIFICATION_ID: Int = 1003
        const val PENDING_INTENT_REQUEST_CODE: Int = 1003

        const val ACTION_OPEN_DUE_REVIEW: String =
            "com.uth.memora.action.OPEN_DUE_REVIEW_FROM_REMINDER"

        // Cờ Intent mở thẳng màn Ôn tập đến hạn (tương thích sẵn MainActivity.rememberOpenStudyFromIntent) (Mục 10.3)
        const val EXTRA_START_DUE_REVIEW: String = "EXTRA_START_DUE_REVIEW"
        const val EXTRA_OPEN_STUDY_REMINDER: String = "EXTRA_OPEN_STUDY_REMINDER"
        const val EXTRA_DESTINATION: String = "destination"
        const val DESTINATION_STUDY: String = "study"

        const val NOTIFICATION_TITLE: String = "Đến giờ ôn tập cùng Memora!"

        const val KEY_NOTIFICATION_SHOWN: String = "notification_shown"
        const val KEY_DUE_CARDS_COUNT: String = "due_cards_count"
        const val KEY_NEW_CARDS_COUNT: String = "new_cards_count"
        const val KEY_LAST_REMINDED_LEARNING_DAY: String = "last_reminded_learning_day"
        const val KEY_SKIP_REASON: String = "skip_reason"
        const val KEY_IGNORE_DAILY_LIMIT_FOR_TEST: String = "ignore_daily_limit_for_test"

        const val SKIP_REASON_REMINDER_DISABLED: String = "reminder_disabled"
        const val SKIP_REASON_ALREADY_REMINDED_TODAY: String = "already_reminded_today"
        const val SKIP_REASON_PERMISSION_DENIED: String = "permission_denied"
        const val SKIP_REASON_NOTIFICATIONS_DISABLED: String = "notifications_disabled"
        const val SKIP_REASON_DAILY_GOAL_COMPLETED: String = "daily_goal_completed"

        private const val REMINDER_PREFS_NAME: String = "memora_reminder_prefs"
        private const val PREF_KEY_LAST_REMINDED_LEARNING_DAY: String = "last_reminded_learning_day"
        private const val NO_REMINDER_DAY_RECORDED: Long = Long.MIN_VALUE

        const val UNIQUE_IMMEDIATE_TEST_WORK_NAME: String = "memora_daily_reminder_immediate_test"
        const val TAG_DAILY_REMINDER: String = "memora_daily_reminder"

        const val MAX_RETRY_ATTEMPTS: Int = 2

        /**
         * Tạo kênh thông báo "Nhắc học Memora" cho Android 8.0+ (API 26+) (Mục 10.3).
         * Gọi lặp lại nhiều lần hoàn toàn an toàn theo cơ chế idempotent của hệ thống.
         */
        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
            notificationManager.createNotificationChannel(channel)
        }

        /**
         * Kiểm tra quyền `POST_NOTIFICATIONS` an toàn trên Android 13+ (API 33+) (Mục 6.10 & 10.3).
         */
        fun hasNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true
            }
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Xác định hôm nay đã gửi thông báo nhắc học hay chưa để bảo đảm tối đa 1 lần/ngày (Mục 6.10).
         */
        fun hasAlreadyRemindedToday(
            lastRemindedLearningDay: Long?,
            currentLearningDay: Long
        ): Boolean {
            return lastRemindedLearningDay != null && lastRemindedLearningDay == currentLearningDay
        }

        /**
         * Chỉ hiển thị thông báo khi còn ít nhất 1 thẻ đến hạn ôn hoặc 1 thẻ mới còn lại hôm nay (Mục 6.10).
         * Nếu cả hai `<= 0` thì người học đã hoàn thành mục tiêu hôm nay -> bỏ qua để tránh làm phiền.
         */
        fun shouldNotifyForCardCounts(
            dueCardsCount: Int,
            remainingNewCardsToday: Int
        ): Boolean {
            return dueCardsCount > 0 || remainingNewCardsToday > 0
        }

        /**
         * Đọc ngày học gần nhất đã gửi thông báo từ preferences cục bộ của module nhắc học (Mục 6.10).
         */
        fun getLastRemindedLearningDay(context: Context): Long? {
            val prefs = reminderPreferences(context)
            val savedDay = prefs.getLong(PREF_KEY_LAST_REMINDED_LEARNING_DAY, NO_REMINDER_DAY_RECORDED)
            return savedDay.takeIf { it != NO_REMINDER_DAY_RECORDED }
        }

        /**
         * Lưu đồng bộ ngày học vừa gửi thông báo vào preferences để chống bắn trùng khi Worker chạy lại (Mục 6.10).
         */
        fun saveLastRemindedLearningDay(context: Context, learningDay: Long) {
            reminderPreferences(context)
                .edit()
                .putLong(PREF_KEY_LAST_REMINDED_LEARNING_DAY, learningDay)
                .commit()
        }

        /**
         * Xóa dấu vết ngày nhắc gần nhất (tiện ích hỗ trợ kiểm thử thủ công nhiều lần trong ngày) (Mục 6.10).
         */
        fun clearLastRemindedLearningDay(context: Context) {
            reminderPreferences(context)
                .edit()
                .remove(PREF_KEY_LAST_REMINDED_LEARNING_DAY)
                .apply()
        }

        /**
         * Tạo nội dung thông báo hiển thị số thẻ đang chờ ôn tập / thẻ mới hôm nay (Mục 6.10 & 10.3).
         */
        fun buildNotificationContent(
            dueCardsCount: Int,
            remainingNewCardsToday: Int = 0
        ): String {
            val safeDue = dueCardsCount.coerceAtLeast(0)
            val safeNew = remainingNewCardsToday.coerceAtLeast(0)
            return when {
                safeDue > 0 && safeNew > 0 ->
                    "Hôm nay bạn có $safeDue thẻ đang chờ ôn và $safeNew thẻ mới cần học."

                safeDue > 0 ->
                    "Hôm nay bạn có $safeDue thẻ đang chờ ôn."

                safeNew > 0 ->
                    "Hôm nay bạn còn $safeNew thẻ mới chưa học. Vào Memora để hoàn thành mục tiêu nhé!"

                else ->
                    "Hôm nay bạn có 0 thẻ đang chờ ôn. Vào Memora học thêm từ mới để giữ chuỗi nhé!"
            }
        }

        /**
         * Tạo Intent mở `MainActivity` và điều hướng thẳng vào màn Ôn tập thẻ đến hạn (`StudyMode.DUE_REVIEW`) (Mục 10.3).
         *
         * Không gắn `FLAG_ACTIVITY_SINGLE_TOP` vì `MainActivity` dùng `launchMode="standard"`,
         * giúp `FLAG_ACTIVITY_CLEAR_TOP` khởi tạo lại phiên sạch với Intent mới ở cả cold start lẫn warm start.
         */
        fun createDueReviewIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_DUE_REVIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_START_DUE_REVIEW, true)
                putExtra(EXTRA_OPEN_STUDY_REMINDER, true)
                putExtra(EXTRA_DESTINATION, DESTINATION_STUDY)
            }
        }

        /**
         * Tạo `PendingIntent` bất biến (FLAG_IMMUTABLE) mở thẳng màn Ôn tập thẻ đến hạn (Mục 10.3).
         */
        fun createContentPendingIntent(context: Context): PendingIntent {
            val intent = createDueReviewIntent(context)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(
                context,
                PENDING_INTENT_REQUEST_CODE,
                intent,
                flags
            )
        }

        /**
         * Dựng đối tượng `Notification` chuẩn Material/Android với tiêu đề, nội dung số thẻ và `PendingIntent` (Mục 10.3).
         */
        fun buildReminderNotification(
            context: Context,
            dueCardsCount: Int,
            remainingNewCardsToday: Int = 0
        ): Notification {
            val contentText = buildNotificationContent(
                dueCardsCount = dueCardsCount,
                remainingNewCardsToday = remainingNewCardsToday
            )
            val contentPendingIntent = createContentPendingIntent(context)

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)
                .build()
        }

        /**
         * Chạy thử `DailyReminderWorker` ngay lập tức qua WorkManager để kiểm thử hiển thị thông báo (Mục 10.3).
         */
        fun enqueueImmediateTest(
            context: Context,
            ignoreDailyLimit: Boolean = false
        ) {
            val request = buildImmediateTestRequest(ignoreDailyLimit = ignoreDailyLimit)
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_IMMEDIATE_TEST_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        internal fun buildImmediateTestRequest(ignoreDailyLimit: Boolean = false): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<DailyReminderWorker>()
                .setInputData(workDataOf(KEY_IGNORE_DAILY_LIMIT_FOR_TEST to ignoreDailyLimit))
                .addTag(TAG_DAILY_REMINDER)
                .build()
        }

        private fun reminderPreferences(context: Context): SharedPreferences {
            return context.applicationContext.getSharedPreferences(
                REMINDER_PREFS_NAME,
                Context.MODE_PRIVATE
            )
        }

        /**
         * Xác định lý do bỏ qua gửi thông báo dựa trên phiên bản Android và trạng thái quyền (Mục 6.10 & 10.3).
         * Trả về `null` khi đủ điều kiện hiển thị thông báo.
         */
        internal fun evaluateNotificationSkipReason(
            sdkInt: Int,
            isPostNotificationsGranted: Boolean,
            areNotificationsEnabled: Boolean
        ): String? {
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU && !isPostNotificationsGranted) {
                return SKIP_REASON_PERMISSION_DENIED
            }
            if (!areNotificationsEnabled) {
                return SKIP_REASON_NOTIFICATIONS_DISABLED
            }
            return null
        }

        internal fun shouldRetry(runAttemptCount: Int): Boolean {
            return runAttemptCount < MAX_RETRY_ATTEMPTS
        }

        private fun postNotificationSafely(
            context: Context,
            notification: Notification
        ): Boolean {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }

            return try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
                true
            } catch (_: SecurityException) {
                false
            }
        }
    }
}
