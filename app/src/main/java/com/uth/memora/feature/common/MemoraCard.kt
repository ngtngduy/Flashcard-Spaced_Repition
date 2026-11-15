package com.uth.memora.feature.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.theme.MemoraTheme
import java.util.Locale

/**
 * Khung thẻ chuẩn Figma của Memora (Mục 6 & 11):
 * - Surface bo góc 16.dp, nền surface, viền 1.dp màu outlineVariant
 * - Hỗ trợ onClick tùy chọn cho các thẻ có thể bấm vào (như thẻ Bộ từ)
 */
@Composable
fun MemoraSectionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val border = BorderStroke(1.dp, borderColor)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            border = border
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * Nhãn Kicker chữ in hoa nhỏ có letterSpacing nhẹ theo Figma (Mục 6 & 11).
 */
@Composable
fun MemoraSectionKicker(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary
) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
    )
}

@Preview(name = "MemoraCard - Dark", showBackground = true)
@Composable
private fun MemoraCardDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemoraSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {}
                ) {
                    MemoraSectionKicker(text = "BỘ TỪ NỔI BẬT")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tiếng Anh Giao Tiếp Hàng Ngày",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "30 thẻ · 12 thẻ đến hạn ôn",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(name = "MemoraCard - Light", showBackground = true)
@Composable
private fun MemoraCardLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemoraSectionCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {}
                ) {
                    MemoraSectionKicker(text = "BỘ TỪ NỔI BẬT")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tiếng Anh Giao Tiếp Hàng Ngày",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "30 thẻ · 12 thẻ đến hạn ôn",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
