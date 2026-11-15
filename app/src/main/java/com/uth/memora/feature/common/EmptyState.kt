package com.uth.memora.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Khung trạng thái rỗng (Empty State) chuẩn của Memora (Mục 6 & 11):
 * - Icon minh họa trong vòng tròn nền dịu
 * - Tiêu đề và mô tả hướng dẫn tiếng Việt
 * - Nút CTA hành động tùy chọn (ví dụ: "+ Tạo bộ từ", "+ Thêm thẻ mới")
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Inbox,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!actionText.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            MemoraPrimaryButton(
                text = actionText,
                onClick = onActionClick
            )
        }
    }
}

@Preview(name = "EmptyState - Dark", showBackground = true)
@Composable
private fun EmptyStateDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            EmptyState(
                title = "Chưa có bộ từ vựng nào",
                description = "Hãy tạo bộ từ đầu tiên hoặc nhập từ kho Bộ có sẵn để bắt đầu học.",
                actionText = "+ Tạo bộ từ mới",
                onActionClick = {}
            )
        }
    }
}

@Preview(name = "EmptyState - Light", showBackground = true)
@Composable
private fun EmptyStateLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            EmptyState(
                title = "Chưa có bộ từ vựng nào",
                description = "Hãy tạo bộ từ đầu tiên hoặc nhập từ kho Bộ có sẵn để bắt đầu học.",
                actionText = "+ Tạo bộ từ mới",
                onActionClick = {}
            )
        }
    }
}
