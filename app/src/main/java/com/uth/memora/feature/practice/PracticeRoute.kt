package com.uth.memora.feature.practice

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.integration.speech.SpeechToTextController
import com.uth.memora.integration.speech.SpeechUiState
import com.uth.memora.integration.worker.GradingScheduler
import com.uth.memora.memoraViewModelFactory
import java.util.Locale

/**
 * Điểm vào điều hướng cho tab Luyện đặt câu với Chatbot AI (Figma 2061:119, 2212:109 & 2230:194 · Mục 6.7, 6.8 & 8.2).
 * Đấu nối [PracticeViewModel] qua [memoraViewModelFactory], [SpeechToTextController] (kèm quyền Micro),
 * phát âm TTS và theo dõi trạng thái mạng với [PracticeScreen]. Giữ nguyên chữ ký hàm để tương thích với `MemoraNavHost`.
 */
@Composable
fun PracticeRoute(
    onNavigateToAuth: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }

    val viewModel: PracticeViewModel = viewModel(
        factory = memoraViewModelFactory { container ->
            PracticeViewModel(
                practiceSentenceRepository = container.practiceSentenceRepository,
                userPreferencesRepository = container.userPreferencesRepository,
                authRepository = container.authRepository,
                gradingScheduler = GradingScheduler(appContext),
                isOnlineProvider = { checkNetworkAvailable(appContext) }
            )
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnline by rememberIsNetworkAvailable(appContext)
    val speakWord = rememberPracticeWordSpeaker(appContext)

    // Tích hợp SpeechToTextController, kiểm tra hỗ trợ thiết bị và giải phóng khi rời màn (Mục 6.7 & 9.5)
    val speechController = remember(appContext) { SpeechToTextController(appContext) }
    val isVoiceInputAvailable by speechController.isAvailable.collectAsStateWithLifecycle()
    val speechState by speechController.state.collectAsStateWithLifecycle()
    DisposableEffect(speechController) {
        onDispose {
            speechController.destroy()
        }
    }

    // Kiểm tra lại khả năng nhận diện giọng nói khi quay về màn hình (Mục 6.7)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        speechController.refreshAvailability()
    }

    // Quản lý quyền RECORD_AUDIO và tự bắt đầu ghi âm ngay khi được cấp quyền (Mục 6.7 & 9.5)
    val micPermission = rememberMicPermissionState(
        onPermissionGranted = {
            if (isVoiceInputAvailable) {
                speechController.startListening()
            }
        }
    )

    // Nhận kết quả Speech-to-Text, tự chuẩn hóa chữ hoa/dấu câu và gắn nhắc nhẹ nếu lowConfidence == true (Mục 6.7 & 6.8)
    LaunchedEffect(speechState) {
        val result = speechState as? SpeechUiState.Result ?: return@LaunchedEffect
        viewModel.onVoiceInputResult(
            rawTranscript = result.text,
            lowConfidence = result.lowConfidence
        )
        speechController.reset()
    }

    // Dừng phiên ghi âm nếu người dùng chuyển về chế độ Khách (Mục 4 & 6.7)
    LaunchedEffect(uiState.isGuest) {
        if (uiState.isGuest) {
            speechController.reset()
        }
    }

    PracticeScreen(
        uiState = uiState,
        onNavigateToAuth = onNavigateToAuth,
        onSelectTargetCard = { index ->
            speechController.reset()
            viewModel.selectTargetCard(index)
        },
        onNextCard = {
            speechController.reset()
            viewModel.selectNextCard()
        },
        onRefreshTargetCards = {
            speechController.reset()
            viewModel.refreshTargetCards()
        },
        onSpeakWord = speakWord,
        onInputTextChange = { newText ->
            if (speechState != SpeechUiState.Idle) {
                speechController.reset()
            }
            viewModel.onInputTextChange(newText)
        },
        onSubmitSentence = {
            speechController.reset()
            viewModel.submitSentence(isOnline = isOnline)
        },
        onSaveCorrectedAsExample = viewModel::saveCorrectedAsExample,
        onRetryFailedSentence = { sentenceId ->
            viewModel.retryFailedSentence(
                sentenceId = sentenceId,
                isOnline = isOnline
            )
        },
        onDismissMessage = viewModel::clearMessages,
        isOnline = isOnline,
        isVoiceInputAvailable = isVoiceInputAvailable,
        speechState = if (isVoiceInputAvailable) speechState else SpeechUiState.Idle,
        onStartVoiceInput = {
            if (isVoiceInputAvailable) {
                viewModel.clearVoiceLowConfidenceHint()
                micPermission.requestPermission()
            }
        },
        onStopVoiceInput = speechController::stopListening,
        modifier = modifier
    )

    // Hộp thoại tiếng Việt giải thích quyền Micro và điều hướng Cài đặt ứng dụng khi bị từ chối (Mục 6.7 & 9.5)
    if (isVoiceInputAvailable) {
        MicPermissionDeniedDialog(state = micPermission)
    }
}

/**
 * Theo dõi trạng thái kết nối mạng thời gian thực để phân biệt gửi AI trực tiếp và hàng đợi Chờ chấm (Mục 6.8 & 8.2).
 */
@Composable
private fun rememberIsNetworkAvailable(context: Context): State<Boolean> {
    val state = remember(context) { mutableStateOf(checkNetworkAvailable(context)) }

    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            onDispose {}
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    state.value = checkNetworkAvailable(context)
                }

                override fun onLost(network: Network) {
                    state.value = checkNetworkAvailable(context)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    state.value = networkCapabilities.hasCapability(
                        NetworkCapabilities.NET_CAPABILITY_INTERNET
                    )
                }
            }

            val registered = runCatching {
                connectivityManager.registerDefaultNetworkCallback(callback)
                true
            }.getOrDefault(false)

            onDispose {
                if (registered) {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                }
            }
        }
    }

    return state
}

private fun checkNetworkAvailable(context: Context): Boolean = runCatching {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}.getOrDefault(true)

/**
 * Bộ phát âm tiếng Anh (TextToSpeech) cho nút loa trên `TargetWordCard` (Mục 6.7).
 */
@Composable
private fun rememberPracticeWordSpeaker(context: Context): (String) -> Unit {
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = engine?.setLanguage(Locale.US)
                isReady = langResult != TextToSpeech.LANG_MISSING_DATA &&
                    langResult != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        ttsEngine = engine

        onDispose {
            runCatching {
                engine.stop()
                engine.shutdown()
            }
            ttsEngine = null
            isReady = false
        }
    }

    return remember(ttsEngine, isReady) {
        { word: String ->
            val cleanWord = word.trim()
            if (isReady && cleanWord.isNotEmpty()) {
                runCatching {
                    ttsEngine?.speak(cleanWord, TextToSpeech.QUEUE_FLUSH, null, "practice_word_tts")
                }
            }
        }
    }
}
