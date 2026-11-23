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
import java.io.IOException

/**
 * Phân loại lỗi khi gọi Gemini AI chấm câu để hiển thị thông báo phù hợp
 * và hỗ trợ quyết định thử lại ở WorkManager (Mục 6.7, 8.2 & 9.5).
 */
enum class AiGradingErrorType {
    MISSING_API_KEY,
    INVALID_API_KEY,
    RATE_LIMITED,
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    INVALID_RESPONSE,
    UNKNOWN
}

/**
 * Ngoại lệ chuẩn hóa cho luồng chấm câu AI kèm thông báo tiếng Việt thân thiện (Mục 9.5).
 */
class AiGradingException(
    val type: AiGradingErrorType,
    override val message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: TÍCH HỢP GEMINI AI SDK (Mục 6.7 & 9.5)
 * ============================================================================
 * Gọi `GenerativeModel` từ [GeminiModelProvider] với timeout 30 giây,
 * sau đó chuyển chuỗi JSON phản hồi qua [AiJsonParser] thành [AiEvaluationResult].
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
            return Result.failure(
                AiGradingException(
                    type = AiGradingErrorType.MISSING_API_KEY,
                    message = "Chưa cấu hình khóa API Gemini. Vui lòng kiểm tra cài đặt ứng dụng."
                )
            )
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
            return Result.failure(
                AiGradingException(
                    type = AiGradingErrorType.TIMEOUT,
                    message = "AI phản hồi quá thời gian cho phép (30 giây). Vui lòng thử chấm lại sau.",
                    cause = e
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleGenerativeAIException) {
            return Result.failure(classifyException(e))
        } catch (e: Exception) {
            return Result.failure(classifyException(e))
        }

        return AiJsonParser.parseEvaluationResult(rawResponseText)
    }

    private fun classifyException(throwable: Throwable): AiGradingException {
        val className = throwable::class.java.simpleName
        val combinedMessage = generateSequence(throwable) { it.cause }
            .mapNotNull { it.message }
            .joinToString(separator = " ")
            .lowercase()

        // Ưu tiên nhận diện lỗi vượt hạn mức / HTTP 429 (Mục 9.5)
        if (
            className.contains("Quota", ignoreCase = true) ||
            combinedMessage.contains("429") ||
            combinedMessage.contains("resource_exhausted") ||
            combinedMessage.contains("quota") ||
            combinedMessage.contains("rate limit") ||
            combinedMessage.contains("too many requests")
        ) {
            return AiGradingException(
                type = AiGradingErrorType.RATE_LIMITED,
                message = "Hệ thống AI đang quá tải hoặc vượt giới hạn lượt gọi (429). Vui lòng thử lại sau ít phút.",
                cause = throwable
            )
        }

        // Lỗi xác thực khóa API (401 / 403)
        if (
            className.contains("InvalidAPIKey", ignoreCase = true) ||
            combinedMessage.contains("api key") ||
            combinedMessage.contains("api_key") ||
            combinedMessage.contains("401") ||
            combinedMessage.contains("403") ||
            combinedMessage.contains("unauthenticated") ||
            combinedMessage.contains("permission_denied")
        ) {
            return AiGradingException(
                type = AiGradingErrorType.INVALID_API_KEY,
                message = "Khóa API Gemini không hợp lệ hoặc không có quyền truy cập.",
                cause = throwable
            )
        }

        // Lỗi mất mạng / đường truyền không ổn định
        val hasNetworkCause = generateSequence(throwable) { it.cause }
            .any { it is IOException }
        if (
            hasNetworkCause ||
            combinedMessage.contains("unable to resolve host") ||
            combinedMessage.contains("no address associated") ||
            combinedMessage.contains("failed to connect") ||
            combinedMessage.contains("network") ||
            combinedMessage.contains("socket") ||
            combinedMessage.contains("timed out")
        ) {
            return AiGradingException(
                type = AiGradingErrorType.NETWORK_UNAVAILABLE,
                message = "Không có kết nối mạng hoặc đường truyền không ổn định. Vui lòng kiểm tra mạng và thử lại.",
                cause = throwable
            )
        }

        // Lỗi dữ liệu trả về hoặc bị chặn từ phía mô hình AI
        if (
            className.contains("Serialization", ignoreCase = true) ||
            className.contains("ResponseStopped", ignoreCase = true) ||
            className.contains("PromptBlocked", ignoreCase = true)
        ) {
            return AiGradingException(
                type = AiGradingErrorType.INVALID_RESPONSE,
                message = "Phản hồi từ AI không hợp lệ hoặc bị gián đoạn. Vui lòng thử chấm lại.",
                cause = throwable
            )
        }

        return AiGradingException(
            type = AiGradingErrorType.UNKNOWN,
            message = "Không thể chấm câu bằng AI lúc này. Vui lòng thử lại sau.",
            cause = throwable
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
    }
}
