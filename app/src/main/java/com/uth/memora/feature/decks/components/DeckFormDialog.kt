package com.uth.memora.feature.decks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Deck
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Hộp thoại Tạo mới hoặc Chỉnh sửa Bộ từ vựng (Mục 6.2 & Mục 11).
 *
 * Quy tắc kiểm tra hợp lệ:
 * - Tên bộ từ: bắt buộc, từ 1 đến [Deck.MAX_NAME_LENGTH] (50) ký tự, không trùng tên trong cùng người dùng.
 * - Mô tả bộ từ: tùy chọn, tối đa [Deck.MAX_DESCRIPTION_LENGTH] (200) ký tự.
 */
@Composable
fun DeckFormDialog(
    isEditMode: Boolean,
    name: String,
    description: String,
    nameError: String?,
    descriptionError: String?,
    isSaving: Boolean,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trimmedNameLength = name.trim().length
    val descriptionLength = description.length

    AlertDialog(
        modifier = modifier,
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (isEditMode) "Chỉnh sửa bộ từ" else "Tạo bộ từ mới",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên bộ từ *") },
                    placeholder = { Text("Ví dụ: Tiếng Anh Công nghệ") },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = nameError != null,
                    shape = RoundedCornerShape(10.dp),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = nameError ?: "1–${Deck.MAX_NAME_LENGTH} ký tự",
                                color = if (nameError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$trimmedNameLength/${Deck.MAX_NAME_LENGTH}",
                                color = if (trimmedNameLength > Deck.MAX_NAME_LENGTH) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mô tả (không bắt buộc)") },
                    placeholder = { Text("Mô tả ngắn gọn chủ đề bộ từ...") },
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isSaving,
                    isError = descriptionError != null,
                    shape = RoundedCornerShape(10.dp),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = descriptionError ?: "Tối đa ${Deck.MAX_DESCRIPTION_LENGTH} ký tự",
                                color = if (descriptionError != null) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$descriptionLength/${Deck.MAX_DESCRIPTION_LENGTH}",
                                color = if (descriptionLength > Deck.MAX_DESCRIPTION_LENGTH) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isSaving,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = when {
                        isSaving -> "Đang lưu..."
                        isEditMode -> "Lưu thay đổi"
                        else -> "Tạo bộ từ"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving
            ) {
                Text(
                    text = "Hủy",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Preview(name = "DeckFormDialog - Create Dark", showBackground = true)
@Composable
private fun DeckFormDialogCreateDarkPreview() {
    MemoraTheme(darkTheme = true) {
        DeckFormDialog(
            isEditMode = false,
            name = "Technology & AI",
            description = "Bộ từ tiếng Anh tự soạn về công nghệ và trí tuệ nhân tạo.",
            nameError = null,
            descriptionError = null,
            isSaving = false,
            onNameChange = {},
            onDescriptionChange = {},
            onConfirm = {},
            onDismiss = {}
        )
    }
}
