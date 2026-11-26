package com.uth.memora.feature.practice.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.feature.common.MemoraSectionCard
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusSuccessLight
import com.uth.memora.feature.theme.StatusWarning
import com.uth.memora.feature.theme.StatusWarningLight
import com.uth.memora.feature.theme.SurfaceLight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Danh sách lịch sử câu luyện đặt câu của từ mục tiêu hiện tại (Figma 2061:119 & 2212:95 · Mục 6.7, 6.8 & 8.2):
 * - Mỗi mục hiển thị câu học viên đã viết, thời gian gửi và badge màu theo [SentenceStatus]:
 *   `PENDING_GRADE` ("Chờ có mạng"), `GRADING` ("Đang chấm"), `GRADED` ("X/10"), `GRADE_ERROR` ("Lỗi chấm").
 * - Tự động mở [ResultCard] bên trong cho câu đã `GRADED` mới nhất và cho phép đóng/mở từng câu đã chấm.
 */
@Composable
fun SentenceHistory(
    history: List<PracticeSentence>,
    onSaveCorrectedAsExample: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetrySentence: ((String) -> Unit)? = null,
    retryingSentenceId: String? = null,
    savingExampleSentenceId: String? = null,
    savedExampleSentenceIds: Set<String> = emptySet()
) {
    if (history.isEmpty()) return

    val firstGradedId = remember(history) {
        history.firstOrNull { it.status == SentenceStatus.GRADED }?.id
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tiêu đề khu vực lịch sử câu của từ đang chọn (Mục 6.7 & 8.2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lịch sử câu của từ này",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${history.size} câu",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        history.forEach { sentence ->
            SentenceHistoryItem(
                sentence = sentence,
                defaultExpanded = sentence.id == firstGradedId,
                onSaveCorrectedAsExample = onSaveCorrectedAsExample,
                onRetrySentence = onRetrySentence,
                isRetrying = retryingSentenceId == sentence.id,
                isSavingExample = savingExampleSentenceId == sentence.id,
                isSavedAsExample = sentence.id in savedExampleSentenceIds
            )
        }
    }
}

@Composable
private fun SentenceHistoryItem(
    sentence: PracticeSentence,
    defaultExpanded: Boolean,
    onSaveCorrectedAsExample: (String) -> Unit,
    onRetrySentence: ((String) -> Unit)?,
    isRetrying: Boolean,
    isSavingExample: Boolean,
    isSavedAsExample: Boolean
) {
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val successColor = if (isLight) StatusSuccessLight else StatusSuccess
    val warningColor = if (isLight) StatusWarningLight else StatusWarning

    val (badgeText, badgeColor) = resolveStatusBadge(
        sentence = sentence,
        successColor = successColor,
        warningColor = warningColor,
        primaryColor = MaterialTheme.colorScheme.primary,
        errorColor = MaterialTheme.colorScheme.error
    )
    val submittedTimeText = remember(sentence.createdAtMillis) {
        formatSubmittedTime(sentence.createdAtMillis)
    }

    var isResultExpanded by remember(sentence.id, sentence.status, defaultExpanded) {
        mutableStateOf(sentence.status == SentenceStatus.GRADED && defaultExpanded)
    }

    MemoraSectionCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = if (sentence.status == SentenceStatus.GRADE_ERROR) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    ) {
        // Hàng đầu: Thời gian gửi + Badge màu theo SentenceStatus (Figma 2063:19 & 2212:95..108)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = submittedTimeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            MemoraStatusBadge(
                text = badgeText,
                accentColor = badgeColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Câu học viên đã viết (Figma 2063:22)
        Text(
            text = sentence.sentence,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )

        // Phần nội dung chi tiết tương ứng từng trạng thái (Mục 6.7, 6.8 & 8.2)
        when (sentence.status) {
            SentenceStatus.GRADED -> {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isResultExpanded = !isResultExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isResultExpanded) {
                            "Thu gọn đánh giá AI"
                        } else {
                            "Xem đánh giá chi tiết từ AI"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isResultExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (isResultExpanded) "Thu gọn" else "Mở rộng",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = isResultExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        ResultCard(
                            sentence = sentence,
                            onSaveCorrectedAsExample = onSaveCorrectedAsExample,
                            isSavingExample = isSavingExample,
                            isSavedAsExample = isSavedAsExample
                        )
                    }
                }
            }

            SentenceStatus.PENDING_GRADE -> {
                Spacer(modifier = Modifier.height(8.dp))
                val pendingMessage = sentence.errorMessage?.takeIf { it.isNotBlank() }
                    ?: "Đã lưu vào hàng đợi ngoại tuyến — sẽ tự động chấm khi có mạng trở lại."
                Text(
                    text = pendingMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SentenceStatus.GRADING -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI đang phân tích ngữ pháp và cách dùng từ...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SentenceStatus.GRADE_ERROR -> {
                Spacer(modifier = Modifier.height(8.dp))
                val errorText = sentence.errorMessage?.takeIf { it.isNotBlank() }
                    ?: "Chưa thể chấm câu lúc này, bạn bấm Chấm lại nhé."
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                if (onRetrySentence != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = { onRetrySentence(sentence.id) },
                            enabled = !isRetrying,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isRetrying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (isRetrying) "Đang chấm lại..." else "Chấm lại",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveStatusBadge(
    sentence: PracticeSentence,
    successColor: Color,
    warningColor: Color,
    primaryColor: Color,
    errorColor: Color
): Pair<String, Color> = when (sentence.status) {
    SentenceStatus.PENDING_GRADE -> "Chờ có mạng" to warningColor
    SentenceStatus.GRADING -> "Đang chấm" to primaryColor
    SentenceStatus.GRADED -> "${formatHistoryScore(sentence.score)}/10" to successColor
    SentenceStatus.GRADE_ERROR -> "Lỗi chấm" to errorColor
}

private fun formatHistoryScore(score: Float?): String {
    val clamped = (score ?: 0f).coerceIn(0f, 10f)
    val rounded = clamped.toInt().toFloat()
    return if (abs(clamped - rounded) < 0.05f) {
        clamped.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", clamped)
    }
}

private fun formatSubmittedTime(
    createdAtMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): String {
    val dateTime = Instant.ofEpochMilli(createdAtMillis).atZone(zoneId)
    val today = LocalDate.now(zoneId)
    return if (dateTime.toLocalDate() == today) {
        "Hôm nay, ${dateTime.format(TIME_FORMATTER)}"
    } else {
        dateTime.format(DATE_TIME_FORMATTER)
    }
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm · dd/MM", Locale.US)

@Preview(name = "SentenceHistory - 4 trạng thái (Dark)", showBackground = true)
@Composable
private fun SentenceHistoryAllStatusesDarkPreview() {
    val now = 1_764_170_400_000L
    val sampleHistory = listOf(
        PracticeSentence(
            id = "s_graded",
            cardId = "card_narrative",
            targetWord = "Narrative",
            sentence = "The movie is a narrative about a young artist who learn to balance work and family.",
            status = SentenceStatus.GRADED,
            score = 8f,
            cefrLevel = "B1",
            feedbackVi = "Câu rõ nghĩa và dùng đúng từ mục tiêu, cần chia \"learns\" cho chủ ngữ số ít.",
            grammarAnalysis = listOf(
                "Động từ \"learn\" cần chia thành \"learns\" theo chủ ngữ số ít \"a young artist\"."
            ),
            correctedSentence = "The movie is a narrative about a young artist who learns to balance work and family.",
            naturalAlternative = "The movie weaves a compelling narrative around a young artist struggling to balance her career and family life.",
            learningDay = 20_000L,
            createdAtMillis = now
        ),
        PracticeSentence(
            id = "s_grading",
            cardId = "card_narrative",
            targetWord = "Narrative",
            sentence = "The author built a vivid narrative throughout the novel.",
            status = SentenceStatus.GRADING,
            learningDay = 20_000L,
            createdAtMillis = now - 120_000L
        ),
        PracticeSentence(
            id = "s_pending",
            cardId = "card_narrative",
            targetWord = "Narrative",
            sentence = "Her personal narrative inspired many students.",
            status = SentenceStatus.PENDING_GRADE,
            learningDay = 20_000L,
            createdAtMillis = now - 300_000L
        ),
        PracticeSentence(
            id = "s_error",
            cardId = "card_narrative",
            targetWord = "Narrative",
            sentence = "Every historical event has more than one narrative.",
            status = SentenceStatus.GRADE_ERROR,
            errorMessage = "Chưa thể chấm câu lúc này, bạn bấm Chấm lại nhé.",
            retryCount = 3,
            learningDay = 20_000L,
            createdAtMillis = now - 600_000L
        )
    )

    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SentenceHistory(
                history = sampleHistory,
                onSaveCorrectedAsExample = {},
                onRetrySentence = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "SentenceHistory - Light Mode", showBackground = true)
@Composable
private fun SentenceHistoryLightPreview() {
    val now = 1_764_170_400_000L
    val sampleHistory = listOf(
        PracticeSentence(
            id = "s_light_graded",
            cardId = "card_resilient",
            targetWord = "Resilient",
            sentence = "Children are remarkably resilient when adapting to new environments.",
            status = SentenceStatus.GRADED,
            score = 9.5f,
            cefrLevel = "C1",
            feedbackVi = "Câu tự nhiên, chính xác về ngữ pháp và sắc thái từ vựng.",
            grammarAnalysis = listOf(
                "Cấu trúc \"resilient when adapting to\" mạch lạc và chuẩn tự nhiên."
            ),
            correctedSentence = "Children are remarkably resilient when adapting to new environments.",
            naturalAlternative = "Children are remarkably resilient as they adapt to unfamiliar surroundings.",
            learningDay = 20_000L,
            createdAtMillis = now
        ),
        PracticeSentence(
            id = "s_light_pending",
            cardId = "card_resilient",
            targetWord = "Resilient",
            sentence = "Our team stayed resilient during the product launch.",
            status = SentenceStatus.PENDING_GRADE,
            learningDay = 20_000L,
            createdAtMillis = now - 240_000L
        )
    )

    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SentenceHistory(
                history = sampleHistory,
                onSaveCorrectedAsExample = {},
                savedExampleSentenceIds = setOf("s_light_graded"),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
