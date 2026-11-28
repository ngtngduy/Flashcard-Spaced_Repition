package com.uth.memora.feature.cardeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.memoraViewModelFactory
import kotlinx.coroutines.launch

/**
 * Điểm vào điều hướng của màn Thêm / Sửa Flashcard (Figma 2101:9).
 * Kết nối [CardEditorViewModel] thông qua [memoraViewModelFactory] và Android Photo Picker với [CardEditorScreen].
 */
@Composable
fun CardEditorRoute(
    deckId: String,
    cardId: String?,
    onCardSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel: CardEditorViewModel = viewModel(
        key = "card_editor_${deckId}_${cardId ?: "new"}",
        factory = memoraViewModelFactory { container ->
            CardEditorViewModel(
                initialDeckId = deckId,
                cardId = cardId,
                deckRepository = container.deckRepository,
                cardRepository = container.cardRepository,
                userPreferencesRepository = container.userPreferencesRepository,
                aiCardAutoFillService = container.aiCardAutoFillService
            )
        }
    )

    val uiState by viewModel.uiState.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                CardImageStorageHelper.copyAndCompressToInternalStorage(
                    context = context,
                    sourceUri = uri
                ).onSuccess { savedFile ->
                    viewModel.onImageSelected(
                        imagePathOrUri = savedFile.absolutePath,
                        fileSizeBytes = savedFile.length()
                    )
                }.onFailure { error ->
                    viewModel.onImagePickFailed(
                        message = error.message ?: "Không thể lưu ảnh minh họa vào bộ nhớ ứng dụng."
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.shouldCloseScreen) {
        if (uiState.shouldCloseScreen) {
            viewModel.onScreenClosed()
            onCardSaved()
        }
    }

    CardEditorScreen(
        uiState = uiState,
        onSelectDeck = viewModel::onSelectDeck,
        onWordChange = viewModel::onWordChange,
        onPhoneticChange = viewModel::onPhoneticChange,
        onPartOfSpeechChange = viewModel::onPartOfSpeechChange,
        onMeaningChange = viewModel::onMeaningChange,
        onExampleChange = viewModel::onExampleChange,
        onAutoFillClick = viewModel::autoFillWithAi,
        onPickImageClick = {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onRemoveImageClick = viewModel::onRemoveImage,
        onToggleContinuousAddMode = viewModel::onToggleContinuousAddMode,
        onConfirmSaveDuplicateWord = viewModel::confirmSaveDuplicateWord,
        onDismissDuplicateWordDialog = viewModel::dismissDuplicateWordDialog,
        onSaveClick = { viewModel.saveCard(closeAfterSave = false) },
        onBack = onBack,
        modifier = modifier
    )
}
