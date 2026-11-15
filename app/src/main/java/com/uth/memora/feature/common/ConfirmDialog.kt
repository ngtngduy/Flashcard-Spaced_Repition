package com.uth.memora.feature.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Hộp thoại xác nhận chuẩn cho thao tác xóa mềm bộ từ/thẻ hoặc đặt lại tiến độ (Mục 6.1, 6.2 & 6.9).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "Xác nhận",
    dismissText: String = "Hủy",
    isDestructive: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = if (isDestructive) {
                        MaterialTheme.colorScheme.onError
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    }
                )
            ) {
                Text(
                    text = confirmText,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = dismissText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Preview(name = "ConfirmDialog - Dark", showBackground = true)
@Composable
private fun ConfirmDialogDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ConfirmDialog(
                title = "Xóa bộ từ vựng?",
                message = "Xóa bộ 'TOEIC 600' sẽ xóa mềm toàn bộ 25 thẻ bên trong. Bạn chắc chắn chứ?",
                confirmText = "Xóa bộ từ",
                onConfirm = {},
                onDismiss = {}
            )
        }
    }
}

@Preview(name = "ConfirmDialog - Light", showBackground = true)
@Composable
private fun ConfirmDialogLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ConfirmDialog(
                title = "Đặt lại tiến độ?",
                message = "Toàn bộ tiến độ SM-2 của thẻ này sẽ quay về Cấp 0 – Chưa học.",
                confirmText = "Đặt lại",
                onConfirm = {},
                onDismiss = {}
            )
        }
    }
}
