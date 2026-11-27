package com.uth.memora.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.CloudSyncManager
import com.uth.memora.core.domain.SyncStatusProvider
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel cho màn hình Cài đặt ứng dụng (Figma Node 13:2 · Mục 6.2, 6.10, 11 & 13).
 *
 * Kết hợp dữ liệu thời gian thực từ [UserPreferencesRepository], [AuthRepository],
 * [SyncStatusProvider] và điều phối đồng bộ thủ công qua [CloudSyncManager].
 */
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val syncStatusProvider: SyncStatusProvider,
    private val cloudSyncManager: CloudSyncManager,
    private val onAccountLocalCleanup: (suspend (String) -> Unit)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isLoading = true))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        observeSettingsData()
    }

    /**
     * Quan sát đồng thời cài đặt người dùng, tài khoản đăng nhập và trạng thái đồng bộ đám mây.
     */
    fun observeSettingsData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            combine(
                userPreferencesRepository.observeUserPreferences(),
                authRepository.observeCurrentUser(),
                syncStatusProvider.observeSyncSummary()
            ) { prefs, user, syncSummary ->
                Triple(prefs, user, syncSummary)
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải cài đặt ứng dụng."
                    )
                }
            }.collect { (prefs, user, syncSummary) ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        preferences = prefs,
                        themeMode = prefs.themeMode,
                        dailyNewCardsLimit = prefs.dailyNewCardsLimit.coerceIn(
                            UserPreferences.MIN_DAILY_NEW_CARDS,
                            UserPreferences.MAX_DAILY_NEW_CARDS
                        ),
                        isReminderEnabled = prefs.isReminderEnabled,
                        reminderHour = prefs.reminderHour.coerceIn(0, 23),
                        reminderMinute = prefs.reminderMinute.coerceIn(0, 59),
                        isSoundEnabled = prefs.isSoundEnabled,
                        aiAutoFillRemainingToday = prefs.aiAutoFillRemainingToday.coerceAtLeast(0),
                        aiSentenceGradingRemainingToday = prefs.aiSentenceGradingRemainingToday.coerceAtLeast(0),
                        currentUser = user,
                        syncSummary = syncSummary
                    )
                }
            }
        }
    }

    /**
     * Đổi chế độ giao diện Hệ thống / Sáng / Tối (tác động tức thì lên `MainActivity`).
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Không thể cập nhật chế độ giao diện.")
                    }
                }
        }
    }

    /**
     * Cập nhật hạn mức thẻ mới mỗi ngày trong khoảng [5..50] thẻ.
     */
    fun setDailyNewCardsLimit(limit: Int) {
        val clamped = limit.coerceIn(
            UserPreferences.MIN_DAILY_NEW_CARDS,
            UserPreferences.MAX_DAILY_NEW_CARDS
        )
        _uiState.update { it.copy(dailyNewCardsLimit = clamped) }
        viewModelScope.launch {
            userPreferencesRepository.setDailyNewCardsLimit(clamped)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Không thể lưu hạn mức thẻ mới.")
                    }
                }
        }
    }

    /**
     * Cập nhật trạng thái bật/tắt và khung giờ nhắc học hằng ngày (Mục 6.10).
     */
    fun setDailyReminder(
        enabled: Boolean,
        hour: Int = _uiState.value.reminderHour,
        minute: Int = _uiState.value.reminderMinute
    ) {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        viewModelScope.launch {
            userPreferencesRepository.setDailyReminder(
                enabled = enabled,
                hour = safeHour,
                minute = safeMinute
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isReminderEnabled = enabled,
                        reminderHour = safeHour,
                        reminderMinute = safeMinute,
                        isTimePickerVisible = false,
                        isNotificationPermissionGuideVisible = if (enabled) {
                            false
                        } else {
                            it.isNotificationPermissionGuideVisible
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Không thể lưu cài đặt nhắc học.")
                }
            }
        }
    }

    /**
     * Khi người dùng từ chối quyền `POST_NOTIFICATIONS` trên Android 13+ (API 33+):
     * tự động tắt công tắc nhắc học và hiển thị hướng dẫn mở lại quyền trong Cài đặt máy (Mục 6.10).
     */
    fun onNotificationPermissionDenied() {
        viewModelScope.launch {
            userPreferencesRepository.setDailyReminder(
                enabled = false,
                hour = _uiState.value.reminderHour,
                minute = _uiState.value.reminderMinute
            )
            _uiState.update {
                it.copy(
                    isReminderEnabled = false,
                    isNotificationPermissionGuideVisible = true,
                    feedbackMessage = "Quyền thông báo đang tắt. Hãy cấp quyền trong Cài đặt máy để nhận nhắc nhở học tập."
                )
            }
        }
    }

    fun dismissNotificationPermissionGuide() {
        _uiState.update { it.copy(isNotificationPermissionGuideVisible = false) }
    }

    fun openTimePicker() {
        _uiState.update { it.copy(isTimePickerVisible = true) }
    }

    fun dismissTimePicker() {
        _uiState.update { it.copy(isTimePickerVisible = false) }
    }

    /**
     * Bật/tắt âm thanh phát âm từ vựng và hiệu ứng âm thanh.
     */
    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSoundEnabled(enabled)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.message ?: "Không thể cập nhật cài đặt âm thanh.")
                    }
                }
        }
    }

    /**
     * Bấm nút "Đồng bộ ngay": kích hoạt đồng bộ thủ công qua [SyncStatusProvider] & [CloudSyncManager].
     */
    fun syncNow() {
        if (_uiState.value.isManualSyncInProgress || _uiState.value.syncSummary.isSyncing) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isManualSyncInProgress = true,
                    errorMessage = null,
                    feedbackMessage = null
                )
            }

            val providerResult = syncStatusProvider.triggerImmediateSync()
            val managerResult = if (providerResult.isSuccess) {
                providerResult
            } else {
                cloudSyncManager.syncPendingChanges()
            }

            managerResult.onSuccess { syncedCount ->
                val now = System.currentTimeMillis()
                userPreferencesRepository.setLastSyncedAtMillis(now)
                _uiState.update {
                    it.copy(
                        isManualSyncInProgress = false,
                        feedbackMessage = if (syncedCount > 0) {
                            "Đã đồng bộ thành công $syncedCount thay đổi lên đám mây."
                        } else {
                            "Dữ liệu đã ở trạng thái đồng bộ mới nhất."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isManualSyncInProgress = false,
                        errorMessage = error.message ?: "Đồng bộ đám mây thất bại. Vui lòng kiểm tra kết nối mạng."
                    )
                }
            }
        }
    }

    /**
     * Yêu cầu đăng xuất: nếu còn bản ghi `pendingChangesCount > 0` chưa đồng bộ thì bật hộp thoại
     * cảnh báo xác nhận trước khi xóa dữ liệu cục bộ của tài khoản và đăng xuất (Mục 6.2).
     */
    fun requestSignOut() {
        val pendingCount = _uiState.value.syncSummary.pendingChangesCount
        if (pendingCount > 0) {
            _uiState.update { it.copy(isSignOutWarningDialogVisible = true) }
        } else {
            signOut()
        }
    }

    fun dismissSignOutWarningDialog() {
        _uiState.update { it.copy(isSignOutWarningDialogVisible = false) }
    }

    /**
     * Thực hiện đăng xuất tài khoản và dọn dữ liệu cục bộ của tài khoản đó (Mục 6.2).
     */
    fun signOut() {
        if (_uiState.value.isSigningOut) return

        viewModelScope.launch {
            val uidToCleanup = _uiState.value.currentUser?.takeIf { !it.isAnonymous }?.uid
            _uiState.update {
                it.copy(
                    isSigningOut = true,
                    isSignOutWarningDialogVisible = false,
                    errorMessage = null
                )
            }

            authRepository.signOut()
                .onSuccess {
                    if (!uidToCleanup.isNullOrBlank()) {
                        runCatching { onAccountLocalCleanup?.invoke(uidToCleanup) }
                    }
                    _uiState.update {
                        it.copy(
                            isSigningOut = false,
                            feedbackMessage = "Đã đăng xuất tài khoản. Bạn đang sử dụng chế độ ngoại tuyến trên máy."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSigningOut = false,
                            errorMessage = error.message ?: "Không thể đăng xuất tài khoản."
                        )
                    }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
    }
}
