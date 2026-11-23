package com.uth.memora.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.domain.logic.StreakCalculator
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.core.model.UserPreferences
import com.uth.memora.core.sm2.LearningDayCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.memoraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = UserPreferencesDataStore.DATASTORE_NAME
)

/**
 * Hiện thực [UserPreferencesRepository] dựa trên Jetpack DataStore Preferences (Mục 10.2, 11 & 13).
 */
class UserPreferencesDataStore(
    private val dataStore: DataStore<Preferences>,
    private val learningDayProvider: () -> Long = { LearningDayCalculator.toLearningDay() }
) : UserPreferencesRepository {

    constructor(context: Context) : this(context.applicationContext.memoraDataStore)

    override fun observeUserPreferences(): Flow<UserPreferences> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { prefs -> mapPreferences(prefs) }

    override suspend fun getUserPreferences(): UserPreferences =
        observeUserPreferences().first()

    override suspend fun setThemeMode(mode: ThemeMode): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.THEME_MODE] = mode.name
        }
    }

    override suspend fun setDailyNewCardsLimit(limit: Int): Result<Unit> = runCatching {
        val clamped = limit.coerceIn(
            UserPreferences.MIN_DAILY_NEW_CARDS,
            UserPreferences.MAX_DAILY_NEW_CARDS
        )
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.DAILY_NEW_CARDS_LIMIT] = clamped
        }
    }

    override suspend fun setDailyReminder(
        enabled: Boolean,
        hour: Int,
        minute: Int
    ): Result<Unit> = runCatching {
        val clampedHour = hour.coerceIn(0, 23)
        val clampedMinute = minute.coerceIn(0, 59)
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.REMINDER_ENABLED] = enabled
            prefs[PreferenceKeys.REMINDER_HOUR] = clampedHour
            prefs[PreferenceKeys.REMINDER_MINUTE] = clampedMinute
        }
    }

    override suspend fun setSoundEnabled(enabled: Boolean): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.SOUND_ENABLED] = enabled
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.ONBOARDING_COMPLETED] = completed
        }
    }

    override suspend fun updateStreak(
        streak: Int,
        isFrozen: Boolean,
        lastStudyLearningDay: Long?,
        lastFreezeUsedDay: Long?
    ): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.CURRENT_STREAK] = streak.coerceAtLeast(0)
            prefs[PreferenceKeys.IS_STREAK_FREEZE_ACTIVE] = isFrozen
            if (lastStudyLearningDay != null) {
                prefs[PreferenceKeys.LAST_STUDY_LEARNING_DAY] = lastStudyLearningDay
            }
            if (lastFreezeUsedDay != null) {
                prefs[PreferenceKeys.LAST_STREAK_FREEZE_USED_DAY] = lastFreezeUsedDay
            }
            val effectiveFreezeUsedDay =
                lastFreezeUsedDay ?: prefs[PreferenceKeys.LAST_STREAK_FREEZE_USED_DAY]
            val currentAvailable = prefs[PreferenceKeys.HAS_STREAK_FREEZE_AVAILABLE] ?: true
            val referenceDay = lastStudyLearningDay ?: learningDayProvider()
            prefs[PreferenceKeys.HAS_STREAK_FREEZE_AVAILABLE] = if (isFrozen) {
                false
            } else {
                StreakCalculator.isFreezeAvailableOn(
                    todayLearningDay = referenceDay,
                    isFreezeAvailable = currentAvailable,
                    lastFreezeUsedDay = effectiveFreezeUsedDay
                )
            }
        }
    }

    override suspend fun consumeAutoFillQuota(): Result<Int> = runCatching {
        var remaining = 0
        dataStore.edit { prefs ->
            val current = prefs[PreferenceKeys.AI_AUTO_FILL_REMAINING]
                ?: UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT
            if (current <= 0) {
                throw IllegalStateException("Bạn đã dùng hết 30 lượt AI Auto-Fill hôm nay (tự hồi lúc 4:00 sáng).")
            }
            remaining = current - 1
            prefs[PreferenceKeys.AI_AUTO_FILL_REMAINING] = remaining
        }
        remaining
    }

    override suspend fun consumeSentenceGradingQuota(): Result<Int> = runCatching {
        var remaining = 0
        dataStore.edit { prefs ->
            val current = prefs[PreferenceKeys.AI_SENTENCE_REMAINING]
                ?: UserPreferences.DEFAULT_AI_SENTENCE_LIMIT
            if (current <= 0) {
                throw IllegalStateException("Bạn đã dùng hết 20 lượt chấm câu AI hôm nay (tự hồi lúc 4:00 sáng).")
            }
            remaining = current - 1
            prefs[PreferenceKeys.AI_SENTENCE_REMAINING] = remaining
        }
        remaining
    }

    override suspend fun resetDailyQuotasIfNeeded(currentEpochDay: Long): Result<Unit> =
        Result.success(Unit)

    override suspend fun setLastSyncedAtMillis(timestampMillis: Long): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.LAST_SYNCED_AT_MILLIS] = timestampMillis
        }
    }

    /**
     * Xóa trạng thái phiên học và hạn mức cá nhân khi người dùng đăng xuất (Mục 6.2 & 9.7),
     * giữ lại cài đặt thiết bị (giao diện Sáng/Tối, âm thanh, cờ đã xem Onboarding).
     */
    suspend fun clearSessionOnSignOut(): Result<Unit> = runCatching {
        dataStore.edit { prefs ->
            prefs.remove(PreferenceKeys.CURRENT_STREAK)
            prefs.remove(PreferenceKeys.IS_STREAK_FREEZE_ACTIVE)
            prefs.remove(PreferenceKeys.HAS_STREAK_FREEZE_AVAILABLE)
            prefs.remove(PreferenceKeys.LAST_STUDY_LEARNING_DAY)
            prefs.remove(PreferenceKeys.LAST_STREAK_FREEZE_USED_DAY)
            prefs.remove(PreferenceKeys.AI_AUTO_FILL_REMAINING)
            prefs.remove(PreferenceKeys.AI_SENTENCE_REMAINING)
            prefs.remove(PreferenceKeys.LAST_QUOTA_RESET_EPOCH_DAY)
            prefs.remove(PreferenceKeys.LAST_SYNCED_AT_MILLIS)
        }
    }

    private fun mapPreferences(prefs: Preferences): UserPreferences {
        val todayLearningDay = learningDayProvider()
        val themeName = prefs[PreferenceKeys.THEME_MODE]
        val themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM

        val dailyLimit = (prefs[PreferenceKeys.DAILY_NEW_CARDS_LIMIT]
            ?: UserPreferences.DEFAULT_DAILY_NEW_CARDS)
            .coerceIn(UserPreferences.MIN_DAILY_NEW_CARDS, UserPreferences.MAX_DAILY_NEW_CARDS)

        val reminderEnabled = prefs[PreferenceKeys.REMINDER_ENABLED] ?: true
        val reminderHour = (prefs[PreferenceKeys.REMINDER_HOUR]
            ?: UserPreferences.DEFAULT_REMINDER_HOUR)
            .coerceIn(0, 23)
        val reminderMinute = (prefs[PreferenceKeys.REMINDER_MINUTE]
            ?: UserPreferences.DEFAULT_REMINDER_MINUTE)
            .coerceIn(0, 59)
        val soundEnabled = prefs[PreferenceKeys.SOUND_ENABLED] ?: true
        val onboardingCompleted = prefs[PreferenceKeys.ONBOARDING_COMPLETED] ?: false

        val currentStreak = (prefs[PreferenceKeys.CURRENT_STREAK] ?: 0).coerceAtLeast(0)
        val isStreakFreezeActive = prefs[PreferenceKeys.IS_STREAK_FREEZE_ACTIVE] ?: false
        val lastStudyLearningDay = prefs[PreferenceKeys.LAST_STUDY_LEARNING_DAY]
        val lastStreakFreezeUsedDay = prefs[PreferenceKeys.LAST_STREAK_FREEZE_USED_DAY]
        val storedFreezeAvailable = prefs[PreferenceKeys.HAS_STREAK_FREEZE_AVAILABLE] ?: true
        val hasStreakFreezeAvailable = StreakCalculator.isFreezeAvailableOn(
            todayLearningDay = todayLearningDay,
            isFreezeAvailable = storedFreezeAvailable,
            lastFreezeUsedDay = lastStreakFreezeUsedDay
        )
        val lastSyncedAtMillis = prefs[PreferenceKeys.LAST_SYNCED_AT_MILLIS]
        val autoFillRemaining = (prefs[PreferenceKeys.AI_AUTO_FILL_REMAINING]
            ?: UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT)
            .coerceIn(0, UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT)
        val sentenceRemaining = (prefs[PreferenceKeys.AI_SENTENCE_REMAINING]
            ?: UserPreferences.DEFAULT_AI_SENTENCE_LIMIT)
            .coerceIn(0, UserPreferences.DEFAULT_AI_SENTENCE_LIMIT)
        val lastQuotaResetEpochDay = prefs[PreferenceKeys.LAST_QUOTA_RESET_EPOCH_DAY] ?: 0L

        return UserPreferences(
            themeMode = themeMode,
            dailyNewCardsLimit = dailyLimit,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute,
            isReminderEnabled = reminderEnabled,
            isSoundEnabled = soundEnabled,
            isOnboardingCompleted = onboardingCompleted,
            currentStreak = currentStreak,
            isStreakFreezeActive = isStreakFreezeActive,
            hasStreakFreezeAvailable = hasStreakFreezeAvailable,
            lastStudyLearningDay = lastStudyLearningDay,
            lastStreakFreezeUsedDay = lastStreakFreezeUsedDay,
            aiAutoFillRemainingToday = autoFillRemaining,
            aiSentenceGradingRemainingToday = sentenceRemaining,
            lastQuotaResetEpochDay = lastQuotaResetEpochDay,
            lastSyncedAtMillis = lastSyncedAtMillis
        )
    }

    companion object {
        const val DATASTORE_NAME = "memora_prefs"
    }
}
