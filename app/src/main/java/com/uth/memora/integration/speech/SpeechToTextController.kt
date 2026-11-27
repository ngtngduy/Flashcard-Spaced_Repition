package com.uth.memora.integration.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trạng thái UI của luồng nhận diện giọng nói tiếng Anh trên màn Luyện đặt câu (Mục 6.7).
 */
sealed interface SpeechUiState {
    /** Trạng thái nghỉ, sẵn sàng chạm micro để bắt đầu đọc câu. */
    data object Idle : SpeechUiState

    /** Micro đang mở và lắng nghe giọng nói tiếng Anh của người dùng. */
    data object Listening : SpeechUiState

    /** Kết quả nhận diện tạm thời theo thời gian thực khi người dùng đang đọc. */
    data class PartialResult(val text: String) : SpeechUiState

    /**
     * Kết quả nhận diện cuối cùng sau khi dừng nói.
     *
     * @param text Câu tiếng Anh nhận diện được.
     * @param lowConfidence `true` khi độ tin cậy < 0.7f để nhắc người dùng kiểm tra lại câu.
     */
    data class Result(
        val text: String,
        val lowConfidence: Boolean = false
    ) : SpeechUiState

    /** Lỗi nhận diện giọng nói kèm thông báo tiếng Việt thân thiện (Mục 9.5). */
    data class Error(val messageVi: String) : SpeechUiState
}

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: NHẬP CÂU BẰNG GIỌNG NÓI (Mục 6.7 & 9.5)
 * ============================================================================
 * Bọc [android.speech.SpeechRecognizer] với ngôn ngữ tiếng Anh (`en-US`), hỗ trợ
 * kết quả tạm thời (`PARTIAL_RESULTS`), kiểm tra độ tin cậy (`CONFIDENCE_SCORES < 0.7f`)
 * và tự điều phối mọi thao tác về Main Thread nhằm tránh crash/memory leak.
 */
class SpeechToTextController(context: Context) {

    // Giữ applicationContext thay vì Activity Context để tránh memory leak (Mục 6.7)
    private val appContext: Context = context.applicationContext
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<SpeechUiState>(SpeechUiState.Idle)
    val state: StateFlow<SpeechUiState> = _state.asStateFlow()

    // Khả năng hỗ trợ SpeechRecognizer trên thiết bị/máy ảo (Mục 6.7 & 9.5)
    private val _isAvailable = MutableStateFlow(isSpeechRecognitionAvailable(appContext))
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    @Volatile
    private var isDestroyed: Boolean = false

    @Volatile
    private var initializationFailed: Boolean = false

    // Các biến dưới đây chỉ được đọc/ghi trên Main Thread
    private var speechRecognizer: SpeechRecognizer? = null
    private var isSessionActive: Boolean = false
    private var isRecording: Boolean = false
    private var hasDeliveredResult: Boolean = false
    private var suppressCallbacks: Boolean = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isDestroyed || suppressCallbacks) return
            isSessionActive = true
            isRecording = true
            _state.value = SpeechUiState.Listening
        }

        override fun onBeginningOfSpeech() {
            // Người dùng bắt đầu nói, tiếp tục duy trì trạng thái Listening (Mục 6.7)
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Không sử dụng cường độ âm thanh ở bước hiện tại
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Không cần xử lý buffer âm thanh thô
        }

        override fun onEndOfSpeech() {
            if (isDestroyed || suppressCallbacks) return
            // Ngừng thu âm và chờ hệ thống trả kết quả cuối trong onResults/onError (Mục 6.7)
            isRecording = false
        }

        override fun onError(error: Int) {
            if (isDestroyed || suppressCallbacks || hasDeliveredResult) return
            isSessionActive = false
            isRecording = false
            _state.value = SpeechUiState.Error(mapErrorMessage(error))
        }

        override fun onResults(results: Bundle?) {
            if (isDestroyed || suppressCallbacks) return
            isSessionActive = false
            isRecording = false
            hasDeliveredResult = true

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
            if (recognizedText.isEmpty()) {
                _state.value = SpeechUiState.Error(ERROR_NO_MATCH_VI)
                return
            }

            // Đọc CONFIDENCE_SCORES để cảnh báo nếu điểm tin cậy < 0.7f (Mục 6.7)
            val confidenceScores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
            val lowConfidence = isLowConfidence(confidenceScores)

            _state.value = SpeechUiState.Result(
                text = recognizedText,
                lowConfidence = lowConfidence
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (isDestroyed || suppressCallbacks || hasDeliveredResult) return
            val partialText = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (partialText.isNotEmpty()) {
                _state.value = SpeechUiState.PartialResult(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Không có sự kiện mở rộng cần xử lý
        }
    }

    /**
     * Bắt đầu lắng nghe giọng nói tiếng Anh (`en-US`) và phát kết quả qua [state] (Mục 6.7).
     */
    fun startListening() {
        if (isDestroyed) return

        // Kiểm tra quyền RECORD_AUDIO trước khi khởi chạy bộ nhận diện (Mục 6.7 & 9.5)
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPermission) {
            _state.value = SpeechUiState.Error(ERROR_PERMISSION_VI)
            return
        }

        // Kiểm tra thiết bị/máy ảo có dịch vụ nhận diện giọng nói khả dụng hay không (Mục 6.7 & 9.5)
        if (!refreshAvailability()) {
            _state.value = SpeechUiState.Error(ERROR_NOT_AVAILABLE_VI)
            return
        }

        runOnMainThread {
            if (isDestroyed) return@runOnMainThread

            val recognizer = ensureRecognizer() ?: return@runOnMainThread

            // Hủy phiên cũ an toàn trước khi bắt đầu phiên mới để tránh ERROR_RECOGNIZER_BUSY
            if (isSessionActive) {
                cancelSilently(recognizer)
            }

            hasDeliveredResult = false
            isSessionActive = true
            isRecording = true
            _state.value = SpeechUiState.Listening

            try {
                recognizer.startListening(buildRecognizerIntent())
            } catch (_: SecurityException) {
                isSessionActive = false
                isRecording = false
                _state.value = SpeechUiState.Error(ERROR_PERMISSION_VI)
            } catch (_: Exception) {
                isSessionActive = false
                isRecording = false
                _state.value = SpeechUiState.Error(ERROR_UNKNOWN_VI)
            }
        }
    }

    /**
     * Dừng thu âm chủ động và yêu cầu [SpeechRecognizer] trả về kết quả cuối cùng (Mục 6.7).
     */
    fun stopListening() {
        if (isDestroyed) return

        runOnMainThread {
            if (isDestroyed || !isRecording) return@runOnMainThread
            isRecording = false

            val recognizer = speechRecognizer ?: return@runOnMainThread
            try {
                recognizer.stopListening()
            } catch (_: Exception) {
                isSessionActive = false
                _state.value = SpeechUiState.Idle
            }
        }
    }

    /**
     * Đưa trạng thái về [SpeechUiState.Idle] và hủy phiên ghi âm nếu đang chạy (Mục 6.7).
     */
    fun reset() {
        if (isDestroyed) return

        runOnMainThread {
            if (isDestroyed) return@runOnMainThread
            speechRecognizer?.let { recognizer ->
                if (isSessionActive) {
                    cancelSilently(recognizer)
                }
            }
            isSessionActive = false
            isRecording = false
            hasDeliveredResult = false
            _state.value = SpeechUiState.Idle
        }
    }

    /**
     * Kiểm tra lại khả năng nhận diện giọng nói của thiết bị (an toàn trên máy ảo không có Google App - Mục 6.7 & 9.5).
     */
    fun refreshAvailability(): Boolean {
        val available = !initializationFailed && isSpeechRecognitionAvailable(appContext)
        _isAvailable.value = available
        if (!available && !isDestroyed) {
            runOnMainThread {
                if (isSessionActive) {
                    speechRecognizer?.let(::cancelSilently)
                }
            }
        }
        return available
    }

    /**
     * Giải phóng hoàn toàn [SpeechRecognizer] và listener để tránh memory leak (Mục 6.7).
     * Nên gọi trong `DisposableEffect.onDispose` hoặc `ViewModel.onCleared`.
     */
    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true

        // Xóa toàn bộ tác vụ chờ trên Main Handler để tránh khởi tạo lại sau khi hủy
        mainHandler.removeCallbacksAndMessages(null)

        runOnMainThread {
            releaseRecognizer()
            _state.value = SpeechUiState.Idle
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        speechRecognizer?.let { return it }

        return try {
            SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(recognitionListener)
            }.also {
                speechRecognizer = it
            }
        } catch (_: Throwable) {
            // Máy ảo thiếu Google App hoặc ROM lỗi dịch vụ -> ẩn nút Micro duyên dáng thay vì crash (Mục 6.7 & 9.5)
            initializationFailed = true
            _isAvailable.value = false
            _state.value = SpeechUiState.Error(ERROR_NOT_AVAILABLE_VI)
            null
        }
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE_EN_US)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE_EN_US)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }

    private fun cancelSilently(recognizer: SpeechRecognizer) {
        suppressCallbacks = true
        try {
            recognizer.cancel()
        } catch (_: Exception) {
            // Bỏ qua lỗi đặc thù của một số thiết bị khi hủy phiên đang đóng
        } finally {
            suppressCallbacks = false
            isSessionActive = false
            isRecording = false
        }
    }

    private fun releaseRecognizer() {
        val recognizer = speechRecognizer
        speechRecognizer = null
        isSessionActive = false
        isRecording = false
        hasDeliveredResult = false

        if (recognizer != null) {
            suppressCallbacks = true
            try {
                recognizer.setRecognitionListener(null)
                recognizer.cancel()
                recognizer.destroy()
            } catch (_: Exception) {
                // Tránh crash khi dịch vụ hệ thống đã ngắt kết nối trước đó
            } finally {
                suppressCallbacks = false
            }
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        const val LANGUAGE_EN_US: String = "en-US"
        const val LOW_CONFIDENCE_THRESHOLD: Float = 0.7f
        private const val MAX_RESULTS: Int = 1

        const val ERROR_PERMISSION_VI: String =
            "Chưa có quyền truy cập micro. Vui lòng cấp quyền ghi âm để đọc câu tiếng Anh."
        const val ERROR_NOT_AVAILABLE_VI: String =
            "Thiết bị chưa hỗ trợ nhận diện giọng nói. Vui lòng nhập câu bằng bàn phím."
        const val ERROR_NETWORK_VI: String =
            "Không có kết nối mạng khi nhận diện giọng nói. Vui lòng kiểm tra mạng và thử lại."
        const val ERROR_NETWORK_TIMEOUT_VI: String =
            "Kết nối mạng quá thời gian chờ khi nhận diện giọng nói. Vui lòng thử lại."
        const val ERROR_AUDIO_VI: String =
            "Không thể thu âm từ micro. Vui lòng kiểm tra micro và thử lại."
        const val ERROR_SERVER_VI: String =
            "Dịch vụ nhận diện giọng nói tạm thời gặp sự cố. Vui lòng thử lại sau."
        const val ERROR_CLIENT_VI: String =
            "Không thể xử lý phiên ghi âm lúc này. Vui lòng thử lại."
        const val ERROR_SPEECH_TIMEOUT_VI: String =
            "Không phát hiện giọng nói. Vui lòng chạm micro và đọc câu tiếng Anh."
        const val ERROR_NO_MATCH_VI: String =
            "Không nghe rõ câu tiếng Anh bạn đọc. Vui lòng thử nói chậm và rõ hơn."
        const val ERROR_BUSY_VI: String =
            "Bộ nhận diện giọng nói đang bận. Vui lòng đợi một lát rồi thử lại."
        const val ERROR_TOO_MANY_REQUESTS_VI: String =
            "Bạn thao tác ghi âm quá nhanh (429). Vui lòng đợi một lát rồi thử lại."
        const val ERROR_LANGUAGE_VI: String =
            "Thiết bị chưa sẵn sàng nhận diện tiếng Anh (en-US). Vui lòng kiểm tra mạng hoặc gói ngôn ngữ."
        const val ERROR_UNKNOWN_VI: String =
            "Không thể nhận diện giọng nói lúc này. Vui lòng thử lại hoặc nhập bằng bàn phím."

        /**
         * Kiểm tra an toàn thiết bị/máy ảo có hỗ trợ [SpeechRecognizer] hay không (Mục 6.7 & 9.5).
         * Trả `false` nếu máy ảo không có Google App / RecognitionService hoặc hệ thống ném ngoại lệ.
         */
        fun isSpeechRecognitionAvailable(context: Context): Boolean = runCatching {
            SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
        }.getOrDefault(false)

        /**
         * Đánh giá kết quả nhận diện có độ tin cậy thấp hay không dựa trên [SpeechRecognizer.CONFIDENCE_SCORES] (Mục 6.7).
         * Điểm hợp lệ nằm trong `[0.0f, 1.0f]`; giá trị `< 0f` (ví dụ `-1.0f`) biểu thị thiết bị không trả điểm tin cậy.
         */
        internal fun isLowConfidence(confidenceScores: FloatArray?): Boolean {
            val topScore = confidenceScores?.firstOrNull() ?: return false
            return topScore >= 0f && topScore < LOW_CONFIDENCE_THRESHOLD
        }

        /**
         * Chuyển mã lỗi `SpeechRecognizer.ERROR_*` sang thông báo tiếng Việt thân thiện (Mục 9.5).
         */
        internal fun mapErrorMessage(errorCode: Int): String = when (errorCode) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> ERROR_NETWORK_TIMEOUT_VI
            SpeechRecognizer.ERROR_NETWORK -> ERROR_NETWORK_VI
            SpeechRecognizer.ERROR_AUDIO -> ERROR_AUDIO_VI
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> ERROR_SERVER_VI
            SpeechRecognizer.ERROR_CLIENT -> ERROR_CLIENT_VI
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ERROR_SPEECH_TIMEOUT_VI
            SpeechRecognizer.ERROR_NO_MATCH -> ERROR_NO_MATCH_VI
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> ERROR_BUSY_VI
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ERROR_PERMISSION_VI
            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> ERROR_TOO_MANY_REQUESTS_VI
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> ERROR_LANGUAGE_VI
            else -> ERROR_UNKNOWN_VI
        }
    }
}
