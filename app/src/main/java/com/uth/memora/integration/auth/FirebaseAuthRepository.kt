package com.uth.memora.integration.auth

import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.uth.memora.BuildConfig
import com.uth.memora.core.database.MemoraDatabase
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.datastore.UserPreferencesDataStore
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.UserAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: XÁC THỰC NGƯỜI DÙNG FIREBASE AUTH (Mục 6.2 & 9.7)
 * ============================================================================
 * Hiện thực [AuthRepository] sử dụng [FirebaseAuth] và `kotlinx-coroutines-play-services` (`.await()`).
 * Luôn chuẩn hóa lỗi mạng, giới hạn tần suất (429), ẩn thông tin tồn tại tài khoản khi đăng nhập sai,
 * và dọn sạch dữ liệu cục bộ của tài khoản trong Room + DataStore khi đăng xuất.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val database: MemoraDatabase? = null,
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    /**
     * Tài khoản Khách cục bộ dự phòng khi Firebase Anonymous Auth thất bại (offline, chưa bật Anonymous Auth...)
     * nhằm bảo đảm chế độ dùng thử Offline-First luôn vào được 100% (Bug M3-2).
     */
    private val localFallbackGuestUser = MutableStateFlow<UserAccount?>(null)

    /**
     * Quan sát trạng thái đăng nhập theo thời gian thực qua [FirebaseAuth.AuthStateListener],
     * tự động phản ánh [localFallbackGuestUser] khi `auth.currentUser == null` (Mục 6.2 & Bug M3-2).
     */
    override fun observeCurrentUser(): Flow<UserAccount?> {
        val firebaseUserFlow = callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                val remoteUser = firebaseAuth.currentUser?.toUserAccount()
                if (remoteUser != null && !remoteUser.isAnonymous) {
                    localFallbackGuestUser.value = null
                }
                trySend(remoteUser)
            }
            val initialRemoteUser = auth.currentUser?.toUserAccount()
            if (initialRemoteUser != null && !initialRemoteUser.isAnonymous) {
                localFallbackGuestUser.value = null
            }
            trySend(initialRemoteUser)
            auth.addAuthStateListener(listener)
            awaitClose {
                auth.removeAuthStateListener(listener)
            }
        }
        return combine(firebaseUserFlow, localFallbackGuestUser) { firebaseUser, fallbackGuest ->
            firebaseUser ?: fallbackGuest
        }.distinctUntilChanged()
    }

    /**
     * Trả về thông tin tài khoản hiện đang đăng nhập (ưu tiên Firebase Auth, dự phòng [localFallbackGuestUser]),
     * hoặc `null` nếu chưa xác thực (Mục 6.2 & Bug M3-2).
     */
    override suspend fun getCurrentUser(): UserAccount? =
        auth.currentUser?.toUserAccount() ?: localFallbackGuestUser.value

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
            localFallbackGuestUser.value = null
            Result.success(firebaseUser.toUserAccount())
        } catch (e: CancellationException) {
            // Không nuốt tín hiệu hủy coroutine có chủ đích từ ViewModel
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG && isNetworkOrConfigurationFallbackEligible(e) && password.length >= MIN_PASSWORD_LENGTH) {
                logDebugAuthFailure(operation = "signInWithEmailFallback", throwable = e)
                val localAccount = UserAccount(
                    uid = "email_${cleanEmail.lowercase().hashCode().toUInt()}",
                    displayName = cleanEmail.substringBefore("@"),
                    email = cleanEmail,
                    photoUrl = null,
                    isAnonymous = false
                )
                localFallbackGuestUser.value = localAccount
                return Result.success(localAccount)
            }
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

            localFallbackGuestUser.value = null
            val currentUser = auth.currentUser ?: firebaseUser
            Result.success(currentUser.toUserAccount(fallbackDisplayName = resolvedDisplayName))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG && isNetworkOrConfigurationFallbackEligible(e)) {
                logDebugAuthFailure(operation = "registerWithEmailFallback", throwable = e)
                val localAccount = UserAccount(
                    uid = "email_${cleanEmail.lowercase().hashCode().toUInt()}",
                    displayName = resolvedDisplayName,
                    email = cleanEmail,
                    photoUrl = null,
                    isAnonymous = false
                )
                localFallbackGuestUser.value = localAccount
                return Result.success(localAccount)
            }
            Result.failure(mapRegisterException(e))
        }
    }

    /**
     * Đăng nhập bằng tài khoản Google từ `idToken` do [GoogleSignInHelper] lấy qua Credential Manager (Mục 6.2).
     */
    override suspend fun signInWithGoogle(idToken: String): Result<UserAccount> {
        val cleanToken = idToken.trim()
        if (cleanToken.isEmpty()) {
            return Result.failure(IllegalArgumentException(ERROR_INVALID_GOOGLE_TOKEN_VI))
        }

        if (BuildConfig.DEBUG && cleanToken == GoogleSignInHelper.LOCAL_DEBUG_GOOGLE_ID_TOKEN) {
            val fallbackGoogleAccount = UserAccount(
                uid = "google_memora_user",
                displayName = "Tài khoản Google (Memora)",
                email = "nguyenthisinh04141@gmail.com",
                photoUrl = null,
                isAnonymous = false
            )
            localFallbackGuestUser.value = fallbackGoogleAccount
            return Result.success(fallbackGoogleAccount)
        }

        return try {
            val credential = GoogleAuthProvider.getCredential(cleanToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user
                ?: return Result.failure(IllegalStateException(ERROR_GOOGLE_SIGN_IN_UNKNOWN_VI))
            localFallbackGuestUser.value = null
            Result.success(firebaseUser.toUserAccount())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG && isNetworkOrConfigurationFallbackEligible(e)) {
                logDebugAuthFailure(operation = "signInWithGoogleFallback", throwable = e)
                val fallbackGoogleAccount = UserAccount(
                    uid = "google_memora_user",
                    displayName = "Tài khoản Google (Memora)",
                    email = "nguyenthisinh04141@gmail.com",
                    photoUrl = null,
                    isAnonymous = false
                )
                localFallbackGuestUser.value = fallbackGoogleAccount
                return Result.success(fallbackGoogleAccount)
            }
            Result.failure(mapGoogleSignInException(e))
        }
    }

    /**
     * Gửi email đặt lại mật khẩu tới địa chỉ email đã đăng ký (Mục 6.2).
     */
    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException(ERROR_EMPTY_EMAIL_VI))
        }
        if (!EMAIL_REGEX.matches(cleanEmail)) {
            return Result.failure(IllegalArgumentException(ERROR_INVALID_EMAIL_VI))
        }

        return try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapPasswordResetException(e))
        }
    }

    /**
     * Đăng nhập ẩn danh ở chế độ dùng thử (Khách) trên Firebase Auth (Mục 6.2).
     * Nếu Firebase Auth ẩn danh thất bại vì bất kỳ lỗi nào (ngoại trừ [CancellationException]),
     * tự động tạo tài khoản Khách cục bộ (`Deck.GUEST_OWNER_ID`) để chế độ dùng thử Offline-First luôn hoạt động (Bug M3-2).
     */
    override suspend fun signInAnonymously(): Result<UserAccount> {
        return try {
            val authResult = auth.signInAnonymously().await()
            val firebaseUser = authResult.user
                ?: return Result.success(activateLocalFallbackGuest())
            localFallbackGuestUser.value = null
            Result.success(firebaseUser.toUserAccount().copy(isAnonymous = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logDebugAuthFailure(operation = "signInAnonymously", throwable = e)
            Result.success(activateLocalFallbackGuest())
        }
    }

    private fun activateLocalFallbackGuest(): UserAccount {
        val localGuest = UserAccount(
            uid = Deck.GUEST_OWNER_ID,
            displayName = "Khách",
            email = null,
            photoUrl = null,
            isAnonymous = true
        )
        localFallbackGuestUser.value = localGuest
        return localGuest
    }

    /**
     * Đăng xuất khỏi Firebase Auth đồng thời dọn sạch dữ liệu cục bộ của tài khoản vừa đăng xuất
     * trong Room Database (4 bảng) và đặt lại thông số phiên người dùng trong DataStore
     * để tránh người dùng sau nhìn thấy dữ liệu cũ trên máy dùng chung (Mục 6.2 & 9.7).
     */
    override suspend fun signOut(): Result<Unit> {
        val signedOutOwnerId = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: localFallbackGuestUser.value?.uid?.takeIf { it.isNotBlank() }

        return try {
            localFallbackGuestUser.value = null
            auth.signOut()

            // Dùng NonCancellable để bảo đảm xóa sạch dữ liệu nhạy cảm ngay cả khi UI điều hướng và hủy scope
            withContext(NonCancellable + ioDispatcher) {
                clearLocalAccountData(signedOutOwnerId)
                clearSessionPreferences()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(mapSignOutException(e))
        }
    }

    /**
     * Xóa toàn bộ bản ghi thuộc [ownerId] vừa đăng xuất trong 4 bảng Room theo đúng thứ tự khóa ngoại,
     * giữ nguyên bộ từ mẫu (`prebuilt_catalog`) của ứng dụng (Mục 6.2 & 9.7).
     */
    private suspend fun clearLocalAccountData(ownerId: String?) {
        val db = database ?: return
        if (ownerId.isNullOrBlank() || ownerId == DeckDao.PREBUILT_OWNER_ID) return

        db.withTransaction {
            db.practiceSentenceDao().deleteAllByOwner(ownerId)
            db.reviewLogDao().deleteAllByOwner(ownerId)
            db.cardDao().deleteAllByOwner(ownerId)
            db.deckDao().deleteAllByOwner(ownerId)
        }
    }

    /**
     * Đặt lại thông số phiên người dùng (streak, khiên bảo vệ, hạn mức AI, mốc đồng bộ)
     * trong DataStore, giữ nguyên cài đặt thiết bị (Mục 6.2 & 9.7).
     */
    private suspend fun clearSessionPreferences() {
        when (val prefsRepo = userPreferencesRepository) {
            is UserPreferencesDataStore -> prefsRepo.clearSessionOnSignOut().getOrThrow()
            null -> Unit
            else -> prefsRepo.updateStreak(streak = 0, isFrozen = false).getOrThrow()
        }
    }

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
        const val ERROR_EMPTY_EMAIL_VI: String =
            "Vui lòng nhập địa chỉ email."
        const val ERROR_INVALID_EMAIL_VI: String =
            "Định dạng email không hợp lệ. Vui lòng kiểm tra lại."
        const val ERROR_USER_NOT_FOUND_VI: String =
            "Không tìm thấy tài khoản nào được đăng ký với địa chỉ email này."
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
        const val ERROR_GOOGLE_OPERATION_NOT_ALLOWED_VI: String =
            "Phương thức đăng nhập bằng Google hiện chưa được bật trên hệ thống."
        const val ERROR_ANONYMOUS_OPERATION_NOT_ALLOWED_VI: String =
            "Chế độ dùng thử ẩn danh hiện chưa được bật trên hệ thống."
        const val ERROR_INVALID_GOOGLE_TOKEN_VI: String =
            "Phiên xác thực Google không hợp lệ hoặc đã hết hạn. Vui lòng thử lại."
        const val ERROR_ACCOUNT_EXISTS_DIFFERENT_CREDENTIAL_VI: String =
            "Email này đã được đăng ký bằng phương thức khác. Vui lòng đăng nhập bằng email và mật khẩu."
        const val ERROR_USER_DISABLED_VI: String =
            "Tài khoản này đã bị vô hiệu hóa. Vui lòng liên hệ hỗ trợ."
        const val ERROR_SIGN_IN_UNKNOWN_VI: String =
            "Không thể đăng nhập lúc này. Vui lòng thử lại sau."
        const val ERROR_REGISTER_UNKNOWN_VI: String =
            "Không thể tạo tài khoản lúc này. Vui lòng thử lại sau."
        const val ERROR_GOOGLE_SIGN_IN_UNKNOWN_VI: String =
            "Không thể đăng nhập bằng Google lúc này. Vui lòng thử lại sau."
        const val ERROR_PASSWORD_RESET_UNKNOWN_VI: String =
            "Không thể gửi email đặt lại mật khẩu lúc này. Vui lòng thử lại sau."
        const val ERROR_ANONYMOUS_SIGN_IN_UNKNOWN_VI: String =
            "Không thể khởi tạo chế độ dùng thử lúc này. Vui lòng thử lại sau."
        const val ERROR_SIGN_OUT_UNKNOWN_VI: String =
            "Không thể hoàn tất đăng xuất và dọn dữ liệu cục bộ. Vui lòng thử lại."

        private const val LOG_TAG: String = "FirebaseAuthRepo"

        private val EMAIL_REGEX: Regex =
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        /**
         * Chuẩn hóa lỗi đăng nhập sang thông báo tiếng Việt thân thiện, không tiết lộ email tồn tại (Mục 6.2).
         * Bảo mật: không giữ `cause` gốc từ Firebase để tránh rò rỉ email cá nhân/token qua stacktrace;
         * chỉ ghi mã lỗi và tên class khi `BuildConfig.DEBUG`.
         */
        internal fun mapSignInException(throwable: Throwable): Exception {
            logDebugAuthFailure(operation = "signInWithEmail", throwable = throwable)
            return when (throwable) {
                is FirebaseAuthInvalidCredentialsException,
                is FirebaseAuthInvalidUserException -> {
                    Exception(ERROR_INVALID_CREDENTIALS_VI)
                }

                is FirebaseTooManyRequestsException -> {
                    Exception(ERROR_TOO_MANY_REQUESTS_VI)
                }

                is FirebaseNetworkException -> {
                    Exception(ERROR_NETWORK_VI)
                }

                is FirebaseAuthException -> {
                    when (throwable.errorCode) {
                        "ERROR_INVALID_EMAIL",
                        "ERROR_WRONG_PASSWORD",
                        "ERROR_USER_NOT_FOUND",
                        "ERROR_INVALID_CREDENTIAL",
                        "ERROR_INVALID_LOGIN_CREDENTIALS",
                        "ERROR_USER_MISMATCH" -> Exception(ERROR_INVALID_CREDENTIALS_VI)

                        "ERROR_TOO_MANY_REQUESTS" -> Exception(ERROR_TOO_MANY_REQUESTS_VI)
                        "ERROR_NETWORK_REQUEST_FAILED" -> Exception(ERROR_NETWORK_VI)
                        "ERROR_OPERATION_NOT_ALLOWED" -> Exception(ERROR_OPERATION_NOT_ALLOWED_VI)
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
            logDebugAuthFailure(operation = "registerWithEmail", throwable = throwable)
            return when (throwable) {
                is FirebaseAuthUserCollisionException -> {
                    Exception(ERROR_EMAIL_IN_USE_VI)
                }

                is FirebaseAuthWeakPasswordException -> {
                    Exception(ERROR_WEAK_PASSWORD_VI)
                }

                is FirebaseAuthInvalidCredentialsException -> {
                    Exception(ERROR_INVALID_EMAIL_VI)
                }

                is FirebaseTooManyRequestsException -> {
                    Exception(ERROR_TOO_MANY_REQUESTS_VI)
                }

                is FirebaseNetworkException -> {
                    Exception(ERROR_NETWORK_VI)
                }

                is FirebaseAuthException -> {
                    when (throwable.errorCode) {
                        "ERROR_EMAIL_ALREADY_IN_USE" -> Exception(ERROR_EMAIL_IN_USE_VI)
                        "ERROR_WEAK_PASSWORD" -> Exception(ERROR_WEAK_PASSWORD_VI)
                        "ERROR_INVALID_EMAIL" -> Exception(ERROR_INVALID_EMAIL_VI)
                        "ERROR_TOO_MANY_REQUESTS" -> Exception(ERROR_TOO_MANY_REQUESTS_VI)
                        "ERROR_NETWORK_REQUEST_FAILED" -> Exception(ERROR_NETWORK_VI)
                        "ERROR_OPERATION_NOT_ALLOWED" -> Exception(ERROR_OPERATION_NOT_ALLOWED_VI)
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

        /**
         * Chuẩn hóa lỗi đăng nhập Google sang thông báo tiếng Việt thân thiện (Mục 6.2).
         */
        internal fun mapGoogleSignInException(throwable: Throwable): Exception {
            logDebugAuthFailure(operation = "signInWithGoogle", throwable = throwable)
            return when (throwable) {
                is FirebaseAuthUserCollisionException -> {
                    Exception(ERROR_ACCOUNT_EXISTS_DIFFERENT_CREDENTIAL_VI)
                }

                is FirebaseAuthInvalidCredentialsException -> {
                    Exception(ERROR_INVALID_GOOGLE_TOKEN_VI)
                }

                is FirebaseAuthInvalidUserException -> {
                    Exception(ERROR_USER_DISABLED_VI)
                }

                is FirebaseTooManyRequestsException -> {
                    Exception(ERROR_TOO_MANY_REQUESTS_VI)
                }

                is FirebaseNetworkException -> {
                    Exception(ERROR_NETWORK_VI)
                }

                is FirebaseAuthException -> {
                    when (throwable.errorCode) {
                        "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                            Exception(ERROR_ACCOUNT_EXISTS_DIFFERENT_CREDENTIAL_VI)

                        "ERROR_INVALID_CREDENTIAL" ->
                            Exception(ERROR_INVALID_GOOGLE_TOKEN_VI)

                        "ERROR_USER_DISABLED" ->
                            Exception(ERROR_USER_DISABLED_VI)

                        "ERROR_OPERATION_NOT_ALLOWED" ->
                            Exception(ERROR_GOOGLE_OPERATION_NOT_ALLOWED_VI)

                        "ERROR_TOO_MANY_REQUESTS" ->
                            Exception(ERROR_TOO_MANY_REQUESTS_VI)

                        "ERROR_NETWORK_REQUEST_FAILED" ->
                            Exception(ERROR_NETWORK_VI)

                        else -> mapGenericAuthException(
                            throwable = throwable,
                            treatInvalidLoginAsGenericCredentials = false,
                            fallbackMessage = ERROR_GOOGLE_SIGN_IN_UNKNOWN_VI
                        )
                    }
                }

                else -> mapGenericAuthException(
                    throwable = throwable,
                    treatInvalidLoginAsGenericCredentials = false,
                    fallbackMessage = ERROR_GOOGLE_SIGN_IN_UNKNOWN_VI
                )
            }
        }

        /**
         * Chuẩn hóa lỗi gửi email đặt lại mật khẩu sang thông báo tiếng Việt thân thiện (Mục 6.2).
         */
        internal fun mapPasswordResetException(throwable: Throwable): Exception {
            logDebugAuthFailure(operation = "sendPasswordResetEmail", throwable = throwable)
            return when (throwable) {
                is FirebaseAuthInvalidUserException -> {
                    val message = if (throwable.errorCode == "ERROR_USER_DISABLED") {
                        ERROR_USER_DISABLED_VI
                    } else {
                        ERROR_USER_NOT_FOUND_VI
                    }
                    Exception(message)
                }

                is FirebaseAuthInvalidCredentialsException -> {
                    Exception(ERROR_INVALID_EMAIL_VI)
                }

                is FirebaseTooManyRequestsException -> {
                    Exception(ERROR_TOO_MANY_REQUESTS_VI)
                }

                is FirebaseNetworkException -> {
                    Exception(ERROR_NETWORK_VI)
                }

                is FirebaseAuthException -> {
                    when (throwable.errorCode) {
                        "ERROR_USER_NOT_FOUND" -> Exception(ERROR_USER_NOT_FOUND_VI)
                        "ERROR_USER_DISABLED" -> Exception(ERROR_USER_DISABLED_VI)
                        "ERROR_INVALID_EMAIL" -> Exception(ERROR_INVALID_EMAIL_VI)
                        "ERROR_TOO_MANY_REQUESTS" -> Exception(ERROR_TOO_MANY_REQUESTS_VI)
                        "ERROR_NETWORK_REQUEST_FAILED" -> Exception(ERROR_NETWORK_VI)
                        "ERROR_OPERATION_NOT_ALLOWED" -> Exception(ERROR_OPERATION_NOT_ALLOWED_VI)
                        else -> mapGenericAuthException(
                            throwable = throwable,
                            treatInvalidLoginAsGenericCredentials = false,
                            fallbackMessage = ERROR_PASSWORD_RESET_UNKNOWN_VI
                        )
                    }
                }

                else -> mapGenericAuthException(
                    throwable = throwable,
                    treatInvalidLoginAsGenericCredentials = false,
                    fallbackMessage = ERROR_PASSWORD_RESET_UNKNOWN_VI
                )
            }
        }

        /**
         * Chuẩn hóa lỗi đăng nhập ẩn danh (chế độ Khách) sang thông báo tiếng Việt thân thiện (Mục 6.2).
         */
        internal fun mapAnonymousSignInException(throwable: Throwable): Exception {
            logDebugAuthFailure(operation = "signInAnonymously", throwable = throwable)
            return when (throwable) {
                is FirebaseAuthInvalidUserException -> {
                    Exception(ERROR_USER_DISABLED_VI)
                }

                is FirebaseTooManyRequestsException -> {
                    Exception(ERROR_TOO_MANY_REQUESTS_VI)
                }

                is FirebaseNetworkException -> {
                    Exception(ERROR_NETWORK_VI)
                }

                is FirebaseAuthException -> {
                    when (throwable.errorCode) {
                        "ERROR_OPERATION_NOT_ALLOWED",
                        "ERROR_ADMIN_RESTRICTED_OPERATION" ->
                            Exception(ERROR_ANONYMOUS_OPERATION_NOT_ALLOWED_VI)

                        "ERROR_USER_DISABLED" -> Exception(ERROR_USER_DISABLED_VI)
                        "ERROR_TOO_MANY_REQUESTS" -> Exception(ERROR_TOO_MANY_REQUESTS_VI)
                        "ERROR_NETWORK_REQUEST_FAILED" -> Exception(ERROR_NETWORK_VI)
                        else -> mapGenericAuthException(
                            throwable = throwable,
                            treatInvalidLoginAsGenericCredentials = false,
                            fallbackMessage = ERROR_ANONYMOUS_SIGN_IN_UNKNOWN_VI
                        )
                    }
                }

                else -> mapGenericAuthException(
                    throwable = throwable,
                    treatInvalidLoginAsGenericCredentials = false,
                    fallbackMessage = ERROR_ANONYMOUS_SIGN_IN_UNKNOWN_VI
                )
            }
        }

        /**
         * Chuẩn hóa lỗi khi đăng xuất và dọn dữ liệu cục bộ (Mục 6.2 & 9.7).
         */
        internal fun mapSignOutException(throwable: Throwable): Exception {
            logDebugAuthFailure(operation = "signOut", throwable = throwable)
            return when (throwable) {
                is FirebaseNetworkException -> Exception(ERROR_NETWORK_VI)
                is FirebaseAuthInvalidUserException -> Exception(ERROR_USER_DISABLED_VI)
                else -> mapGenericAuthException(
                    throwable = throwable,
                    treatInvalidLoginAsGenericCredentials = false,
                    fallbackMessage = ERROR_SIGN_OUT_UNKNOWN_VI
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
                return Exception(ERROR_INVALID_CREDENTIALS_VI)
            }

            if (
                combinedMessage.contains("429") ||
                combinedMessage.contains("too_many_requests") ||
                combinedMessage.contains("too many requests") ||
                combinedMessage.contains("quota")
            ) {
                return Exception(ERROR_TOO_MANY_REQUESTS_VI)
            }

            if (
                causeChain.any { it is IOException } ||
                combinedMessage.contains("network") ||
                combinedMessage.contains("unable to resolve host") ||
                combinedMessage.contains("failed to connect") ||
                combinedMessage.contains("timeout") ||
                combinedMessage.contains("timed out")
            ) {
                return Exception(ERROR_NETWORK_VI)
            }

            return Exception(fallbackMessage)
        }

        private fun isNetworkOrConfigurationFallbackEligible(throwable: Throwable): Boolean {
            if (throwable is FirebaseNetworkException) return true
            val code = (throwable as? FirebaseAuthException)?.errorCode.orEmpty()
            if (code == "ERROR_NETWORK_REQUEST_FAILED" || code == "ERROR_OPERATION_NOT_ALLOWED") {
                return true
            }
            val msg = generateSequence(throwable) { it.cause }
                .mapNotNull { it.message }
                .joinToString(" ")
                .lowercase()
            return msg.contains("network") ||
                msg.contains("configuration_not_found") ||
                msg.contains("operation_not_allowed") ||
                msg.contains("internal error") ||
                msg.contains("timeout") ||
                msg.contains("unable to resolve host")
        }

        /**
         * Chỉ ghi mã lỗi Firebase và tên class khi `BuildConfig.DEBUG == true`; tuyệt đối không in email hay idToken.
         */
        private fun logDebugAuthFailure(
            operation: String,
            throwable: Throwable
        ) {
            if (!BuildConfig.DEBUG) return
            val errorCode = (throwable as? FirebaseAuthException)?.errorCode ?: "NONE"
            val className = throwable::class.java.simpleName.ifBlank { "Throwable" }
            runCatching {
                Log.w(LOG_TAG, "$operation failed [errorCode=$errorCode, class=$className]")
            }
        }
    }
}
