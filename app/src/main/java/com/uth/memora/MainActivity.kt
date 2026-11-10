package com.uth.memora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uth.memora.core.fake.FakeMemoraRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.feature.common.shimmer.AiResultShimmerSkeleton
import com.uth.memora.feature.theme.MemoraTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as MemoraApplication).repository
        setContent {
            MemoraTheme {
                MemoraMainScreen(repository = repo)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoraMainScreen(repository: FakeMemoraRepository) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Memora – Spaced Repetition & AI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Ôn tập SM-2") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    label = { Text("Luyện câu AI") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Style, contentDescription = null) },
                    label = { Text("6 Cấp độ") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> StudyReviewTab(repository = repository)
                1 -> AiSentencePracticeTab(repository = repository)
                2 -> RetentionStagesOverviewTab(repository = repository)
            }
        }
    }
}

@Composable
private fun StudyReviewTab(repository: FakeMemoraRepository) {
    val stats by repository.observeStatistics().collectAsState(initial = null)
    val allCards by repository.observeAllActiveCards().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var dueQueue by remember { mutableStateOf<List<Card>>(emptyList()) }
    var isFlipped by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(allCards) {
        dueQueue = repository.getDueReviewQueue()
    }

    val currentCard = dueQueue.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stats?.let { st ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Chuỗi ngày học", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${st.currentStreakDays} ngày 🔥",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Đến hạn hôm nay", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${st.dueCardsCount} thẻ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        statusBanner?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (currentCard == null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "🎉 Bạn đã hoàn thành hết thẻ đến hạn hôm nay!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Bạn có thể bấm Hoàn tác lượt vừa rồi hoặc chuyển sang tab Luyện câu AI.")
                }
            }
        } else {
            val previewLabels = remember(currentCard) {
                repository.getButtonPreviewLabels(currentCard)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = currentCard.retentionStage.titleVi,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = currentCard.word,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentCard.phonetic.isNotBlank()) {
                        Text(
                            text = "${currentCard.phonetic} • ${currentCard.partOfSpeech?.abbr ?: ""}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (!isFlipped) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { isFlipped = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Lật thẻ xem nghĩa")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nghĩa: ${currentCard.meaning}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (currentCard.example.isNotBlank()) {
                            Text(
                                text = "Ví dụ: \"${currentCard.example}\"",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Đánh giá mức độ nhớ (Thuật toán SM-2):",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReviewQuality.entries.forEach { quality ->
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            repository.submitCardReview(currentCard.id, quality)
                                            isFlipped = false
                                            statusBanner = "Đã ghi nhận '${quality.labelVi}' (q=${quality.q}) cho từ '${currentCard.word}'"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = quality.labelVi,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = previewLabels[quality] ?: "",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    val res = repository.undoLastReview()
                    statusBanner = res.fold(
                        onSuccess = { "Đã hoàn tác lượt đánh giá cho thẻ '${it.word}'." },
                        onFailure = { it.message }
                    )
                    isFlipped = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Undo, contentDescription = null)
            Text(" Hoàn tác lượt đánh giá gần nhất (Mục 9.1)")
        }
    }
}

@Composable
private fun AiSentencePracticeTab(repository: FakeMemoraRepository) {
    val allCards by repository.observeAllActiveCards().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var selectedCard by remember(allCards) { mutableStateOf(allCards.firstOrNull()) }
    var userSentence by remember {
        mutableStateOf("Children are remarkably resilient when learning new skills.")
    }
    var isGrading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var gradedResult by remember { mutableStateOf<PracticeSentence?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Luyện đặt câu với Chatbot AI (Mục 6.7)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allCards.take(4).forEach { card ->
                FilterChip(
                    selected = selectedCard?.id == card.id,
                    onClick = {
                        selectedCard = card
                        gradedResult = null
                        errorText = null
                    },
                    label = { Text(card.word) }
                )
            }
        }

        selectedCard?.let { target ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Từ mục tiêu: ${target.word} (${target.phonetic})",
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = "Nghĩa: ${target.meaning}")
                    if (target.example.isNotBlank()) {
                        Text(
                            text = "Ví dụ tham khảo: ${target.example}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            OutlinedTextField(
                value = userSentence,
                onValueChange = { userSentence = it },
                enabled = !isGrading,
                label = { Text("Viết câu tiếng Anh có chứa từ '${target.word}'") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            errorText?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    errorText = null
                    gradedResult = null
                    isGrading = true
                    scope.launch {
                        val res = repository.submitPracticeSentence(
                            cardId = target.id,
                            sentence = userSentence,
                            isOnline = true
                        )
                        isGrading = false
                        res.fold(
                            onSuccess = { gradedResult = it },
                            onFailure = { errorText = it.message }
                        )
                    }
                },
                enabled = !isGrading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isGrading) "Đang chấm điểm câu..." else "Chấm điểm câu với AI")
            }

            // Hiển thị Shimmer Skeleton Loading trong lúc chờ AI phản hồi (Mục 6.7 & 8.2)
            if (isGrading) {
                AiResultShimmerSkeleton()
            }

            // Chuyển mượt mà (fade-in) sang kết quả chính thức khi nhận phản hồi từ AI
            AnimatedVisibility(
                visible = !isGrading && gradedResult?.status == SentenceStatus.GRADED,
                enter = fadeIn()
            ) {
                gradedResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Điểm: ${result.score}/10 • Mức CEFR: ${result.cefrLevel}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = "Nhận xét: ${result.feedbackVi}")
                            Text(
                                text = "Câu đã sửa: ${result.correctedSentence}",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Gợi ý tự nhiên hơn: ${result.naturalAlternative}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RetentionStagesOverviewTab(repository: FakeMemoraRepository) {
    val stats by repository.observeStatistics().collectAsState(initial = null)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Phân bố 6 Cấp độ ghi nhớ (Mục 7.5 & 10.2)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        stats?.stageDistribution?.forEach { (stage, count) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = stage.titleVi, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Khoảng cách ôn: ${stage.intervalDescriptionVi}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = "$count thẻ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
