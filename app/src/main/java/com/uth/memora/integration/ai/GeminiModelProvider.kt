package com.uth.memora.integration.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.generationConfig
import com.uth.memora.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Cung cấp thực thể [GenerativeModel] và bộ gọi REST JSON trực tiếp tới Gemini API (`gemini-3.5-flash` / `gemini-3.8-flash`)
 * với cấu hình đầu ra JSON chuẩn phục vụ các tác vụ AI của Memora như chấm câu đặt câu và gợi ý tự điền thẻ (Mục 6.3, 6.7 & 9.5).
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

    /**
     * Gọi trực tiếp endpoint `v1beta/models/{model}:generateContent` của Google Gemini với định dạng
     * `application/json`, hỗ trợ tự động chuyển đổi giữa các model Gemini 3.5/3.7/3.8 Flash khi một model
     * đang quá tải (503) hoặc ngừng hỗ trợ (404), đồng thời tương thích đầy đủ với trường `thoughtSignature`
     * mới của Gemini 3.x và hỗ trợ vượt lỗi kẹt DNS `10.0.2.3` trên máy ảo Android.
     */
    fun generateJsonContent(prompt: String): String? {
        val candidateModels = listOf(
            modelName,
            "gemini-3.5-flash",
            "gemini-3.7-flash",
            "gemini-3.6-flash",
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite"
        ).distinct()

        val payloadBytes = buildRequestJson(prompt).toByteArray(Charsets.UTF_8)
        var lastException: Exception? = null

        for (candidate in candidateModels) {
            try {
                return executeGenerateContentHttp(
                    candidateModel = candidate,
                    payloadBytes = payloadBytes
                )
            } catch (e: RetryableModelException) {
                lastException = e
                continue
            } catch (e: Exception) {
                lastException = e
                break
            }
        }

        throw (lastException ?: IOException("Unable to generate content from Gemini API"))
    }

    private fun buildRequestJson(prompt: String): String {
        val partObj = JSONObject().put("text", prompt)
        val partsArr = JSONArray().put(partObj)
        val contentObj = JSONObject().put("parts", partsArr)
        val contentsArr = JSONArray().put(contentObj)

        val genConfigObj = JSONObject()
            .put("responseMimeType", JSON_MIME_TYPE)
            .put("temperature", temperature.toDouble())

        return JSONObject()
            .put("contents", contentsArr)
            .put("generationConfig", genConfigObj)
            .toString()
    }

    private fun executeGenerateContentHttp(
        candidateModel: String,
        payloadBytes: ByteArray
    ): String {
        val endpointUrl =
            "https://$GEMINI_HOST/v1beta/models/$candidateModel:generateContent?key=$normalizedApiKey"

        return try {
            performHttpsPost(
                url = URL(endpointUrl),
                customSslFactory = null,
                payloadBytes = payloadBytes
            )
        } catch (dnsErr: UnknownHostException) {
            // Khi máy ảo Android bị kẹt DNS 10.0.2.3 nhưng mạng IP vẫn thông, kết nối TLS trực tiếp
            // qua IP máy chủ Google với SNI & xác thực chứng chỉ chuẩn cho generativelanguage.googleapis.com
            var lastIpError: Exception = dnsErr
            for (fallbackIp in FALLBACK_GOOGLE_IPS) {
                try {
                    return performHttpsPost(
                        url = URL(endpointUrl),
                        customSslFactory = DirectIpSniSslSocketFactory(
                            targetHost = GEMINI_HOST,
                            targetIp = fallbackIp
                        ),
                        payloadBytes = payloadBytes
                    )
                } catch (ipErr: RetryableModelException) {
                    throw ipErr
                } catch (ipErr: Exception) {
                    lastIpError = ipErr
                }
            }
            throw lastIpError
        }
    }

    private fun performHttpsPost(
        url: URL,
        customSslFactory: SSLSocketFactory?,
        payloadBytes: ByteArray
    ): String {
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Host", GEMINI_HOST)
            if (customSslFactory != null) {
                sslSocketFactory = customSslFactory
            }
        }

        try {
            conn.outputStream.use { out ->
                out.write(payloadBytes)
                out.flush()
            }

            val code = conn.responseCode
            val bodyText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (code in 200..299) {
                return extractCandidateText(bodyText)
            }

            if (code == 404 || code == 503) {
                throw RetryableModelException("HTTP $code: $bodyText")
            }

            throw IOException("HTTP $code: $bodyText")
        } finally {
            conn.disconnect()
        }
    }

    private fun extractCandidateText(rawResponseBody: String): String {
        val root = JSONObject(rawResponseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IOException("Empty candidates from Gemini response")
        if (candidates.length() == 0) {
            throw IOException("No candidate returned from Gemini response")
        }
        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
            ?: throw IOException("Missing content in Gemini candidate")
        val parts = content.optJSONArray("parts")
            ?: throw IOException("Missing parts in Gemini content")

        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val text = part.optString("text", "").trim()
            if (text.isNotEmpty()) {
                return text
            }
        }
        throw IOException("Empty text part in Gemini candidate")
    }

    private class RetryableModelException(message: String) : IOException(message)

    /**
     * [SSLSocketFactory] mở kết nối TCP trực tiếp tới [targetIp] khi DNS cục bộ của máy ảo bị treo,
     * sau đó bọc TLS với SNI [targetHost] để kiểm tra chứng chỉ SSL chính chủ của Google.
     */
    private class DirectIpSniSslSocketFactory(
        private val targetHost: String,
        private val targetIp: String
    ) : SSLSocketFactory() {
        private val delegate: SSLSocketFactory = getDefault() as SSLSocketFactory

        override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

        private fun connectViaTargetIp(port: Int): Socket {
            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(targetIp, port), 8_000)
            val sslSocket = delegate.createSocket(rawSocket, targetHost, port, true) as SSLSocket
            val params = sslSocket.sslParameters
            params.serverNames = listOf(SNIHostName(targetHost))
            sslSocket.sslParameters = params
            return sslSocket
        }

        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
            delegate.createSocket(s, targetHost, port, autoClose)

        override fun createSocket(host: String?, port: Int): Socket =
            connectViaTargetIp(port)

        override fun createSocket(
            host: String?,
            port: Int,
            localHost: InetAddress?,
            localPort: Int
        ): Socket = connectViaTargetIp(port)

        override fun createSocket(host: InetAddress?, port: Int): Socket =
            connectViaTargetIp(port)

        override fun createSocket(
            address: InetAddress?,
            port: Int,
            localAddress: InetAddress?,
            localPort: Int
        ): Socket = connectViaTargetIp(port)
    }

    override fun toString(): String =
        "GeminiModelProvider(modelName=$modelName, temperature=$temperature, hasApiKey=${hasApiKey()})"

    companion object {
        const val DEFAULT_MODEL_NAME: String = "gemini-3.5-flash"
        const val DEFAULT_TEMPERATURE: Float = 0.3f
        const val JSON_MIME_TYPE: String = "application/json"
        private const val GEMINI_HOST: String = "generativelanguage.googleapis.com"
        private val FALLBACK_GOOGLE_IPS: List<String> = listOf(
            "172.217.119.4",
            "142.250.199.74",
            "142.250.204.106"
        )
    }
}
