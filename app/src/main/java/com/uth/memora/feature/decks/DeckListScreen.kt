package com.uth.memora.feature.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.uth.memora.core.model.Deck
import com.uth.memora.feature.common.ConfirmDialog
import com.uth.memora.feature.common.EmptyState
import com.uth.memora.feature.common.shimmer.shimmerEffect
import com.uth.memora.feature.decks.components.DeckFormDialog
import com.uth.memora.feature.decks.components.PrebuiltDeckCard
import com.uth.memora.feature.theme.MemoraTheme
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Màn hình Quản lý Bộ từ (Flashcard Decks - Figma Node 8:323 & Mục 6.2).
 * Gồm các tab lọc ("Bộ từ của tôi", "Bộ có sẵn", "Tất cả"), danh sách thẻ bộ từ,
 * Shimmer skeleton khi đang tải, EmptyState khi danh sách trống và nút "+ Tạo bộ từ".
 */
@Composable
fun DeckListScreen(
    uiState: DeckListUiState,
    onSelectTab: (DeckFilterTab) -> Unit,
    onDeckClick: (String) -> Unit,
    onCreateDeckClick: () -> Unit,
    onEditDeckClick: (Deck) -> Unit,
    onDeleteDeckClick: (Deck) -> Unit,
    onImportPrebuiltDeckClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onDialogNameChange: (String) -> Unit = {},
    onDialogDescriptionChange: (String) -> Unit = {},
    onSubmitDeckForm: () -> Unit = {},
    onDismissDeckFormDialog: () -> Unit = {},
    onConfirmDeleteDeck: () -> Unit = {},
    onDismissDeleteDeckDialog: () -> Unit = {}
) {
    if (uiState.isCreateOrEditDialogVisible) {
        DeckFormDialog(
            isEditMode = uiState.editingDeck != null,
            name = uiState.dialogNameInput,
            description = uiState.dialogDescriptionInput,
            nameError = uiState.dialogNameError,
            descriptionError = uiState.dialogDescriptionError,
            isSaving = uiState.isSavingDeck,
            onNameChange = onDialogNameChange,
            onDescriptionChange = onDialogDescriptionChange,
            onConfirm = onSubmitDeckForm,
            onDismiss = onDismissDeckFormDialog
        )
    }

    uiState.deletingDeck?.let { deckToDelete ->
        val deckCardCount = uiState.userDeckItems
            .firstOrNull { it.deck.id == deckToDelete.id }
            ?.totalCardsCount ?: 0

        ConfirmDialog(
            title = "Xóa bộ từ vựng?",
            message = "Xóa bộ \"${deckToDelete.name}\" sẽ xóa mềm toàn bộ $deckCardCount thẻ từ vựng bên trong. Bạn chắc chắn muốn xóa chứ?",
            confirmText = if (uiState.isDeletingDeck) "Đang xóa..." else "Xóa bộ từ",
            dismissText = "Hủy",
            isDestructive = true,
            onConfirm = {
                if (!uiState.isDeletingDeck) {
                    onConfirmDeleteDeck()
                }
            },
            onDismiss = {
                if (!uiState.isDeletingDeck) {
                    onDismissDeleteDeckDialog()
                }
            }
        )
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateDeckClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Tạo bộ từ mới"
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Tạo bộ từ",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Navbar tiêu đề màn hình (Figma 2078:5)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Flashcard Deck",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 14.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: "Bộ từ của tôi" + Nút "+ Tạo bộ" (Figma 2078:9)
                item(key = "deck_list_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bộ từ của tôi",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )
                        Button(
                            onClick = onCreateDeckClick,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Tạo bộ từ mới",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Tạo bộ",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Thanh tìm kiếm không dấu theo tên bộ từ, mô tả và thẻ từ vựng (Figma 2078:14 & Mục 6.9)
                item(key = "deck_search_bar") {
                    DeckSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClearQuery = onClearSearch
                    )
                }

                // Hàng Tab lọc: Bộ từ của tôi, Bộ có sẵn, Tất cả (Figma 2078:18)
                item(key = "deck_filter_tabs") {
                    DeckFilterTabsRow(
                        selectedTab = uiState.selectedTab,
                        userCount = uiState.userDeckItems.size,
                        prebuiltCount = uiState.prebuiltDeckItems.size,
                        allCount = uiState.totalAllDecksCount,
                        onSelectTab = onSelectTab
                    )
                }

                // Thông báo phản hồi hoặc lỗi nếu có
                uiState.feedbackMessage?.let { msg ->
                    item(key = "deck_feedback_banner") {
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
                    item(key = "deck_error_banner") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                if (uiState.isLoading) {
                    items(count = 4, key = { "deck_shimmer_$it" }) {
                        DeckCardShimmerSkeleton()
                    }
                } else {
                    val showUserSection = uiState.selectedTab == DeckFilterTab.ALL ||
                        uiState.selectedTab == DeckFilterTab.USER
                    val showPrebuiltSection = uiState.selectedTab == DeckFilterTab.ALL ||
                        uiState.selectedTab == DeckFilterTab.PREBUILT

                    val userList = uiState.filteredUserDeckItems
                    val prebuiltList = uiState.filteredPrebuiltDeckItems
                    val cardMatches = uiState.matchingCards
                    val hasCardMatches = uiState.searchQuery.isNotBlank() && cardMatches.isNotEmpty()

                    val isCurrentViewEmpty = when (uiState.selectedTab) {
                        DeckFilterTab.USER -> userList.isEmpty() && !hasCardMatches
                        DeckFilterTab.PREBUILT -> prebuiltList.isEmpty()
                        DeckFilterTab.ALL -> userList.isEmpty() && prebuiltList.isEmpty() && !hasCardMatches
                    }

                    if (isCurrentViewEmpty) {
                        item(key = "deck_empty_state") {
                            val isSearching = uiState.searchQuery.isNotBlank()
                            EmptyState(
                                title = if (isSearching) {
                                    "Không tìm thấy bộ từ hoặc từ vựng phù hợp"
                                } else {
                                    "Chưa có bộ từ vựng nào"
                                },
                                description = if (isSearching) {
                                    "Không có kết quả cho \"${uiState.searchQuery}\". Hãy thử từ khóa khác (hỗ trợ tìm tiếng Việt không dấu)."
                                } else {
                                    "Hãy tạo bộ từ đầu tiên hoặc sao chép từ kho Bộ có sẵn để bắt đầu học."
                                },
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                actionText = "+ Tạo bộ từ mới",
                                onActionClick = onCreateDeckClick
                            )
                        }
                    } else {
                        if (showUserSection && userList.isNotEmpty()) {
                            item(key = "section_header_user") {
                                DeckSectionHeader(
                                    title = "Bộ từ của tôi",
                                    countText = "${userList.size} bộ"
                                )
                            }
                            items(
                                items = userList,
                                key = { "user_deck_${it.deck.id}" }
                            ) { item ->
                                UserDeckItemCard(
                                    item = item,
                                    onClick = { onDeckClick(item.deck.id) },
                                    onEditClick = { onEditDeckClick(item.deck) },
                                    onDeleteClick = { onDeleteDeckClick(item.deck) }
                                )
                            }
                        }

                        if (showPrebuiltSection && prebuiltList.isNotEmpty()) {
                            item(key = "section_header_prebuilt") {
                                DeckSectionHeader(
                                    title = "Bộ từ có sẵn",
                                    countText = "${prebuiltList.size} bộ"
                                )
                            }
                            items(
                                items = prebuiltList,
                                key = { "prebuilt_deck_${it.deck.id}" }
                            ) { item ->
                                val matchedImportedDeckId = item.importedUserDeckId
                                    ?: uiState.activeDecks.firstOrNull { it.sourcePrebuiltId == item.deck.id }?.id
                                val isImporting = item.deck.id in uiState.importingPrebuiltIds
                                PrebuiltDeckCard(
                                    item = item.copy(importedUserDeckId = matchedImportedDeckId),
                                    isImporting = isImporting,
                                    onImportClick = { onImportPrebuiltDeckClick(item.deck.id) },
                                    onOpenImportedClick = {
                                        matchedImportedDeckId?.let(onDeckClick)
                                    }
                                )
                            }
                        }

                        if (showUserSection && hasCardMatches) {
                            item(key = "section_header_matched_cards") {
                                DeckSectionHeader(
                                    title = "Thẻ từ vựng khớp từ khóa",
                                    countText = "${cardMatches.size} thẻ"
                                )
                            }
                            items(
                                items = cardMatches,
                                key = { "matched_card_${it.card.id}" }
                            ) { match ->
                                DeckSearchCardMatchItem(
                                    match = match,
                                    onClick = { onDeckClick(match.card.deckId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thanh tìm kiếm không phân biệt hoa/thường và dấu tiếng Việt trên màn Quản lý Bộ từ (Figma 2078:14 & Mục 6.9).
 */
@Composable
fun DeckSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        placeholder = {
            Text(
                text = "Tìm bộ từ hoặc từ vựng...",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Tìm kiếm bộ từ",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
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
}

/**
 * Thẻ hiển thị kết quả từ vựng khớp với từ khóa tìm kiếm trên màn Quản lý Bộ từ.
 */
@Composable
fun DeckSearchCardMatchItem(
    match: DeckSearchCardMatch,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = match.card.word,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (match.card.phonetic.isNotBlank()) {
                        Text(
                            text = match.card.phonetic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = match.deckName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = match.card.meaning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Hàng tab lọc danh sách bộ từ theo thiết kế pill của Figma 8:323 (`2078:18`).
 */
@Composable
fun DeckFilterTabsRow(
    selectedTab: DeckFilterTab,
    userCount: Int,
    prebuiltCount: Int,
    allCount: Int,
    onSelectTab: (DeckFilterTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        DeckFilterTab.USER to "Bộ từ của tôi ($userCount)",
        DeckFilterTab.PREBUILT to "Bộ có sẵn ($prebuiltCount)",
        DeckFilterTab.ALL to "Tất cả ($allCount)"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (tab, label) ->
            val isSelected = selectedTab == tab
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelectTab(tab) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                border = if (isSelected) {
                    null
                } else {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun DeckSectionHeader(
    title: String,
    countText: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Thẻ hiển thị một bộ từ cá nhân của người dùng theo Figma 8:323 (`2078:29`).
 */
@Composable
fun UserDeckItemCard(
    item: DeckSummaryItem,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val avatarInitial = remember(item.deck.name) {
        item.deck.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M"
    }
    val relativeUpdatedText = remember(item.deck.updatedAtMillis) {
        formatRelativeTimeVi(item.deck.updatedAtMillis)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
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
                            text = "${item.totalCardsCount} từ • ${item.dueCardsCount} đến hạn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Của tôi",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Tùy chọn bộ từ ${item.deck.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chỉnh sửa bộ từ") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Chỉnh sửa bộ từ"
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onEditClick()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Xóa bộ từ",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Xóa bộ từ",
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chi tiết",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Xem & học bộ từ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "${item.totalCardsCount} từ • $relativeUpdatedText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Thẻ hiển thị một bộ từ có sẵn của ứng dụng theo Figma 8:323 (`2078:105`).
 */
@Composable
fun PrebuiltDeckItemCard(
    item: DeckSummaryItem,
    isImporting: Boolean,
    onImportClick: () -> Unit,
    onOpenImportedClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarInitial = remember(item.deck.name) {
        item.deck.name.trim().firstOrNull()?.uppercase(Locale.getDefault()) ?: "A"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable {
                if (item.isAlreadyImported) {
                    onOpenImportedClick()
                } else if (!isImporting) {
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
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Có sẵn",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Đã sao chép (Mở bộ từ)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        text = if (isImporting) "Đang sao chép..." else "Sao chép vào bộ của tôi",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
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

/**
 * Khung Shimmer Skeleton cho thẻ Bộ từ khi đang tải danh sách (Mục 11).
 */
@Composable
fun DeckCardShimmerSkeleton(
    modifier: Modifier = Modifier
) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .width(90.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .shimmerEffect()
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmerEffect()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmerEffect()
            )
        }
    }
}

internal fun formatRelativeTimeVi(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diffMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val days = TimeUnit.MILLISECONDS.toDays(diffMillis)
    return when {
        days <= 0L -> "Cập nhật hôm nay"
        days == 1L -> "1 ngày trước"
        days < 7L -> "$days ngày trước"
        days < 30L -> "${days / 7} tuần trước"
        else -> "${days / 30} tháng trước"
    }
}

@Preview(name = "DeckListScreen - Dark", widthDp = 360, showBackground = true)
@Composable
private fun DeckListScreenDarkPreview() {
    val sampleUserDecks = listOf(
        DeckSummaryItem(
            deck = Deck(
                id = "1",
                name = "Work & Productivity",
                description = "Bộ từ tiếng Anh tự soạn về công việc, quản lý thời gian và nâng cao hiệu suất mỗi ngày."
            ),
            totalCardsCount = 128,
            dueCardsCount = 14,
            newCardsCount = 20
        ),
        DeckSummaryItem(
            deck = Deck(
                id = "2",
                name = "Everyday English",
                description = "Bộ từ tiếng Anh tự soạn cho giao tiếp hằng ngày, từ sinh hoạt đến những cuộc trò chuyện quen thuộc."
            ),
            totalCardsCount = 96,
            dueCardsCount = 8,
            newCardsCount = 15
        )
    )
    MemoraTheme(darkTheme = true) {
        DeckListScreen(
            uiState = DeckListUiState(
                isLoading = false,
                selectedTab = DeckFilterTab.USER,
                userDeckItems = sampleUserDecks,
                filteredUserDeckItems = sampleUserDecks
            ),
            onSelectTab = {},
            onDeckClick = {},
            onCreateDeckClick = {},
            onEditDeckClick = {},
            onDeleteDeckClick = {},
            onImportPrebuiltDeckClick = {}
        )
    }
}
