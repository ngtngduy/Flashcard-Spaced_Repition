package com.uth.memora.feature.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryBlue
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.StatusWarning

/**
 * Pill badge hiển thị trạng thái hoặc nhãn phân loại với màu nền nhạt, viền và chữ đồng bộ (Mục 6 & 7.5).
 */
@Composable
fun MemoraStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Overload tiện lợi hiển thị trực tiếp cấp độ ghi nhớ RetentionStage (Cấp 0..5 - Mục 7.5).
 */
@Composable
fun MemoraStatusBadge(
    stage: RetentionStage,
    modifier: Modifier = Modifier
) {
    val stageColor = when (stage) {
        RetentionStage.LEVEL_0_UNLEARNED -> MaterialTheme.colorScheme.onSurfaceVariant
        RetentionStage.LEVEL_1_STARTING -> StatusWarning
        RetentionStage.LEVEL_2_SHORT_TERM -> Color(0xFF38BDF8)
        RetentionStage.LEVEL_3_MID_TERM -> PrimaryBlue
        RetentionStage.LEVEL_4_NEAR_MASTERED -> Color(0xFF14B8A6)
        RetentionStage.LEVEL_5_MASTERED -> StatusSuccess
    }
    MemoraStatusBadge(
        text = stage.titleVi,
        modifier = modifier,
        accentColor = stageColor
    )
}

/**
 * Thanh tiến độ ngang bo tròn mượt dùng trong Trang chính, Chi tiết bộ từ và Phiên học (Mục 6.5 & 6.6).
 */
@Composable
fun MemoraLinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "memora_linear_progress"
    )

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round
    )
}

@Preview(name = "Badges & Progress - Dark", showBackground = true)
@Composable
private fun MemoraBadgesDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RetentionStage.entries.forEach { stage ->
                    MemoraStatusBadge(stage = stage)
                }
                MemoraLinearProgressBar(progress = 0.65f)
            }
        }
    }
}

@Preview(name = "Badges & Progress - Light", showBackground = true)
@Composable
private fun MemoraBadgesLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RetentionStage.entries.forEach { stage ->
                    MemoraStatusBadge(stage = stage)
                }
                MemoraLinearProgressBar(progress = 0.65f)
            }
        }
    }
}
