package com.uth.memora.feature.decks.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Deck
import com.uth.memora.feature.decks.DeckSummaryItem
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.StatusSuccess
import com.uth.memora.feature.theme.SurfaceLight
import java.util.Locale

/**
 * Khu vực hiển thị danh sách Bộ từ có sẵn của ứng dụng (Figma 8:323 · Node 2078:101 & Mục 6.2).
 *
 * Quy tắc sao chép 1 lần:
 * - Kiểm tra `activeDecks.firstOrNull { it.sourcePrebuiltId == deck.id }` (thông qua [DeckSummaryItem.importedUserDeckId]).
 * - Nếu chưa sao chép: hiển thị nút "Sao chép vào bộ của tôi" để gọi `deckRepository.importPrebuiltDeck(deck.id)`.
 * - Nếu đã sao chép: khóa sao chép trùng lặp, hiển thị trạng thái "Đã có trong bộ của tôi" và cho phép mở nhanh bộ từ đã sao chép.
 */
@Composable
fun PrebuiltDecksTab(
    prebuiltItems: List<DeckSummaryItem>,
    activeDecks: List<Deck>,
    importingPrebuiltIds: Set<String>,
    onImportPrebuiltDeck: (String) -> Unit,
    onOpenImportedDeck: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        prebuiltItems.forEach { item ->
            val matchedImportedDeckId = item.importedUserDeckId
                ?: activeDecks.firstOrNull { it.sourcePrebuiltId == item.deck.id }?.id
            val isImporting = item.deck.id in importingPrebuiltIds

            PrebuiltDeckCard(
                item = item.copy(importedUserDeckId = matchedImportedDeckId),
                isImporting = isImporting,
                onImportClick = { onImportPrebuiltDeck(item.deck.id) },
                onOpenImportedClick = {
                    matchedImportedDeckId?.let(onOpenImportedDeck)
                }
            )
        }
    }
}

/**
 * Thẻ hiển thị một Bộ từ có sẵn của ứng dụng theo Figma 8:323 (`2078:105`, `2078:122`).
 */
@Composable
fun PrebuiltDeckCard(
    item: DeckSummaryItem,
    isImporting: Boolean,
    onImportClick: () -> Unit,
    onOpenImportedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarInitial = remember(item.deck.name) {
        item.deck.name.trim().firstOrNull()?.uppercase(Locale.getDefault()) ?: "A"
    }
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val importedBadgeColor = if (isLight) Color(0xFF15803D) else StatusSuccess

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(enabled = !isImporting) {
                if (item.isAlreadyImported) {
                    onOpenImportedClick()
                } else {
                    onImportClick()
                }
            },
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = avatarInitial,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.deck.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Bộ từ mẫu • ${item.totalCardsCount} từ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (item.isAlreadyImported) {
                        importedBadgeColor.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (item.isAlreadyImported) {
                        BorderStroke(1.dp, importedBadgeColor.copy(alpha = 0.35f))
                    } else {
                        null
                    }
                ) {
                    Text(
                        text = if (item.isAlreadyImported) "Đã sao chép" else "Có sẵn",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (item.isAlreadyImported) {
                            importedBadgeColor
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (item.deck.description.isNotBlank()) {
                Text(
                    text = item.deck.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.isAlreadyImported) {
                    TextButton(
                        onClick = onOpenImportedClick,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = importedBadgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Đã có trong bộ của tôi · Mở",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Mở bộ từ đã sao chép",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    TextButton(
                        onClick = onImportClick,
                        enabled = !isImporting,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Đang sao chép...",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sao chép vào bộ của tôi",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Text(
                    text = "${item.totalCardsCount} từ • Cập nhật hôm nay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(name = "PrebuiltDecksTab - Dark", widthDp = 360, showBackground = true)
@Composable
private fun PrebuiltDecksTabDarkPreview() {
    val samplePrebuilt = listOf(
        DeckSummaryItem(
            deck = Deck(
                id = "prebuilt_academic",
                name = "Academic Words",
                description = "Bộ từ có sẵn của app, chứa từ vựng học thuật thường dùng trong bài thi và tài liệu học thuật."
            ),
            totalCardsCount = 240,
            isPrebuilt = true,
            importedUserDeckId = null
        ),
        DeckSummaryItem(
            deck = Deck(
                id = "prebuilt_idioms",
                name = "Idioms & Phrases",
                description = "Bộ từ có sẵn của app, chứa cụm từ và thành ngữ thường dùng trong giao tiếp và bài thi tiếng Anh."
            ),
            totalCardsCount = 156,
            isPrebuilt = true,
            importedUserDeckId = "user_imported_idioms"
        )
    )
    MemoraTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            PrebuiltDecksTab(
                prebuiltItems = samplePrebuilt,
                activeDecks = listOf(
                    Deck(
                        id = "user_imported_idioms",
                        name = "Idioms & Phrases",
                        sourcePrebuiltId = "prebuilt_idioms"
                    )
                ),
                importingPrebuiltIds = emptySet(),
                onImportPrebuiltDeck = {},
                onOpenImportedDeck = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
