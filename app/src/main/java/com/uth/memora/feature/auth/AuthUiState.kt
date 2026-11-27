package com.uth.memora.feature.auth

/**
 * Ba chế độ màn hình trong luồng Xác thực tài khoản (Figma 17:484, 17:449 & 2193:2 · Mục 6.2).
 */
enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

/**
 * Trạng thái giao diện bất biến (Immutable UI State) cho các màn hình Xác thực (Mục 6.2, 10.1 & 11).
 *
 * Dùng chung cho Đăng nhập (`17:484`), Đăng ký (`17:449`), Quên mật khẩu (`2193:2`)
 * và hộp thoại gộp dữ liệu Khách (`MergeGuestDialog`).
 */
data class AuthUiState(
    val currentMode: AuthMode = AuthMode.LOGIN,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val isGuestLoading: Boolean = false,
    val errorMessage: String? = null,
    val resetEmailSent: Boolean = false,
    val isAuthSuccess: Boolean = false,
    val pendingMergeAccountId: String? = null,
    val displayNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val hasLocalGuestData: Boolean = false,
    val isMergingGuestData: Boolean = false,
    val shouldTriggerCloudSync: Boolean = false
) {
    /** Hiển thị hộp thoại gộp dữ liệu khách khi đã đăng nhập tài khoản thật và phát hiện dữ liệu Khách cục bộ (Mục 10.1). */
    val showMergeGuestDialog: Boolean
        get() = !pendingMergeAccountId.isNullOrBlank()
}
