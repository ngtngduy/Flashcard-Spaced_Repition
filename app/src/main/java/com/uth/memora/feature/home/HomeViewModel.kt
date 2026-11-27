package com.uth.memora.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.domain.StatisticsRepository
import com.uth.memora.core.domain.SyncStatusProvider
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.StudyStatistics
import com.uth.memora.core.model.UserPreferences
import com.uth.memora.core.sm2.LearningDayCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tóm tắt thông tin bộ từ hiển thị trên danh sách nổi bật ở Trang chính (Figma 1:2 & Mục 6.2).
 */
data class HomeDeckItem(
    val deck: Deck,
    val totalCardsCount: Int = 0,
    val dueCardsCount: Int = 0,
    val newCardsCount: Int = 0
)

/**
 * Trạng thái giao diện cho màn Trang chính (Home Dashboard - Figma 1:2, Mục 6.5, 6.6 & 10).
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val statistics: StudyStatistics = StudyStatistics(),
    val activeDecks: List<Deck> = emptyList(),
    val deckItems: List<HomeDeckItem> = emptyList(),
    val userPreferences: UserPreferences = UserPreferences(),
    val newCardsLearnedToday: Int = 0,
    val dailyNewCardLimit: Int = UserPreferences.DEFAULT_DAILY_NEW_CARDS,
    val remainingNewCardsToday: Int = 0,
    val dueCardsCount: Int = 0,
    val totalDecksCount: Int = 0,
    val currentStreakDays: Int = 0,
    val streakFreezeAvailable: Boolean = true,
    val isStreakProtectedToday: Boolean = false,
    val syncMessage: String? = null,
    val errorMessage: String? = null
) {
    /**
     * Tỷ lệ hoàn thành hạn mức thẻ mới hôm nay (0f..1f).
     */
    val newCardsProgressFraction: Float
        get() {
            if (dailyNewCardLimit <= 0) return 0f
            return (newCardsLearnedToday.toFloat() / dailyNewCardLimit.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * ViewModel kết hợp dữ liệu từ StatisticsRepository, DeckRepository, UserPreferencesRepository
 * (và CardRepository / SyncStatusProvider) cho màn hình Trang chính (Mục 6 & 10).
 */
class HomeViewModel(
    private val deckRepository: DeckRepository,
    private val statisticsRepository: StatisticsRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cardRepository: CardRepository? = null,
    private val syncStatusProvider: SyncStatusProvider? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeHomeData()
    }

    /**
     * Quan sát luồng dữ liệu thống kê, bộ từ đang hoạt động và cài đặt người dùng.
     */
    fun observeHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val cardsFlow = cardRepository?.observeAllActiveCards() ?: flowOf(emptyList())

            combine(
                statisticsRepository.observeStatistics(),
                deckRepository.observeActiveDecks(),
                userPreferencesRepository.observeUserPreferences(),
                cardsFlow
            ) { stats, decks, prefs, allCards ->
                val nowMillis = System.currentTimeMillis()
                val currentLearningDay = LearningDayCalculator.toLearningDay(nowMillis)
                val cardsByDeck = allCards.filter { !it.isDeleted }.groupBy { it.deckId }

                val deckItems = decks.map { deck ->
                    val deckCards = cardsByDeck[deck.id].orEmpty()
                    val dueCount = deckCards.count { it.isDue(currentLearningDay, nowMillis) }
                    val newCount = deckCards.count { it.state == CardState.NEW }
                    HomeDeckItem(
                        deck = deck,
                        totalCardsCount = deckCards.size,
                        dueCardsCount = dueCount,
                        newCardsCount = newCount
                    )
                }

                val effectiveLimit = if (prefs.dailyNewCardsLimit > 0) {
                    prefs.dailyNewCardsLimit
                } else {
                    stats.dailyNewCardLimit
                }
                val effectiveStreak = maxOf(stats.currentStreakDays, prefs.currentStreak)
                val freezeAvailable = stats.streakFreezeAvailable && prefs.hasStreakFreezeAvailable
                val streakProtected = stats.isStreakProtectedToday || prefs.isStreakFreezeActive

                _uiState.value.copy(
                    isLoading = false,
                    statistics = stats,
                    activeDecks = decks,
                    deckItems = deckItems,
                    userPreferences = prefs,
                    newCardsLearnedToday = stats.newCardsLearnedToday,
                    dailyNewCardLimit = effectiveLimit,
                    remainingNewCardsToday = stats.remainingNewCardsToday,
                    dueCardsCount = stats.dueCardsCount,
                    totalDecksCount = decks.size,
                    currentStreakDays = effectiveStreak,
                    streakFreezeAvailable = freezeAvailable,
                    isStreakProtectedToday = streakProtected,
                    errorMessage = null
                )
            }.catch { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải dữ liệu Trang chính."
                    )
                }
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    /**
     * Kéo xuống làm mới (Pull-to-Refresh) gọi SyncStatusProvider.triggerImmediateSync() (Mục 10.1).
     */
    fun triggerImmediateSync() {
        val provider = syncStatusProvider
        if (provider == null) {
            _uiState.update { it.copy(isRefreshing = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, syncMessage = null) }
            provider.triggerImmediateSync()
                .onSuccess { syncedCount ->
                    val message = if (syncedCount > 0) {
                        "Đã đồng bộ $syncedCount thay đổi lên đám mây."
                    } else {
                        "Dữ liệu đã ở trạng thái mới nhất."
                    }
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            syncMessage = message
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            syncMessage = error.message ?: "Chưa thể đồng bộ lúc này (Chế độ Offline)."
                        )
                    }
                }
        }
    }

    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }
}
