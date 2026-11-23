package com.uth.memora.feature.study

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyMode
import com.uth.memora.feature.common.AppHeader
import com.uth.memora.feature.common.EmptyState
import com.uth.memora.feature.common.MemoraLinearProgressBar
import com.uth.memora.feature.common.MemoraPrimaryButton
import com.uth.memora.feature.common.MemoraSectionCard
import com.uth.memora.feature.study.components.CardBack
import com.uth.memora.feature.study.components.CardFront
import com.uth.memora.feature.study.components.FlipCard
import com.uth.memora.feature.study.components.RatingButtons
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusSuccess
import java.util.Locale

/**
 * Khởi tạo và quản lý vòng đời Android TextToSpeech (Locale.US) an toàn (Mục 11).
 * Nếu thiết bị thiếu gói giọng đọc tiếng Anh hoặc chưa sẵn sàng sẽ báo rõ qua callback mà không crash.
 */
@Composable
fun rememberMemoraTtsSpeaker(
    onError: (String) -> Unit
): (String) -> Unit {
    val context = LocalContext.current
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isLanguageSupported by remember { mutableStateOf(true) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = engine?.setLanguage(Locale.US)
                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    isLanguageSupported = false
                    isTtsReady = false
                } else {
                    isLanguageSupported = true
                    isTtsReady = true
                }
            } else {
                isTtsReady = false
            }
        }
        ttsInstance = engine

        onDispose {
            engine?.stop()
            engine?.shutdown()
            ttsInstance = null
            isTtsReady = false
        }
    }

    return remember(ttsInstance, isTtsReady, isLanguageSupported) {
        { word: String ->
            val cleanWord = word.trim()
            if (cleanWord.isNotEmpty()) {
                val engine = ttsInstance
                when {
                    engine == null || !isTtsReady -> {
                        if (!isLanguageSupported) {
                            onError("Thiết bị chưa cài đặt gói giọng đọc tiếng Anh (US).")
                        } else {
                            onError("Bộ đọc phát âm đang khởi tạo, vui lòng thử lại sau giây lát.")
                        }
                    }
                    else -> {
                        val speakResult = engine.speak(
                            cleanWord,
                            TextToSpeech.QUEUE_FLUSH,
                            null,
                            "memora_tts_${cleanWord.hashCode()}"
                        )
                        if (speakResult == TextToSpeech.ERROR) {
                            onError("Không thể phát âm từ này trên thiết bị hiện tại.")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Màn hình phiên học Flashcard stateless (Figma 2104:23, 2101:14, 2101:15, 2101:16 - Mục 6.5 & 6.6):
 * - Thanh tiến độ phiên học
 * - Thẻ lật 3D (FlipCard) kết hợp CardFront & CardBack
 * - Nút đánh giá 2 nút (Học từ mới) hoặc 4 nút SM-2 (Ôn tập)
 */
@Composable
fun StudyScreen(
    uiState: StudyUiState,
    onFlipCard: () -> Unit,
    onRateCard: (ReviewQuality) -> Unit,
    onSpeakWord: (String) -> Unit,
    onRetry: () -> Unit,
    onFinishSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val screenTitle = when (uiState.studyMode) {
        StudyMode.NEW_CARDS -> "Học từ mới"
        StudyMode.DUE_REVIEW -> "Ôn tập hôm nay"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppHeader(
                title = screenTitle,
                subtitle = if (uiState.queue.isNotEmpty() && !uiState.isSessionFinished) {
                    "${uiState.queue.size} thẻ trong phiên"
                } else {
                    null
                },
                onBack = onBack
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.errorMessage != null && uiState.currentCard == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        title = "Không tải được phiên học",
                        description = uiState.errorMessage,
                        actionText = "Thử lại",
                        onActionClick = onRetry
                    )
                }
            }

            uiState.isSessionFinished -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusSuccess,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Hoàn thành phiên học!",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bạn đã hoàn thành ${uiState.queue.size} lượt thẻ trong phiên này.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    MemoraPrimaryButton(
                        text = "Về Trang chính",
                        onClick = onFinishSession,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            uiState.currentCard == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.School,
                        title = if (uiState.studyMode == StudyMode.NEW_CARDS) {
                            "Không còn thẻ mới hôm nay"
                        } else {
                            "Bạn đã ôn hết thẻ đến hạn!"
                        },
                        description = if (uiState.studyMode == StudyMode.NEW_CARDS) {
                            "Bạn đã hoàn thành hạn mức thẻ mới hôm nay hoặc bộ từ chưa có thêm thẻ mới."
                        } else {
                            "Hiện chưa có thẻ nào đến hạn ôn tập. Hãy quay lại sau hoặc học thêm từ mới nhé!"
                        },
                        actionText = "Quay lại",
                        onActionClick = onBack
                    )
                }
            }

            else -> {
                val currentCard = uiState.currentCard!!
                val totalCards = if (uiState.totalCardsInChunk > 0) uiState.totalCardsInChunk else uiState.queue.size
                val currentNumber = (uiState.currentIndex + 1).coerceAtMost(totalCards.coerceAtLeast(1))

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Khung tiến độ phiên học (Figma 2059:392 & 2059:730)
                    MemoraSectionCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thẻ $currentNumber / $totalCards",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${uiState.currentIndex} thẻ đã xong",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        MemoraLinearProgressBar(progress = uiState.progressFraction)
                    }

                    // Thẻ lật 3D (FlipCard) đặt trong key(currentCard.id, currentIndex)
                    // để khi sang thẻ mới luôn hiện ngay mặt trước (rotationY = 0f), không bị lộ mặt sau trong lúc xoay về
                    key(currentCard.id, uiState.currentIndex) {
                        FlipCard(
                            isFlipped = uiState.isFlipped,
                            onFlip = onFlipCard,
                            front = {
                                CardFront(
                                    card = currentCard,
                                    onSpeakClick = { onSpeakWord(currentCard.word) }
                                )
                            },
                            back = {
                                CardBack(
                                    card = currentCard,
                                    onSpeakClick = { onSpeakWord(currentCard.word) }
                                )
                            }
                        )
                    }

                    // Hàng công cụ lật thẻ nhanh (Figma 2059:424 & 2059:765)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onFlipCard) {
                            Icon(
                                imageVector = Icons.Default.Flip,
                                contentDescription = "Lật thẻ",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (uiState.isFlipped) "Xem mặt trước" else "Lật thẻ xem nghĩa",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }

                    // Khu vực nút đánh giá theo StudyMode
                    RatingButtons(
                        studyMode = uiState.studyMode,
                        previewLabels = uiState.previewLabels,
                        onRate = onRateCard,
                        enabled = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Preview(name = "StudyScreen (NEW_CARDS) - Dark", showBackground = true)
@Composable
private fun StudyScreenNewCardsDarkPreview() {
    val sampleCard = Card(
        id = "c1",
        deckId = "deck_everyday",
        word = "improve",
        meaning = "cải thiện, nâng cao",
        phonetic = "/ɪmˈpruːv/",
        partOfSpeech = PartOfSpeech.VERB,
        example = "Reading books helps improve your vocabulary.",
        state = CardState.NEW
    )
    MemoraTheme(darkTheme = true) {
        StudyScreen(
            uiState = StudyUiState(
                isLoading = false,
                studyMode = StudyMode.NEW_CARDS,
                queue = listOf(sampleCard),
                currentIndex = 0,
                isFlipped = false,
                totalCardsInChunk = 12,
                previewLabels = mapOf(
                    ReviewQuality.AGAIN to "Xếp cuối hàng đợi",
                    ReviewQuality.GOOD to "Ôn sau 1 ngày"
                )
            ),
            onFlipCard = {},
            onRateCard = {},
            onSpeakWord = {},
            onRetry = {},
            onFinishSession = {},
            onBack = {}
        )
    }
}

@Preview(name = "StudyScreen (DUE_REVIEW Back) - Light", showBackground = true)
@Composable
private fun StudyScreenDueReviewLightPreview() {
    val sampleCard = Card(
        id = "c2",
        deckId = "deck_everyday",
        word = "confident",
        meaning = "tự tin",
        phonetic = "/ˈkɒn.fɪ.dənt/",
        partOfSpeech = PartOfSpeech.ADJECTIVE,
        example = "She feels confident when speaking English.",
        state = CardState.REVIEW,
        interval = 5
    )
    MemoraTheme(darkTheme = false) {
        StudyScreen(
            uiState = StudyUiState(
                isLoading = false,
                studyMode = StudyMode.DUE_REVIEW,
                queue = listOf(sampleCard),
                currentIndex = 0,
                isFlipped = true,
                totalCardsInChunk = 24,
                previewLabels = mapOf(
                    ReviewQuality.AGAIN to "1 phút",
                    ReviewQuality.HARD to "10 phút",
                    ReviewQuality.GOOD to "3 ngày",
                    ReviewQuality.EASY to "7 ngày"
                )
            ),
            onFlipCard = {},
            onRateCard = {},
            onSpeakWord = {},
            onRetry = {},
            onFinishSession = {},
            onBack = {}
        )
    }
}
