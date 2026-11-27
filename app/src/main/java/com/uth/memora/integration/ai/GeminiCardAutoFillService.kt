package com.uth.memora.integration.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GoogleGenerativeAIException
import com.uth.memora.BuildConfig
import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AutoFillCardData
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
 * Phân loại lỗi khi gọi Gemini AI gợi ý tự điền thông tin thẻ học (Mục 6.3, 9.5 & 11).
 */
enum class AiAutoFillErrorType {
    BLANK_WORD,
    MISSING_API_KEY,
    INVALID_API_KEY,
    RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    INVALID_JSON,
    UNKNOWN
}

/**
 * Hệ thống ngoại lệ chuẩn hóa cho luồng gợi ý tự điền thẻ bằng AI kèm thông báo tiếng Việt thân thiện
 * (Mục 6.3, 9.5 & 11).
 */
sealed class AiAutoFillException(
    val type: AiAutoFillErrorType,
    override val message: String,
    val isRetryable: Boolean,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** Người dùng chưa nhập từ tiếng Anh trước khi gọi gợi ý AI (Mục 6.3). */
    class BlankWord(
        message: String = ERROR_BLANK_WORD_VI
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.BLANK_WORD,
        message = message,
        isRetryable = false
    )

    /** Chưa cấu hình khóa API Gemini trong ứng dụng (Mục 9.5). */
    class MissingApiKey(
        message: String = ERROR_MISSING_API_KEY_VI
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.MISSING_API_KEY,
        message = message,
        isRetryable = false
    )

    /** Khóa API Gemini không hợp lệ hoặc không có quyền truy cập (HTTP 401/403). */
    class InvalidApiKey(
        message: String = ERROR_INVALID_API_KEY_VI,
        cause: Throwable? = null
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.INVALID_API_KEY,
        message = message,
        isRetryable = false,
        cause = cause
    )

    /** Mất kết nối mạng hoặc gián đoạn đường truyền (Mục 9.5). */
    class NetworkUnavailable(
        message: String = ERROR_NETWORK_VI,
        cause: Throwable? = null
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.NETWORK_UNAVAILABLE,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Chạm giới hạn lượt gọi / quá tải HTTP 429 (Quota / Rate limit) (Mục 9.5). */
    class RateLimited(
        message: String = ERROR_RATE_LIMIT_VI,
        cause: Throwable? = null
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.RATE_LIMITED,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Quá thời gian chờ phản hồi AI (tối đa 10 giây theo Mục 11). */
    class Timeout(
        message: String = ERROR_TIMEOUT_VI,
        cause: Throwable? = null
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.TIMEOUT,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Phản hồi AI rỗng, bị chặn hoặc JSON không đúng định dạng (Mục 6.3 & 9.5). */
    class InvalidJsonFormat(
        message: String = ERROR_INVALID_JSON_VI,
        cause: Throwable? = null
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.INVALID_JSON,
        message = message,
        isRetryable = true,
        cause = cause
    )

    /** Lỗi không xác định khác khi gọi Gemini AI tự điền thẻ. */
    class Unknown(
        message: String = ERROR_UNKNOWN_VI,
        cause: Throwable? = null
    ) : AiAutoFillException(
        type = AiAutoFillErrorType.UNKNOWN,
        message = message,
        isRetryable = true,
        cause = cause
    )

    companion object {
        const val ERROR_BLANK_WORD_VI: String =
            "Vui lòng nhập một từ tiếng Anh trước khi dùng AI gợi ý nhé."
        const val ERROR_MISSING_API_KEY_VI: String =
            "Tính năng AI gợi ý hiện chưa sẵn sàng. Bạn có thể tự nhập thông tin thẻ nhé."
        const val ERROR_INVALID_API_KEY_VI: String =
            "Không thể kết nối tới trợ lý AI lúc này. Bạn thử lại sau hoặc tự nhập thông tin thẻ nhé."
        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng. Hãy kiểm tra Wi-Fi/4G rồi thử gợi ý lại hoặc tự nhập thẻ nhé."
        const val ERROR_RATE_LIMIT_VI: String =
            "Trợ lý AI đang bận do có nhiều lượt yêu cầu. Bạn đợi một lát rồi thử gợi ý lại hoặc tự nhập thẻ nhé."
        const val ERROR_TIMEOUT_VI: String =
            "AI phản hồi hơi lâu. Bạn kiểm tra mạng rồi thử gợi ý lại hoặc tự nhập thẻ nhé."
        const val ERROR_INVALID_JSON_VI: String =
            "Kết quả gợi ý từ AI chưa đọc được. Bạn thử bấm gợi ý lại hoặc tự nhập thông tin thẻ nhé."
        const val ERROR_UNKNOWN_VI: String =
            "Chưa thể gợi ý thông tin thẻ lúc này. Bạn thử lại sau hoặc tự nhập giúp mình nhé."
    }
}

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: GEMINI AI GỢI Ý TỰ ĐIỀN THẺ HỌC (Mục 6.3, 9.5 & 11)
 * ============================================================================
 * Hiện thực [AiCardAutoFillService] bằng [GeminiModelProvider] với định dạng đầu ra
 * `application/json`, giới hạn thời gian chờ tối đa 10 giây (Mục 11), phân loại lỗi
 * mạng / 429 / timeout / JSON sai định dạng kèm thông báo tiếng Việt thân thiện,
 * rồi phân tích JSON qua [AiJsonParser.parseAutoFillJson] thành [AutoFillCardData].
 */
class GeminiCardAutoFillService internal constructor(
    private val modelProvider: GeminiModelProvider,
    private val timeoutMillis: Long,
    private val ioDispatcher: CoroutineDispatcher,
    private val generateRawText: (suspend (String) -> String?)?
) : AiCardAutoFillService {

    constructor(
        apiKey: String = BuildConfig.GEMINI_API_KEY,
        modelProvider: GeminiModelProvider = GeminiModelProvider(apiKey = apiKey),
        timeoutMillis: Long = REQUEST_TIMEOUT_MILLIS,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        modelProvider = modelProvider,
        timeoutMillis = timeoutMillis,
        ioDispatcher = ioDispatcher,
        generateRawText = null
    )

    private val jsonModel: GenerativeModel by lazy {
        modelProvider.createModel(
            generationConfig = modelProvider.createGenerationConfig(
                responseMimeType = GeminiModelProvider.JSON_MIME_TYPE
            )
        )
    }

    override suspend fun autoFillCard(word: String): Result<AutoFillCardData> {
        // Kiểm tra từ đầu vào và API key trước khi tạo kết nối mạng (Mục 6.3 & 9.5)
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) {
            return Result.failure(AiAutoFillException.BlankWord())
        }
        if (!modelProvider.hasApiKey()) {
            return Result.failure(AiAutoFillException.MissingApiKey())
        }

        val prompt = AutoFillPrompt.build(cleanWord)

        val rawResponseText = try {
            withContext(ioDispatcher) {
                withTimeout(timeoutMillis) {
                    val customGenerator = generateRawText
                    if (customGenerator != null) {
                        customGenerator(prompt)
                    } else {
                        jsonModel.generateContent(prompt).text
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            return Result.failure(mapToAutoFillException(e))
        } catch (e: CancellationException) {
            // Không nuốt tín hiệu hủy coroutine có chủ đích từ ViewModel
            throw e
        } catch (e: SocketTimeoutException) {
            return Result.failure(mapToAutoFillException(e))
        } catch (e: UnknownHostException) {
            return Result.failure(mapToAutoFillException(e))
        } catch (e: IOException) {
            return Result.failure(mapToAutoFillException(e))
        } catch (e: AiJsonParseException) {
            return Result.failure(mapToAutoFillException(e))
        } catch (e: JSONException) {
            return Result.failure(mapToAutoFillException(e))
        } catch (e: GoogleGenerativeAIException) {
            return Result.failure(mapToAutoFillException(e))
        } catch (e: Exception) {
            return Result.failure(mapToAutoFillException(e))
        }

        // Chuẩn hóa lỗi parse JSON sang AiAutoFillException.InvalidJsonFormat (Mục 6.3 & 9.5)
        return AiJsonParser.parseAutoFillJson(
            rawJson = rawResponseText,
            fallbackWord = cleanWord
        ).fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(mapToAutoFillException(error))
            }
        )
    }

    override fun toString(): String =
        "GeminiCardAutoFillService(modelProvider=$modelProvider, timeoutMillis=$timeoutMillis)"

    companion object {
        private const val LOG_TAG: String = "GeminiCardAutoFill"

        /** Thời gian chờ tối đa 10 giây cho gợi ý tự điền thẻ bằng AI (Mục 11). */
        const val REQUEST_TIMEOUT_MILLIS: Long = 10_000L

        /**
         * Phân loại ngoại lệ phát sinh trong quá trình gọi Gemini AI sang [AiAutoFillException]
         * kèm thông báo tiếng Việt thân thiện (Mục 6.3, 9.5 & 11).
         *
         * Bảo mật: không giữ `cause` gốc từ SDK/HTTP để tránh rò rỉ `GEMINI_API_KEY`
         * hay HTTP header qua stacktrace/Logcat; chỉ ghi mã lỗi & tên class khi `BuildConfig.DEBUG`.
         */
        internal fun mapToAutoFillException(throwable: Throwable): AiAutoFillException {
            if (throwable is AiAutoFillException) return throwable

            val causeChain = generateSequence(throwable) { it.cause }.toList()
            val className = throwable::class.java.simpleName.ifBlank { "Throwable" }
            val combinedMessage = causeChain
                .mapNotNull { it.message }
                .joinToString(separator = " ")
                .lowercase()

            val mapped: AiAutoFillException = when {
                // 1. Timeout (withTimeout 10s hoặc SocketTimeoutException từ tầng mạng)
                throwable is TimeoutCancellationException ||
                    causeChain.any { it is SocketTimeoutException } ->
                    AiAutoFillException.Timeout()

                // 2. Mất mạng / không phân giải được tên miền (UnknownHostException)
                causeChain.any { it is UnknownHostException } ->
                    AiAutoFillException.NetworkUnavailable()

                // 3. Lỗi phân tích JSON sai định dạng (chỉ giữ thông điệp tiếng Việt của AiJsonParseException)
                throwable is AiJsonParseException ->
                    AiAutoFillException.InvalidJsonFormat(
                        message = throwable.message ?: AiAutoFillException.ERROR_INVALID_JSON_VI
                    )

                causeChain.any { it is JSONException } ->
                    AiAutoFillException.InvalidJsonFormat()

                // 4. Lỗi hết hạn mức / giới hạn tốc độ gọi HTTP 429 (Quota / Rate limit)
                className.contains("Quota", ignoreCase = true) ||
                    combinedMessage.contains("429") ||
                    combinedMessage.contains("resource_exhausted") ||
                    combinedMessage.contains("quota") ||
                    combinedMessage.contains("rate limit") ||
                    combinedMessage.contains("too many requests") ->
                    AiAutoFillException.RateLimited()

                // 5. Lỗi khóa API không hợp lệ hoặc không có quyền (401 / 403)
                className.contains("InvalidAPIKey", ignoreCase = true) ||
                    combinedMessage.contains("api key") ||
                    combinedMessage.contains("api_key") ||
                    combinedMessage.contains("401") ||
                    combinedMessage.contains("403") ||
                    combinedMessage.contains("unauthenticated") ||
                    combinedMessage.contains("permission_denied") ->
                    AiAutoFillException.InvalidApiKey()

                // 6. Lỗi mất mạng / gián đoạn đường truyền (IOException, SSL, HTTP 5xx hoặc lỗi kết nối tương đương)
                causeChain.any { it is IOException } ||
                    className.contains("UnknownHost", ignoreCase = true) ||
                    className.contains("ConnectException", ignoreCase = true) ||
                    className.contains("SSL", ignoreCase = true) ||
                    combinedMessage.contains("unknownhost") ||
                    combinedMessage.contains("unable to resolve host") ||
                    combinedMessage.contains("no address associated") ||
                    combinedMessage.contains("failed to connect") ||
                    combinedMessage.contains("connection refused") ||
                    combinedMessage.contains("connection reset") ||
                    combinedMessage.contains("broken pipe") ||
                    combinedMessage.contains("service unavailable") ||
                    combinedMessage.contains("bad gateway") ||
                    combinedMessage.contains("http 500") ||
                    combinedMessage.contains("http 502") ||
                    combinedMessage.contains("http 503") ||
                    combinedMessage.contains("http 504") ||
                    combinedMessage.contains("network") ||
                    combinedMessage.contains("socket") ->
                    AiAutoFillException.NetworkUnavailable()

                // 7. Lỗi timeout được báo qua thông điệp từ SDK
                className.contains("Timeout", ignoreCase = true) ||
                    combinedMessage.contains("timed out") ||
                    combinedMessage.contains("timeout") ||
                    combinedMessage.contains("deadline_exceeded") ->
                    AiAutoFillException.Timeout()

                // 8. Lỗi phản hồi bị chặn hoặc sai cấu trúc dữ liệu từ phía Gemini SDK
                className.contains("Json", ignoreCase = true) ||
                    className.contains("Serialization", ignoreCase = true) ||
                    className.contains("ResponseStopped", ignoreCase = true) ||
                    className.contains("PromptBlocked", ignoreCase = true) ||
                    combinedMessage.contains("json") ||
                    combinedMessage.contains("malformed") ||
                    combinedMessage.contains("unexpected token") ||
                    combinedMessage.contains("unexpected end") ->
                    AiAutoFillException.InvalidJsonFormat()

                else -> AiAutoFillException.Unknown()
            }

            logDebugFailure(errorType = mapped.type, causeClassName = className)
            return mapped
        }

        private fun logDebugFailure(
            errorType: AiAutoFillErrorType,
            causeClassName: String
        ) {
            if (!BuildConfig.DEBUG) return
            runCatching {
                Log.w(LOG_TAG, "autoFillCard failed [type=$errorType, class=$causeClassName]")
            }
        }
    }
}
