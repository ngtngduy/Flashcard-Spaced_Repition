package com.uth.memora.feature.cardeditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.feature.cardeditor.components.AutoFillButton
import com.uth.memora.feature.common.ConfirmDialog
import com.uth.memora.feature.common.MemoraStatusBadge
import com.uth.memora.feature.common.shimmer.shimmerEffect
import com.uth.memora.feature.theme.MemoraTheme

/**
 * Màn hình Thêm / Sửa Flashcard (Card Editor - Figma Node 2101:9 & Mục 6.3).
 * Hỗ trợ chế độ thêm liên tục (giữ nguyên bộ từ, làm trống form sau khi lưu) và các khe cắm
 * mở rộng cho bộ chọn bộ từ, loại từ, nút AI Auto-Fill và chọn ảnh minh họa.
 */
@Composable
fun CardEditorScreen(
    uiState: CardEditorUiState,
    onWordChange: (String) -> Unit,
    onPhoneticChange: (String) -> Unit,
    onMeaningChange: (String) -> Unit,
    onExampleChange: (String) -> Unit,
    onToggleContinuousAddMode: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectDeck: (String) -> Unit = {},
    onPartOfSpeechChange: (PartOfSpeech?) -> Unit = {},
    onAutoFillClick: () -> Unit = {},
    onPickImageClick: () -> Unit = {},
    onRemoveImageClick: () -> Unit = {},
    onConfirmSaveDuplicateWord: () -> Unit = {},
    onDismissDuplicateWordDialog: () -> Unit = {},
    deckSelectorContent: (@Composable () -> Unit)? = null,
    autoFillButtonContent: (@Composable () -> Unit)? = null,
    partOfSpeechPickerContent: (@Composable () -> Unit)? = null,
    imagePickerContent: (@Composable () -> Unit)? = null
) {
    val isFormEditable = !uiState.isSaving && !uiState.isAutoFilling

    if (uiState.isDuplicateWordDialogVisible) {
        val deckName = uiState.selectedDeckOption?.deck?.name ?: "bộ từ này"
        ConfirmDialog(
            title = "Từ vựng đã tồn tại trong bộ",
            message = "Từ \"${uiState.duplicateWordCandidate}\" đã có trong bộ \"$deckName\". Bạn có muốn vẫn lưu thêm thẻ trùng này không?",
            confirmText = "Vẫn lưu thẻ",
            dismissText = "Hủy & Sửa lại",
            isDestructive = false,
            onConfirm = onConfirmSaveDuplicateWord,
            onDismiss = onDismissDuplicateWordDialog
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // App Header (Figma 2094:74)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !uiState.isSaving
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (uiState.isEditMode) "Chỉnh sửa Flashcard" else "Thêm Flashcard mới",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Phần giới thiệu (Figma 2094:80)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (uiState.isEditMode) {
                            "Cập nhật thông tin flashcard."
                        } else {
                            "Thêm từ, nhớ lâu hơn."
                        },
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (uiState.isEditMode) {
                            "Chỉnh sửa nội dung từ vựng mà vẫn giữ nguyên tiến độ ôn tập SM-2 hiện tại."
                        } else {
                            "Chọn bộ từ và tạo thẻ từ vựng của riêng bạn."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.isEditMode && uiState.originalCard != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MemoraStatusBadge(stage = uiState.originalCard.retentionStage)
                            if (uiState.originalCard.interval > 0) {
                                Text(
                                    text = "Chu kỳ ôn: ${uiState.originalCard.interval} ngày",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Thông báo phản hồi & số thẻ đã thêm trong phiên
                uiState.feedbackMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.cardsAddedInSessionCount > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "+${uiState.cardsAddedInSessionCount}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Thông báo lỗi chung
                uiState.errorMessage?.let { err ->
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

                // Dropdown chọn bộ từ đích (Figma 2094:83..2094:90)
                if (deckSelectorContent != null) {
                    deckSelectorContent()
                } else {
                    DeckDropdownSelector(
                        availableDecks = uiState.availableDecks,
                        selectedOption = uiState.selectedDeckOption,
                        deckError = uiState.deckError,
                        enabled = isFormEditable,
                        onSelectDeck = onSelectDeck
                    )
                }

                // Trường 1: Từ hoặc cụm từ tiếng Anh (Figma 2094:92)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardEditorFieldHeader(
                        label = "Từ hoặc cụm từ tiếng Anh",
                        requirement = "Bắt buộc"
                    )
                    OutlinedTextField(
                        value = uiState.word,
                        onValueChange = onWordChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isFormEditable,
                        isError = uiState.wordError != null,
                        shape = RoundedCornerShape(10.dp),
                        placeholder = {
                            Text(
                                text = "Ví dụ: Resilient",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        supportingText = {
                            CardEditorSupportingCounterText(
                                errorText = uiState.wordError,
                                defaultHintText = "1–${Card.MAX_WORD_LENGTH} ký tự",
                                currentLength = uiState.word.trim().length,
                                maxLength = Card.MAX_WORD_LENGTH
                            )
                        },
                        colors = cardEditorTextFieldColors()
                    )

                    if (autoFillButtonContent != null) {
                        autoFillButtonContent()
                    } else {
                        AutoFillButton(
                            remainingQuota = uiState.aiAutoFillRemainingToday,
                            maxDailyQuota = uiState.aiAutoFillMaxDaily,
                            isLoading = uiState.isAutoFilling,
                            enabled = !uiState.isSaving && uiState.isAiQuotaAvailable,
                            onClick = onAutoFillClick
                        )
                        if (!uiState.isAiQuotaAvailable) {
                            Text(
                                text = "Bạn đã dùng hết ${uiState.aiAutoFillMaxDaily}/${uiState.aiAutoFillMaxDaily} lượt gợi ý AI hôm nay. Hạn mức sẽ tự động đặt lại vào ngày mai.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Trường 2: Phiên âm IPA & Loại từ 7 lựa chọn (Figma 2197:2..2197:16)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PhoneticFieldSection(
                                phonetic = uiState.phonetic,
                                phoneticError = uiState.phoneticError,
                                enabled = isFormEditable,
                                isShimmerLoading = uiState.isAutoFilling,
                                onPhoneticChange = onPhoneticChange
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (partOfSpeechPickerContent != null) {
                                partOfSpeechPickerContent()
                            } else {
                                PartOfSpeechDropdownPicker(
                                    selectedPartOfSpeech = uiState.partOfSpeech,
                                    enabled = isFormEditable,
                                    isShimmerLoading = uiState.isAutoFilling,
                                    onPartOfSpeechChange = onPartOfSpeechChange
                                )
                            }
                        }
                    }
                    Text(
                        text = "Lựa chọn: Danh từ, Động từ, Tính từ, Trạng từ, Giới từ, Liên từ, Khác.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Trường 3: Nghĩa tiếng Việt (Figma 2094:99)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardEditorFieldHeader(
                        label = "Nghĩa tiếng Việt",
                        requirement = "Bắt buộc"
                    )
                    if (uiState.isAutoFilling) {
                        CardEditorFieldShimmerBox(height = 104.dp)
                    } else {
                        OutlinedTextField(
                            value = uiState.meaning,
                            onValueChange = onMeaningChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            enabled = isFormEditable,
                            isError = uiState.meaningError != null,
                            shape = RoundedCornerShape(10.dp),
                            placeholder = {
                                Text(
                                    text = "Kiên cường; có khả năng phục hồi nhanh sau khó khăn.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            supportingText = {
                                CardEditorSupportingCounterText(
                                    errorText = uiState.meaningError,
                                    defaultHintText = "1–${Card.MAX_MEANING_LENGTH} ký tự",
                                    currentLength = uiState.meaning.trim().length,
                                    maxLength = Card.MAX_MEANING_LENGTH
                                )
                            },
                            colors = cardEditorTextFieldColors()
                        )
                    }
                }

                // Trường 4: Câu ví dụ (Figma 2094:106)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardEditorFieldHeader(
                        label = "Câu ví dụ",
                        requirement = "Không bắt buộc"
                    )
                    if (uiState.isAutoFilling) {
                        CardEditorFieldShimmerBox(height = 84.dp)
                    } else {
                        OutlinedTextField(
                            value = uiState.example,
                            onValueChange = onExampleChange,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            enabled = isFormEditable,
                            isError = uiState.exampleError != null,
                            shape = RoundedCornerShape(10.dp),
                            placeholder = {
                                Text(
                                    text = "She stayed resilient through every challenge.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            supportingText = {
                                CardEditorSupportingCounterText(
                                    errorText = uiState.exampleError,
                                    defaultHintText = "Tối đa ${Card.MAX_EXAMPLE_LENGTH} ký tự",
                                    currentLength = uiState.example.trim().length,
                                    maxLength = Card.MAX_EXAMPLE_LENGTH
                                )
                            },
                            colors = cardEditorTextFieldColors()
                        )
                    }
                }

                // Trường 5: Ảnh minh họa thẻ với Photo Picker & SubcomposeAsyncImage + Shimmer (Figma 2197:17..2197:23 & Mục 6.3)
                if (imagePickerContent != null) {
                    imagePickerContent()
                } else {
                    CardEditorImagePickerSection(
                        imagePathOrUri = uiState.localImagePath,
                        imageError = uiState.imageError,
                        enabled = isFormEditable,
                        onPickImageClick = onPickImageClick,
                        onRemoveImageClick = onRemoveImageClick
                    )
                }

                // Chế độ thêm liên tục (chỉ hiển thị khi thêm thẻ mới)
                if (!uiState.isEditMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Thêm liên tục",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Giữ nguyên bộ từ và làm trống form sau khi lưu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.continuousAddMode,
                                onCheckedChange = onToggleContinuousAddMode,
                                enabled = !uiState.isSaving
                            )
                        }
                    }
                }

                // Mẹo học tập (Figma 2094:113)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Thẻ mới sẽ sẵn sàng cho phiên ôn tập tiếp theo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Nút Lưu & Hủy (Figma 2094:116)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val primaryLabel = when {
                        uiState.isSaving -> "Đang lưu..."
                        uiState.isEditMode -> "Lưu thay đổi"
                        uiState.continuousAddMode -> "Lưu & Thêm thẻ tiếp"
                        else -> "Thêm Flashcard"
                    }

                    Button(
                        onClick = onSaveClick,
                        enabled = !uiState.isSaving && !uiState.isAutoFilling,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (uiState.isEditMode) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = primaryLabel,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    TextButton(
                        onClick = onBack,
                        enabled = !uiState.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Hủy",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Hộp chọn Bộ từ đích (Dropdown chọn bộ từ - Figma 2094:83..2094:90).
 */
@Composable
fun DeckDropdownSelector(
    availableDecks: List<CardEditorDeckOption>,
    selectedOption: CardEditorDeckOption?,
    deckError: String?,
    enabled: Boolean,
    onSelectDeck: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = if (deckError != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Chọn bộ từ",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled && availableDecks.isNotEmpty()) {
                        expanded = true
                    },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = selectedOption?.deck?.name ?: "Chưa chọn bộ từ",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (selectedOption != null) {
                                "Bộ dùng gần nhất · ${selectedOption.cardCount} thẻ"
                            } else {
                                "Vui lòng tạo bộ từ trước khi thêm thẻ"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Mở danh sách chọn bộ từ",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                availableDecks.forEach { option ->
                    val isSelected = option.deck.id == selectedOption?.deck?.id
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = option.deck.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                Text(
                                    text = "${option.cardCount} thẻ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Đang chọn",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectDeck(option.deck.id)
                        }
                    )
                }
            }
        }

        if (deckError != null) {
            Text(
                text = deckError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Dropdown chọn 7 Loại từ [PartOfSpeech] theo Figma 2197:9..2197:15:
 * Danh từ, Động từ, Tính từ, Trạng từ, Giới từ, Liên từ, Khác (hoặc Không chọn).
 */
@Composable
fun PartOfSpeechDropdownPicker(
    selectedPartOfSpeech: PartOfSpeech?,
    enabled: Boolean,
    onPartOfSpeechChange: (PartOfSpeech?) -> Unit,
    modifier: Modifier = Modifier,
    isShimmerLoading: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Loại từ",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        if (isShimmerLoading) {
            CardEditorFieldShimmerBox(height = 56.dp)
        } else {
            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { expanded = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedPartOfSpeech?.labelVi ?: "Chọn loại từ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedPartOfSpeech != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Chọn loại từ",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Không chọn",
                                fontWeight = if (selectedPartOfSpeech == null) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            expanded = false
                            onPartOfSpeechChange(null)
                        }
                    )
                    PartOfSpeech.entries.forEach { pos ->
                        val isSelected = selectedPartOfSpeech == pos
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${pos.labelVi} (${pos.abbr}.)",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Đang chọn",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onPartOfSpeechChange(pos)
                            }
                        )
                    }
                }
            }
        }

        Text(
            text = "Không bắt buộc",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun CardEditorFieldHeader(
    label: String,
    requirement: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = requirement,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhoneticFieldSection(
    phonetic: String,
    phoneticError: String?,
    enabled: Boolean,
    onPhoneticChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isShimmerLoading: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CardEditorFieldHeader(
            label = "Phiên âm IPA",
            requirement = "Không bắt buộc"
        )
        if (isShimmerLoading) {
            CardEditorFieldShimmerBox(height = 56.dp)
        } else {
            OutlinedTextField(
                value = phonetic,
                onValueChange = onPhoneticChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                isError = phoneticError != null,
                shape = RoundedCornerShape(10.dp),
                placeholder = {
                    Text(
                        text = "/rɪˈzɪliənt/",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                supportingText = {
                    CardEditorSupportingCounterText(
                        errorText = phoneticError,
                        defaultHintText = "Tối đa ${Card.MAX_PHONETIC_LENGTH}",
                        currentLength = phonetic.trim().length,
                        maxLength = Card.MAX_PHONETIC_LENGTH
                    )
                },
                colors = cardEditorTextFieldColors()
            )
        }
    }
}

/**
 * Khu vực chọn và xem trước Ảnh minh họa thẻ (Figma 2197:17..2197:23, Mục 6.3 & 11):
 * - Mở Android Photo Picker (1 ảnh JPG / PNG / WebP · tối đa 500KB)
 * - Xem trước ảnh bằng Coil [SubcomposeAsyncImage] kèm hiệu ứng [shimmerEffect] bo góc 12.dp
 */
@Composable
fun CardEditorImagePickerSection(
    imagePathOrUri: String?,
    imageError: String?,
    enabled: Boolean,
    onPickImageClick: () -> Unit,
    onRemoveImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CardEditorFieldHeader(
            label = "Ảnh minh họa thẻ",
            requirement = "Không bắt buộc"
        )

        if (imagePathOrUri.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled, onClick = onPickImageClick),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (imageError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Chọn ảnh",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "1 ảnh JPG / PNG / WebP · Tối đa 500KB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = imagePathOrUri,
                        contentDescription = "Ảnh minh họa thẻ đã chọn",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
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
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Không tải được bản xem trước ảnh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onPickImageClick,
                            enabled = enabled
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đổi ảnh khác")
                        }

                        TextButton(
                            onClick = onRemoveImageClick,
                            enabled = enabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xóa ảnh")
                        }
                    }
                }
            }
        }

        if (imageError != null) {
            Text(
                text = imageError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * Hộp Shimmer Skeleton hiển thị trên các ô nhập liệu (IPA, Loại từ, Nghĩa, Ví dụ) khi đang chờ AI gợi ý (Mục 6.3 & 11).
 */
@Composable
private fun CardEditorFieldShimmerBox(
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .shimmerEffect()
    )
}

/**
 * Dòng chú thích dưới mỗi ô nhập liệu kết hợp thông báo lỗi và bộ đếm số ký tự trực tiếp (Mục 6.3 & 11).
 */
@Composable
private fun CardEditorSupportingCounterText(
    errorText: String?,
    defaultHintText: String,
    currentLength: Int,
    maxLength: Int,
    modifier: Modifier = Modifier
) {
    val isOverLimit = currentLength > maxLength
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = errorText ?: defaultHintText,
            color = if (errorText != null || isOverLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        )
        Text(
            text = "$currentLength/$maxLength",
            color = if (isOverLimit) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun cardEditorTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@Preview(name = "CardEditorScreen - Dark", widthDp = 360, showBackground = true)
@Composable
private fun CardEditorScreenDarkPreview() {
    MemoraTheme(darkTheme = true) {
        CardEditorScreen(
            uiState = CardEditorUiState(
                isLoading = false,
                selectedDeckId = "d1",
                availableDecks = listOf(
                    CardEditorDeckOption(
                        deck = Deck(id = "d1", name = "Everyday English"),
                        cardCount = 96
                    )
                ),
                word = "Resilient",
                phonetic = "/rɪˈzɪliənt/",
                meaning = "Kiên cường; có khả năng phục hồi nhanh sau khó khăn.",
                example = "She stayed resilient through every challenge."
            ),
            onWordChange = {},
            onPhoneticChange = {},
            onMeaningChange = {},
            onExampleChange = {},
            onToggleContinuousAddMode = {},
            onSaveClick = {},
            onBack = {}
        )
    }
}

@Preview(name = "CardEditorScreen - Light", widthDp = 360, showBackground = true)
@Composable
private fun CardEditorScreenLightPreview() {
    MemoraTheme(darkTheme = false) {
        CardEditorScreen(
            uiState = CardEditorUiState(
                isLoading = false,
                isEditMode = true,
                selectedDeckId = "d1",
                availableDecks = listOf(
                    CardEditorDeckOption(
                        deck = Deck(id = "d1", name = "Everyday English"),
                        cardCount = 96
                    )
                ),
                word = "Resilient",
                phonetic = "/rɪˈzɪliənt/",
                meaning = "Kiên cường; có khả năng phục hồi nhanh sau khó khăn.",
                example = "She stayed resilient through every challenge."
            ),
            onWordChange = {},
            onPhoneticChange = {},
            onMeaningChange = {},
            onExampleChange = {},
            onToggleContinuousAddMode = {},
            onSaveClick = {},
            onBack = {}
        )
    }
}
