package com.uth.memora.feature.practice.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.feature.common.MemoraSectionCard
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusWarning
import com.uth.memora.feature.theme.StatusWarningLight
import com.uth.memora.feature.theme.SurfaceLight
import com.uth.memora.integration.speech.SpeechUiState

/**
 * Khung nhập câu tiếng Anh và nút gửi chấm điểm AI theo Figma Node 2063:47 & 2212:131 (Mục 6.7, 6.8 & 11):
 * - Ô `OutlinedTextField` đa dòng hiển thị tức thì lỗi tiếng Việt từ `SentenceLocalValidator.validate()` bên dưới.
 * - Nút "Chấm điểm câu" chỉ sáng khi câu hợp lệ cục bộ, không đang gửi và không gửi trùng câu `PENDING_GRADE` / `GRADING`.
 * - Hỗ trợ khe cắm tùy chọn `voiceActionContent` (góc dưới phải ô nhập) để gắn nút Micro Speech-to-Text (Node 2230:145).
 */
@Composable
fun SentenceInput(
    targetWord: String,
    inputText: String,
    validationError: String?,
    isSubmitting: Boolean,
    canSubmit: Boolean,
    onInputTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    isDuplicateInFlight: Boolean = false,
    enabled: Boolean = true,
    speechState: SpeechUiState = SpeechUiState.Idle,
    isLowConfidenceVoiceInput: Boolean = false,
    voiceActionContent: (@Composable () -> Unit)? = null
) {
    val isListening = speechState is SpeechUiState.Listening ||
        speechState is SpeechUiState.PartialResult

    val effectiveCanSubmit = canSubmit &&
        enabled &&
        !isSubmitting &&
        !isDuplicateInFlight &&
        validationError == null

    val trimmedLength = inputText.trim().length
    val statusLabel = when {
        speechState is SpeechUiState.Listening -> "Đang lắng nghe..."
        speechState is SpeechUiState.PartialResult -> "Đang nhận diện..."
        isSubmitting -> "Đang chấm"
        isDuplicateInFlight -> "Đang xử lý"
        trimmedLength > 0 -> "$trimmedLength/${PracticeSentence.MAX_SENTENCE_LENGTH}"
        else -> "Chưa gửi"
    }
    val statusColor = when {
        isListening -> MaterialTheme.colorScheme.error
        isSubmitting -> MaterialTheme.colorScheme.secondary
        isDuplicateInFlight -> StatusWarning
        trimmedLength > PracticeSentence.MAX_SENTENCE_LENGTH -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    MemoraSectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.outlineVariant
    ) {
        // Hàng tiêu đề: "Câu bạn viết" + trạng thái/số ký tự (Figma 2063:48..50 & 2212:132..134)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Câu bạn viết",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ô nhập câu tiếng Anh (Figma 2063:51 & 2230:194) kèm slot nút Micro (Figma 2230:145)
        Box(modifier = Modifier.fillMaxWidth()) {
            val placeholderText = when {
                isListening -> "Đang lắng nghe... Hãy nói câu tiếng Anh của bạn"
                targetWord.isNotBlank() -> "Nhập câu tiếng Anh có chứa từ \"$targetWord\"..."
                else -> "Nhập câu tiếng Anh của bạn tại đây..."
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                enabled = enabled && !isSubmitting,
                isError = validationError != null,
                placeholder = {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (effectiveCanSubmit) {
                            onSubmit()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (isListening || (inputText.isNotBlank() && validationError == null)) {
                        MaterialTheme.colorScheme.primary.copy(alpha = if (isListening) 1f else 0.75f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    errorBorderColor = MaterialTheme.colorScheme.error,
                    focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.16f)
                )
            )

            if (voiceActionContent != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 5.dp)
                ) {
                    voiceActionContent()
                }
            }
        }

        // Trạng thái ghi âm trực tiếp hoặc lỗi từ SpeechRecognizer (Figma 2230:194 · Mục 6.7 & 9.5)
        when (speechState) {
            is SpeechUiState.Listening -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (inputText.isBlank()) {
                            "Chạm lại nút Micro khi bạn đọc xong."
                        } else {
                            "Đang lắng nghe... Chạm lại nút Micro khi bạn đọc xong."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is SpeechUiState.PartialResult -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = "Đang nghe: \"${speechState.text}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            is SpeechUiState.Error -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = speechState.messageVi,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            SpeechUiState.Idle,
            is SpeechUiState.Result -> Unit
        }

        // Nhắc nhẹ khi độ tin cậy nhận diện giọng nói < 0.7f để người học rà lại trước khi gửi (Mục 6.7)
        if (isLowConfidenceVoiceInput && !isListening) {
            val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
            val warningTint = if (isLight) StatusWarningLight else StatusWarning
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = warningTint.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, warningTint.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = warningTint,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = "Độ tin cậy nhận diện giọng nói chưa cao — bạn vui lòng rà lại câu trước khi bấm Gửi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Lỗi kiểm tra cục bộ tiếng Việt hoặc cảnh báo gửi trùng ngay dưới ô nhập (Mục 6.8 & 11)
        Spacer(modifier = Modifier.height(8.dp))
        when {
            validationError != null -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = validationError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            isDuplicateInFlight -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = StatusWarning,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                    )
                    Text(
                        text = "Câu này đang ở trạng thái Chờ chấm hoặc Đang chấm, vui lòng không gửi trùng.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            targetWord.isNotBlank() -> {
                Text(
                    text = "Gợi ý: Tối thiểu ${PracticeSentence.MIN_WORDS} từ, viết hoa chữ đầu, có dấu kết câu (. ! ?) và chứa từ \"$targetWord\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Nút gửi chấm câu: chỉ sáng khi hợp lệ và không gửi trùng (Figma 2063:53 & 2212:137)
        Button(
            onClick = onSubmit,
            enabled = effectiveCanSubmit,
            shape = RoundedCornerShape(10.dp),
            border = if (!effectiveCanSubmit) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            } else {
                null
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đang chấm điểm câu...",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chấm điểm câu",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "SentenceInput - Hợp lệ (Dark)", showBackground = true)
@Composable
private fun SentenceInputValidDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SentenceInput(
                targetWord = "Narrative",
                inputText = "The movie is a narrative about a young artist who learns to balance work and family.",
                validationError = null,
                isSubmitting = false,
                canSubmit = true,
                onInputTextChange = {},
                onSubmit = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "SentenceInput - Lỗi kiểm tra cục bộ (Dark)", showBackground = true)
@Composable
private fun SentenceInputValidationErrorDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SentenceInput(
                targetWord = "Narrative",
                inputText = "The movie is interesting.",
                validationError = "Câu phải chứa từ mục tiêu \"Narrative\" (hoặc dạng chia thì/số nhiều của từ đó).",
                isSubmitting = false,
                canSubmit = false,
                onInputTextChange = {},
                onSubmit = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "SentenceInput - Đang chấm (Light)", showBackground = true)
@Composable
private fun SentenceInputSubmittingLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SentenceInput(
                targetWord = "Resilient",
                inputText = "Children are remarkably resilient in the face of change.",
                validationError = null,
                isSubmitting = true,
                canSubmit = false,
                onInputTextChange = {},
                onSubmit = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "SentenceInput - Đang thu âm (Figma 2230:194)", showBackground = true)
@Composable
private fun SentenceInputListeningDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SentenceInput(
                targetWord = "Narrative",
                inputText = "",
                validationError = null,
                isSubmitting = false,
                canSubmit = false,
                onInputTextChange = {},
                onSubmit = {},
                speechState = SpeechUiState.Listening,
                voiceActionContent = {
                    MicButton(
                        speechState = SpeechUiState.Listening,
                        onStartClick = {},
                        onStopClick = {}
                    )
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "SentenceInput - Nhắc độ tin cậy thấp (Dark)", showBackground = true)
@Composable
private fun SentenceInputLowConfidenceDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            SentenceInput(
                targetWord = "Narrative",
                inputText = "The movie is a narrative about a young artist.",
                validationError = null,
                isSubmitting = false,
                canSubmit = true,
                onInputTextChange = {},
                onSubmit = {},
                isLowConfidenceVoiceInput = true,
                voiceActionContent = {
                    MicButton(
                        speechState = SpeechUiState.Idle,
                        onStartClick = {},
                        onStopClick = {}
                    )
                },
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
