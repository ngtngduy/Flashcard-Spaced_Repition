package com.uth.memora.integration.reminder

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.uth.memora.MemoraApplication
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.UserPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Cấu hình tối giản cho lịch nhắc học hằng ngày rút ra từ [UserPreferences] (Mục 6.10 & Mục 10.3).
 */
data class ReminderScheduleConfig(
    val isReminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int
)

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: BỘ LẬP LỊCH NHẮC HỌC HẰNG NGÀY (Mục 6.10 & Mục 10.3)
 * ============================================================================
 * Quản lý lịch [PeriodicWorkRequest] chu kỳ 24 giờ cho [DailyReminderWorker] bằng WorkManager:
 * - Đọc `isReminderEnabled`, `reminderHour`, `reminderMinute` từ [UserPreferences].
 * - Khi `isReminderEnabled == false`: hủy lịch `cancelUniqueWork("daily_study_reminder")`.
 * - Khi `isReminderEnabled == true`: tính khoảng trễ từ thời điểm hiện tại đến mốc `giờ:phút`
 *   kế tiếp và cập nhật tác vụ định kỳ 24 giờ bằng `ExistingPeriodicWorkPolicy.UPDATE`.
 * - Hoạt động hoàn toàn ngoại tuyến (không yêu cầu mạng) và được WorkManager tự khôi phục
 *   sau khi khởi động lại thiết bị nhờ quyền `RECEIVE_BOOT_COMPLETED` trong `AndroidManifest.xml`.
 */
class ReminderScheduler(context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * Đồng bộ lịch nhắc học một lần dựa trên [UserPreferences] hiện tại trong [UserPreferencesRepository] (Mục 10.3).
     * Phù hợp gọi khi khởi động ứng dụng hoặc sau khi lưu thay đổi cài đặt nhắc học.
     */
    suspend fun syncWithPreferences(userPreferencesRepository: UserPreferencesRepository) {
        val preferences = try {
            userPreferencesRepository.getUserPreferences()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return
        }
        scheduleOrCancel(preferences)
    }

    /**
     * Tiện ích đọc trực tiếp `UserPreferencesRepository` từ [MemoraApplication.container] để đồng bộ lịch (Mục 10.3).
     */
    suspend fun syncFromApplicationContainer() {
        val repository = (appContext as? MemoraApplication)?.container?.userPreferencesRepository
            ?: return
        syncWithPreferences(repository)
    }

    /**
     * Quan sát liên tục các thay đổi `isReminderEnabled`, `reminderHour`, `reminderMinute` từ DataStore
     * và tự động cập nhật/hủy lịch WorkManager mỗi khi người dùng đổi cài đặt trong Settings (Mục 6.10 & 10.3).
     *
     * Lưu ý: hàm này `collect` liên tục nên cần chạy trong một coroutine riêng (VD: `applicationScope.launch`).
     */
    suspend fun observeAndSync(userPreferencesRepository: UserPreferencesRepository) {
        userPreferencesRepository
            .observeUserPreferences()
            .map { preferences -> extractScheduleConfig(preferences) }
            .distinctUntilChanged()
            .collect { config ->
                scheduleOrCancel(
                    isReminderEnabled = config.isReminderEnabled,
                    hour = config.reminderHour,
                    minute = config.reminderMinute
                )
            }
    }

    /**
     * Áp dụng trực tiếp cài đặt nhắc học từ đối tượng [UserPreferences] (Mục 6.10 & 10.3).
     */
    fun scheduleOrCancel(preferences: UserPreferences) {
        val config = extractScheduleConfig(preferences)
        scheduleOrCancel(
            isReminderEnabled = config.isReminderEnabled,
            hour = config.reminderHour,
            minute = config.reminderMinute
        )
    }

    /**
     * Bật hoặc hủy lịch nhắc học hằng ngày theo công tắc và mốc `giờ:phút` (Mục 6.10 & 10.3).
     */
    fun scheduleOrCancel(
        isReminderEnabled: Boolean,
        hour: Int,
        minute: Int
    ) {
        if (!isReminderEnabled) {
            cancel()
        } else {
            scheduleDailyReminder(hour = hour, minute = minute)
        }
    }

    /**
     * Đặt hoặc cập nhật lịch nhắc học định kỳ mỗi 24 giờ cho mốc `hour:minute` kế tiếp (Mục 10.3).
     *
     * Dùng `ExistingPeriodicWorkPolicy.UPDATE` để cập nhật ngay mốc giờ mới mà không tạo tác vụ trùng.
     */
    fun scheduleDailyReminder(
        hour: Int,
        minute: Int
    ) {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)

        // Đảm bảo kênh "Nhắc học Memora" luôn sẵn sàng trên Android 8+ (Mục 10.3)
        DailyReminderWorker.ensureNotificationChannel(appContext)

        val initialDelayMillis = calculateInitialDelayMillis(
            targetHour = safeHour,
            targetMinute = safeMinute
        )
        val request = buildDailyReminderRequest(initialDelayMillis)

        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Hủy toàn bộ lịch nhắc học hằng ngày đang đăng ký với WorkManager (Mục 6.10 & 10.3).
     */
    fun cancel() {
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "daily_study_reminder"
        const val REPEAT_INTERVAL_HOURS: Long = 24L
        const val TAG_DAILY_REMINDER: String = DailyReminderWorker.TAG_DAILY_REMINDER

        /**
         * Rút gọn và chuẩn hóa khung giờ nhắc học từ [UserPreferences] (Mục 6.10).
         */
        internal fun extractScheduleConfig(preferences: UserPreferences): ReminderScheduleConfig {
            return ReminderScheduleConfig(
                isReminderEnabled = preferences.isReminderEnabled,
                reminderHour = preferences.reminderHour.coerceIn(0, 23),
                reminderMinute = preferences.reminderMinute.coerceIn(0, 59)
            )
        }

        /**
         * Tính khoảng thời gian trễ (mili giây) từ thời điểm [now] đến mốc `targetHour:targetMinute` kế tiếp (Mục 10.3).
         *
         * - Nếu mốc giờ hôm nay chưa tới (`nextTrigger > now`): trả về khoảng cách tới mốc hôm nay.
         * - Nếu mốc giờ hôm nay đã qua hoặc trùng đúng thời điểm hiện tại (`nextTrigger <= now`):
         *   đẩy sang cùng mốc `targetHour:targetMinute` của ngày hôm sau.
         */
        fun calculateInitialDelayMillis(
            targetHour: Int,
            targetMinute: Int,
            now: ZonedDateTime = ZonedDateTime.now()
        ): Long {
            val safeHour = targetHour.coerceIn(0, 23)
            val safeMinute = targetMinute.coerceIn(0, 59)

            var nextTrigger = now
                .withHour(safeHour)
                .withMinute(safeMinute)
                .withSecond(0)
                .withNano(0)

            if (!nextTrigger.isAfter(now)) {
                nextTrigger = nextTrigger.plusDays(1)
            }

            return Duration.between(now, nextTrigger)
                .toMillis()
                .coerceAtLeast(0L)
        }

        /**
         * Tạo [PeriodicWorkRequest] lặp mỗi 24 giờ cho [DailyReminderWorker] (không cần mạng) (Mục 10.3).
         */
        internal fun buildDailyReminderRequest(initialDelayMillis: Long): PeriodicWorkRequest {
            val safeDelayMillis = initialDelayMillis.coerceAtLeast(0L)
            return PeriodicWorkRequestBuilder<DailyReminderWorker>(
                REPEAT_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setInitialDelay(safeDelayMillis, TimeUnit.MILLISECONDS)
                .addTag(TAG_DAILY_REMINDER)
                .build()
        }
    }
}
