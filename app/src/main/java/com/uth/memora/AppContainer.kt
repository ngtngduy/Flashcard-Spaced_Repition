package com.uth.memora

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.firebase.FirebaseApp
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
import com.uth.memora.core.domain.AutoFillCardData
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.CloudSyncManager
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
import com.uth.memora.core.model.AiEvaluationResult
import com.uth.memora.integration.ai.AiAutoFillException
import com.uth.memora.integration.ai.GeminiAiGradingService
import com.uth.memora.integration.ai.GeminiCardAutoFillService
import com.uth.memora.integration.auth.FirebaseAuthRepository
import com.uth.memora.integration.reminder.ReminderScheduler
import com.uth.memora.integration.sync.FirestoreCloudSyncManager
import com.uth.memora.integration.sync.SyncStatusProviderImpl
import com.uth.memora.integration.worker.GradingScheduler
import com.uth.memora.integration.worker.SyncScheduler

/**
 * Thùng chứa phụ thuộc (Manual Dependency Injection Container).
 *
 * Cung cấp toàn bộ Repository và Service thật (Room Database, DataStore Preferences,
 * Gemini AI Grading & Auto-Fill, Firebase Auth, Firestore Cloud Sync, WorkManager Schedulers)
 * cho các ViewModel thông qua [memoraViewModelFactory].
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

    private val isFirebaseAvailable: Boolean by lazy {
        runCatching {
            FirebaseApp.getApps(appContext).isNotEmpty() ||
                FirebaseApp.initializeApp(appContext) != null
        }.getOrDefault(false)
    }

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
        if (isFirebaseAvailable) {
            runCatching {
                FirebaseAuthRepository(
                    context = appContext,
                    database = database,
                    userPreferencesRepository = userPreferencesRepository
                )
            }.getOrElse { FakeAuthRepository() }
        } else {
            FakeAuthRepository()
        }
    }

    override val aiGradingService: AiGradingService by lazy {
        val fallback = FakeAiGradingService(simulatedDelayMillis = 400L)
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            val gemini = GeminiAiGradingService(apiKey = BuildConfig.GEMINI_API_KEY)
            object : AiGradingService {
                override suspend fun evaluateSentence(
                    targetWord: String,
                    meaningVi: String,
                    userSentence: String
                ): Result<AiEvaluationResult> {
                    val primary = gemini.evaluateSentence(targetWord, meaningVi, userSentence)
                    return if (primary.isSuccess) {
                        primary
                    } else {
                        fallback.evaluateSentence(targetWord, meaningVi, userSentence)
                    }
                }
            }
        } else {
            fallback
        }
    }

    override val aiCardAutoFillService: AiCardAutoFillService by lazy {
        val fallback = FakeAiCardAutoFillService()
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) {
            val gemini = GeminiCardAutoFillService(apiKey = BuildConfig.GEMINI_API_KEY)
            object : AiCardAutoFillService {
                override suspend fun autoFillCard(word: String): Result<AutoFillCardData> {
                    val primary = gemini.autoFillCard(word)
                    return if (primary.isSuccess) {
                        primary
                    } else {
                        val err = primary.exceptionOrNull()
                        if (err is AiAutoFillException.BlankWord) {
                            primary
                        } else {
                            fallback.autoFillCard(word)
                        }
                    }
                }
            }
        } else {
            fallback
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
        if (isFirebaseAvailable) {
            runCatching {
                FirestoreCloudSyncManager(
                    context = appContext,
                    authRepository = authRepository,
                    database = database,
                    userPreferencesRepository = userPreferencesRepository
                )
            }.getOrElse { FakeCloudSyncManager() }
        } else {
            FakeCloudSyncManager()
        }
    }

    override val syncStatusProvider: SyncStatusProvider by lazy {
        SyncStatusProviderImpl(
            context = appContext,
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
     * 3. Đặt lịch đồng bộ định kỳ và theo dõi cài đặt giờ nhắc học hằng ngày qua WorkManager.
     */
    suspend fun initializeStartupTasks() {
        runCatching { prebuiltDeckSeeder.seedIfNeeded() }
        runCatching {
            GradingScheduler(appContext).resumePendingSentences(practiceSentenceRepository)
        }
        runCatching {
            SyncScheduler.schedulePeriodicSync(appContext)
            SyncScheduler.enqueueImmediateSync(appContext)
        }
        runCatching {
            ReminderScheduler(appContext).observeAndSync(userPreferencesRepository)
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
