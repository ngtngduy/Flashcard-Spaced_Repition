package com.uth.memora.core.fake

import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bản giả lập DataStore lưu trữ cài đặt và hạn mức (In-Memory).
 */
class FakeUserPreferencesRepository(
    initialPreferences: UserPreferences = UserPreferences()
) : UserPreferencesRepository {

    private val _preferences = MutableStateFlow(initialPreferences)

    override fun observeUserPreferences(): Flow<UserPreferences> = _preferences.asStateFlow()

    override suspend fun getUserPreferences(): UserPreferences = _preferences.value

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> {
        _preferences.value = _preferences.value.copy(themeMode = mode)
        return Result.success(Unit)
    }

    override suspend fun setDailyNewCardsLimit(limit: Int): Result<Unit> {
        val clamped = limit.coerceIn(UserPreferences.MIN_DAILY_NEW_CARDS, UserPreferences.MAX_DAILY_NEW_CARDS)
        _preferences.value = _preferences.value.copy(dailyNewCardsLimit = clamped)
        return Result.success(Unit)
    }

    override suspend fun setDailyReminder(enabled: Boolean, hour: Int, minute: Int): Result<Unit> {
        _preferences.value = _preferences.value.copy(
            isReminderEnabled = enabled,
            reminderHour = hour.coerceIn(0, 23),
            reminderMinute = minute.coerceIn(0, 59)
        )
        return Result.success(Unit)
    }

    override suspend fun setSoundEnabled(enabled: Boolean): Result<Unit> {
        _preferences.value = _preferences.value.copy(isSoundEnabled = enabled)
        return Result.success(Unit)
    }

    override suspend fun setOnboardingCompleted(completed: Boolean): Result<Unit> {
        _preferences.value = _preferences.value.copy(isOnboardingCompleted = completed)
        return Result.success(Unit)
    }

    override suspend fun updateStreak(
        streak: Int,
        isFrozen: Boolean,
        lastStudyLearningDay: Long?,
        lastFreezeUsedDay: Long?
    ): Result<Unit> {
        _preferences.value = _preferences.value.copy(
            currentStreak = streak.coerceAtLeast(0),
            isStreakFreezeActive = isFrozen,
            lastStudyLearningDay = lastStudyLearningDay ?: _preferences.value.lastStudyLearningDay,
            lastStreakFreezeUsedDay = lastFreezeUsedDay ?: _preferences.value.lastStreakFreezeUsedDay
        )
        return Result.success(Unit)
    }

    override suspend fun consumeAutoFillQuota(): Result<Int> {
        val current = _preferences.value.aiAutoFillRemainingToday
        if (current <= 0) {
            return Result.failure(IllegalStateException("Đã hết 30 lượt AI Auto-Fill hôm nay."))
        }
        val remaining = current - 1
        _preferences.value = _preferences.value.copy(aiAutoFillRemainingToday = remaining)
        return Result.success(remaining)
    }

    override suspend fun consumeSentenceGradingQuota(): Result<Int> {
        val current = _preferences.value.aiSentenceGradingRemainingToday
        if (current <= 0) {
            return Result.failure(IllegalStateException("Đã hết 20 câu luyện tập AI hôm nay."))
        }
        val remaining = current - 1
        _preferences.value = _preferences.value.copy(aiSentenceGradingRemainingToday = remaining)
        return Result.success(remaining)
    }

    override suspend fun resetDailyQuotasIfNeeded(currentEpochDay: Long): Result<Unit> {
        if (_preferences.value.lastQuotaResetEpochDay < currentEpochDay) {
            _preferences.value = _preferences.value.copy(
                aiAutoFillRemainingToday = UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT,
                aiSentenceGradingRemainingToday = UserPreferences.DEFAULT_AI_SENTENCE_LIMIT,
                lastQuotaResetEpochDay = currentEpochDay
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setLastSyncedAtMillis(timestampMillis: Long): Result<Unit> {
        _preferences.value = _preferences.value.copy(lastSyncedAtMillis = timestampMillis)
        return Result.success(Unit)
    }
}
