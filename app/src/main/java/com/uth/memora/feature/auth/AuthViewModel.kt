package com.uth.memora.feature.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.CloudSyncManager
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.UserAccount
import com.uth.memora.integration.auth.GoogleSignInException
import com.uth.memora.integration.sync.FirestoreCloudSyncManager
import com.uth.memora.memoraViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * ViewModel điều phối toàn bộ luồng Xác thực người dùng: Đăng nhập, Đăng ký, Quên mật khẩu,
 * Đăng nhập Google, Chế độ Khách và kiểm tra gộp dữ liệu Khách (Mục 6.2, 10.1 & 11 · Figma 17:484, 17:449, 2193:2).
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val deckRepository: DeckRepository? = null,
    private val cloudSyncManager: CloudSyncManager? = null,
    initialMode: AuthMode = AuthMode.LOGIN,
    private val hasLocalGuestDataChecker: (suspend () -> Boolean)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(currentMode = initialMode))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        refreshLocalGuestDataFlag()
    }

    /**
     * Chuyển đổi giữa 3 chế độ màn hình (LOGIN / REGISTER / FORGOT_PASSWORD) và dọn lỗi form cũ (Mục 6.2).
     */
    fun switchMode(mode: AuthMode) {
        _uiState.update { current ->
            if (current.currentMode == mode) {
                current
            } else {
                current.copy(
                    currentMode = mode,
                    password = "",
                    confirmPassword = "",
                    errorMessage = null,
                    resetEmailSent = false,
                    displayNameError = null,
                    emailError = null,
                    passwordError = null,
                    confirmPasswordError = null,
                    isPasswordVisible = false,
                    isConfirmPasswordVisible = false
                )
            }
        }
    }

    fun onDisplayNameChange(value: String) {
        _uiState.update {
            it.copy(
                displayName = value,
                displayNameError = null,
                errorMessage = null
            )
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                emailError = null,
                errorMessage = null,
                resetEmailSent = false
            )
        }
    }

    fun onPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                passwordError = null,
                confirmPasswordError = if (
                    it.currentMode == AuthMode.REGISTER &&
                    it.confirmPassword.isNotEmpty() &&
                    value == it.confirmPassword
                ) {
                    null
                } else {
                    it.confirmPasswordError
                },
                errorMessage = null
            )
        }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update {
            it.copy(
                confirmPassword = value,
                confirmPasswordError = null,
                errorMessage = null
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }
    }

    /**
     * Xử lý nút hành động chính theo `currentMode` hiện tại (Mục 6.2).
     */
    fun submitCurrentMode() {
        when (_uiState.value.currentMode) {
            AuthMode.LOGIN -> submitLogin()
            AuthMode.REGISTER -> submitRegister()
            AuthMode.FORGOT_PASSWORD -> submitForgotPassword()
        }
    }

    /**
     * Kiểm tra form và đăng nhập bằng email & mật khẩu (Mục 6.2 & 11 · Figma 17:484, 17:502).
     * Trước khi đăng nhập tài khoản chính thức sẽ kiểm tra dữ liệu Khách cục bộ để bật `MergeGuestDialog`.
     */
    fun submitLogin() {
        val state = _uiState.value
        if (state.isLoading || state.isGuestLoading || state.isMergingGuestData) return

        val cleanEmail = state.email.trim()
        val emailErr = validateEmail(cleanEmail)
        val passwordErr = validatePassword(state.password)

        if (emailErr != null || passwordErr != null) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    passwordError = passwordErr,
                    errorMessage = null
                )
            }
            return
        }

        authenticateOfficialAccount(
            fallbackError = ERROR_LOGIN_FAILED_VI,
            action = { authRepository.signInWithEmail(cleanEmail, state.password) }
        )
    }

    /**
     * Kiểm tra form (họ tên, email hợp lệ, mật khẩu >= 8 ký tự, xác nhận khớp) và đăng ký tài khoản mới
     * (Mục 6.2 & 11 · Figma 17:449, 17:475).
     */
    fun submitRegister() {
        val state = _uiState.value
        if (state.isLoading || state.isGuestLoading || state.isMergingGuestData) return

        val cleanDisplayName = state.displayName.trim()
        val cleanEmail = state.email.trim()

        val displayNameErr = validateDisplayName(cleanDisplayName)
        val emailErr = validateEmail(cleanEmail)
        val passwordErr = validatePassword(state.password)
        val confirmPasswordErr = validateConfirmPassword(state.password, state.confirmPassword)

        if (
            displayNameErr != null ||
            emailErr != null ||
            passwordErr != null ||
            confirmPasswordErr != null
        ) {
            _uiState.update {
                it.copy(
                    displayNameError = displayNameErr,
                    emailError = emailErr,
                    passwordError = passwordErr,
                    confirmPasswordError = confirmPasswordErr,
                    errorMessage = null
                )
            }
            return
        }

        authenticateOfficialAccount(
            fallbackError = ERROR_REGISTER_FAILED_VI,
            action = {
                authRepository.registerWithEmail(
                    email = cleanEmail,
                    password = state.password,
                    displayName = cleanDisplayName
                )
            }
        )
    }

    /**
     * Kiểm tra định dạng email và gửi liên kết đặt lại mật khẩu (Mục 6.2 · Figma 2193:2).
     */
    fun submitForgotPassword() {
        val state = _uiState.value
        if (state.isLoading || state.isGuestLoading || state.isMergingGuestData) return

        val cleanEmail = state.email.trim()
        val emailErr = validateEmail(cleanEmail)
        if (emailErr != null) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    errorMessage = null,
                    resetEmailSent = false
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    emailError = null,
                    errorMessage = null,
                    resetEmailSent = false
                )
            }

            val result = try {
                authRepository.sendPasswordResetEmail(cleanEmail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetEmailSent = true,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            resetEmailSent = false,
                            errorMessage = mapErrorToVietnamese(
                                throwable = error,
                                fallback = ERROR_PASSWORD_RESET_FAILED_VI
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Đăng nhập tài khoản Google bằng `idToken` từ `GoogleSignInHelper` (Mục 6.2 & 10.1).
     * Kiểm tra dữ liệu Khách cục bộ trước khi đăng nhập để bật `MergeGuestDialog` nếu cần.
     */
    fun signInWithGoogle(idToken: String) {
        val state = _uiState.value
        if (state.isLoading || state.isGuestLoading || state.isMergingGuestData) return

        val cleanToken = idToken.trim()
        if (cleanToken.isEmpty()) {
            _uiState.update { it.copy(errorMessage = ERROR_GOOGLE_SIGN_IN_FAILED_VI) }
            return
        }

        authenticateOfficialAccount(
            fallbackError = ERROR_GOOGLE_SIGN_IN_FAILED_VI,
            action = { authRepository.signInWithGoogle(cleanToken) }
        )
    }

    /**
     * Tiếp nhận lỗi từ `GoogleSignInHelper` tại tầng UI (`AuthRoute`).
     * Bỏ qua hiển thị lỗi đỏ nếu người dùng chủ động hủy hộp thoại chọn tài khoản Google (Mục 6.2).
     */
    fun onGoogleSignInError(throwable: Throwable) {
        if (throwable is GoogleSignInException.Cancelled) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = mapErrorToVietnamese(
                    throwable = throwable,
                    fallback = ERROR_GOOGLE_SIGN_IN_FAILED_VI
                )
            )
        }
    }

    /**
     * Tiếp tục sử dụng ứng dụng ở chế độ Khách ẩn danh để học offline ngay chưa cần tạo tài khoản (Mục 6.2).
     */
    fun continueAsGuest() {
        val state = _uiState.value
        if (state.isLoading || state.isGuestLoading || state.isMergingGuestData) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGuestLoading = true,
                    errorMessage = null,
                    resetEmailSent = false
                )
            }

            val result = try {
                authRepository.signInAnonymously()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isGuestLoading = false,
                            errorMessage = null,
                            pendingMergeAccountId = null,
                            isAuthSuccess = true,
                            shouldTriggerCloudSync = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isGuestLoading = false,
                            errorMessage = mapErrorToVietnamese(
                                throwable = error,
                                fallback = ERROR_GUEST_SIGN_IN_FAILED_VI
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Thực thi lựa chọn của người dùng trên `MergeGuestDialog` (Mục 6.2 & 10.1):
     * - `discardGuestData = false`: gộp dữ liệu Khách cục bộ vào tài khoản chính thức.
     * - `discardGuestData = true`: bỏ dữ liệu Khách cục bộ và dùng dữ liệu tài khoản.
     */
    fun resolveGuestDataMerge(discardGuestData: Boolean) {
        val accountId = _uiState.value.pendingMergeAccountId?.takeIf { it.isNotBlank() } ?: return
        if (_uiState.value.isMergingGuestData) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isMergingGuestData = true,
                    errorMessage = null
                )
            }

            val mergeResult = try {
                cloudSyncManager?.mergeGuestDataIntoAccount(
                    accountId = accountId,
                    discardGuestData = discardGuestData
                ) ?: Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            mergeResult.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isMergingGuestData = false,
                            pendingMergeAccountId = null,
                            hasLocalGuestData = false,
                            isAuthSuccess = true,
                            shouldTriggerCloudSync = true,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { _ ->
                    // Tài khoản đã xác thực thành công: vẫn đóng MergeGuestDialog và cho vào app, SyncWorker sẽ đồng bộ bù ở nền (Bug M3-1)
                    _uiState.update {
                        it.copy(
                            isMergingGuestData = false,
                            pendingMergeAccountId = null,
                            hasLocalGuestData = false,
                            isAuthSuccess = true,
                            shouldTriggerCloudSync = true,
                            errorMessage = null
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumeAuthSuccess() {
        _uiState.update {
            it.copy(
                isAuthSuccess = false,
                shouldTriggerCloudSync = false
            )
        }
    }

    /**
     * Luồng dùng chung cho các hình thức đăng nhập/đăng ký tài khoản chính thức (Email & Google):
     * - Ghi nhận `ownerId` Khách & kiểm tra dữ liệu Khách cục bộ TRƯỚC khi gọi `authRepository`.
     * - Sau khi đăng nhập tài khoản chính thức:
     *   - Nếu có dữ liệu Khách cục bộ VÀ tài khoản đã có dữ liệu trên Cloud -> bật `MergeGuestDialog` (Figma 2196:16).
     *   - Nếu có dữ liệu Khách cục bộ nhưng tài khoản Cloud đang trống -> tự động gộp vào tài khoản mới rồi đồng bộ.
     *   - Nếu không có dữ liệu Khách cục bộ -> hoàn tất và kích hoạt `SyncScheduler` tải dữ liệu Cloud về máy (Mục 6.2 & 10.1).
     */
    private fun authenticateOfficialAccount(
        fallbackError: String,
        action: suspend () -> Result<UserAccount>
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    displayNameError = null,
                    emailError = null,
                    passwordError = null,
                    confirmPasswordError = null,
                    errorMessage = null,
                    resetEmailSent = false
                )
            }

            // Ghi nhận UID Khách (ẩn danh Firebase hoặc guest_local) và kiểm tra dữ liệu cục bộ TRƯỚC khi chuyển tài khoản (Mục 10.1)
            val previousUser = runCatching { authRepository.getCurrentUser() }.getOrNull()
            val guestOwnerIdBeforeAuth = previousUser
                ?.takeIf { it.isAnonymous }
                ?.uid
                ?.takeIf { it.isNotBlank() }
                ?: Deck.GUEST_OWNER_ID
            val hadLocalGuestData = checkHasLocalGuestDataBeforeAuth()
            if (hadLocalGuestData) {
                (cloudSyncManager as? FirestoreCloudSyncManager)?.rememberGuestOwnerId(guestOwnerIdBeforeAuth)
            }
            _uiState.update { it.copy(hasLocalGuestData = hadLocalGuestData) }

            val authResult = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            authResult.fold(
                onSuccess = { account ->
                    val isOfficialAccount = !account.isAnonymous && account.uid.isNotBlank()
                    if (!hadLocalGuestData || !isOfficialAccount) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                pendingMergeAccountId = null,
                                isAuthSuccess = true,
                                shouldTriggerCloudSync = isOfficialAccount
                            )
                        }
                        return@fold
                    }

                    val hasCloudData = checkHasRemoteCloudData(account.uid)
                    if (hasCloudData) {
                        // Tài khoản đã có dữ liệu trên Cloud -> mở MergeGuestDialog hỏi rõ người dùng (Figma 2196:16)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                pendingMergeAccountId = account.uid,
                                isAuthSuccess = false,
                                shouldTriggerCloudSync = false
                            )
                        }
                    } else {
                        // Tài khoản mới chưa có dữ liệu Cloud -> tự động chuyển dữ liệu Khách sang tài khoản này (Mục 6.2 & 10.1)
                        val autoMergeResult = try {
                            cloudSyncManager?.mergeGuestDataIntoAccount(
                                accountId = account.uid,
                                discardGuestData = false
                            ) ?: Result.success(Unit)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Result.failure(e)
                        }

                        autoMergeResult.fold(
                            onSuccess = {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        errorMessage = null,
                                        pendingMergeAccountId = null,
                                        hasLocalGuestData = false,
                                        isAuthSuccess = true,
                                        shouldTriggerCloudSync = true
                                    )
                                }
                            },
                            onFailure = { _ ->
                                // Tài khoản đã xác thực thành công: không chặn vào ứng dụng nếu lỗi đồng bộ mạng/Firestore,
                                // đóng hộp thoại và để SyncWorker chạy bù ở nền (Bug M3-1)
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        isMergingGuestData = false,
                                        pendingMergeAccountId = null,
                                        hasLocalGuestData = false,
                                        isAuthSuccess = true,
                                        shouldTriggerCloudSync = true,
                                        errorMessage = null
                                    )
                                }
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = mapErrorToVietnamese(
                                throwable = error,
                                fallback = fallbackError
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Kiểm tra tài khoản [accountId] đã có dữ liệu trên Cloud Firestore hay chưa (Mục 6.2 & 10.1).
     * Nếu không kiểm tra được Firestore (lỗi mạng/quyền) -> trả `false` để coi như chưa có dữ liệu Cloud
     * từ xa, tránh bật nhầm hộp thoại `MergeGuestDialog` làm kẹt người dùng.
     */
    private suspend fun checkHasRemoteCloudData(accountId: String): Boolean {
        val firestoreSync = cloudSyncManager as? FirestoreCloudSyncManager ?: return true
        return try {
            firestoreSync.hasRemoteCloudData(accountId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    private fun refreshLocalGuestDataFlag() {
        viewModelScope.launch {
            val hasGuestData = checkHasLocalGuestDataBeforeAuth()
            _uiState.update { it.copy(hasLocalGuestData = hasGuestData) }
        }
    }

    /**
     * Xác định liệu máy hiện có dữ liệu Khách cục bộ chưa gộp hay không (Mục 6.2 & 10.1).
     */
    private suspend fun checkHasLocalGuestDataBeforeAuth(): Boolean {
        if (hasLocalGuestDataChecker != null) {
            return try {
                hasLocalGuestDataChecker.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false
            }
        }

        val repo = deckRepository ?: return false
        return try {
            val currentUser = authRepository.getCurrentUser()
            val isCurrentlyGuest = currentUser == null || currentUser.isAnonymous
            if (!isCurrentlyGuest) {
                false
            } else {
                repo.observeActiveDecks().first().isNotEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }

    companion object {
        /** Mật khẩu tối thiểu 8 ký tự (Mục 6.2, Mục 11 & Figma 17:475, 17:502). */
        const val MIN_PASSWORD_LENGTH: Int = 8
        const val MAX_DISPLAY_NAME_LENGTH: Int = 50

        const val ERROR_EMPTY_DISPLAY_NAME_VI: String =
            "Vui lòng nhập họ và tên."
        const val ERROR_DISPLAY_NAME_TOO_LONG_VI: String =
            "Họ và tên không được vượt quá 50 ký tự."
        const val ERROR_EMPTY_EMAIL_VI: String =
            "Vui lòng nhập địa chỉ email."
        const val ERROR_INVALID_EMAIL_VI: String =
            "Địa chỉ email không hợp lệ."
        const val ERROR_EMPTY_PASSWORD_VI: String =
            "Vui lòng nhập mật khẩu."
        const val ERROR_SHORT_PASSWORD_VI: String =
            "Mật khẩu phải có ít nhất 8 ký tự."
        const val ERROR_EMPTY_CONFIRM_PASSWORD_VI: String =
            "Vui lòng nhập lại mật khẩu xác nhận."
        const val ERROR_PASSWORD_MISMATCH_VI: String =
            "Mật khẩu xác nhận không khớp."

        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng hoặc đường truyền không ổn định. Vui lòng kiểm tra mạng và thử lại."
        const val ERROR_RATE_LIMIT_VI: String =
            "Bạn đã thử quá nhiều lần (429). Vui lòng đợi một lát rồi thử lại."
        const val ERROR_INVALID_RESPONSE_VI: String =
            "Dữ liệu phản hồi không đúng định dạng. Vui lòng thử lại sau."
        const val ERROR_LOGIN_FAILED_VI: String =
            "Không thể đăng nhập lúc này. Vui lòng thử lại sau."
        const val ERROR_REGISTER_FAILED_VI: String =
            "Không thể tạo tài khoản lúc này. Vui lòng thử lại sau."
        const val ERROR_PASSWORD_RESET_FAILED_VI: String =
            "Không thể gửi email đặt lại mật khẩu lúc này. Vui lòng thử lại sau."
        const val ERROR_GOOGLE_SIGN_IN_FAILED_VI: String =
            "Không thể đăng nhập bằng Google lúc này. Vui lòng thử lại sau."
        const val ERROR_GUEST_SIGN_IN_FAILED_VI: String =
            "Không thể khởi tạo chế độ khách lúc này. Vui lòng thử lại sau."
        const val ERROR_MERGE_GUEST_FAILED_VI: String =
            "Không thể xử lý dữ liệu khách cục bộ lúc này. Vui lòng thử lại."

        private val FALLBACK_EMAIL_REGEX: Regex =
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        /**
         * Kiểm tra định dạng email bằng [Patterns.EMAIL_ADDRESS] trên Android
         * và tự dự phòng bằng regex thuần Kotlin trong môi trường JVM unit test (Mục 11).
         */
        internal fun isValidEmail(email: String): Boolean {
            val trimmed = email.trim()
            if (trimmed.isEmpty()) return false
            val androidPattern = runCatching { Patterns.EMAIL_ADDRESS }.getOrNull()
            return if (androidPattern != null) {
                androidPattern.matcher(trimmed).matches()
            } else {
                FALLBACK_EMAIL_REGEX.matches(trimmed)
            }
        }

        internal fun validateDisplayName(displayName: String): String? {
            val trimmed = displayName.trim()
            return when {
                trimmed.isEmpty() -> ERROR_EMPTY_DISPLAY_NAME_VI
                trimmed.length > MAX_DISPLAY_NAME_LENGTH -> ERROR_DISPLAY_NAME_TOO_LONG_VI
                else -> null
            }
        }

        internal fun validateEmail(email: String): String? {
            val trimmed = email.trim()
            return when {
                trimmed.isEmpty() -> ERROR_EMPTY_EMAIL_VI
                !isValidEmail(trimmed) -> ERROR_INVALID_EMAIL_VI
                else -> null
            }
        }

        internal fun validatePassword(password: String): String? = when {
            password.isEmpty() -> ERROR_EMPTY_PASSWORD_VI
            password.length < MIN_PASSWORD_LENGTH -> ERROR_SHORT_PASSWORD_VI
            else -> null
        }

        internal fun validateConfirmPassword(password: String, confirmPassword: String): String? = when {
            confirmPassword.isEmpty() -> ERROR_EMPTY_CONFIRM_PASSWORD_VI
            password != confirmPassword -> ERROR_PASSWORD_MISMATCH_VI
            else -> null
        }

        /**
         * Phân loại ngoại lệ mạng / 429 / sai định dạng sang thông báo tiếng Việt thân thiện (Mục 6.2 & 9.5).
         */
        internal fun mapErrorToVietnamese(
            throwable: Throwable,
            fallback: String
        ): String {
            val causeChain = generateSequence(throwable) { it.cause }.toList()
            val combinedLower = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            if (causeChain.any { it is JSONException }) {
                return ERROR_INVALID_RESPONSE_VI
            }

            if (
                combinedLower.contains("429") ||
                combinedLower.contains("too many requests") ||
                combinedLower.contains("too_many_requests") ||
                combinedLower.contains("rate limit")
            ) {
                return ERROR_RATE_LIMIT_VI
            }

            if (
                causeChain.any {
                    it is UnknownHostException ||
                        it is SocketTimeoutException ||
                        it is IOException
                } ||
                combinedLower.contains("unable to resolve host") ||
                combinedLower.contains("failed to connect") ||
                combinedLower.contains("network error") ||
                combinedLower.contains("network_request_failed") ||
                combinedLower.contains("timed out")
            ) {
                return ERROR_NETWORK_VI
            }

            val rawMessage = throwable.message?.trim().orEmpty()
            return rawMessage.ifEmpty { fallback }
        }

        /**
         * Khởi tạo [AuthViewModel] qua [memoraViewModelFactory] inject [AuthRepository],
         * [DeckRepository] (kiểm tra dữ liệu Khách) và [CloudSyncManager] (gộp dữ liệu Khách) (Mục 6.2 & 10.1).
         */
        fun factory(
            initialMode: AuthMode = AuthMode.LOGIN
        ): ViewModelProvider.Factory = memoraViewModelFactory { container ->
            AuthViewModel(
                authRepository = container.authRepository,
                deckRepository = container.deckRepository,
                cloudSyncManager = container.cloudSyncManager,
                initialMode = initialMode
            )
        }

        val Factory: ViewModelProvider.Factory = factory()
    }
}
