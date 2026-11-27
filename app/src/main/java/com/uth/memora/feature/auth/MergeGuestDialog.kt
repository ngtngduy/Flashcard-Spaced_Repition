package com.uth.memora.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Hộp thoại xử lý dữ liệu Khách cục bộ chuẩn Figma Node `2196:16` (Mục 6.2 & Mục 10.1).
 *
 * Hiển thị khi người dùng đang học ở chế độ Khách (`isAnonymous == true`) có dữ liệu cục bộ
 * chuyển sang đăng nhập tài khoản chính thức đã có dữ liệu trên Cloud Firestore, cung cấp 2 lựa chọn:
 * - Gộp dữ liệu Khách vào tài khoản (`discardGuestData = false`).
 * - Dùng dữ liệu trên Cloud và xóa dữ liệu Khách cục bộ (`discardGuestData = true`).
 */
@Composable
fun MergeGuestDialog(
    onMergeIntoAccount: () -> Unit,
    onDiscardGuestData: () -> Unit,
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    errorMessage: String? = null,
    title: String = "Phát hiện dữ liệu học thử trên máy",
    message: String = "Tài khoản của bạn đã có dữ liệu trên đám mây. Bạn muốn xử lý dữ liệu học thử trên máy như thế nào?",
    mergeButtonText: String = "Gộp vào tài khoản",
    discardButtonText: String = "Bỏ dữ liệu khách"
) {
    Dialog(
        onDismissRequest = {
            // Bắt buộc người dùng chọn rõ gộp hoặc bỏ dữ liệu khách, không đóng ngầm bằng phím Back / chạm ngoài
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        MergeGuestDialogContent(
            title = title,
            message = message,
            mergeButtonText = mergeButtonText,
            discardButtonText = discardButtonText,
            isProcessing = isProcessing,
            errorMessage = errorMessage,
            onMergeIntoAccount = onMergeIntoAccount,
            onDiscardGuestData = onDiscardGuestData,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 393.dp)
        )
    }
}

/**
 * Nội dung stateless của hộp thoại gộp dữ liệu Khách (Figma Node `2196:16..23`),
 * tách riêng khỏi [Dialog] để hỗ trợ xem trước `@Preview` chính xác trên cả Dark/Light theme.
 */
@Composable
internal fun MergeGuestDialogContent(
    title: String,
    message: String,
    mergeButtonText: String,
    discardButtonText: String,
    isProcessing: Boolean,
    errorMessage: String?,
    onMergeIntoAccount: () -> Unit,
    onDiscardGuestData: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Tiêu đề hộp thoại (Figma 2196:17: Inter Bold 24sp / 32sp)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            // Mô tả bối cảnh dữ liệu đám mây & dữ liệu học thử (Figma 2196:18: Inter Regular 16sp / 24sp)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            // Hiển thị thông báo lỗi tiếng Việt ngay trong hộp thoại nếu gộp/đồng bộ thất bại (Mục 10.1)
            if (!errorMessage.isNullOrBlank()) {
                AuthErrorBanner(
                    message = errorMessage,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Hai nút lựa chọn xử lý dữ liệu xếp dọc (Figma 2196:19..23, khoảng cách 12dp, cao 56dp, bo góc 15dp)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onMergeIntoAccount,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Đang đồng bộ dữ liệu...",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = mergeButtonText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDiscardGuestData,
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                ) {
                    Text(
                        text = discardButtonText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(name = "MergeGuestDialog - Dark (Figma 2196:16)", showBackground = true)
@Composable
private fun MergeGuestDialogDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MergeGuestDialogContent(
                title = "Phát hiện dữ liệu học thử trên máy",
                message = "Tài khoản của bạn đã có dữ liệu trên đám mây. Bạn muốn xử lý dữ liệu học thử trên máy như thế nào?",
                mergeButtonText = "Gộp vào tài khoản",
                discardButtonText = "Bỏ dữ liệu khách",
                isProcessing = false,
                errorMessage = null,
                onMergeIntoAccount = {},
                onDiscardGuestData = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Preview(name = "MergeGuestDialog - Light", showBackground = true)
@Composable
private fun MergeGuestDialogLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MergeGuestDialogContent(
                title = "Phát hiện dữ liệu học thử trên máy",
                message = "Tài khoản của bạn đã có dữ liệu trên đám mây. Bạn muốn xử lý dữ liệu học thử trên máy như thế nào?",
                mergeButtonText = "Gộp vào tài khoản",
                discardButtonText = "Bỏ dữ liệu khách",
                isProcessing = false,
                errorMessage = null,
                onMergeIntoAccount = {},
                onDiscardGuestData = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}

@Preview(name = "MergeGuestDialog - Processing & Error", showBackground = true)
@Composable
private fun MergeGuestDialogErrorPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MergeGuestDialogContent(
                title = "Phát hiện dữ liệu học thử trên máy",
                message = "Tài khoản của bạn đã có dữ liệu trên đám mây. Bạn muốn xử lý dữ liệu học thử trên máy như thế nào?",
                mergeButtonText = "Gộp vào tài khoản",
                discardButtonText = "Bỏ dữ liệu khách",
                isProcessing = false,
                errorMessage = "Không có kết nối mạng. Vui lòng thử lại.",
                onMergeIntoAccount = {},
                onDiscardGuestData = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    }
}
