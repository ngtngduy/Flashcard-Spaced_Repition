package com.uth.memora.feature.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.feature.common.ConfirmDialog
import com.uth.memora.feature.common.EmptyState
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.theme.MemoraTheme
import com.uth.memora.feature.theme.SurfaceLight

/**
 * Màn hình Chi tiết Bộ từ (Deck Detail - Figma Node 2101:11, Mục 6.2 & 7.5).
 * Hiển thị:
 * - Thông tin định danh bộ từ (monogram, tên, mô tả, số lượng từ)
 * - Khung tiến độ học tập (% từ đạt Cấp 5 – Thành thạo + phân bố 6 cấp độ ghi nhớ)
 * - Nút "Ôn tập bộ thẻ" ("Học ngay") & "+ Thêm thẻ"
 * - Thanh tìm kiếm từ vựng không dấu + bộ lọc theo cấp độ ghi nhớ
 * - Danh sách thẻ từ vựng với thao tác Sửa / Xóa mềm
 */
@Composable
fun DeckDetailScreen(
    uiState: DeckDetailUiState,
    onBack: () -> Unit,
    onStartStudy: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (Card) -> Unit,
    onRequestDeleteCard: (Card) -> Unit,
    onConfirmDeleteCard: () -> Unit,
    onDismissDeleteCardDialog: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onStageFilterSelected: (RetentionStage?) -> Unit,
    modifier: Modifier = Modifier,
    onRequestResetCardProgress: (Card) -> Unit = {},
    onConfirmResetCardProgress: () -> Unit = {},
    onDismissResetCardDialog: () -> Unit = {},
    onRequestResetDeckProgress: () -> Unit = {},
    onConfirmResetDeckProgress: () -> Unit = {},
    onDismissResetDeckDialog: () -> Unit = {},
    onRetryLoad: () -> Unit = {}
) {
    uiState.deletingCard?.let { cardToDelete ->
        ConfirmDialog(
            title = "Xóa thẻ từ vựng?",
            message = "Bạn chắc chắn muốn xóa thẻ \"${cardToDelete.word}\" khỏi bộ từ này?",
            confirmText = if (uiState.isDeletingCard) "Đang xóa..." else "Xóa thẻ",
            dismissText = "Hủy",
            isDestructive = true,
            onConfirm = {
                if (!uiState.isDeletingCard) onConfirmDeleteCard()
            },
            onDismiss = {
                if (!uiState.isDeletingCard) onDismissDeleteCardDialog()
            }
        )
    }

    uiState.resettingCard?.let { cardToReset ->
        ConfirmDialog(
            title = "Đặt lại tiến độ thẻ?",
            message = "Toàn bộ tiến độ SM-2 của thẻ \"${cardToReset.word}\" sẽ quay về Cấp 0 – Chưa học. Bạn chắc chắn muốn đặt lại?",
            confirmText = if (uiState.isResettingCard) "Đang đặt lại..." else "Đặt lại tiến độ",
            dismissText = "Hủy",
            isDestructive = false,
            onConfirm = {
                if (!uiState.isResettingCard) onConfirmResetCardProgress()
            },
            onDismiss = {
                if (!uiState.isResettingCard) onDismissResetCardDialog()
            }
        )
    }

    if (uiState.isResetDeckDialogVisible) {
        val deckTitle = uiState.deck?.name ?: "bộ từ này"
        ConfirmDialog(
            title = "Đặt lại toàn bộ tiến độ bộ?",
            message = "Toàn bộ ${uiState.totalCardsCount} thẻ trong bộ \"$deckTitle\" sẽ quay về Cấp 0 – Chưa học. Hành động này sẽ làm mới toàn bộ lịch ôn SM-2 của bộ.",
            confirmText = if (uiState.isResettingDeck) "Đang đặt lại..." else "Đặt lại toàn bộ",
            dismissText = "Hủy",
            isDestructive = true,
            onConfirm = {
                if (!uiState.isResettingDeck) onConfirmResetDeckProgress()
            },
            onDismiss = {
                if (!uiState.isResettingDeck) onDismissResetDeckDialog()
            }
        )
    }

    var topMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Thanh điều hướng trên cùng (Figma 2083:63)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại danh sách bộ từ",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Bộ từ của tôi",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(onClick = { topMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Tùy chọn bộ từ",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = topMenuExpanded,
                        onDismissRequest = { topMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Thêm thẻ mới") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Thêm thẻ mới vào bộ"
                                )
                            },
                            onClick = {
                                topMenuExpanded = false
                                onAddCard()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Đặt lại toàn bộ tiến độ bộ") },
                            enabled = uiState.totalCardsCount > 0,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Đặt lại toàn bộ tiến độ bộ từ"
                                )
                            },
                            onClick = {
                                topMenuExpanded = false
                                onRequestResetDeckProgress()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 20.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Thông tin định danh bộ từ (Figma 2083:68)
                item(key = "deck_identity") {
                    DeckIdentityHeader(
                        deckName = uiState.deck?.name ?: "Bộ từ vựng",
                        description = uiState.deck?.description.orEmpty(),
                        totalCardsCount = uiState.totalCardsCount
                    )
                }

                // Thông báo phản hồi hoặc lỗi
                uiState.feedbackMessage?.let { msg ->
                    item(key = "deck_detail_feedback") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                uiState.errorMessage?.let { err ->
                    item(key = "deck_detail_error") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onRetryLoad) {
                                    Text(
                                        text = "Thử lại",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Khung Tiến độ học tập & Phân bố 6 cấp độ ghi nhớ (Figma 2083:74)
                item(key = "deck_learning_progress") {
                    DeckRetentionDistributionCard(
                        masteredCount = uiState.masteredCardsCount,
                        totalCardsCount = uiState.totalCardsCount,
                        masteredPercentage = uiState.masteredPercentage,
                        stageDistribution = uiState.stageDistribution
                    )
                }

                // Hàng nút hành động: "Ôn tập bộ thẻ" ("Học ngay") & "+ Thêm thẻ" (Figma 2083:101)
                item(key = "deck_action_buttons") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartStudy,
                            enabled = uiState.totalCardsCount > 0,
                            modifier = Modifier
                                .weight(1.35f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val studyButtonLabel = when {
                                uiState.dueCardsCount == 0 && uiState.newCardsCount > 0 ->
                                    "Học thẻ mới (${uiState.newCardsCount})"
                                uiState.dueCardsCount > 0 ->
                                    "Ôn tập bộ thẻ (${uiState.dueCardsCount})"
                                else -> "Ôn tập bộ thẻ"
                            }
                            Text(
                                text = studyButtonLabel,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onAddCard,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Thêm thẻ",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Thanh tìm kiếm từ vựng & nút lọc cấp độ (Figma 2083:105)
                item(key = "deck_vocab_search_filter") {
                    DeckVocabSearchAndFilterRow(
                        searchQuery = uiState.searchQuery,
                        selectedStageFilter = uiState.selectedStageFilter,
                        onSearchQueryChange = onSearchQueryChange,
                        onClearSearch = onClearSearch,
                        onStageFilterSelected = onStageFilterSelected
                    )
                }

                // Tiêu đề danh sách từ vựng & bộ lọc đang chọn (Figma 2083:111)
                item(key = "deck_vocab_list_heading") {
                    DeckVocabListHeading(
                        filteredCount = uiState.filteredCards.size,
                        selectedStageFilter = uiState.selectedStageFilter,
                        onStageFilterSelected = onStageFilterSelected
                    )
                }

                if (uiState.isLoading) {
                    items(count = 3, key = { "deck_detail_shimmer_$it" }) {
                        DeckCardShimmerSkeleton()
                    }
                } else if (uiState.filteredCards.isEmpty()) {
                    item(key = "deck_cards_empty") {
                        val isFiltering = uiState.searchQuery.isNotBlank() || uiState.selectedStageFilter != null
                        EmptyState(
                            title = if (isFiltering) {
                                "Không tìm thấy thẻ từ vựng phù hợp"
                            } else {
                                "Bộ từ này chưa có thẻ nào"
                            },
                            description = if (isFiltering) {
                                "Hãy thử xóa bộ lọc hoặc tìm với từ khóa khác."
                            } else {
                                "Hãy thêm thẻ từ vựng đầu tiên vào bộ để bắt đầu học với thuật toán SM-2."
                            },
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            actionText = "+ Thêm thẻ mới",
                            onActionClick = onAddCard
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredCards,
                        key = { "deck_card_${it.id}" }
                    ) { card ->
                        DeckVocabularyCardItem(
                            card = card,
                            onEditClick = { onEditCard(card) },
                            onResetProgressClick = { onRequestResetCardProgress(card) },
                            onDeleteClick = { onRequestDeleteCard(card) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckIdentityHeader(
    deckName: String,
    description: String,
    totalCardsCount: Int,
    modifier: Modifier = Modifier
) {
    val monogram = remember(deckName) {
        deckName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    }
    val subtitleText = remember(description, totalCardsCount) {
        val descPart = description.trim().ifEmpty { "Bộ từ vựng" }
        "$descPart • $totalCardsCount từ • Của tôi"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = monogram,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = deckName,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Khung tiến độ học tập & Phân bố flashcard theo 6 cấp độ ghi nhớ chuẩn Figma 2101:11 (`2083:74`).
 */
@Composable
fun DeckRetentionDistributionCard(
    masteredCount: Int,
    totalCardsCount: Int,
    masteredPercentage: Int,
    stageDistribution: Map<RetentionStage, Int>,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.surface == SurfaceLight
    val masteredAccent = if (isLight) Color(0xFF15803D) else Color(0xFF69D3AA)

    Surface(
        modifier = modifier.fillMaxWidth(),
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
            // Hàng hoàn thành: "X / Y từ hoàn thành" + "Z%" (Figma 2083:75)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$masteredCount / $totalCardsCount từ hoàn thành",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$masteredPercentage%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = masteredAccent
                )
            }

            // Thanh phân bố màu đa đoạn (Figma 2083:78)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (totalCardsCount <= 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    RetentionStage.entries.forEach { stage ->
                        val count = stageDistribution[stage] ?: 0
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .weight(count.toFloat())
                                    .fillMaxHeight()
                                    .background(retentionStageAccentColor(stage, isLight))
                            )
                        }
                    }
                }
            }

            Text(
                text = "Phân bố flashcard · 6 cấp độ ghi nhớ",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // 6 hàng cấp độ ghi nhớ (Figma 2083:84)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RetentionStage.entries.forEach { stage ->
                    val count = stageDistribution[stage] ?: 0
                    val stageColor = retentionStageAccentColor(stage, isLight)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(stageColor)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stage.titleVi,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stage.intervalDescriptionVi,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = if (stage == RetentionStage.LEVEL_0_UNLEARNED && count == 0) "—" else "$count",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = stageColor
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Chỉ “Cấp 5 – Thành thạo” được tính là hoàn thành.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Cấp 0: thẻ mới chưa qua lượt học đầu tiên.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeckVocabSearchAndFilterRow(
    searchQuery: String,
    selectedStageFilter: RetentionStage?,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onStageFilterSelected: (RetentionStage?) -> Unit,
    modifier: Modifier = Modifier
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            placeholder = {
                Text(
                    text = "Tìm từ hoặc nghĩa...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm từ vựng trong bộ",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Xóa từ khóa tìm kiếm",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Box {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { filterMenuExpanded = true },
                shape = RoundedCornerShape(10.dp),
                color = if (selectedStageFilter != null) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selectedStageFilter != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Lọc theo cấp độ ghi nhớ",
                        tint = if (selectedStageFilter != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            StageFilterDropdownMenu(
                expanded = filterMenuExpanded,
                selectedStage = selectedStageFilter,
                onDismiss = { filterMenuExpanded = false },
                onSelectStage = { stage ->
                    filterMenuExpanded = false
                    onStageFilterSelected(stage)
                }
            )
        }
    }
}

@Composable
private fun DeckVocabListHeading(
    filteredCount: Int,
    selectedStageFilter: RetentionStage?,
    onStageFilterSelected: (RetentionStage?) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Từ vựng ($filteredCount)",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedStageFilter?.titleVi ?: "Tất cả cấp độ",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Chọn cấp độ lọc",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            StageFilterDropdownMenu(
                expanded = menuExpanded,
                selectedStage = selectedStageFilter,
                onDismiss = { menuExpanded = false },
                onSelectStage = { stage ->
                    menuExpanded = false
                    onStageFilterSelected(stage)
                }
            )
        }
    }
}

@Composable
private fun StageFilterDropdownMenu(
    expanded: Boolean,
    selectedStage: RetentionStage?,
    onDismiss: () -> Unit,
    onSelectStage: (RetentionStage?) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = "Tất cả cấp độ",
                    fontWeight = if (selectedStage == null) FontWeight.Bold else FontWeight.Normal
                )
            },
            onClick = { onSelectStage(null) }
        )
        RetentionStage.entries.forEach { stage ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stage.titleVi,
                        fontWeight = if (selectedStage == stage) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = { onSelectStage(stage) }
            )
        }
    }
}

/**
 * Thẻ hiển thị một từ vựng trong màn Chi tiết Bộ từ kèm huy hiệu cấp độ ghi nhớ [RetentionStage.titleVi]
 * và các tùy chọn Sửa / Đặt lại tiến độ thẻ / Xóa mềm theo Figma 2101:11 (`2083:117`).
 */
@Composable
fun DeckVocabularyCardItem(
    card: Card,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onResetProgressClick: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val posPrefix = card.partOfSpeech?.abbr?.let { "$it.  " }.orEmpty()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onEditClick),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = card.word,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (card.phonetic.isNotBlank()) {
                            Text(
                                text = card.phonetic,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = "$posPrefix${card.meaning}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Tùy chọn thẻ ${card.word}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa thẻ") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Đặt lại tiến độ thẻ") },
                            enabled = card.retentionStage != RetentionStage.LEVEL_0_UNLEARNED,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onResetProgressClick()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Xóa thẻ",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            // Huy hiệu cấp độ ghi nhớ RetentionStage.titleVi (Figma 2083:121..2083:158 & Mục 7.5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MemoraStatusBadge(stage = card.retentionStage)

                if (card.interval > 0) {
                    Text(
                        text = "Chu kỳ: ${card.interval} ngày",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

internal fun retentionStageAccentColor(stage: RetentionStage, isLight: Boolean): Color {
    return when (stage) {
        RetentionStage.LEVEL_0_UNLEARNED -> if (isLight) Color(0xFF64748B) else Color(0xFF737B89)
        RetentionStage.LEVEL_1_STARTING -> if (isLight) Color(0xFF475569) else Color(0xFFB9BEC8)
        RetentionStage.LEVEL_2_SHORT_TERM -> if (isLight) Color(0xFFB45309) else Color(0xFFF4BE66)
        RetentionStage.LEVEL_3_MID_TERM -> if (isLight) Color(0xFF1D4ED8) else Color(0xFF83AEFF)
        RetentionStage.LEVEL_4_NEAR_MASTERED -> if (isLight) Color(0xFF6D28D9) else Color(0xFFC6A2F8)
        RetentionStage.LEVEL_5_MASTERED -> if (isLight) Color(0xFF15803D) else Color(0xFF69D3AA)
    }
}

@Preview(name = "DeckDetailScreen - Dark", widthDp = 360, showBackground = true)
@Composable
private fun DeckDetailScreenDarkPreview() {
    val sampleCards = listOf(
        Card(
            id = "c1",
            deckId = "d1",
            word = "biodiversity",
            meaning = "sự đa dạng sinh học",
            partOfSpeech = PartOfSpeech.NOUN,
            state = CardState.LEARNING
        ),
        Card(
            id = "c2",
            deckId = "d1",
            word = "sustainable",
            meaning = "bền vững",
            partOfSpeech = PartOfSpeech.ADJECTIVE,
            state = CardState.REVIEW,
            interval = 2
        ),
        Card(
            id = "c3",
            deckId = "d1",
            word = "habitat",
            meaning = "môi trường sống",
            partOfSpeech = PartOfSpeech.NOUN,
            state = CardState.REVIEW,
            interval = 25
        )
    )
    MemoraTheme(darkTheme = true) {
        DeckDetailScreen(
            uiState = DeckDetailUiState(
                deckId = "d1",
                isLoading = false,
                deck = Deck(
                    id = "d1",
                    name = "Everyday English",
                    description = "Giao tiếp hằng ngày"
                ),
                allCards = sampleCards,
                filteredCards = sampleCards,
                stageDistribution = mapOf(
                    RetentionStage.LEVEL_0_UNLEARNED to 0,
                    RetentionStage.LEVEL_1_STARTING to 1,
                    RetentionStage.LEVEL_2_SHORT_TERM to 1,
                    RetentionStage.LEVEL_3_MID_TERM to 0,
                    RetentionStage.LEVEL_4_NEAR_MASTERED to 0,
                    RetentionStage.LEVEL_5_MASTERED to 1
                ),
                masteredCardsCount = 1,
                masteredPercentage = 33
            ),
            onBack = {},
            onStartStudy = {},
            onAddCard = {},
            onEditCard = {},
            onRequestDeleteCard = {},
            onConfirmDeleteCard = {},
            onDismissDeleteCardDialog = {},
            onSearchQueryChange = {},
            onClearSearch = {},
            onStageFilterSelected = {}
        )
    }
}
