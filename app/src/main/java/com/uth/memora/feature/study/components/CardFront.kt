package com.uth.memora.feature.study.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.feature.common.MemoraSectionKicker
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Mặt trước thẻ Flashcard theo Figma (Node 2104:23 & 2101:15 - Mục 6.5 & 6.6):
 * - Hiển thị badge cấp độ ghi nhớ (RetentionStage Cấp 0..5)
 * - Từ vựng tiếng Anh in đậm cỡ lớn (displayLarge)
 * - Phiên âm IPA + nút loa phát âm (onSpeakClick) + loại từ (PartOfSpeech.abbr)
 * - Khung ẩn nghĩa bên dưới kèm gợi ý "Chạm thẻ để xem nghĩa"
 */
@Composable
fun CardFront(
    card: Card,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp)
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Hàng đầu: Badge cấp độ ghi nhớ (RetentionStage)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemoraStatusBadge(stage = card.retentionStage)

            if (card.partOfSpeech != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "${card.partOfSpeech.labelVi} (${card.partOfSpeech.abbr})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Khối trung tâm: Từ vựng + Phiên âm IPA + Nút loa phát âm (Figma 2059:405)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.word,
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (card.phonetic.isNotBlank()) {
                    Text(
                        text = card.phonetic,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Khối gợi ý ẩn nghĩa phía dưới khớp Figma 2059:413..423
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MemoraSectionKicker(text = "NGHĨA TIẾNG VIỆT")
                Text(
                    text = "Chưa xem",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box( contentAlignment = Alignment.Center ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Chạm thẻ để xem nghĩa và câu ví dụ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(name = "CardFront - Dark", showBackground = true)
@Composable
private fun CardFrontDarkPreview() {
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            CardFront(
                card = Card(
                    id = "card_1",
                    deckId = "deck_everyday",
                    word = "improve",
                    meaning = "cải thiện, nâng cao",
                    phonetic = "/ɪmˈpruːv/",
                    partOfSpeech = PartOfSpeech.VERB,
                    example = "Reading books helps improve your vocabulary.",
                    state = CardState.NEW
                ),
                onSpeakClick = {}
            )
        }
    }
}

@Preview(name = "CardFront - Light", showBackground = true)
@Composable
private fun CardFrontLightPreview() {
    MemoraTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            CardFront(
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
