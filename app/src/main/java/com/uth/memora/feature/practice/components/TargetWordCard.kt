package com.uth.memora.feature.practice.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.feature.common.MemoraSectionCard
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Thẻ hiển thị từ vựng mục tiêu cho tab Luyện đặt câu (Figma Node 2061:119 · Mục 6.7):
 * - Khung thẻ viền màu primary chuẩn Figma (Node 2063:11)
 * - Nhãn "Từ flashcard" và chip chỉ số "Từ x/5"
 * - Từ mục tiêu cỡ lớn nổi bật (displayLarge) kèm nút loa phát âm
 * - Phiên âm IPA, loại từ + nghĩa tiếng Việt và nút "Đổi từ khác" (Node 2212:89)
 */
@Composable
fun TargetWordCard(
    card: Card,
    currentIndex: Int,
    totalCount: Int,
    onSpeakClick: () -> Unit,
    onNextCard: () -> Unit,
    modifier: Modifier = Modifier,
    isNextEnabled: Boolean = totalCount > 1
) {
    val safeTotal = totalCount.coerceAtLeast(1)
    val displayIndex = (currentIndex + 1).coerceIn(1, safeTotal)
    val meaningDisplayText = if (card.partOfSpeech != null) {
        "${card.partOfSpeech.labelVi}: ${card.meaning}"
    } else {
        card.meaning
    }

    MemoraSectionCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        borderColor = MaterialTheme.colorScheme.primary
    ) {
        // Hàng đầu: Nhãn "Từ flashcard" và chip chỉ số "Từ 1/5" (Figma 2063:12..15 & 2212:88)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Từ flashcard",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            MemoraStatusBadge(
                text = "Từ $displayIndex/$safeTotal",
                accentColor = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hàng chính: Từ mục tiêu to rõ (32sp Bold) + nút loa phát âm (Figma 2063:16 & Mục 6.7)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = card.word,
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (card.phonetic.isNotBlank()) {
                    Text(
                        text = card.phonetic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Nút loa phát âm từ mục tiêu đồng bộ phong cách CardFront (Mục 6.7)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            ) {
                IconButton(
                    onClick = onSpeakClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Phát âm từ ${card.word}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Nghĩa tiếng Việt kèm loại từ (Figma 2063:17)
        Text(
            text = meaningDisplayText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Hàng cuối: Nút pill "Đổi từ khác" chuyển sang từ kế tiếp (Figma 2212:89 · Mục 6.7)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val switchContainerColor = if (isNextEnabled) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
            val switchContentColor = if (isNextEnabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            }

            Surface(
                onClick = onNextCard,
                enabled = isNextEnabled,
                shape = RoundedCornerShape(16.dp),
                color = switchContainerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isNextEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier.semantics {
                    role = Role.Button
                    contentDescription = "Đổi từ mục tiêu khác"
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = switchContentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Đổi từ khác",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = switchContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(name = "TargetWordCard - Dark (Figma 2061:119)", showBackground = true)
@Composable
private fun TargetWordCardDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TargetWordCard(
                card = Card(
                    id = "card_narrative",
                    deckId = "deck_everyday",
                    word = "Narrative",
                    phonetic = "/ˈnær.ə.tɪv/",
                    partOfSpeech = PartOfSpeech.NOUN,
                    meaning = "câu chuyện, lời kể, một dạng văn hoặc phim kể về một sự việc.",
                    example = "The movie is a gripping narrative about resilience.",
                    state = CardState.LEARNING
                ),
                currentIndex = 0,
                totalCount = 5,
                onSpeakClick = {},
                onNextCard = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "TargetWordCard - Light", showBackground = true)
@Composable
private fun TargetWordCardLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            TargetWordCard(
                card = Card(
                    id = "card_resilient",
                    deckId = "deck_everyday",
                    word = "Resilient",
                    phonetic = "/rɪˈzɪl.i.ənt/",
                    partOfSpeech = PartOfSpeech.ADJECTIVE,
                    meaning = "kiên cường, có khả năng phục hồi nhanh sau khó khăn.",
                    example = "Children are remarkably resilient in the face of change.",
                    state = CardState.REVIEW
                ),
                currentIndex = 2,
                totalCount = 5,
                onSpeakClick = {},
                onNextCard = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
