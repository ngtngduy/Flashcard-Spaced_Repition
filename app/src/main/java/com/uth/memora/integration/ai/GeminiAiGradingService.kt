package com.uth.memora.integration.ai

import com.google.ai.client.generativeai.type.GoogleGenerativeAIException
import com.uth.memora.BuildConfig
import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.model.AiEvaluationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Phân loại lỗi khi gọi Gemini AI chấm câu để hiển thị thông báo tiếng Việt phù hợp
 * và hỗ trợ quyết định thử lại ở WorkManager (Mục 6.7, 6.8, 8.2 & 9.5).
 */
enum class AiGradingErrorType {
    MISSING_API_KEY,
    INVALID_API_KEY,
    RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    INVALID_JSON,
    UNKNOWN
}

/**
 * Hệ thống ngoại lệ chuẩn hóa cho luồng chấm câu AI kèm thông báo tiếng Việt thân thiện (Mục 9.5).
 */
sealed class AiGradingException(
    val type: AiGradingErrorType,
    override val message: String,
    val isRetryable: Boolean,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** Chưa cấu hình khóa API Gemini trong ứng dụng (Mục 9.5). */
    class MissingApiKey(
        message: String = ERROR_MISSING_API_KEY_VI
    ) : AiGradingException(
        type = AiGradingErrorType.MISSING_API_KEY,
        message = message,
        isRetryable = false
    )

    /** Khóa API Gemini không hợp lệ hoặc không có quyền truy cập (HTTP 401/403). */
    class InvalidApiKey(
        message: String = ERROR_INVALID_API_KEY_VI,
        cause: Throwable? = null
    ) : AiGradingException(
        type = AiGradingErrorType.INVALID_API_KEY,
        message = message,
        isRetryable = false,
        cause = cause
    )

    /** Mất kết nối mạng (UnknownHostException / IOException) (Mục 6.8 & 9.5). */
    class NetworkUnavailable(
        message: String = ERROR_NETWORK_VI,
        cause: Throwable? = null
    ) : AiGradingException(
        type = AiGradingErrorType.NETWORK_UNAVAILABLE,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Hết hạn mức hoặc quá tải lượt gọi HTTP 429 (Quota / Rate limit) (Mục 9.5). */
    class RateLimited(
        message: String = ERROR_RATE_LIMIT_VI,
        cause: Throwable? = null
    ) : AiGradingException(
        type = AiGradingErrorType.RATE_LIMITED,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Quá thời gian chờ phản hồi AI (30 giây theo Mục 9.5). */
    class Timeout(
        message: String = ERROR_TIMEOUT_VI,
        cause: Throwable? = null
    ) : AiGradingException(
        type = AiGradingErrorType.TIMEOUT,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Phản hồi AI rỗng, bị chặn hoặc JSON không đúng định dạng (Mục 6.7 & 9.5). */
    class InvalidJsonFormat(
        message: String = ERROR_INVALID_JSON_VI,
        cause: Throwable? = null
    ) : AiGradingException(
        type = AiGradingErrorType.INVALID_JSON,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Lỗi không xác định khác khi chấm câu bằng Gemini AI. */
    class Unknown(
        message: String = ERROR_UNKNOWN_VI,
        cause: Throwable? = null
    ) : AiGradingException(
        type = AiGradingErrorType.UNKNOWN,
        message = message,
        isRetryable = true,
        cause = cause
    )

    companion object {
        const val ERROR_MISSING_API_KEY_VI: String =
            "Chưa cấu hình khóa API Gemini. Vui lòng kiểm tra cấu hình ứng dụng."
        const val ERROR_INVALID_API_KEY_VI: String =
            "Khóa API Gemini không hợp lệ hoặc không có quyền truy cập."
        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng hoặc đường truyền không ổn định. Vui lòng kiểm tra mạng và thử lại."
        const val ERROR_RATE_LIMIT_VI: String =
            "Hệ thống AI đã chạm giới hạn lượt gọi (429). Vui lòng đợi một lát rồi thử lại."
        const val ERROR_TIMEOUT_VI: String =
            "AI phản hồi quá thời gian cho phép (30 giây). Vui lòng thử chấm lại sau."
        const val ERROR_INVALID_JSON_VI: String =
            "Phản hồi từ AI không đúng định dạng JSON chuẩn. Vui lòng thử chấm lại."
        const val ERROR_UNKNOWN_VI: String =
            "Không thể chấm câu bằng AI lúc này. Vui lòng thử lại sau."
    }
}

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: TÍCH HỢP GEMINI AI SDK (Mục 6.7 & 9.5)
 * ============================================================================
 * Gọi `GenerativeModel` từ [GeminiModelProvider] với timeout 30 giây,
 * phân loại rõ lỗi mạng / 429 / timeout / JSON sai định dạng với thông báo tiếng Việt,
 * rồi chuyển chuỗi JSON phản hồi qua [AiJsonParser] thành [AiEvaluationResult].
 */
class GeminiAiGradingService internal constructor(
    private val modelProvider: GeminiModelProvider,
    private val timeoutMillis: Long,
    private val ioDispatcher: CoroutineDispatcher,
    private val generateRawText: suspend (String) -> String?
) : AiGradingService {

    constructor(
        apiKey: String = BuildConfig.GEMINI_API_KEY,
        modelProvider: GeminiModelProvider = GeminiModelProvider(apiKey = apiKey),
        timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        modelProvider = modelProvider,
        timeoutMillis = timeoutMillis,
        ioDispatcher = ioDispatcher,
        generateRawText = { prompt ->
            modelProvider.getModel().generateContent(prompt).text
        }
    )

    override suspend fun evaluateSentence(
        targetWord: String,
        meaningVi: String,
        userSentence: String
    ): Result<AiEvaluationResult> {
        // Kiểm tra API key trước khi tạo kết nối mạng (Mục 9.5)
        if (!modelProvider.hasApiKey()) {
            return Result.failure(AiGradingException.MissingApiKey())
        }

        val prompt = buildGradingPrompt(
            targetWord = targetWord,
            meaningVi = meaningVi,
            userSentence = userSentence
        )

        val rawResponseText = try {
            withContext(ioDispatcher) {
                withTimeout(timeoutMillis) {
                    generateRawText(prompt)
                }
            }
        } catch (e: TimeoutCancellationException) {
            return Result.failure(AiGradingException.Timeout(cause = e))
        } catch (e: CancellationException) {
            // Không nuốt tín hiệu hủy coroutine có chủ đích từ ViewModel/WorkManager
            throw e
        } catch (e: SocketTimeoutException) {
            return Result.failure(AiGradingException.Timeout(cause = e))
        } catch (e: UnknownHostException) {
            return Result.failure(AiGradingException.NetworkUnavailable(cause = e))
        } catch (e: IOException) {
            return Result.failure(mapToGradingException(e))
        } catch (e: AiJsonParseException) {
            return Result.failure(
                AiGradingException.InvalidJsonFormat(
                    message = e.message ?: AiGradingException.ERROR_INVALID_JSON_VI,
                    cause = e
                )
            )
        } catch (e: JSONException) {
            return Result.failure(AiGradingException.InvalidJsonFormat(cause = e))
        } catch (e: GoogleGenerativeAIException) {
            return Result.failure(mapToGradingException(e))
        } catch (e: Exception) {
            return Result.failure(mapToGradingException(e))
        }

        // Chuẩn hóa lỗi parse JSON thành AiGradingException.InvalidJsonFormat (Mục 9.5)
        return AiJsonParser.parseEvaluationResult(rawResponseText).fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    AiGradingException.InvalidJsonFormat(
                        message = error.message ?: AiGradingException.ERROR_INVALID_JSON_VI,
                        cause = error
                    )
                )
            }
        )
    }

    override fun toString(): String =
        "GeminiAiGradingService(modelProvider=$modelProvider, timeoutMillis=$timeoutMillis)"

    companion object {
        /** Thời gian chờ tối đa 30 giây cho mỗi lần chấm câu AI (Mục 9.5). */
        const val REQUEST_TIMEOUT_MILLIS: Long = 30_000L

        fun buildGradingPrompt(
            targetWord: String,
            meaningVi: String,
            userSentence: String
        ): String = GradingPrompt.build(
            targetWord = targetWord,
            meaningVi = meaningVi,
            userSentence = userSentence
        )

        /**
         * Phân loại ngoại lệ phát sinh trong quá trình gọi Gemini AI sang [AiGradingException]
         * kèm thông báo tiếng Việt thân thiện (Mục 9.5).
         */
        internal fun mapToGradingException(throwable: Throwable): AiGradingException {
            if (throwable is AiGradingException) return throwable

            val causeChain = generateSequence(throwable) { it.cause }.toList()
            val className = throwable::class.java.simpleName
            val combinedMessage = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            // 1. Timeout (withTimeout hoặc SocketTimeoutException từ tầng mạng)
            if (
                throwable is TimeoutCancellationException ||
                causeChain.any { it is SocketTimeoutException }
            ) {
                return AiGradingException.Timeout(cause = throwable)
            }

            // 2. Mất mạng / không phân giải được tên miền (UnknownHostException)
            if (causeChain.any { it is UnknownHostException }) {
                return AiGradingException.NetworkUnavailable(cause = throwable)
            }

            // 3. Lỗi phân tích JSON sai định dạng
            if (
                throwable is AiJsonParseException ||
                causeChain.any { it is JSONException }
            ) {
                return AiGradingException.InvalidJsonFormat(
                    message = throwable.message ?: AiGradingException.ERROR_INVALID_JSON_VI,
                    cause = throwable
                )
            }

            // 4. Lỗi hết hạn mức / giới hạn tốc độ gọi HTTP 429 (Quota / Rate limit)
            if (
                className.contains("Quota", ignoreCase = true) ||
                combinedMessage.contains("429") ||
                combinedMessage.contains("resource_exhausted") ||
                combinedMessage.contains("quota") ||
                combinedMessage.contains("rate limit") ||
                combinedMessage.contains("too many requests")
            ) {
                return AiGradingException.RateLimited(cause = throwable)
            }

            // 5. Lỗi khóa API không hợp lệ hoặc không có quyền (401 / 403)
            if (
                className.contains("InvalidAPIKey", ignoreCase = true) ||
                combinedMessage.contains("api key") ||
                combinedMessage.contains("api_key") ||
                combinedMessage.contains("401") ||
                combinedMessage.contains("403") ||
                combinedMessage.contains("unauthenticated") ||
                combinedMessage.contains("permission_denied")
            ) {
                return AiGradingException.InvalidApiKey(cause = throwable)
            }

            // 6. Lỗi mất mạng / gián đoạn đường truyền (IOException hoặc lỗi kết nối tương đương)
            if (
                causeChain.any { it is IOException } ||
                combinedMessage.contains("unable to resolve host") ||
                combinedMessage.contains("no address associated") ||
                combinedMessage.contains("failed to connect") ||
                combinedMessage.contains("connection refused") ||
                combinedMessage.contains("network") ||
                combinedMessage.contains("socket")
            ) {
                return AiGradingException.NetworkUnavailable(cause = throwable)
            }

            // 7. Lỗi timeout được báo qua thông điệp từ SDK
            if (combinedMessage.contains("timed out") || combinedMessage.contains("timeout")) {
                return AiGradingException.Timeout(cause = throwable)
            }

            // 8. Lỗi phản hồi bị chặn hoặc sai cấu trúc dữ liệu từ phía Gemini SDK
            if (
                className.contains("Serialization", ignoreCase = true) ||
                className.contains("ResponseStopped", ignoreCase = true) ||
                className.contains("PromptBlocked", ignoreCase = true) ||
                combinedMessage.contains("json")
            ) {
                return AiGradingException.InvalidJsonFormat(cause = throwable)
            }

            return AiGradingException.Unknown(cause = throwable)
        }
    }
}
