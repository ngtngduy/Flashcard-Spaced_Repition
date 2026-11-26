package com.uth.memora.feature.practice.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.uth.memora.feature.theme.ErrorContainerLight
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.PrimaryContainerLight
import com.uth.memora.feature.theme.StatusErrorLight
import com.uth.memora.feature.theme.SurfaceVariantLight
import com.uth.memora.integration.speech.SpeechUiState

/**
 * Nút Micro nhận diện giọng nói theo thiết kế Figma Node 2230:194 (Mục 6.7).
 * Tự nhận biết trạng thái đang lắng nghe từ [SpeechUiState]:
 * - [SpeechUiState.Idle] / [SpeechUiState.Result] / [SpeechUiState.Error]: bấm gọi [onStartClick].
 * - [SpeechUiState.Listening] / [SpeechUiState.PartialResult]: bấm gọi [onStopClick].
 */
@Composable
fun MicButton(
    speechState: SpeechUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showVoiceWave: Boolean = true
) {
    val isListening = speechState is SpeechUiState.Listening ||
        speechState is SpeechUiState.PartialResult

    MicButton(
        isListening = isListening,
        onStartClick = onStartClick,
        onStopClick = onStopClick,
        modifier = modifier,
        enabled = enabled,
        showVoiceWave = showVoiceWave
    )
}

/**
 * Nút Micro không trạng thái khớp Figma Node 2230:194 (Mục 6.7):
 * - Default (Idle): nền tròn 36.dp màu trung tính (`#F1F5F9`), icon micro xanh nhấn.
 * - Hover/Pressed: nền chuyển dịu sang `#DBEAFE`.
 * - Listening/Recording: nền nhấn đỏ dịu (`#FEE2E2`), icon & sóng âm 5 thanh (`#DC2626`),
 *   kèm vòng gợn sóng nhẹ `infiniteRepeatable` (scale + alpha pulse) quanh nút.
 */
@Composable
fun MicButton(
    isListening: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showVoiceWave: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val targetContainerColor = when {
        isListening -> ErrorContainerLight
        isPressed || isHovered -> PrimaryContainerLight
        else -> SurfaceVariantLight
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = COLOR_ANIMATION_MS),
        label = "micButtonContainerColor"
    )

    val targetIconColor = if (isListening) {
        StatusErrorLight
    } else {
        MaterialTheme.colorScheme.primary
    }
    val iconColor by animateColorAsState(
        targetValue = targetIconColor,
        animationSpec = tween(durationMillis = COLOR_ANIMATION_MS),
        label = "micButtonIconColor"
    )

    val actionDescription = if (isListening) {
        "Dừng ghi âm giọng nói"
    } else {
        "Đọc câu tiếng Anh bằng giọng nói"
    }

    Row(
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Cụm 5 thanh sóng âm đỏ bên trái nút micro khi đang thu âm (Figma 2230:174 & 2230:188)
        AnimatedVisibility(
            visible = showVoiceWave && isListening,
            enter = fadeIn(animationSpec = tween(durationMillis = COLOR_ANIMATION_MS)),
            exit = fadeOut(animationSpec = tween(durationMillis = COLOR_ANIMATION_MS))
        ) {
            VoiceWaveIndicator()
        }

        // Vùng chạm chuẩn 48.dp bao quanh nút 36.dp và vòng gợn sóng pulse (Mục 6.7 & 11)
        Box(
            modifier = Modifier
                .size(TOUCH_TARGET_SIZE)
                .semantics { contentDescription = actionDescription }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = 24.dp),
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        if (isListening) onStopClick() else onStartClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isListening) {
                MicPulseRing()
            }

            Box(
                modifier = Modifier
                    .size(BUTTON_VISUAL_SIZE)
                    .clip(CircleShape)
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(MIC_ICON_SIZE)
                )
            }
        }
    }
}

/**
 * Hiệu ứng vòng gợn sóng nhẹ (scale + alpha pulse) lặp vô hạn quanh nút Micro khi đang thu âm (Mục 6.7).
 */
@Composable
private fun MicPulseRing() {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulseTransition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PULSE_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "micPulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = PULSE_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "micPulseAlpha"
    )

    Box(
        modifier = Modifier
            .size(BUTTON_VISUAL_SIZE)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
                alpha = pulseAlpha
            }
            .clip(CircleShape)
            .background(StatusErrorLight)
    )
}

/**
 * Cụm 5 thanh sóng âm màu đỏ mô phỏng đúng 2 keyframe Recording & Recording pulse trên Figma 2230:194.
 */
@Composable
private fun VoiceWaveIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voiceWaveTransition")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = WAVE_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceWaveProgress"
    )

    Row(
        modifier = modifier.height(WAVE_MAX_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WAVE_BAR_SPACING)
    ) {
        WaveBarHeightsA.indices.forEach { index ->
            val barHeight = lerp(WaveBarHeightsA[index], WaveBarHeightsB[index], waveProgress)
            Box(
                modifier = Modifier
                    .width(WAVE_BAR_WIDTH)
                    .height(barHeight)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(StatusErrorLight)
            )
        }
    }
}

private val BUTTON_VISUAL_SIZE: Dp = 36.dp
private val TOUCH_TARGET_SIZE: Dp = 48.dp
private val MIC_ICON_SIZE: Dp = 20.dp
private val WAVE_MAX_HEIGHT: Dp = 20.dp
private val WAVE_BAR_WIDTH: Dp = 3.dp
private val WAVE_BAR_SPACING: Dp = 3.dp

private const val COLOR_ANIMATION_MS: Int = 180
private const val PULSE_DURATION_MS: Int = 1100
private const val WAVE_DURATION_MS: Int = 520
private const val DISABLED_ALPHA: Float = 0.5f

// Chiều cao 5 thanh sóng âm trích từ Figma Node 2230:174 (Recording) & 2230:188 (Recording pulse)
private val WaveBarHeightsA = listOf(8.dp, 14.dp, 20.dp, 12.dp, 6.dp)
private val WaveBarHeightsB = listOf(18.dp, 8.dp, 12.dp, 20.dp, 14.dp)

@Preview(name = "MicButton States - Dark (Figma 2230:194)", showBackground = true)
@Composable
private fun MicButtonDarkStatesPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MicButtonPreviewCard(
                    label = "Default (Idle)",
                    speechState = SpeechUiState.Idle,
                    enabled = true
                )
                MicButtonPreviewCard(
                    label = "Listening / Recording",
                    speechState = SpeechUiState.Listening,
                    enabled = true
                )
                MicButtonPreviewCard(
                    label = "PartialResult (Đang nói)",
                    speechState = SpeechUiState.PartialResult("The movie is a narrative..."),
                    enabled = true
                )
                MicButtonPreviewCard(
                    label = "Disabled (Đang chấm AI)",
                    speechState = SpeechUiState.Idle,
                    enabled = false
                )
            }
        }
    }
}

@Preview(name = "MicButton States - Light", showBackground = true)
@Composable
private fun MicButtonLightStatesPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MicButtonPreviewCard(
                    label = "Default (Idle)",
                    speechState = SpeechUiState.Idle,
                    enabled = true
                )
                MicButtonPreviewCard(
                    label = "Listening / Recording",
                    speechState = SpeechUiState.Listening,
                    enabled = true
                )
            }
        }
    }
}

@Composable
private fun MicButtonPreviewCard(
    label: String,
    speechState: SpeechUiState,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        MicButton(
            speechState = speechState,
            onStartClick = {},
            onStopClick = {},
            enabled = enabled
        )
    }
}
