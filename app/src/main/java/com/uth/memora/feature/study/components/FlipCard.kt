package com.uth.memora.feature.study.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Khung thẻ Flashcard lật 3D theo trục Y chuẩn Figma (Node 2059:399 & 2059:737 - Mục 6.5 & 6.6):
 * - Sử dụng animateFloatAsState(rotationY = 0f <-> 180f) kèm cameraDistance = 12f * density
 * - Khi rotationY <= 90f hiển thị front(), khi > 90f hiển thị back() với rotationY = 180f chống ngược chữ
 */
@Composable
fun FlipCard(
    isFlipped: Boolean,
    onFlip: () -> Unit,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "memora_flip_card_rotation"
    )

    Surface(
        onClick = onFlip,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))
    ) {
        if (rotation <= 90f) {
            Box(modifier = Modifier.fillMaxWidth()) {
                front()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationY = 180f
                    }
            ) {
                back()
            }
        }
    }
}

@Preview(name = "FlipCard Front - Dark", showBackground = true)
@Composable
private fun FlipCardFrontDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FlipCard(
                isFlipped = false,
                onFlip = {},
                modifier = Modifier.padding(16.dp),
                front = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mặt trước: improve",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                back = {}
            )
        }
    }
}

@Preview(name = "FlipCard Back - Light", showBackground = true)
@Composable
private fun FlipCardBackLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            FlipCard(
                isFlipped = true,
                onFlip = {},
                modifier = Modifier.padding(16.dp),
                front = {},
                back = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mặt sau: cải thiện, nâng cao",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    }
}
