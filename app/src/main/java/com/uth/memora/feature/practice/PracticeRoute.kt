package com.uth.memora.feature.practice

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.integration.worker.GradingScheduler
import com.uth.memora.memoraViewModelFactory
import java.util.Locale

/**
 * Điểm vào điều hướng cho tab Luyện đặt câu với Chatbot AI (Figma 2061:119 & 2212:109 · Mục 6.7, 6.8 & 8.2).
 * Đấu nối [PracticeViewModel] qua [memoraViewModelFactory], phát âm TTS và theo dõi trạng thái mạng
 * với [PracticeScreen]. Giữ nguyên chữ ký hàm để tương thích với `MemoraNavHost`.
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

    PracticeScreen(
        uiState = uiState,
        onNavigateToAuth = onNavigateToAuth,
        onSelectTargetCard = viewModel::selectTargetCard,
        onNextCard = viewModel::selectNextCard,
        onRefreshTargetCards = viewModel::refreshTargetCards,
        onSpeakWord = speakWord,
        onInputTextChange = viewModel::onInputTextChange,
        onSubmitSentence = { viewModel.submitSentence(isOnline = isOnline) },
        onSaveCorrectedAsExample = viewModel::saveCorrectedAsExample,
        onRetryFailedSentence = { sentenceId ->
            viewModel.retryFailedSentence(
                sentenceId = sentenceId,
                isOnline = isOnline
            )
        },
        onDismissMessage = viewModel::clearMessages,
        isOnline = isOnline,
        modifier = modifier
    )
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
