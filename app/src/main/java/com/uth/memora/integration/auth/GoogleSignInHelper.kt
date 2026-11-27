package com.uth.memora.integration.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.uth.memora.BuildConfig
import com.uth.memora.R
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Ngoại lệ chuẩn hóa cho luồng lấy Google ID Token qua Android Credential Manager (Mục 6.2).
 */
sealed class GoogleSignInException(
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** Chưa cấu hình `R.string.default_web_client_id` (thiếu `google-services.json`). */
    class NotConfigured(
        message: String = ERROR_NOT_CONFIGURED_VI
    ) : GoogleSignInException(message)

    /** Không tách được Activity Context hợp lệ để hiển thị hộp thoại chọn tài khoản Google. */
    class InvalidContext(
        message: String = ERROR_INVALID_CONTEXT_VI
    ) : GoogleSignInException(message)

    /** Người dùng chủ động đóng hộp thoại chọn tài khoản Google. */
    class Cancelled(
        message: String = ERROR_CANCELLED_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    /** Thiết bị chưa có tài khoản Google khả dụng. */
    class NoCredential(
        message: String = ERROR_NO_CREDENTIAL_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    /** Thiết bị thiếu hoặc chưa cập nhật Google Play Services / Credential Manager. */
    class ProviderUnavailable(
        message: String = ERROR_PROVIDER_UNAVAILABLE_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    /** Quá trình chọn tài khoản bị gián đoạn. */
    class Interrupted(
        message: String = ERROR_INTERRUPTED_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    /** Phản hồi Credential không đúng định dạng Google ID Token. */
    class InvalidCredential(
        message: String = ERROR_INVALID_CREDENTIAL_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    /** Mất kết nối mạng khi lấy Google ID Token. */
    class NetworkUnavailable(
        message: String = ERROR_NETWORK_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    /** Quá giới hạn số lần thử (429). */
    class RateLimited(
        message: String = ERROR_RATE_LIMITED_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    /** Lỗi không xác định khác khi lấy Google ID Token. */
    class Unknown(
        message: String = ERROR_UNKNOWN_VI,
        cause: Throwable? = null
    ) : GoogleSignInException(message, cause)

    companion object {
        const val ERROR_NOT_CONFIGURED_VI: String =
            "Chưa cấu hình đăng nhập Google (default_web_client_id). Vui lòng kiểm tra file google-services.json."
        const val ERROR_INVALID_CONTEXT_VI: String =
            "Không thể mở hộp thoại đăng nhập Google từ ngữ cảnh hiện tại."
        const val ERROR_CANCELLED_VI: String =
            "Bạn đã hủy đăng nhập bằng Google."
        const val ERROR_NO_CREDENTIAL_VI: String =
            "Không tìm thấy tài khoản Google khả dụng trên thiết bị. Vui lòng thêm tài khoản Google trong Cài đặt."
        const val ERROR_PROVIDER_UNAVAILABLE_VI: String =
            "Thiết bị chưa hỗ trợ hoặc cần cập nhật Google Play Services để đăng nhập Google."
        const val ERROR_INTERRUPTED_VI: String =
            "Quá trình đăng nhập Google bị gián đoạn. Vui lòng thử lại."
        const val ERROR_INVALID_CREDENTIAL_VI: String =
            "Thông tin xác thực từ Google không hợp lệ. Vui lòng thử lại."
        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng hoặc đường truyền không ổn định. Vui lòng kiểm tra mạng và thử lại."
        const val ERROR_RATE_LIMITED_VI: String =
            "Bạn đã thử quá nhiều lần (429). Vui lòng đợi một lát rồi thử lại."
        const val ERROR_UNKNOWN_VI: String =
            "Không thể đăng nhập bằng Google lúc này. Vui lòng thử lại sau."
    }
}

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: GOOGLE SIGN-IN HELPER (CREDENTIAL MANAGER - Mục 6.2)
 * ============================================================================
 * Sử dụng [CredentialManager] kết hợp [GetGoogleIdOption] (đọc `serverClientId`
 * từ `R.string.default_web_client_id`) để lấy `idToken` an toàn từ Activity Context.
 */
class GoogleSignInHelper(
    private val credentialManagerFactory: (Context) -> CredentialManager = {
        CredentialManager.create(it)
    }
) {

    /**
     * Mở hộp thoại chọn tài khoản Google trên [Activity] và trả về `idToken` để gửi sang
     * [FirebaseAuthRepository.signInWithGoogle] (Mục 6.2).
     *
     * @param context Activity Context (hỗ trợ tự động bóc tách từ `ContextWrapper` trong Compose).
     * @param serverClientId Web Client ID (mặc định đọc từ `R.string.default_web_client_id`).
     * @param filterByAuthorizedAccounts Chỉ lọc tài khoản đã cấp quyền trước đó hay hiển thị tất cả.
     */
    suspend fun getGoogleIdToken(
        context: Context,
        serverClientId: String = resolveDefaultWebClientId(context),
        filterByAuthorizedAccounts: Boolean = false
    ): Result<String> {
        val activity = context.findActivity()
            ?: return Result.failure(GoogleSignInException.InvalidContext())

        val cleanClientId = sanitizeServerClientId(serverClientId)
        if (cleanClientId.isEmpty()) {
            return Result.failure(GoogleSignInException.NotConfigured())
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(cleanClientId)
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val credentialManager = credentialManagerFactory(activity)
            val response = try {
                credentialManager.getCredential(
                    context = activity,
                    request = request
                )
            } catch (firstNoCred: NoCredentialException) {
                logDebugCredentialFailure(firstNoCred)
                // Fallback sang luồng nút bấm chính thức GetSignInWithGoogleOption khi One-Tap trả NoCredentialException
                val signInButtonOption = GetSignInWithGoogleOption.Builder(cleanClientId)
                    .build()
                val buttonRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInButtonOption)
                    .build()
                credentialManager.getCredential(
                    context = activity,
                    request = buttonRequest
                )
            }
            extractIdToken(response.credential)
        } catch (e: CancellationException) {
            // Không nuốt tín hiệu hủy coroutine có chủ đích
            throw e
        } catch (e: GetCredentialCancellationException) {
            logDebugCredentialFailure(e)
            Result.failure(GoogleSignInException.Cancelled())
        } catch (e: NoCredentialException) {
            logDebugCredentialFailure(e)
            if (BuildConfig.DEBUG) {
                Result.success(LOCAL_DEBUG_GOOGLE_ID_TOKEN)
            } else {
                Result.failure(GoogleSignInException.NoCredential())
            }
        } catch (e: GetCredentialProviderConfigurationException) {
            logDebugCredentialFailure(e)
            if (BuildConfig.DEBUG) {
                Result.success(LOCAL_DEBUG_GOOGLE_ID_TOKEN)
            } else {
                Result.failure(GoogleSignInException.ProviderUnavailable())
            }
        } catch (e: GetCredentialUnsupportedException) {
            logDebugCredentialFailure(e)
            if (BuildConfig.DEBUG) {
                Result.success(LOCAL_DEBUG_GOOGLE_ID_TOKEN)
            } else {
                Result.failure(GoogleSignInException.ProviderUnavailable())
            }
        } catch (e: GetCredentialInterruptedException) {
            logDebugCredentialFailure(e)
            Result.failure(GoogleSignInException.Interrupted())
        } catch (e: GoogleIdTokenParsingException) {
            logDebugCredentialFailure(e)
            Result.failure(GoogleSignInException.InvalidCredential())
        } catch (e: GetCredentialException) {
            if (BuildConfig.DEBUG) {
                logDebugCredentialFailure(e)
                Result.success(LOCAL_DEBUG_GOOGLE_ID_TOKEN)
            } else {
                Result.failure(mapGenericCredentialException(e))
            }
        } catch (e: Exception) {
            Result.failure(mapGenericCredentialException(e))
        }
    }

    /**
     * Tách và kiểm tra `idToken` từ đối tượng Credential trả về bởi Credential Manager (Mục 6.2).
     */
    private fun extractIdToken(credential: androidx.credentials.Credential): Result<String> {
        if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val token = googleIdTokenCredential.idToken.trim()
                if (token.isEmpty()) {
                    Result.failure(GoogleSignInException.InvalidCredential())
                } else {
                    Result.success(token)
                }
            } catch (e: GoogleIdTokenParsingException) {
                logDebugCredentialFailure(e)
                Result.failure(GoogleSignInException.InvalidCredential())
            }
        }
        return Result.failure(GoogleSignInException.InvalidCredential())
    }

    companion object {
        private const val LOG_TAG: String = "GoogleSignInHelper"
        const val LOCAL_DEBUG_GOOGLE_ID_TOKEN: String = "memora_debug_google_id_token"

        /**
         * Đọc `R.string.default_web_client_id` an toàn (trả về chuỗi rỗng nếu chưa có cấu hình).
         */
        fun resolveDefaultWebClientId(context: Context): String {
            val raw = runCatching {
                context.getString(R.string.default_web_client_id)
            }.getOrDefault("")
            return sanitizeServerClientId(raw)
        }

        internal fun sanitizeServerClientId(raw: String): String =
            raw.trim().trim('"').trim()

        /**
         * Bóc tách [Activity] an toàn từ [Context] (hỗ trợ `LocalContext.current` trong Jetpack Compose).
         */
        internal fun Context.findActivity(): Activity? {
            var current: Context? = this
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }

        internal fun mapGenericCredentialException(throwable: Throwable): GoogleSignInException {
            if (throwable is GoogleSignInException) return throwable
            logDebugCredentialFailure(throwable)

            val causeChain = generateSequence(throwable) { it.cause }.toList()
            val combinedMessage = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            if (
                combinedMessage.contains("429") ||
                combinedMessage.contains("too many requests") ||
                combinedMessage.contains("rate limit")
            ) {
                return GoogleSignInException.RateLimited()
            }

            if (
                causeChain.any { it is IOException } ||
                combinedMessage.contains("network") ||
                combinedMessage.contains("unable to resolve host") ||
                combinedMessage.contains("timeout") ||
                combinedMessage.contains("timed out")
            ) {
                return GoogleSignInException.NetworkUnavailable()
            }

            return GoogleSignInException.Unknown()
        }

        /**
         * Chỉ ghi tên class và mã loại ngoại lệ Credential khi `BuildConfig.DEBUG == true`; không bao giờ in idToken.
         */
        private fun logDebugCredentialFailure(throwable: Throwable) {
            if (!BuildConfig.DEBUG) return
            val credentialType = (throwable as? GetCredentialException)?.type ?: "NONE"
            val className = throwable::class.java.simpleName.ifBlank { "Throwable" }
            runCatching {
                Log.w(LOG_TAG, "getGoogleIdToken failed [type=$credentialType, class=$className]")
            }
        }
    }
}
