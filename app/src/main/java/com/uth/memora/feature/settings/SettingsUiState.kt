package com.uth.memora.feature.settings

import com.uth.memora.core.domain.CloudSyncSummary
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.core.model.UserAccount
import com.uth.memora.core.model.UserPreferences
import java.util.Locale

/**
 * Trạng thái hiển thị đồng bộ đám mây trên màn Cài đặt (Figma Node 13:2 & Mục 13).
 */
enum class SyncBadgeStateUi {
    SYNCING,
    SYNCED,
    PENDING,
    ERROR
}

/**
 * Trạng thái giao diện cho màn Cài đặt ứng dụng (Figma Node 13:2 · Mục 6.2, 6.10, 11 & 13).
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyNewCardsLimit: Int = UserPreferences.DEFAULT_DAILY_NEW_CARDS,
    val isReminderEnabled: Boolean = true,
    val reminderHour: Int = UserPreferences.DEFAULT_REMINDER_HOUR,
    val reminderMinute: Int = UserPreferences.DEFAULT_REMINDER_MINUTE,
    val isSoundEnabled: Boolean = true,
    val aiAutoFillRemainingToday: Int = UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT,
    val aiSentenceGradingRemainingToday: Int = UserPreferences.DEFAULT_AI_SENTENCE_LIMIT,
    val currentUser: UserAccount? = null,
    val syncSummary: CloudSyncSummary = CloudSyncSummary(),
    val isManualSyncInProgress: Boolean = false,
    val isTimePickerVisible: Boolean = false,
    val isNotificationPermissionGuideVisible: Boolean = false,
    val isSignOutWarningDialogVisible: Boolean = false,
    val isSigningOut: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
) {
    /**
     * Người dùng đã đăng nhập tài khoản chính thức (không phải Khách ẩn danh).
     */
    val isSignedInAccount: Boolean
        get() = currentUser != null && !currentUser.isAnonymous

    /**
     * Chuỗi giờ:phút nhắc học định dạng 24h (VD: "20:00", "07:30").
     */
    val formattedReminderTime: String
        get() = String.format(
            Locale.US,
            "%02d:%02d",
            reminderHour.coerceIn(0, 23),
            reminderMinute.coerceIn(0, 59)
        )

    /**
     * Trạng thái đồng bộ đám mây hiện tại để hiển thị biểu tượng & huy hiệu màu.
     */
    val syncBadgeState: SyncBadgeStateUi
        get() = when {
            isManualSyncInProgress || syncSummary.isSyncing -> SyncBadgeStateUi.SYNCING
            !syncSummary.syncErrorMessage.isNullOrBlank() -> SyncBadgeStateUi.ERROR
            syncSummary.pendingChangesCount > 0 -> SyncBadgeStateUi.PENDING
            else -> SyncBadgeStateUi.SYNCED
        }

    /**
     * Thời gian đồng bộ gần nhất lấy từ `SyncStatusProvider` hoặc `UserPreferences`.
     */
    val effectiveLastSyncedAtMillis: Long?
        get() = syncSummary.lastSyncedAtMillis ?: preferences.lastSyncedAtMillis
}
