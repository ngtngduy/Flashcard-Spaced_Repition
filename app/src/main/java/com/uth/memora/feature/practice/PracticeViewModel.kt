package com.uth.memora.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.PracticeSentenceRepository
import com.uth.memora.core.domain.SentenceLocalValidator
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.UserPreferences
import com.uth.memora.core.sm2.LearningDayCalculator
import com.uth.memora.integration.ai.AiGradingErrorType
import com.uth.memora.integration.ai.AiGradingException
import com.uth.memora.integration.ai.AiJsonParseException
import com.uth.memora.integration.ai.GeminiAiGradingService
import com.uth.memora.integration.worker.GradingScheduler
import com.uth.memora.integration.worker.SentenceGradingWorker
import com.uth.memora.memoraViewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/**
 * ViewModel điều phối màn hình Luyện đặt câu với Chatbot AI (Figma 2061:119 & 2212:109 · Mục 6.7, 6.8, 8.2, 9.5 & 13):
 * - Quan sát tài khoản từ [AuthRepository.observeCurrentUser] để xác định chế độ khách (`isGuest`).
 * - Quan sát [UserPreferencesRepository.observeUserPreferences] để cập nhật hạn mức 20 câu/ngày.
 * - Tự động chuyển luồng [PracticeSentenceRepository.observeSentencesByCard] mỗi khi đổi thẻ mục tiêu.
 * - Kiểm tra câu cục bộ qua [SentenceLocalValidator.validate], hỗ trợ hàng đợi ngoại tuyến qua [GradingScheduler].
 */
class PracticeViewModel(
    private val practiceSentenceRepository: PracticeSentenceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val gradingScheduler: GradingScheduler? = null,
    initialDeckId: String? = null,
    private val isOnlineProvider: () -> Boolean = { true }
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PracticeUiState(
            isLoading = true,
            selectedDeckId = normalizeDeckId(initialDeckId)
        )
    )
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var lastObservedUserUid: String? = null
    private var hasObservedInitialUser: Boolean = false
    private var loadTargetCardsJob: Job? = null

    init {
        observeAuthUser()
        observeQuotaPreferences()
        observeSelectedCardSentences()
        refreshTargetCards()
    }

    /**
     * Quan sát trạng thái đăng nhập (Mục 6.7, 9.5 & 13):
     * `isGuest = true` khi `currentUser == null || currentUser.isAnonymous`.
     */
    private fun observeAuthUser() {
        viewModelScope.launch {
            authRepository.observeCurrentUser()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { it.copy(isGuest = true) }
                }
                .collect { currentUser ->
                    val guest = currentUser == null || currentUser.isAnonymous
                    val previousUid = lastObservedUserUid
                    val wasInitialized = hasObservedInitialUser
                    hasObservedInitialUser = true
                    lastObservedUserUid = currentUser?.uid

                    _uiState.update { state ->
                        state.copy(
                            isGuest = guest,
                            errorMessage = if (!guest && state.errorMessage == ERROR_LOGIN_REQUIRED_VI) {
                                null
                            } else {
                                state.errorMessage
                            }
                        )
                    }

                    // Tải lại danh sách từ gợi ý khi người dùng chuyển đổi tài khoản (Mục 6.7 & 13)
                    if (wasInitialized && previousUid != currentUser?.uid) {
                        refreshTargetCards()
                    }

                    // Khôi phục hàng đợi chấm câu ngoại tuyến khi đã đăng nhập tài khoản thật (Mục 6.8 & 8.2)
                    if (!guest && gradingScheduler != null) {
                        viewModelScope.launch {
                            gradingScheduler.resumePendingSentences(practiceSentenceRepository)
                        }
                    }
                }
        }
    }

    /**
     * Quan sát hạn mức chấm câu AI còn lại trong ngày (tối đa 20 câu/ngày, tự hồi lúc 04:00 sáng - Mục 6.7, 7.4 & 11).
     */
    private fun observeQuotaPreferences() {
        viewModelScope.launch {
            runCatching {
                userPreferencesRepository.resetDailyQuotasIfNeeded(
                    LearningDayCalculator.toLearningDay()
                )
            }

            userPreferencesRepository.observeUserPreferences()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(remainingDailyQuota = UserPreferences.DEFAULT_AI_SENTENCE_LIMIT)
                    }
                }
                .collect { prefs ->
                    val clampedQuota = prefs.aiSentenceGradingRemainingToday
                        .coerceIn(0, PracticeSentence.MAX_DAILY_QUOTA)
                    _uiState.update { it.copy(remainingDailyQuota = clampedQuota) }
                }
        }
    }

    /**
     * Tự động hủy luồng cũ và quan sát lịch sử câu của thẻ mới mỗi khi thẻ mục tiêu thay đổi (Mục 6.7 & 8.2).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedCardSentences() {
        viewModelScope.launch {
            _uiState
                .map { state -> state.selectedCard?.id }
                .distinctUntilChanged()
                .flatMapLatest { cardId ->
                    if (cardId.isNullOrBlank()) {
                        flowOf(emptyList())
                    } else {
                        practiceSentenceRepository.observeSentencesByCard(cardId)
                            .catch { error ->
                                if (error is CancellationException) throw error
                                _uiState.update {
                                    it.copy(errorMessage = mapErrorToVietnamese(error))
                                }
                                emit(emptyList())
                            }
                    }
                }
                .collect { sentences ->
                    _uiState.update { it.copy(history = sentences) }
                }
        }
    }

    /**
     * Làm mới danh sách tối đa 5 từ mục tiêu (ưu tiên thẻ đang học / vừa ôn sai / đến hạn)
     * theo `selectedDeckId` hiện tại khi khởi tạo hoặc bấm nút làm mới (Mục 6.7 · Figma 2212:88).
     */
    fun refreshTargetCards() {
        loadSuggestedTargetCards(selectedDeckId = _uiState.value.selectedDeckId)
    }

    /**
     * Chọn bộ từ để lọc danh sách từ luyện đặt câu (`null` = lấy trên toàn bộ thẻ đang học)
     * và tải lại tối đa 5 từ mục tiêu (Mục 6.7 · Figma 2212:89).
     */
    fun selectDeck(deckId: String?) {
        val normalizedDeck = normalizeDeckId(deckId)
        loadSuggestedTargetCards(selectedDeckId = normalizedDeck)
    }

    /**
     * Tải danh sách tối đa 5 thẻ mục tiêu từ [PracticeSentenceRepository.getSuggestedPracticeCards] (Mục 6.7).
     */
    fun loadTargetCards(deckId: String? = _uiState.value.selectedDeckId) {
        loadSuggestedTargetCards(selectedDeckId = normalizeDeckId(deckId))
    }

    private fun loadSuggestedTargetCards(selectedDeckId: String?) {
        loadTargetCardsJob?.cancel()
        loadTargetCardsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedDeckId = selectedDeckId,
                    errorMessage = null,
                    feedbackMessage = null
                )
            }

            try {
                // Lấy tối đa 5 từ mục tiêu, ưu tiên thẻ vừa học / ôn sai (Mục 6.7)
                val suggestedCards = practiceSentenceRepository.getSuggestedPracticeCards(
                    deckId = selectedDeckId,
                    limit = TARGET_CARDS_LIMIT
                )
                    .filter { !it.isDeleted }
                    .take(TARGET_CARDS_LIMIT)

                _uiState.update { current ->
                    val previousCardId = current.selectedCard?.id
                    val resolvedIndex = 0
                    val nextCardId = suggestedCards.getOrNull(resolvedIndex)?.id
                    val isCardChanged = nextCardId != previousCardId

                    current.copy(
                        isLoading = false,
                        selectedDeckId = selectedDeckId,
                        targetCards = suggestedCards,
                        selectedCardIndex = resolvedIndex,
                        inputText = if (isCardChanged) "" else current.inputText,
                        validationError = null,
                        isLowConfidenceVoiceInput = if (isCardChanged) false else current.isLowConfidenceVoiceInput,
                        history = if (isCardChanged) emptyList() else current.history
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = mapErrorToVietnamese(
                            e,
                            fallback = "Không thể tải danh sách từ mục tiêu lúc này."
                        )
                    )
                }
            }
        }
    }

    /**
     * Chọn thẻ mục tiêu tại vị trí [index] trong danh sách tối đa 5 từ của buổi luyện (Mục 6.7).
     */
    fun selectTargetCard(index: Int) {
        _uiState.update { state ->
            if (index !in state.targetCards.indices || index == state.selectedCardIndex) {
                state
            } else {
                val previousCardId = state.selectedCard?.id
                val nextCardId = state.targetCards.getOrNull(index)?.id
                val isCardChanged = nextCardId != previousCardId

                state.copy(
                    selectedCardIndex = index,
                    inputText = "",
                    validationError = null,
                    errorMessage = null,
                    feedbackMessage = null,
                    isLowConfidenceVoiceInput = false,
                    history = if (isCardChanged) emptyList() else state.history
                )
            }
        }
    }

    /**
     * Alias tương thích chuyển sang [selectTargetCard] (Mục 6.7).
     */
    fun selectCard(index: Int) {
        selectTargetCard(index)
    }

    /**
     * Chuyển nhanh sang từ mục tiêu kế tiếp trong buổi luyện ("Đổi từ" - Figma 2212:89 · Mục 6.7).
     */
    fun selectNextCard() {
        val cards = _uiState.value.targetCards
        if (cards.size <= 1) return
        val nextIndex = (_uiState.value.selectedCardIndex + 1) % cards.size
        selectTargetCard(nextIndex)
    }

    /**
     * Chọn thẻ mục tiêu theo mã định danh [cardId] nếu có trong danh sách hiện tại (Mục 6.7).
     */
    fun selectCardById(cardId: String) {
        val targetIndex = _uiState.value.targetCards.indexOfFirst { it.id == cardId }
        if (targetIndex >= 0) {
            selectTargetCard(targetIndex)
        }
    }

    /**
     * Cập nhật câu người học đang nhập và chạy kiểm tra cục bộ tức thì bằng [SentenceLocalValidator.validate]
     * để hiển thị lỗi tiếng Việt ngay dưới ô nhập (Mục 6.8 & 11).
     */
    fun onInputTextChange(newText: String) {
        val targetCard = _uiState.value.selectedCard
        val liveValidationError = when {
            newText.isBlank() -> null
            targetCard != null -> {
                val result = SentenceLocalValidator.validate(
                    targetWord = targetCard.word,
                    rawSentence = newText
                )
                if (result.isValid) null else result.errorMessageVi
            }

            newText.trim().length > PracticeSentence.MAX_SENTENCE_LENGTH -> {
                "Câu không được vượt quá ${PracticeSentence.MAX_SENTENCE_LENGTH} ký tự (hiện tại: ${newText.trim().length})."
            }

            else -> null
        }

        _uiState.update {
            it.copy(
                inputText = newText,
                validationError = liveValidationError,
                errorMessage = null,
                feedbackMessage = null,
                isLowConfidenceVoiceInput = false
            )
        }
    }

    /**
     * Nhận văn bản từ Speech-to-Text, tự động viết hoa chữ cái đầu câu và thêm dấu chấm `.`
     * nếu chưa kết thúc bằng `.`, `!`, `?` trước khi cập nhật `inputText` (Mục 6.7 & 6.8).
     * Gắn cờ `isLowConfidenceVoiceInput` nếu độ tin cậy < 0.7f để nhắc người học rà lại trước khi gửi.
     */
    fun onVoiceInputResult(
        rawTranscript: String,
        lowConfidence: Boolean = false
    ) {
        val normalized = normalizeVoiceSentence(rawTranscript)
        if (normalized.isEmpty()) return
        onInputTextChange(normalized)
        if (lowConfidence) {
            _uiState.update { it.copy(isLowConfidenceVoiceInput = true) }
        }
    }

    /** Xóa nhắc nhẹ độ tin cậy giọng nói khi người học bắt đầu phiên ghi âm mới hoặc đã rà xong (Mục 6.7). */
    fun clearVoiceLowConfidenceHint() {
        _uiState.update { it.copy(isLowConfidenceVoiceInput = false) }
    }

    /**
     * Kiểm tra cục bộ và gửi câu luyện tập lên AI hoặc lưu vào hàng đợi Chờ chấm (Mục 6.7, 6.8, 8.2 & 9.5).
     */
    fun submitSentence(isOnline: Boolean = isOnlineProvider()) {
        val state = _uiState.value
        if (state.isSubmitting) return

        // Chặn gửi khi chưa đăng nhập tài khoản thật (Mục 6.7 & 9.5)
        if (state.isGuest) {
            _uiState.update { it.copy(errorMessage = ERROR_LOGIN_REQUIRED_VI) }
            return
        }

        val targetCard = state.selectedCard
        if (targetCard == null) {
            _uiState.update {
                it.copy(errorMessage = "Chưa có thẻ từ vựng mục tiêu để chấm câu.")
            }
            return
        }

        // Kiểm tra cục bộ trước khi đưa vào hàng đợi hoặc gọi AI (Mục 6.8)
        val validation = SentenceLocalValidator.validate(
            targetWord = targetCard.word,
            rawSentence = state.inputText
        )
        if (!validation.isValid) {
            _uiState.update {
                it.copy(validationError = validation.errorMessageVi)
            }
            return
        }

        val cleanSentence = state.inputText.trim()

        // Chống gửi trùng câu đang ở trạng thái Chờ chấm hoặc Đang chấm (Mục 6.7 & 9.5)
        val isDuplicateInFlight = state.history.any { existing ->
            existing.sentence.trim().equals(cleanSentence, ignoreCase = true) &&
                (existing.status == SentenceStatus.PENDING_GRADE ||
                    existing.status == SentenceStatus.GRADING)
        }
        if (isDuplicateInFlight) {
            _uiState.update {
                it.copy(
                    validationError = null,
                    feedbackMessage = MESSAGE_DUPLICATE_IN_FLIGHT_VI
                )
            }
            return
        }

        val isQuotaExhausted = state.remainingDailyQuota <= 0
        val shouldQueueOffline = !isOnline || isQuotaExhausted

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    validationError = null,
                    errorMessage = null,
                    feedbackMessage = null,
                    isLowConfidenceVoiceInput = false
                )
            }

            if (shouldQueueOffline) {
                enqueueSentenceForLaterGrading(
                    cardId = targetCard.id,
                    cleanSentence = cleanSentence,
                    isQuotaExhausted = isQuotaExhausted
                )
                return@launch
            }

            val submitResult = try {
                practiceSentenceRepository.submitPracticeSentence(
                    cardId = targetCard.id,
                    sentence = cleanSentence,
                    isOnline = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            submitResult.fold(
                onSuccess = { savedSentence ->
                    handleSubmittedSentenceResult(savedSentence)
                },
                onFailure = { error ->
                    // Mất mạng hoặc quá hạn chờ khi đang gửi -> lưu về Chờ chấm để tự gửi lại khi có mạng (Mục 9.5)
                    if (isTransientNetworkOrTimeout(error)) {
                        enqueueSentenceForLaterGrading(
                            cardId = targetCard.id,
                            cleanSentence = cleanSentence,
                            isQuotaExhausted = false
                        )
                    } else {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                errorMessage = mapErrorToVietnamese(error)
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * Bấm "Chấm lại" cho một câu đang ở trạng thái `GRADE_ERROR` (Figma 2212:107 · Mục 8.2 & 9.5):
     * Gọi `practiceSentenceRepository.retryFailedSentence(sentenceId)` và đưa vào `GradingScheduler`
     * nếu câu ở trạng thái chờ/cần xử lý nền.
     */
    fun retryFailedSentence(sentenceId: String, isOnline: Boolean = isOnlineProvider()) {
        val state = _uiState.value
        if (sentenceId.isBlank() || state.retryingSentenceId != null || state.isSubmitting) return

        if (state.isGuest) {
            _uiState.update { it.copy(errorMessage = ERROR_LOGIN_REQUIRED_VI) }
            return
        }

        if (state.remainingDailyQuota <= 0) {
            _uiState.update {
                it.copy(errorMessage = SentenceGradingWorker.ERROR_QUOTA_EXCEEDED_VI)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    retryingSentenceId = sentenceId,
                    errorMessage = null,
                    feedbackMessage = null
                )
            }

            if (!isOnline) {
                val savePending = runCatching {
                    practiceSentenceRepository.saveAiGradingResult(
                        sentenceId = sentenceId,
                        status = SentenceStatus.PENDING_GRADE,
                        errorMessage = null,
                        retryCount = 0
                    )
                }.getOrElse { err ->
                    if (err is CancellationException) throw err
                    Result.failure(err)
                }

                savePending.fold(
                    onSuccess = {
                        enqueueWithSchedulerSafely(sentenceId, replaceExisting = true)
                        _uiState.update {
                            it.copy(
                                retryingSentenceId = null,
                                feedbackMessage = MESSAGE_SAVED_OFFLINE_VI
                            )
                        }
                    },
                    onFailure = { err ->
                        _uiState.update {
                            it.copy(
                                retryingSentenceId = null,
                                errorMessage = mapErrorToVietnamese(err)
                            )
                        }
                    }
                )
                return@launch
            }

            val retryResult = try {
                practiceSentenceRepository.retryFailedSentence(sentenceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            retryResult.fold(
                onSuccess = { updatedSentence ->
                    when (updatedSentence.status) {
                        SentenceStatus.GRADED -> {
                            val updatedQuota = userPreferencesRepository
                                .consumeSentenceGradingQuota()
                                .getOrNull()
                            _uiState.update { current ->
                                current.copy(
                                    retryingSentenceId = null,
                                    remainingDailyQuota = updatedQuota
                                        ?: current.remainingDailyQuota,
                                    errorMessage = null
                                )
                            }
                        }

                        SentenceStatus.PENDING_GRADE -> {
                            enqueueWithSchedulerSafely(updatedSentence.id, replaceExisting = true)
                            _uiState.update {
                                it.copy(
                                    retryingSentenceId = null,
                                    feedbackMessage = MESSAGE_RETRY_QUEUED_VI
                                )
                            }
                        }

                        SentenceStatus.GRADE_ERROR -> {
                            _uiState.update {
                                it.copy(
                                    retryingSentenceId = null,
                                    errorMessage = normalizeStoredErrorMessage(
                                        updatedSentence.errorMessage
                                    )
                                )
                            }
                        }

                        SentenceStatus.GRADING -> {
                            enqueueWithSchedulerSafely(updatedSentence.id, replaceExisting = true)
                            _uiState.update { it.copy(retryingSentenceId = null) }
                        }
                    }
                },
                onFailure = { error ->
                    if (isTransientNetworkOrTimeout(error)) {
                        runCatching {
                            practiceSentenceRepository.saveAiGradingResult(
                                sentenceId = sentenceId,
                                status = SentenceStatus.PENDING_GRADE,
                                errorMessage = null,
                                retryCount = 0
                            )
                        }
                        enqueueWithSchedulerSafely(sentenceId, replaceExisting = true)
                        _uiState.update {
                            it.copy(
                                retryingSentenceId = null,
                                feedbackMessage = MESSAGE_SAVED_OFFLINE_VI
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                retryingSentenceId = null,
                                errorMessage = mapErrorToVietnamese(error)
                            )
                        }
                    }
                }
            )
        }
    }

    /**
     * Alias tương thích chuyển sang [retryFailedSentence] (Mục 8.2).
     */
    fun retrySentence(sentenceId: String, isOnline: Boolean = isOnlineProvider()) {
        retryFailedSentence(sentenceId = sentenceId, isOnline = isOnline)
    }

    /**
     * Lưu câu đã chỉnh sửa từ AI làm câu ví dụ riêng cho thẻ hiện tại (Figma 2212:91..92 · Mục 6.7).
     */
    fun saveCorrectedAsExample(sentenceId: String) {
        val state = _uiState.value
        if (sentenceId.isBlank() || state.savingExampleSentenceId != null) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    savingExampleSentenceId = sentenceId,
                    errorMessage = null,
                    feedbackMessage = null
                )
            }

            val saveResult = try {
                practiceSentenceRepository.saveCorrectedSentenceAsExample(sentenceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

            saveResult.fold(
                onSuccess = { updatedCard ->
                    _uiState.update { current ->
                        val updatedTargets = current.targetCards.map { card ->
                            if (card.id == updatedCard.id) updatedCard else card
                        }
                        current.copy(
                            savingExampleSentenceId = null,
                            targetCards = updatedTargets,
                            savedExampleSentenceIds = current.savedExampleSentenceIds + sentenceId,
                            feedbackMessage = "Đã lưu câu chỉnh sửa làm ví dụ cho thẻ \"${updatedCard.word}\"."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            savingExampleSentenceId = null,
                            errorMessage = mapErrorToVietnamese(
                                error,
                                fallback = "Không thể lưu câu ví dụ cho thẻ lúc này."
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Alias tương thích chuyển sang [saveCorrectedAsExample] (Mục 6.7).
     */
    fun saveCorrectedSentenceAsExample(sentenceId: String) {
        saveCorrectedAsExample(sentenceId)
    }

    /**
     * Xóa thông báo lỗi hoặc phản hồi đang hiển thị trên giao diện.
     */
    fun clearMessages() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                feedbackMessage = null
            )
        }
    }

    private suspend fun enqueueSentenceForLaterGrading(
        cardId: String,
        cleanSentence: String,
        isQuotaExhausted: Boolean
    ) {
        val offlineResult = try {
            practiceSentenceRepository.submitPracticeSentence(
                cardId = cardId,
                sentence = cleanSentence,
                isOnline = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }

        offlineResult.fold(
            onSuccess = { pendingSentence ->
                enqueueWithSchedulerSafely(pendingSentence.id, replaceExisting = false)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        inputText = "",
                        errorMessage = if (isQuotaExhausted) {
                            SentenceGradingWorker.ERROR_QUOTA_EXCEEDED_VI
                        } else {
                            null
                        },
                        feedbackMessage = if (isQuotaExhausted) {
                            null
                        } else {
                            MESSAGE_SAVED_OFFLINE_VI
                        }
                    )
                }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = mapErrorToVietnamese(error)
                    )
                }
            }
        )
    }

    private suspend fun handleSubmittedSentenceResult(savedSentence: PracticeSentence) {
        when (savedSentence.status) {
            SentenceStatus.GRADED -> {
                val updatedQuota = userPreferencesRepository
                    .consumeSentenceGradingQuota()
                    .getOrNull()
                _uiState.update { current ->
                    current.copy(
                        isSubmitting = false,
                        inputText = "",
                        remainingDailyQuota = updatedQuota ?: current.remainingDailyQuota,
                        errorMessage = null
                    )
                }
            }

            SentenceStatus.PENDING_GRADE -> {
                enqueueWithSchedulerSafely(savedSentence.id, replaceExisting = false)
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        inputText = "",
                        feedbackMessage = savedSentence.errorMessage ?: MESSAGE_SAVED_OFFLINE_VI
                    )
                }
            }

            SentenceStatus.GRADE_ERROR -> {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = normalizeStoredErrorMessage(savedSentence.errorMessage)
                    )
                }
            }

            SentenceStatus.GRADING -> {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        inputText = ""
                    )
                }
            }
        }
    }

    private fun enqueueWithSchedulerSafely(sentenceId: String, replaceExisting: Boolean) {
        if (sentenceId.isBlank()) return
        try {
            gradingScheduler?.enqueueSentence(
                sentenceId = sentenceId,
                replaceExisting = replaceExisting
            )
        } catch (_: Throwable) {
            // Bỏ qua nếu WorkManager chưa khởi tạo trong môi trường test cục bộ
        }
    }

    /**
     * Phân loại ngoại lệ mạng / HTTP 429 / timeout / sai JSON thành thông báo tiếng Việt thân thiện,
     * tuyệt đối không lộ thông tin nhạy cảm hay API key (Mục 9.5).
     */
    private fun mapErrorToVietnamese(
        throwable: Throwable,
        fallback: String = AiGradingException.ERROR_UNKNOWN_VI
    ): String {
        if (throwable is AiGradingException) {
            return throwable.message
        }
        if (throwable is AiJsonParseException) {
            return throwable.message ?: AiGradingException.ERROR_INVALID_JSON_VI
        }

        val causeChain = generateSequence(throwable) { it.cause }.toList()
        val combinedLower = causeChain
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()

        val shouldDelegateToAiClassifier =
            throwable is TimeoutCancellationException ||
                causeChain.any {
                    it is UnknownHostException ||
                        it is SocketTimeoutException ||
                        it is IOException ||
                        it is AiJsonParseException
                } ||
                combinedLower.contains("429") ||
                combinedLower.contains("quota") ||
                combinedLower.contains("rate limit") ||
                combinedLower.contains("resource_exhausted") ||
                combinedLower.contains("too many requests") ||
                combinedLower.contains("api key") ||
                combinedLower.contains("api_key") ||
                combinedLower.contains("unauthenticated") ||
                combinedLower.contains("permission_denied") ||
                combinedLower.contains("json") ||
                combinedLower.contains("timeout") ||
                combinedLower.contains("timed out") ||
                combinedLower.contains("unable to resolve host") ||
                combinedLower.contains("failed to connect")

        if (shouldDelegateToAiClassifier) {
            return GeminiAiGradingService.mapToGradingException(throwable).message
        }

        val rawMessage = throwable.message?.trim().orEmpty()
        return rawMessage.ifEmpty { fallback }
    }

    private fun normalizeStoredErrorMessage(rawErrorMessage: String?): String {
        val clean = rawErrorMessage?.trim().orEmpty()
        if (clean.isEmpty()) {
            return SentenceGradingWorker.ERROR_GRADE_FAILED_VI
        }
        return mapErrorToVietnamese(RuntimeException(clean), fallback = clean)
    }

    private fun isTransientNetworkOrTimeout(throwable: Throwable): Boolean {
        val mapped = GeminiAiGradingService.mapToGradingException(throwable)
        return mapped.type == AiGradingErrorType.NETWORK_UNAVAILABLE ||
            mapped.type == AiGradingErrorType.TIMEOUT
    }

    private fun normalizeDeckId(deckId: String?): String? =
        deckId?.trim()?.takeIf { it.isNotEmpty() && !it.equals("all", ignoreCase = true) }

    companion object {
        const val TARGET_CARDS_LIMIT: Int = 5
        const val ERROR_LOGIN_REQUIRED_VI: String =
            "Chức năng Luyện đặt câu với AI yêu cầu đăng nhập tài khoản để sử dụng."
        const val MESSAGE_SAVED_OFFLINE_VI: String =
            "Không có kết nối mạng. Câu đã được lưu Chờ chấm và sẽ tự động gửi khi có mạng."
        const val MESSAGE_RETRY_QUEUED_VI: String =
            "Đã đưa câu vào hàng đợi chấm lại với AI."
        const val MESSAGE_DUPLICATE_IN_FLIGHT_VI: String =
            "Câu này đang ở trạng thái Chờ chấm hoặc Đang chấm, vui lòng đợi kết quả."

        private val SENTENCE_END_PUNCTUATIONS = setOf('.', '!', '?')

        /**
         * Chuẩn hóa văn bản nhận từ giọng nói: viết hoa ký tự đầu và đảm bảo dấu kết câu (Mục 6.7).
         */
        internal fun normalizeVoiceSentence(rawText: String): String {
            val collapsed = rawText.trim().replace(Regex("\\s+"), " ")
            if (collapsed.isEmpty()) return ""

            val capitalized = collapsed.replaceFirstChar { first ->
                if (first.isLowerCase()) first.titlecase(Locale.US) else first.toString()
            }
            return if (capitalized.last() in SENTENCE_END_PUNCTUATIONS) {
                capitalized
            } else {
                "$capitalized."
            }
        }

        /**
         * Khởi tạo [PracticeViewModel] thông qua [memoraViewModelFactory], inject
         * [PracticeSentenceRepository], [UserPreferencesRepository], [AuthRepository]
         * và optional [GradingScheduler] (Mục 6.7 & 8.2).
         */
        fun factory(
            gradingScheduler: GradingScheduler? = null,
            initialDeckId: String? = null
        ): ViewModelProvider.Factory = memoraViewModelFactory { container ->
            val appContext =
                this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]?.applicationContext
            PracticeViewModel(
                practiceSentenceRepository = container.practiceSentenceRepository,
                userPreferencesRepository = container.userPreferencesRepository,
                authRepository = container.authRepository,
                gradingScheduler = gradingScheduler ?: appContext?.let { GradingScheduler(it) },
                initialDeckId = initialDeckId
            )
        }

        val Factory: ViewModelProvider.Factory = factory()
    }
}
