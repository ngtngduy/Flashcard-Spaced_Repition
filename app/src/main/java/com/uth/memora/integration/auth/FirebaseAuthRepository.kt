package com.uth.memora.integration.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.model.UserAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.io.IOException

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: XÁC THỰC NGƯỜI DÙNG FIREBASE AUTH (Mục 6.2)
 * ============================================================================
 * Hiện thực [AuthRepository] sử dụng [FirebaseAuth] và `kotlinx-coroutines-play-services` (`.await()`).
 * Luôn chuẩn hóa lỗi mạng, giới hạn tần suất (429) và ẩn thông tin tồn tại tài khoản khi đăng nhập sai.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    /**
     * Quan sát trạng thái đăng nhập theo thời gian thực qua [FirebaseAuth.AuthStateListener] (Mục 6.2).
     */
    override fun observeCurrentUser(): Flow<UserAccount?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toUserAccount())
        }
        trySend(auth.currentUser?.toUserAccount())
        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }.distinctUntilChanged()

    /**
     * Trả về thông tin tài khoản hiện đang đăng nhập, hoặc `null` nếu chưa xác thực (Mục 6.2).
     */
    override suspend fun getCurrentUser(): UserAccount? =
        auth.currentUser?.toUserAccount()

    /**
     * Đăng nhập bằng email và mật khẩu (Mục 6.2).
     * Khi sai mật khẩu hoặc email không tồn tại luôn trả thông báo chung
     * `"Email hoặc mật khẩu không đúng"` để không lộ sự tồn tại tài khoản.
     */
    override suspend fun signInWithEmail(email: String, password: String): Result<UserAccount> {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException(ERROR_EMPTY_SIGN_IN_FIELDS_VI))
        }
        if (!EMAIL_REGEX.matches(cleanEmail)) {
            return Result.failure(IllegalArgumentException(ERROR_INVALID_CREDENTIALS_VI))
        }

        return try {
            val authResult = auth.signInWithEmailAndPassword(cleanEmail, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException(ERROR_SIGN_IN_UNKNOWN_VI))
            Result.success(firebaseUser.toUserAccount())
        } catch (e: CancellationException) {
            // Không nuốt tín hiệu hủy coroutine có chủ đích từ ViewModel
            throw e
        } catch (e: Exception) {
            Result.failure(mapSignInException(e))
        }
    }

    /**
     * Đăng ký tài khoản mới bằng email, mật khẩu (>= 8 ký tự) và lưu tên hiển thị qua
     * [UserProfileChangeRequest] (Mục 6.2).
     */
    override suspend fun registerWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<UserAccount> {
        val cleanEmail = email.trim()
        val cleanDisplayName = displayName.trim()

        if (!EMAIL_REGEX.matches(cleanEmail)) {
            return Result.failure(IllegalArgumentException(ERROR_INVALID_EMAIL_VI))
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            return Result.failure(IllegalArgumentException(ERROR_WEAK_PASSWORD_VI))
        }

        val resolvedDisplayName = cleanDisplayName.ifEmpty { cleanEmail.substringBefore("@") }

        return try {
            val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException(ERROR_REGISTER_UNKNOWN_VI))

            // Cập nhật họ tên hiển thị lên hồ sơ Firebase Auth (Mục 6.2)
            val profileUpdates = UserProfileChangeRequest.Builder()
                .setDisplayName(resolvedDisplayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            val currentUser = auth.currentUser ?: firebaseUser
            Result.success(currentUser.toUserAccount(fallbackDisplayName = resolvedDisplayName))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapRegisterException(e))
        }
    }

    // Các phương thức dưới đây sẽ được hiện thực ở bước tiếp theo của nhánh m3/auth (Mục 6.2)
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> =
        Result.failure(UnsupportedOperationException(ERROR_NOT_IMPLEMENTED_YET_VI))

    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> =
        Result.failure(UnsupportedOperationException(ERROR_NOT_IMPLEMENTED_YET_VI))

    override suspend fun signInAnonymously(): Result<UserAccount> =
        Result.failure(UnsupportedOperationException(ERROR_NOT_IMPLEMENTED_YET_VI))

    override suspend fun signOut(): Result<Unit> =
        Result.failure(UnsupportedOperationException(ERROR_NOT_IMPLEMENTED_YET_VI))

    /**
     * Ánh xạ [FirebaseUser] sang domain model [UserAccount] (Mục 6.2).
     */
    private fun FirebaseUser.toUserAccount(fallbackDisplayName: String? = null): UserAccount {
        val resolvedName = displayName?.takeIf { it.isNotBlank() }
            ?: fallbackDisplayName?.takeIf { it.isNotBlank() }
        return UserAccount(
            uid = uid,
            displayName = resolvedName,
            email = email,
            photoUrl = photoUrl?.toString(),
            isAnonymous = isAnonymous
        )
    }

    companion object {
        /** Mật khẩu tối thiểu 8 ký tự theo yêu cầu nghiệp vụ (Mục 6.2). */
        const val MIN_PASSWORD_LENGTH: Int = 8

        const val ERROR_INVALID_CREDENTIALS_VI: String =
            "Email hoặc mật khẩu không đúng"
        const val ERROR_EMPTY_SIGN_IN_FIELDS_VI: String =
            "Vui lòng nhập đầy đủ email và mật khẩu."
        const val ERROR_INVALID_EMAIL_VI: String =
            "Định dạng email không hợp lệ. Vui lòng kiểm tra lại."
        const val ERROR_WEAK_PASSWORD_VI: String =
            "Mật khẩu phải có từ 8 ký tự trở lên."
        const val ERROR_EMAIL_IN_USE_VI: String =
            "Email này đã được sử dụng cho tài khoản khác."
        const val ERROR_TOO_MANY_REQUESTS_VI: String =
            "Bạn đã thử quá nhiều lần (429). Vui lòng đợi một lát rồi thử lại."
        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng hoặc đường truyền không ổn định. Vui lòng kiểm tra mạng và thử lại."
        const val ERROR_OPERATION_NOT_ALLOWED_VI: String =
            "Phương thức xác thực bằng email hiện chưa được bật trên hệ thống."
        const val ERROR_SIGN_IN_UNKNOWN_VI: String =
            "Không thể đăng nhập lúc này. Vui lòng thử lại sau."
        const val ERROR_REGISTER_UNKNOWN_VI: String =
            "Không thể tạo tài khoản lúc này. Vui lòng thử lại sau."

        private const val ERROR_NOT_IMPLEMENTED_YET_VI: String =
            "Chức năng xác thực này chưa được kích hoạt."

        private val EMAIL_REGEX: Regex =
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        /**
         * Chuẩn hóa lỗi đăng nhập sang thông báo tiếng Việt thân thiện, không tiết lộ email tồn tại (Mục 6.2).
         */
        internal fun mapSignInException(throwable: Throwable): Exception {
            return when (throwable) {
                is FirebaseAuthInvalidCredentialsException,
                is FirebaseAuthInvalidUserException -> {
                    Exception(ERROR_INVALID_CREDENTIALS_VI, throwable)
                }

                is FirebaseTooManyRequestsException -> {
                    Exception(ERROR_TOO_MANY_REQUESTS_VI, throwable)
                }

                is FirebaseNetworkException -> {
                    Exception(ERROR_NETWORK_VI, throwable)
                }

                is FirebaseAuthException -> {
                    when (throwable.errorCode) {
                        "ERROR_INVALID_EMAIL",
                        "ERROR_WRONG_PASSWORD",
                        "ERROR_USER_NOT_FOUND",
                        "ERROR_INVALID_CREDENTIAL",
                        "ERROR_INVALID_LOGIN_CREDENTIALS",
                        "ERROR_USER_MISMATCH" -> Exception(ERROR_INVALID_CREDENTIALS_VI, throwable)

                        "ERROR_TOO_MANY_REQUESTS" -> Exception(ERROR_TOO_MANY_REQUESTS_VI, throwable)
                        "ERROR_NETWORK_REQUEST_FAILED" -> Exception(ERROR_NETWORK_VI, throwable)
                        "ERROR_OPERATION_NOT_ALLOWED" -> Exception(ERROR_OPERATION_NOT_ALLOWED_VI, throwable)
                        else -> mapGenericAuthException(
                            throwable = throwable,
                            treatInvalidLoginAsGenericCredentials = true,
                            fallbackMessage = ERROR_SIGN_IN_UNKNOWN_VI
                        )
                    }
                }

                else -> mapGenericAuthException(
                    throwable = throwable,
                    treatInvalidLoginAsGenericCredentials = true,
                    fallbackMessage = ERROR_SIGN_IN_UNKNOWN_VI
                )
            }
        }

        /**
         * Chuẩn hóa lỗi đăng ký tài khoản sang thông báo tiếng Việt thân thiện (Mục 6.2).
         */
        internal fun mapRegisterException(throwable: Throwable): Exception {
            return when (throwable) {
                is FirebaseAuthUserCollisionException -> {
                    Exception(ERROR_EMAIL_IN_USE_VI, throwable)
                }

                is FirebaseAuthWeakPasswordException -> {
                    Exception(ERROR_WEAK_PASSWORD_VI, throwable)
                }

                is FirebaseAuthInvalidCredentialsException -> {
                    Exception(ERROR_INVALID_EMAIL_VI, throwable)
                }

                is FirebaseTooManyRequestsException -> {
                    Exception(ERROR_TOO_MANY_REQUESTS_VI, throwable)
                }

                is FirebaseNetworkException -> {
                    Exception(ERROR_NETWORK_VI, throwable)
                }

                is FirebaseAuthException -> {
                    when (throwable.errorCode) {
                        "ERROR_EMAIL_ALREADY_IN_USE" -> Exception(ERROR_EMAIL_IN_USE_VI, throwable)
                        "ERROR_WEAK_PASSWORD" -> Exception(ERROR_WEAK_PASSWORD_VI, throwable)
                        "ERROR_INVALID_EMAIL" -> Exception(ERROR_INVALID_EMAIL_VI, throwable)
                        "ERROR_TOO_MANY_REQUESTS" -> Exception(ERROR_TOO_MANY_REQUESTS_VI, throwable)
                        "ERROR_NETWORK_REQUEST_FAILED" -> Exception(ERROR_NETWORK_VI, throwable)
                        "ERROR_OPERATION_NOT_ALLOWED" -> Exception(ERROR_OPERATION_NOT_ALLOWED_VI, throwable)
                        else -> mapGenericAuthException(
                            throwable = throwable,
                            treatInvalidLoginAsGenericCredentials = false,
                            fallbackMessage = ERROR_REGISTER_UNKNOWN_VI
                        )
                    }
                }

                else -> mapGenericAuthException(
                    throwable = throwable,
                    treatInvalidLoginAsGenericCredentials = false,
                    fallbackMessage = ERROR_REGISTER_UNKNOWN_VI
                )
            }
        }

        private fun mapGenericAuthException(
            throwable: Throwable,
            treatInvalidLoginAsGenericCredentials: Boolean,
            fallbackMessage: String
        ): Exception {
            val causeChain = generateSequence(throwable) { it.cause }.toList()
            val combinedMessage = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            if (
                treatInvalidLoginAsGenericCredentials && (
                    combinedMessage.contains("invalid_login_credentials") ||
                        combinedMessage.contains("invalid_credential") ||
                        combinedMessage.contains("wrong_password") ||
                        combinedMessage.contains("user_not_found")
                    )
            ) {
                return Exception(ERROR_INVALID_CREDENTIALS_VI, throwable)
            }

            if (
                combinedMessage.contains("429") ||
                combinedMessage.contains("too_many_requests") ||
                combinedMessage.contains("too many requests") ||
                combinedMessage.contains("quota")
            ) {
                return Exception(ERROR_TOO_MANY_REQUESTS_VI, throwable)
            }

            if (
                causeChain.any { it is IOException } ||
                combinedMessage.contains("network") ||
                combinedMessage.contains("unable to resolve host") ||
                combinedMessage.contains("failed to connect") ||
                combinedMessage.contains("timeout") ||
                combinedMessage.contains("timed out")
            ) {
                return Exception(ERROR_NETWORK_VI, throwable)
            }

            return Exception(fallbackMessage, throwable)
        }
    }
}
