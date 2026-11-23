package com.uth.memora.feature.study.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.feature.common.MemoraSectionKicker
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.common.shimmer.shimmerEffect
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Mặt sau thẻ Flashcard theo Figma (Node 2101:14 & 2101:16 - Mục 6.3, 6.5, 6.6 & 11):
 * - Từ vựng + phiên âm + nút loa nhỏ ở phần đầu
 * - Vạch ngăn
 * - Nghĩa tiếng Việt nổi bật trong khung viền nhấn
 * - Câu ví dụ in nghiêng trong khung nền nhẹ
 * - Ảnh minh họa tùy chọn (chỉ hiện khi localImagePath hoặc cloudImageUrl != null)
 *   dùng Coil SubcomposeAsyncImage với Modifier.shimmerEffect() bo góc 12.dp chống giật layout
 */
@Composable
fun CardBack(
    card: Card,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageModel = card.localImagePath?.takeIf { it.isNotBlank() }
        ?: card.cloudImageUrl?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hàng metadata đầu thẻ: Badge cấp độ ghi nhớ + Nhãn "Đã lật" (Figma 2059:738 & 2059:758)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoraStatusBadge(stage = card.retentionStage)

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = "Đã lật",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Hàng từ vựng + phiên âm + loại từ + nút loa nhỏ ở trên
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
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                val phoneticAndPos = buildList {
                    if (card.phonetic.isNotBlank()) add(card.phonetic)
                    if (card.partOfSpeech != null) add("${card.partOfSpeech.labelVi} (${card.partOfSpeech.abbr})")
                }.joinToString(" · ")

                if (phoneticAndPos.isNotBlank()) {
                    Text(
                        text = phoneticAndPos,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                IconButton(
                    onClick = onSpeakClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Phát âm từ vựng",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Khối Nghĩa tiếng Việt nổi bật (Figma 2059:753..761)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MemoraSectionKicker(text = "NGHĨA TIẾNG VIỆT")

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Text(
                    text = card.meaning,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        }

        // Khối Câu ví dụ in nghiêng trong khung nền nhẹ (Figma 2059:762)
        if (card.example.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MemoraSectionKicker(text = "CÂU VÍ DỤ")

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "“${card.example}”",
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Khối Ảnh minh họa tùy chọn với SubcomposeAsyncImage + Shimmer bo góc 12.dp (Figma 2199:6, Mục 6.3 & 11)
        if (imageModel != null) {
            Spacer(modifier = Modifier.height(2.dp))
            SubcomposeAsyncImage(
                model = imageModel,
                contentDescription = "Ảnh minh họa cho từ ${card.word}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp)),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                },
                error = {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrokenImage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Không tải được ảnh minh họa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Preview(name = "CardBack - Dark", showBackground = true)
@Composable
private fun CardBackDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            CardBack(
                card = Card(
                    id = "card_2",
                    deckId = "deck_everyday",
                    word = "confident",
                    meaning = "tự tin",
                    phonetic = "/ˈkɒn.fɪ.dənt/",
                    partOfSpeech = PartOfSpeech.ADJECTIVE,
                    example = "She feels confident when speaking English.",
                    state = CardState.REVIEW,
                    interval = 7
                ),
                onSpeakClick = {}
            )
        }
    }
}

@Preview(name = "CardBack - Light", showBackground = true)
@Composable
private fun CardBackLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            CardBack(
                card = Card(
                    id = "card_1",
                    deckId = "deck_everyday",
                    word = "improve",
                    meaning = "cải thiện, nâng cao",
                    phonetic = "/ɪmˈpruːv/",
                    partOfSpeech = PartOfSpeech.VERB,
                    example = "Reading books helps improve your vocabulary.",
                    state = CardState.LEARNING
                ),
                onSpeakClick = {}
            )
        }
    }
}
