package com.uth.memora.feature.practice.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Locale
import kotlin.math.abs

/**
 * Thẻ hiển thị kết quả chấm câu AI cho một [PracticeSentence] đã `GRADED`
 * (Figma Node 2063:23, 2212:109 & 2212:92 · Mục 6.7 & 8.2):
 * - Vòng điểm số `score / 10` và badge trình độ CEFR (`A1`..`C2`).
 * - Nhận xét tổng quan tiếng Việt (`feedbackVi`) và danh sách gạch đầu dòng phân tích ngữ pháp (`grammarAnalysis`).
 * - Khối "Câu đã sửa" (`correctedSentence`) và khối "Cách diễn đạt tự nhiên hơn" (`naturalAlternative`).
 * - Nút "Lưu câu đã sửa làm ví dụ thẻ" (Node 2212:92) gọi `onSaveCorrectedAsExample(sentence.id)`.
 */
@Composable
fun ResultCard(
    sentence: PracticeSentence,
    onSaveCorrectedAsExample: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSavingExample: Boolean = false,
    isSavedAsExample: Boolean = false
) {
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val successColor = if (isLight) StatusSuccessLight else StatusSuccess
    val warningColor = if (isLight) StatusWarningLight else StatusWarning

    val scoreValue = (sentence.score ?: 0f).coerceIn(0f, MAX_SCORE)
    val scoreColor = when {
        scoreValue >= 8f -> successColor
        scoreValue >= 5f -> warningColor
        else -> MaterialTheme.colorScheme.error
    }

    val normalizedCefr = sentence.cefrLevel
        ?.trim()
        ?.uppercase(Locale.US)
        ?.takeIf { it.isNotEmpty() }
        ?: "--"
    val cefrBadgeColor = resolveCefrBadgeColor(
        cefrLevel = normalizedCefr,
        successColor = successColor,
        warningColor = warningColor,
        primaryColor = MaterialTheme.colorScheme.primary
    )

    val cleanGrammarPoints = sentence.grammarAnalysis
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val correctedText = sentence.correctedSentence?.trim().orEmpty()
    val naturalText = sentence.naturalAlternative?.trim().orEmpty()
    val feedbackText = sentence.feedbackVi?.trim().orEmpty()

    MemoraSectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        // Hàng tiêu đề: "Đánh giá chatbot" + badge trạng thái "Đã chấm" (Figma 2063:24 & 2212:93)
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
                text = SentenceStatus.GRADED.labelVi,
                accentColor = successColor
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Khối tổng quan: Vòng điểm số score/10 + Badge CEFR (A1..C2) + Nhận xét tiếng Việt (Figma 2063:28..37)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ScoreProgressRing(
                score = scoreValue,
                accentColor = scoreColor
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Trình độ câu",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MemoraStatusBadge(
                        text = "CEFR $normalizedCefr",
                        accentColor = cefrBadgeColor
                    )
                }

                if (feedbackText.isNotEmpty()) {
                    Text(
                        text = feedbackText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Gạch đầu dòng phân tích chi tiết ngữ pháp (grammarAnalysis - Mục 6.7)
        if (cleanGrammarPoints.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            ResultDetailBlock(title = "Phân tích ngữ pháp") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cleanGrammarPoints.forEach { point ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Khối "Câu đã sửa" (correctedSentence - Figma 2063:38..40 · Mục 6.7)
        if (correctedText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            ResultDetailBlock(title = "Câu đã sửa") {
                Text(
                    text = correctedText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Khối "Cách diễn đạt tự nhiên hơn" (naturalAlternative - Figma 2063:41..43 · Mục 6.7)
        if (naturalText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            ResultDetailBlock(title = "Cách diễn đạt tự nhiên hơn") {
                Text(
                    text = naturalText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Ghi chú ước tính CEFR mang tính tham khảo, không tác động lịch SM-2 (Figma 2063:44 & Mục 9.5)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Ước tính CEFR ($normalizedCefr) chỉ mang tính tham khảo cho câu này và không làm thay đổi lịch ôn SM-2 của thẻ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Nút "Lưu câu đã sửa làm ví dụ thẻ" (Figma Node 2212:91..92 · Mục 6.7)
        if (correctedText.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SaveCorrectedExampleButton(
                isSaving = isSavingExample,
                isSaved = isSavedAsExample,
                successColor = successColor,
                onClick = { onSaveCorrectedAsExample(sentence.id) }
            )
        }
    }
}

/**
 * Vòng tròn tiến độ hiển thị điểm số `score / 10` (Figma 2212:109 · Mục 6.7).
 */
@Composable
private fun ScoreProgressRing(
    score: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val sweepAngle = (score / MAX_SCORE).coerceIn(0f, 1f) * 360f
    val formattedScore = formatScoreNumber(score)

    Box(
        modifier = modifier
            .size(68.dp)
            .semantics {
                contentDescription = "Điểm số $formattedScore trên 10"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(68.dp)) {
            val strokeWidth = 6.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formattedScore,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = "/10",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResultDetailBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

@Composable
private fun SaveCorrectedExampleButton(
    isSaving: Boolean,
    isSaved: Boolean,
    successColor: Color,
    onClick: () -> Unit
) {
    val isEnabled = !isSaving && !isSaved
    val containerColor = when {
        isSaved -> successColor.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val borderColor = when {
        isSaved -> successColor.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        isSaved -> successColor
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.Button
                contentDescription = if (isSaved) {
                    "Đã lưu câu đã sửa làm ví dụ thẻ"
                } else {
                    "Lưu câu đã sửa làm ví dụ thẻ"
                }
            },
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isSaving -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Đang lưu câu ví dụ...",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                isSaved -> {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdded,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Đã lưu câu đã sửa làm ví dụ thẻ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                else -> {
                    Icon(
                        imageVector = Icons.Default.BookmarkAdd,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lưu câu đã sửa làm ví dụ thẻ",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatScoreNumber(score: Float): String {
    val rounded = score.toInt().toFloat()
    return if (abs(score - rounded) < 0.05f) {
        score.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", score)
    }
}

private fun resolveCefrBadgeColor(
    cefrLevel: String,
    successColor: Color,
    warningColor: Color,
    primaryColor: Color
): Color = when {
    cefrLevel.startsWith("C") -> successColor
    cefrLevel.startsWith("B") -> primaryColor
    cefrLevel.startsWith("A") -> warningColor
    else -> primaryColor
}

private const val MAX_SCORE = 10f

@Preview(name = "ResultCard - Đã chấm (Dark - Figma 2063:23 & 2212:92)", showBackground = true)
@Composable
private fun ResultCardGradedDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ResultCard(
                sentence = PracticeSentence(
                    id = "sentence_1",
                    cardId = "card_narrative",
                    targetWord = "Narrative",
                    sentence = "The movie is a narrative about a young artist who learn to balance work and family.",
                    status = SentenceStatus.GRADED,
                    score = 8f,
                    cefrLevel = "B1",
                    feedbackVi = "Câu rõ nghĩa và dùng đúng từ \"narrative\", chỉ cần chia động từ số ít cho \"artist\".",
                    grammarAnalysis = listOf(
                        "Động từ \"learn\" cần chia thành \"learns\" vì chủ ngữ quan hệ \"a young artist\" ở ngôi thứ ba số ít.",
                        "Cấu trúc \"balance work and family\" tự nhiên và dễ hiểu."
                    ),
                    correctedSentence = "The movie is a narrative about a young artist who learns to balance work and family.",
                    naturalAlternative = "The movie weaves a compelling narrative around a young artist struggling to balance her career and family life.",
                    learningDay = 20_000L
                ),
                onSaveCorrectedAsExample = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "ResultCard - Đã lưu ví dụ (Light)", showBackground = true)
@Composable
private fun ResultCardSavedLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ResultCard(
                sentence = PracticeSentence(
                    id = "sentence_2",
                    cardId = "card_resilient",
                    targetWord = "Resilient",
                    sentence = "Children are remarkably resilient in the face of unexpected change.",
                    status = SentenceStatus.GRADED,
                    score = 9.5f,
                    cefrLevel = "C1",
                    feedbackVi = "Câu chuẩn ngữ pháp, dùng cụm \"in the face of\" tự nhiên và giàu sắc thái.",
                    grammarAnalysis = listOf(
                        "Tính từ \"resilient\" được đặt đúng sau động từ liên kết \"are\" và bổ nghĩa bởi trạng từ \"remarkably\"."
                    ),
                    correctedSentence = "Children are remarkably resilient in the face of unexpected change.",
                    naturalAlternative = "Children tend to be remarkably resilient when confronting unexpected change.",
                    learningDay = 20_000L
                ),
                onSaveCorrectedAsExample = {},
                isSavedAsExample = true,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
