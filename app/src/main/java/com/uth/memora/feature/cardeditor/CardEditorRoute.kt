package com.uth.memora.feature.cardeditor

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.memoraViewModelFactory

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
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val fileSizeBytes = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                    ?.use { cursor ->
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                            cursor.getLong(sizeIndex)
                        } else {
                            null
                        }
                    }
            }.getOrNull()

            viewModel.onImageSelected(
                imagePathOrUri = uri.toString(),
                fileSizeBytes = fileSizeBytes
            )
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
