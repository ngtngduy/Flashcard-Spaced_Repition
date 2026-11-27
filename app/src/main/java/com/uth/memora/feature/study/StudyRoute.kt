package com.uth.memora.feature.study

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uth.memora.MemoraApplication
import com.uth.memora.core.model.StudyMode
import com.uth.memora.core.model.UserPreferences
import com.uth.memora.memoraViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Route điều hướng cho màn hình Phiên học Flashcard (Mục 6.5, 6.6 & 9).
 * Giữ nguyên chữ ký hàm để tương thích với MemoraNavHost.kt.
 */
@Composable
fun StudyRoute(
    deckId: String,
    studyMode: StudyMode = StudyMode.DUE_REVIEW,
    onIntermission: (String) -> Unit,
    onFinishSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPractice: () -> Unit = onFinishSession
) {
    val viewModel: StudyViewModel = viewModel(
        key = "study_${deckId}_${studyMode.name}",
        factory = memoraViewModelFactory { container ->
            StudyViewModel(
                repository = container.studySessionRepository,
                deckId = deckId,
                studyMode = studyMode
            )
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val userPreferencesRepository = remember(appContext) {
        (appContext as? MemoraApplication)?.container?.userPreferencesRepository
    }
    val userPreferencesFlow: Flow<UserPreferences?> = remember(userPreferencesRepository) {
        userPreferencesRepository?.observeUserPreferences() ?: flowOf(null)
    }
    val userPreferences by userPreferencesFlow.collectAsStateWithLifecycle(initialValue = null)
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val speakWord = rememberMemoraTtsSpeaker(
        onError = { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    )

    StudyScreen(
        uiState = uiState,
        onFlipCard = viewModel::toggleFlip,
        onRateCard = viewModel::submitRating,
        onSpeakWord = { word ->
            if (userPreferences?.isSoundEnabled != false) {
                speakWord(word)
            }
        },
        onRetry = viewModel::loadQueue,
        onFinishSession = onFinishSession,
        onBack = onBack,
        modifier = modifier,
        onUndoRating = viewModel::undoLastRating,
        onContinueNextChunk = viewModel::continueToNextChunk,
        onPauseFromIntermission = viewModel::finishSessionFromIntermission,
        onNavigateToPractice = onNavigateToPractice,
        snackbarHostState = snackbarHostState
    )
}
