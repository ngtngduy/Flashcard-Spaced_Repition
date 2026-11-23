package com.uth.memora.integration.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.uth.memora.BuildConfig

/**
 * Cung cấp thực thể [GenerativeModel] của Google Generative AI SDK với cấu hình đầu ra JSON chuẩn
 * phục vụ các tác vụ AI của Memora như chấm câu đặt câu (Mục 6.7 & 9.5).
 *
 * Lưu ý bảo mật: API key được nạp qua [BuildConfig.GEMINI_API_KEY] hoặc constructor,
 * không hardcode và tuyệt đối không ghi ra Log.
 */
class GeminiModelProvider(
    apiKey: String = BuildConfig.GEMINI_API_KEY,
    val modelName: String = DEFAULT_MODEL_NAME,
    val temperature: Float = DEFAULT_TEMPERATURE
) {
    private val normalizedApiKey: String = apiKey.trim()

    private val defaultModel: GenerativeModel by lazy {
        createModel()
    }

    /**
     * Kiểm tra khóa API Gemini đã được cấu hình hay chưa trước khi gửi yêu cầu mạng (Mục 9.5).
     */
    fun hasApiKey(): Boolean = normalizedApiKey.isNotBlank()

    /**
     * Tạo cấu hình sinh nội dung ép định dạng JSON và kiểm soát độ sáng tạo ổn định (Mục 6.7).
     */
    fun createGenerationConfig(
        temperature: Float = this.temperature,
        responseMimeType: String = JSON_MIME_TYPE
    ): GenerationConfig = generationConfig {
        this.temperature = temperature
        this.responseMimeType = responseMimeType
    }

    /**
     * Khởi tạo mới một [GenerativeModel] theo tên model và cấu hình sinh nội dung (Mục 6.7 & 9.5).
     */
    fun createModel(
        modelName: String = this.modelName,
        generationConfig: GenerationConfig = createGenerationConfig()
    ): GenerativeModel = GenerativeModel(
        modelName = modelName,
        apiKey = normalizedApiKey,
        generationConfig = generationConfig
    )

    /**
     * Trả về thực thể [GenerativeModel] mặc định dùng chung (khởi tạo lười).
     */
    fun getModel(): GenerativeModel = defaultModel

    override fun toString(): String =
        "GeminiModelProvider(modelName=$modelName, temperature=$temperature, hasApiKey=${hasApiKey()})"

    companion object {
        const val DEFAULT_MODEL_NAME: String = "gemini-2.5-flash"
        const val DEFAULT_TEMPERATURE: Float = 0.3f
        const val JSON_MIME_TYPE: String = "application/json"
    }
}
