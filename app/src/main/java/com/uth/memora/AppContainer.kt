package com.uth.memora

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.uth.memora.core.data.PrebuiltDeckSeeder
import com.uth.memora.core.data.RoomCardRepository
import com.uth.memora.core.data.RoomDeckRepository
import com.uth.memora.core.data.RoomPracticeSentenceRepository
import com.uth.memora.core.data.RoomStatisticsRepository
import com.uth.memora.core.data.RoomStudySessionRepository
import com.uth.memora.core.database.MemoraDatabase
import com.uth.memora.core.datastore.UserPreferencesDataStore
import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.CloudSyncManager
import com.uth.memora.core.domain.CloudSyncSummary
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.domain.PracticeSentenceRepository
import com.uth.memora.core.domain.StatisticsRepository
import com.uth.memora.core.domain.StudySessionRepository
import com.uth.memora.core.domain.SyncStatusProvider
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.fake.FakeAiCardAutoFillService
import com.uth.memora.core.fake.FakeAiGradingService
import com.uth.memora.core.fake.FakeAuthRepository
import com.uth.memora.core.fake.FakeCloudSyncManager
import com.uth.memora.core.model.Deck
import com.uth.memora.integration.ai.GeminiAiGradingService
import com.uth.memora.integration.ai.GeminiCardAutoFillService
import com.uth.memora.integration.worker.GradingScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Thùng chứa phụ thuộc (Manual Dependency Injection Container).
 *
 * Cung cấp toàn bộ Repository và Service thật (Room Database, DataStore Preferences,
 * Gemini AI Grading & Auto-Fill, WorkManager Schedulers) cho các ViewModel thông qua [memoraViewModelFactory].
 */
interface AppContainer {
    val deckRepository: DeckRepository
    val cardRepository: CardRepository
    val studySessionRepository: StudySessionRepository
    val practiceSentenceRepository: PracticeSentenceRepository
    val statisticsRepository: StatisticsRepository
    val userPreferencesRepository: UserPreferencesRepository
    val authRepository: AuthRepository
    val aiGradingService: AiGradingService
    val aiCardAutoFillService: AiCardAutoFillService
    val cloudSyncManager: CloudSyncManager
    val syncStatusProvider: SyncStatusProvider
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val appContext: Context = context.applicationContext

    val database: MemoraDatabase by lazy {
        MemoraDatabase.getInstance(appContext)
    }

    val prebuiltDeckSeeder: PrebuiltDeckSeeder by lazy {
        PrebuiltDeckSeeder(
            context = appContext,
            database = database,
            deckDao = database.deckDao(),
            cardDao = database.cardDao()
        )
    }

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesDataStore(appContext)
    }

    override val authRepository: AuthRepository by lazy {
        FakeAuthRepository()
    }

    override val aiGradingService: AiGradingService by lazy {
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            GeminiAiGradingService(apiKey = BuildConfig.GEMINI_API_KEY)
        } else {
            FakeAiGradingService()
        }
    }

    override val aiCardAutoFillService: AiCardAutoFillService by lazy {
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            GeminiCardAutoFillService(apiKey = BuildConfig.GEMINI_API_KEY)
        } else {
            FakeAiCardAutoFillService()
        }
    }

    override val deckRepository: DeckRepository by lazy {
        RoomDeckRepository(
            database = database,
            deckDao = database.deckDao(),
            cardDao = database.cardDao(),
            authRepository = authRepository,
            prebuiltDeckSeeder = prebuiltDeckSeeder
        )
    }

    override val cardRepository: CardRepository by lazy {
        RoomCardRepository(
            cardDao = database.cardDao(),
            deckDao = database.deckDao(),
            preferencesRepository = userPreferencesRepository,
            aiAutoFillService = aiCardAutoFillService,
            authRepository = authRepository
        )
    }

    override val studySessionRepository: StudySessionRepository by lazy {
        RoomStudySessionRepository(
            database = database,
            cardDao = database.cardDao(),
            reviewLogDao = database.reviewLogDao(),
            preferencesRepository = userPreferencesRepository,
            authRepository = authRepository
        )
    }

    override val practiceSentenceRepository: PracticeSentenceRepository by lazy {
        RoomPracticeSentenceRepository(
            practiceSentenceDao = database.practiceSentenceDao(),
            cardDao = database.cardDao(),
            preferencesRepository = userPreferencesRepository,
            aiGradingService = aiGradingService,
            authRepository = authRepository
        )
    }

    override val statisticsRepository: StatisticsRepository by lazy {
        RoomStatisticsRepository(
            reviewLogDao = database.reviewLogDao(),
            cardDao = database.cardDao(),
            preferencesRepository = userPreferencesRepository,
            authRepository = authRepository
        )
    }

    override val cloudSyncManager: CloudSyncManager by lazy {
        FakeCloudSyncManager()
    }

    override val syncStatusProvider: SyncStatusProvider by lazy {
        RoomBackedSyncStatusProvider(
            database = database,
            authRepository = authRepository,
            userPreferencesRepository = userPreferencesRepository,
            cloudSyncManager = cloudSyncManager
        )
    }

    /**
     * Khởi chạy các tác vụ nền an toàn khi mở ứng dụng:
     * 1. Chèn idempotent 2 bộ từ mẫu ngoại tuyến (`Oxford 3000 cơ bản` & `IELTS cơ bản`) vào Room.
     * 2. Khôi phục hàng đợi chấm câu AI cho các câu đang ở trạng thái `PENDING_GRADE`.
     */
    suspend fun initializeStartupTasks() {
        runCatching { prebuiltDeckSeeder.seedIfNeeded() }
        runCatching {
            GradingScheduler(appContext).resumePendingSentences(practiceSentenceRepository)
        }
    }
}

/**
 * Bộ cung cấp trạng thái đồng bộ thời gian thực dựa trên số bản ghi `PENDING_SYNC` thật trong Room Database
 * kết hợp với `lastSyncedAtMillis` từ DataStore (Mục 8.3, 10.1 & 13).
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class RoomBackedSyncStatusProvider(
    private val database: MemoraDatabase,
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cloudSyncManager: CloudSyncManager
) : SyncStatusProvider {

    private val isSyncingState = MutableStateFlow(false)
    private val lastErrorState = MutableStateFlow<String?>(null)

    override fun observeSyncSummary(): Flow<CloudSyncSummary> {
        val ownerFlow = authRepository
            .observeCurrentUser()
            .map { user -> user?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID }
            .distinctUntilChanged()

        val pendingCountFlow = ownerFlow.flatMapLatest { ownerId ->
            combine(
                database.deckDao().countPendingSync(ownerId),
                database.cardDao().countPendingSync(ownerId),
                database.reviewLogDao().countPendingSync(ownerId),
                database.practiceSentenceDao().countPendingSync(ownerId)
            ) { decks, cards, logs, sentences ->
                (decks + cards + logs + sentences).coerceAtLeast(0)
            }
        }

        return combine(
            pendingCountFlow,
            userPreferencesRepository.observeUserPreferences(),
            isSyncingState,
            lastErrorState
        ) { pendingCount, prefs, isSyncing, errorMsg ->
            CloudSyncSummary(
                isSyncing = isSyncing,
                pendingChangesCount = pendingCount,
                lastSyncedAtMillis = prefs.lastSyncedAtMillis,
                syncErrorMessage = errorMsg
            )
        }
    }

    override suspend fun triggerImmediateSync(): Result<Int> {
        isSyncingState.value = true
        lastErrorState.value = null
        return try {
            val result = cloudSyncManager.syncPendingChanges()
            result.onSuccess {
                userPreferencesRepository.setLastSyncedAtMillis(System.currentTimeMillis())
            }.onFailure { err ->
                lastErrorState.value = err.message
            }
            result
        } finally {
            isSyncingState.value = false
        }
    }
}

/**
 * Trợ giúp khởi tạo ViewModel dễ dàng từ Composable:
 * val viewModel: HomeViewModel = viewModel(factory = memoraViewModelFactory { HomeViewModel(it.deckRepository) })
 */
fun <VM : ViewModel> memoraViewModelFactory(
    initializer: CreationExtras.(AppContainer) -> VM
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as? MemoraApplication
            ?: throw IllegalStateException("Application must be an instance of MemoraApplication")
        return initializer(extras, app.container) as T
    }
}
