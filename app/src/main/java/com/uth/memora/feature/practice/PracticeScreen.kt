package com.uth.memora.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.feature.common.EmptyState
import com.uth.memora.feature.common.MemoraLinearProgressBar
import com.uth.memora.feature.common.MemoraPrimaryButton
import com.uth.memora.feature.common.MemoraSectionCard
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.common.shimmer.AiResultShimmerSkeleton
import com.uth.memora.feature.practice.components.MicButton
import com.uth.memora.feature.practice.components.SentenceHistory
import com.uth.memora.feature.practice.components.SentenceInput
import com.uth.memora.feature.practice.components.TargetWordCard
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusWarning
import com.uth.memora.feature.theme.StatusWarningLight
import com.uth.memora.feature.theme.SurfaceLight
import com.uth.memora.integration.speech.SpeechUiState

private const val QUOTA_EXHAUSTED_NOTICE_VI =
    "Bạn đã dùng hết 20 lượt chấm AI hôm nay (tự hồi lúc 4:00 sáng)"
private const val AI_GRADING_LOADING_LABEL_VI =
    "AI đang phân tích ngữ pháp của bạn..."

/**
 * Màn hình Luyện đặt câu với Chatbot AI (Stateless - Figma 2061:119 & 2212:109 · Mục 4, 6.7, 6.8, 8.2 & 9.5):
 * - Chế độ Khách (`isGuest == true`): hiển thị màn giới thiệu tính năng Chatbot AI kèm nút đăng nhập, chặn gọi AI.
 * - Đã đăng nhập (`isGuest == false`): hiển thị thanh hạn mức "Còn X/20 lượt chấm AI hôm nay",
 *   khóa nút gửi khi `remainingDailyQuota <= 0` kèm thông báo tự hồi lúc 4:00 sáng, thẻ từ mục tiêu và khung nhập câu.
 */
@Composable
fun PracticeScreen(
    uiState: PracticeUiState,
    onNavigateToAuth: () -> Unit,
    onSelectTargetCard: (Int) -> Unit = {},
    onNextCard: () -> Unit = {},
    onRefreshTargetCards: () -> Unit = {},
    onSpeakWord: (String) -> Unit = {},
    onInputTextChange: (String) -> Unit = {},
    onSubmitSentence: () -> Unit = {},
    onSaveCorrectedAsExample: (String) -> Unit = {},
    onRetryFailedSentence: (String) -> Unit = {},
    onDismissMessage: () -> Unit = {},
    modifier: Modifier = Modifier,
    isOnline: Boolean = true,
    isVoiceInputAvailable: Boolean = true,
    speechState: SpeechUiState = SpeechUiState.Idle,
    onStartVoiceInput: () -> Unit = {},
    onStopVoiceInput: () -> Unit = {},
    voiceActionContent: (@Composable () -> Unit)? = null,
    resultSectionContent: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thanh tiêu đề tab Chatbot chuẩn Figma (Node 2061:120..122)
            PracticeTopHeader(
                showRefreshAction = true,
                isRefreshing = uiState.isLoading,
                onRefreshClick = onRefreshTargetCards
            )

            // Hiển thị trực tiếp giao diện Luyện đặt câu với Chatbot AI cho mọi người học (cả đã đăng nhập & dùng thử)
            PracticeSignedInContent(
                uiState = uiState,
                isOnline = isOnline,
                isVoiceInputAvailable = isVoiceInputAvailable,
                speechState = speechState,
                onSelectTargetCard = onSelectTargetCard,
                onNextCard = onNextCard,
                onRefreshTargetCards = onRefreshTargetCards,
                onSpeakWord = onSpeakWord,
                onInputTextChange = onInputTextChange,
                onSubmitSentence = onSubmitSentence,
                onSaveCorrectedAsExample = onSaveCorrectedAsExample,
                onRetryFailedSentence = onRetryFailedSentence,
                onDismissMessage = onDismissMessage,
                onStartVoiceInput = onStartVoiceInput,
                onStopVoiceInput = onStopVoiceInput,
                voiceActionContent = voiceActionContent,
                resultSectionContent = resultSectionContent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PracticeTopHeader(
    showRefreshAction: Boolean,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chatbot",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (showRefreshAction) {
                IconButton(
                    onClick = onRefreshClick,
                    enabled = !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Làm mới danh sách từ mục tiêu",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * Màn giới thiệu tính năng Chatbot AI dành cho người dùng Khách (Mục 4 & 6.7).
 * Tuyệt đối không hiển thị khung nhập/gửi câu hay gọi AI khi chưa đăng nhập Email/Google.
 */
@Composable
private fun PracticeGuestIntroContent(
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        MemoraStatusBadge(
            text = "Yêu cầu đăng nhập tài khoản",
            accentColor = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Luyện đặt câu với Chatbot AI",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Viết hoặc nói câu tiếng Anh với từ vựng flashcard của bạn để Gemini AI chấm điểm ngữ pháp, sửa lỗi và gợi ý cách diễn đạt tự nhiên hơn.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Khung tóm tắt các lợi ích cốt lõi của Chatbot AI (Mục 6.7)
        MemoraSectionCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(16.dp)
        ) {
            GuestFeatureHighlightRow(
                icon = Icons.Default.School,
                title = "Chấm điểm thang 10 & ước tính CEFR",
                description = "Nhận xét ngữ pháp chi tiết bằng tiếng Việt và chỉ rõ lỗi dùng từ."
            )
            Spacer(modifier = Modifier.height(12.dp))
            GuestFeatureHighlightRow(
                icon = Icons.Default.CheckCircle,
                title = "Câu sửa chuẩn & cách nói tự nhiên",
                description = "Lưu trực tiếp câu đã chỉnh sửa từ AI làm câu ví dụ riêng trên thẻ."
            )
            Spacer(modifier = Modifier.height(12.dp))
            GuestFeatureHighlightRow(
                icon = Icons.Default.Mic,
                title = "Nhập giọng nói & hàng đợi ngoại tuyến",
                description = "Tặng 20 lượt chấm AI mỗi ngày, tự động đồng bộ khi đăng nhập tài khoản."
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Nút CTA điều hướng sang màn Đăng nhập Email/Google (Mục 4 & 6.7)
        MemoraPrimaryButton(
            text = "Đăng nhập để sử dụng Chatbot AI",
            onClick = onNavigateToAuth,
            leadingIcon = Icons.AutoMirrored.Filled.Login,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Ở chế độ Khách, bạn vẫn có thể học Flashcard SM-2 ngoại tuyến. Đăng nhập để mở khóa AI Chatbot và đồng bộ đám mây.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GuestFeatureHighlightRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PracticeSignedInContent(
    uiState: PracticeUiState,
    isOnline: Boolean,
    isVoiceInputAvailable: Boolean,
    speechState: SpeechUiState,
    onSelectTargetCard: (Int) -> Unit,
    onNextCard: () -> Unit,
    onRefreshTargetCards: () -> Unit,
    onSpeakWord: (String) -> Unit,
    onInputTextChange: (String) -> Unit,
    onSubmitSentence: () -> Unit,
    onSaveCorrectedAsExample: (String) -> Unit,
    onRetryFailedSentence: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onStartVoiceInput: () -> Unit,
    onStopVoiceInput: () -> Unit,
    voiceActionContent: (@Composable () -> Unit)?,
    resultSectionContent: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier
) {
    val safeRemainingQuota = uiState.remainingDailyQuota.coerceIn(0, uiState.maxDailyQuota)
    val isQuotaExhausted = safeRemainingQuota <= 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Khối giới thiệu ngắn & thanh hạn mức chấm AI hôm nay (Figma 2212:87..88 · Mục 6.7, 9.5 & 11)
        DailyQuotaStatusCard(
            remainingDailyQuota = safeRemainingQuota,
            maxDailyQuota = uiState.maxDailyQuota,
            selectedCardIndex = uiState.selectedCardIndex,
            totalTargetCards = uiState.targetCards.size
        )

        // Banner thông báo lỗi hoặc phản hồi tiếng Việt thân thiện (lọc sạch mọi chuỗi kỹ thuật thô - Mục 9.5)
        val bannerError = uiState.errorMessage
            ?.takeUnless { isQuotaExhausted || it.isBlank() }
            ?.let { PracticeViewModel.sanitizeUserFacingErrorMessage(it) }
        val bannerFeedback = uiState.feedbackMessage?.takeIf { it.isNotBlank() }
        if (!bannerError.isNullOrBlank()) {
            PracticeMessageBanner(
                message = bannerError,
                isError = true,
                onDismiss = onDismissMessage
            )
        } else if (!bannerFeedback.isNullOrBlank()) {
            PracticeMessageBanner(
                message = bannerFeedback,
                isError = false,
                onDismiss = onDismissMessage
            )
        }

        // Nội dung chính: Loading Shimmer / EmptyState / TargetWordCard + SentenceInput + Result
        val selectedCard = uiState.selectedCard
        when {
            uiState.isLoading && uiState.targetCards.isEmpty() -> {
                AiResultShimmerSkeleton()
            }

            selectedCard == null -> {
                EmptyState(
                    title = "Chưa có từ vựng để luyện đặt câu",
                    description = "Hãy thêm thẻ từ vựng hoặc bắt đầu ôn tập để hệ thống gợi ý tối đa ${PracticeSentence.MAX_WORDS_PER_SESSION} từ mục tiêu cho bạn.",
                    actionText = "Tải lại gợi ý",
                    onActionClick = onRefreshTargetCards
                )
            }

            else -> {
                // Hàng chip chọn nhanh tối đa 5 từ mục tiêu trong buổi luyện (Mục 6.7)
                if (uiState.targetCards.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.targetCards,
                            key = { _, card -> card.id }
                        ) { index, card ->
                            val isSelected = index == uiState.selectedCardIndex
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectTargetCard(index) },
                                label = {
                                    Text(
                                        text = "${index + 1}. ${card.word}",
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                // Thẻ từ mục tiêu (Figma 2061:119 · D3)
                TargetWordCard(
                    card = selectedCard,
                    currentIndex = uiState.selectedCardIndex,
                    totalCount = uiState.targetCards.size,
                    onSpeakClick = { onSpeakWord(selectedCard.word) },
                    onNextCard = onNextCard
                )

                // Khung nhập câu & nút Micro/Gửi: ẩn MicButton duyên dáng nếu thiết bị không hỗ trợ SpeechRecognizer (Mục 6.7, 6.8 & 9.5 · D4, D5, E4 & E5)
                val effectiveVoiceAction: (@Composable () -> Unit)? = when {
                    voiceActionContent != null -> voiceActionContent
                    isVoiceInputAvailable -> {
                        {
                            MicButton(
                                speechState = speechState,
                                onStartClick = onStartVoiceInput,
                                onStopClick = onStopVoiceInput,
                                enabled = !uiState.isSubmitting
                            )
                        }
                    }

                    else -> null
                }
                SentenceInput(
                    targetWord = selectedCard.word,
                    inputText = uiState.inputText,
                    validationError = uiState.validationError,
                    isSubmitting = uiState.isSubmitting,
                    canSubmit = uiState.canSubmit && !isQuotaExhausted,
                    onInputTextChange = onInputTextChange,
                    onSubmit = onSubmitSentence,
                    isDuplicateInFlight = uiState.isDuplicateInFlight,
                    speechState = if (isVoiceInputAvailable) speechState else SpeechUiState.Idle,
                    isLowConfidenceVoiceInput = uiState.isLowConfidenceVoiceInput,
                    voiceActionContent = effectiveVoiceAction
                )

                // Hiển thị Shimmer Loading khi history có câu GRADING (hoặc PENDING_GRADE khi vừa gửi lúc online)
                // thay cho thẻ kết quả rỗng (Figma 2212:109 & 2212:145 · Mục 6.7 & 8.2)
                val hasActiveOnlineGrading = uiState.history.any { sentence ->
                    sentence.status == SentenceStatus.GRADING ||
                        (isOnline && sentence.status == SentenceStatus.PENDING_GRADE)
                } || (isOnline && uiState.isSubmitting)

                if (hasActiveOnlineGrading) {
                    PracticeGradingShimmerCard()
                }

                when {
                    resultSectionContent != null -> {
                        resultSectionContent()
                    }

                    uiState.history.isNotEmpty() -> {
                        SentenceHistory(
                            history = uiState.history,
                            onSaveCorrectedAsExample = onSaveCorrectedAsExample,
                            onRetrySentence = onRetryFailedSentence,
                            retryingSentenceId = uiState.retryingSentenceId,
                            savingExampleSentenceId = uiState.savingExampleSentenceId,
                            savedExampleSentenceIds = uiState.savedExampleSentenceIds
                        )
                    }

                    !hasActiveOnlineGrading -> {
                        PracticeEmptyResultCard(targetWord = selectedCard.word)
                    }
                }
            }
        }
    }
}

/**
 * Khung Shimmer Loading mô phỏng kết quả chấm điểm AI trong lúc chờ phản hồi (Figma 2212:145 · Mục 6.7 & 8.2).
 * Tái sử dụng [AiResultShimmerSkeleton] từ `feature/common/shimmer/`.
 */
@Composable
private fun PracticeGradingShimmerCard(
    modifier: Modifier = Modifier
) {
    MemoraSectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Đánh giá chatbot",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            MemoraStatusBadge(
                text = SentenceStatus.GRADING.labelVi,
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        AiResultShimmerSkeleton()

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = AI_GRADING_LOADING_LABEL_VI,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Thẻ kết quả rỗng hiển thị khi thẻ mục tiêu hiện tại chưa có lượt chấm AI nào (Mục 6.7).
 */
@Composable
private fun PracticeEmptyResultCard(
    targetWord: String,
    modifier: Modifier = Modifier
) {
    MemoraSectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Đánh giá chatbot",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Chưa có kết quả",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Text(
                text = "Đặt một câu tiếng Anh có chứa từ \"$targetWord\" rồi bấm \"Chấm điểm câu\" để nhận điểm số, trình độ CEFR và câu chỉnh sửa từ AI.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Thanh trạng thái hạn mức chấm AI hằng ngày (Mục 6.7, 9.5 & 11):
 * Hiển thị "Còn X/20 lượt chấm AI hôm nay"; khi X <= 0 hiển thị thông báo tự hồi lúc 4:00 sáng.
 */
@Composable
private fun DailyQuotaStatusCard(
    remainingDailyQuota: Int,
    maxDailyQuota: Int,
    selectedCardIndex: Int,
    totalTargetCards: Int
) {
    val isQuotaExhausted = remainingDailyQuota <= 0
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val warningColor = if (isLight) StatusWarningLight else StatusWarning
    val progressColor = when {
        isQuotaExhausted -> MaterialTheme.colorScheme.error
        remainingDailyQuota <= 3 -> warningColor
        else -> MaterialTheme.colorScheme.primary
    }
    val progressFraction = if (maxDailyQuota > 0) {
        (remainingDailyQuota.toFloat() / maxDailyQuota.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    MemoraSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = if (isQuotaExhausted) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Còn $remainingDailyQuota/$maxDailyQuota lượt chấm AI hôm nay",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isQuotaExhausted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )

            if (totalTargetCards > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                val currentWordNumber = (selectedCardIndex + 1).coerceIn(1, totalTargetCards)
                MemoraStatusBadge(
                    text = "Từ $currentWordNumber/$totalTargetCards",
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MemoraLinearProgressBar(
            progress = progressFraction,
            height = 6.dp,
            color = progressColor
        )

        if (isQuotaExhausted) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(16.dp)
                    )
                    Text(
                        text = QUOTA_EXHAUSTED_NOTICE_VI,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PracticeMessageBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isError) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Đóng thông báo",
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Preview(name = "PracticeScreen - Chế độ Khách (Dark)", showBackground = true)
@Composable
private fun PracticeScreenGuestDarkPreview() {
    MemoraTheme(darkTheme = true) {
        PracticeScreen(
            uiState = PracticeUiState(isGuest = true),
            onNavigateToAuth = {}
        )
    }
}

@Preview(name = "PracticeScreen - Đã đăng nhập (Dark)", showBackground = true)
@Composable
private fun PracticeScreenSignedInDarkPreview() {
    val sampleCards = listOf(
        Card(
            id = "card_1",
            deckId = "deck_everyday",
            word = "Narrative",
            phonetic = "/ˈnær.ə.tɪv/",
            partOfSpeech = PartOfSpeech.NOUN,
            meaning = "câu chuyện, lời kể, một dạng văn hoặc phim kể về một sự việc.",
            state = CardState.LEARNING
        ),
        Card(
            id = "card_2",
            deckId = "deck_everyday",
            word = "Resilient",
            phonetic = "/rɪˈzɪl.i.ənt/",
            partOfSpeech = PartOfSpeech.ADJECTIVE,
            meaning = "kiên cường, có khả năng phục hồi nhanh sau khó khăn.",
            state = CardState.REVIEW
        )
    )
    MemoraTheme(darkTheme = true) {
        PracticeScreen(
            uiState = PracticeUiState(
                isGuest = false,
                targetCards = sampleCards,
                selectedCardIndex = 0,
                inputText = "The movie is a narrative about a young artist.",
                remainingDailyQuota = 16
            ),
            onNavigateToAuth = {}
        )
    }
}

@Preview(name = "PracticeScreen - Đang chấm Shimmer (Figma 2212:109)", showBackground = true)
@Composable
private fun PracticeScreenGradingShimmerDarkPreview() {
    val sampleCard = Card(
        id = "card_1",
        deckId = "deck_everyday",
        word = "Narrative",
        phonetic = "/ˈnær.ə.tɪv/",
        partOfSpeech = PartOfSpeech.NOUN,
        meaning = "câu chuyện, lời kể, một dạng văn hoặc phim kể về một sự việc.",
        state = CardState.LEARNING
    )
    MemoraTheme(darkTheme = true) {
        PracticeScreen(
            uiState = PracticeUiState(
                isGuest = false,
                targetCards = listOf(sampleCard),
                selectedCardIndex = 0,
                inputText = "The movie is a narrative about a young artist who learn to balance work and family.",
                remainingDailyQuota = 16,
                history = listOf(
                    PracticeSentence(
                        id = "sentence_grading_1",
                        cardId = sampleCard.id,
                        targetWord = sampleCard.word,
                        sentence = "The movie is a narrative about a young artist who learn to balance work and family.",
                        status = SentenceStatus.GRADING,
                        learningDay = 20_000L
                    )
                ),
                isSubmitting = true
            ),
            isOnline = true,
            onNavigateToAuth = {}
        )
    }
}

@Preview(name = "PracticeScreen - Hết hạn mức 0/20 (Light)", showBackground = true)
@Composable
private fun PracticeScreenQuotaExhaustedLightPreview() {
    val sampleCard = Card(
        id = "card_1",
        deckId = "deck_everyday",
        word = "Resilient",
        phonetic = "/rɪˈzɪl.i.ənt/",
        partOfSpeech = PartOfSpeech.ADJECTIVE,
        meaning = "kiên cường, có khả năng phục hồi nhanh sau khó khăn.",
        state = CardState.REVIEW
    )
    MemoraTheme(darkTheme = false) {
        PracticeScreen(
            uiState = PracticeUiState(
                isGuest = false,
                targetCards = listOf(sampleCard),
                selectedCardIndex = 0,
                inputText = "Children are remarkably resilient in difficult times.",
                remainingDailyQuota = 0
            ),
            onNavigateToAuth = {}
        )
    }
}
