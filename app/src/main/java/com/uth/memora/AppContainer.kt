package com.uth.memora

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.uth.memora.core.domain.AiCardAutoFillService
import com.uth.memora.core.domain.AiGradingService
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.CardRepository
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.domain.PracticeSentenceRepository
import com.uth.memora.core.domain.StatisticsRepository
import com.uth.memora.core.domain.StudySessionRepository
import com.uth.memora.core.domain.SyncStatusProvider
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.fake.FakeAiCardAutoFillService
import com.uth.memora.core.fake.FakeAiGradingService
import com.uth.memora.core.fake.FakeAuthRepository
import com.uth.memora.core.fake.FakeMemoraRepository
import com.uth.memora.core.fake.FakeSyncStatusProvider
import com.uth.memora.core.fake.FakeUserPreferencesRepository

/**
 * Thùng chứa phụ thuộc (Manual Dependency Injection Container).
 *
 * Cho phép toàn bộ ứng dụng inject repository qua ViewModelFactory mà không cần dùng Dagger/Hilt.
 * Trong các Sprint đầu (1-3), container cung cấp sẵn các bản Fake để Thành viên 2 và 3 code độc lập.
 * Khi Thành viên 1 hoàn tất Room DB (Sprint 4), chỉ cần đổi binding tại đây sang Room repositories.
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
    val syncStatusProvider: SyncStatusProvider
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val fakeAiGradingService: AiGradingService by lazy {
        FakeAiGradingService()
    }

    private val fakeMemoraRepository: FakeMemoraRepository by lazy {
        FakeMemoraRepository(aiGradingService = fakeAiGradingService)
    }

    override val deckRepository: DeckRepository
        get() = fakeMemoraRepository

    override val cardRepository: CardRepository
        get() = fakeMemoraRepository

    override val studySessionRepository: StudySessionRepository
        get() = fakeMemoraRepository

    override val practiceSentenceRepository: PracticeSentenceRepository
        get() = fakeMemoraRepository

    override val statisticsRepository: StatisticsRepository
        get() = fakeMemoraRepository

    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        FakeUserPreferencesRepository()
    }

    override val authRepository: AuthRepository by lazy {
        FakeAuthRepository()
    }

    override val aiGradingService: AiGradingService
        get() = fakeAiGradingService

    override val aiCardAutoFillService: AiCardAutoFillService by lazy {
        FakeAiCardAutoFillService()
    }

    override val syncStatusProvider: SyncStatusProvider by lazy {
        FakeSyncStatusProvider()
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
