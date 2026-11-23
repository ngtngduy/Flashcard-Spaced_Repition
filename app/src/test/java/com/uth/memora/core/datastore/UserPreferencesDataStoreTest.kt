package com.uth.memora.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.uth.memora.core.model.ThemeMode
import com.uth.memora.core.model.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Kiểm thử toàn bộ UserPreferencesDataStore trên file DataStore thật (M1-C1..C6).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun defaultPreferences_matchSpecificationDefaults() = runTest {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        try {
            val file = tempFolder.newFile("default_test.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = dsScope,
                produceFile = { file }
            )
            val repo = UserPreferencesDataStore(dataStore) { 20_400L }

            val prefs = repo.getUserPreferences()
            assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
            assertEquals(UserPreferences.DEFAULT_DAILY_NEW_CARDS, prefs.dailyNewCardsLimit)
            assertTrue(prefs.isReminderEnabled)
            assertEquals(UserPreferences.DEFAULT_REMINDER_HOUR, prefs.reminderHour)
            assertEquals(UserPreferences.DEFAULT_REMINDER_MINUTE, prefs.reminderMinute)
            assertTrue(prefs.isSoundEnabled)
            assertFalse(prefs.isOnboardingCompleted)
            assertEquals(0, prefs.currentStreak)
            assertFalse(prefs.isStreakFreezeActive)
            assertTrue(prefs.hasStreakFreezeAvailable)
            assertNull(prefs.lastStudyLearningDay)
            assertNull(prefs.lastStreakFreezeUsedDay)
            assertEquals(UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT, prefs.aiAutoFillRemainingToday)
            assertEquals(UserPreferences.DEFAULT_AI_SENTENCE_LIMIT, prefs.aiSentenceGradingRemainingToday)
            assertNull(prefs.lastSyncedAtMillis)
        } finally {
            dsScope.cancel()
        }
    }

    @Test
    fun themeReminderSoundOnboardingAndClampedDailyLimit_persistCorrectly() = runTest {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        try {
            val file = tempFolder.newFile("settings_test.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = dsScope,
                produceFile = { file }
            )
            val repo = UserPreferencesDataStore(dataStore) { 20_400L }

            repo.setThemeMode(ThemeMode.DARK)
            repo.setDailyNewCardsLimit(100) // ép trần về 50
            repo.setDailyReminder(enabled = false, hour = 25, minute = -5) // ép về 23:00
            repo.setSoundEnabled(false)
            repo.setOnboardingCompleted(true)

            val prefs = repo.getUserPreferences()
            assertEquals(ThemeMode.DARK, prefs.themeMode)
            assertEquals(UserPreferences.MAX_DAILY_NEW_CARDS, prefs.dailyNewCardsLimit)
            assertFalse(prefs.isReminderEnabled)
            assertEquals(23, prefs.reminderHour)
            assertEquals(0, prefs.reminderMinute)
            assertFalse(prefs.isSoundEnabled)
            assertTrue(prefs.isOnboardingCompleted)

            repo.setDailyNewCardsLimit(1) // ép sàn về 5
            assertEquals(UserPreferences.MIN_DAILY_NEW_CARDS, repo.getUserPreferences().dailyNewCardsLimit)
        } finally {
            dsScope.cancel()
        }
    }

    @Test
    fun streakAndFreeze_trackUsageAndRecoverAfter30Days() = runTest {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        try {
            var currentDay = 20_402L
            val file = tempFolder.newFile("streak_test.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = dsScope,
                produceFile = { file }
            )
            val repo = UserPreferencesDataStore(dataStore) { currentDay }

            repo.updateStreak(
                streak = 10,
                isFrozen = true,
                lastStudyLearningDay = 20_402L,
                lastFreezeUsedDay = 20_401L
            )
            repo.setLastSyncedAtMillis(1_700_500_000_000L)

            val afterFreeze = repo.getUserPreferences()
            assertEquals(10, afterFreeze.currentStreak)
            assertTrue(afterFreeze.isStreakFreezeActive)
            assertFalse(afterFreeze.hasStreakFreezeAvailable)
            assertEquals(20_402L, afterFreeze.lastStudyLearningDay)
            assertEquals(20_401L, afterFreeze.lastStreakFreezeUsedDay)
            assertEquals(1_700_500_000_000L, afterFreeze.lastSyncedAtMillis)

            // Ngày thứ 29 kể từ 20_401L -> khiên vẫn chưa hồi
            currentDay = 20_430L
            assertFalse(repo.getUserPreferences().hasStreakFreezeAvailable)

            // Đúng ngày thứ 30 kể từ 20_401L -> khiên tự động hồi phục
            currentDay = 20_431L
            assertTrue(repo.getUserPreferences().hasStreakFreezeAvailable)
        } finally {
            dsScope.cancel()
        }
    }

    @Test
    fun dailyAiQuotas_enforceLimitsAndResetAt4amLearningDayBoundary() = runTest {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        try {
            var currentLearningDay = 20_400L
            val file = tempFolder.newFile("quota_test.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = dsScope,
                produceFile = { file }
            )
            val repo = UserPreferencesDataStore(dataStore) { currentLearningDay }

            // Tiêu hết 30 lượt Auto-Fill trong ngày 20_400L
            repeat(UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT) { index ->
                val res = repo.consumeAutoFillQuota()
                assertTrue(res.isSuccess)
                assertEquals(UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT - 1 - index, res.getOrThrow())
            }
            // Lượt thứ 31 bị chặn
            assertTrue(repo.consumeAutoFillQuota().isFailure)

            // Tiêu hết 20 lượt chấm câu AI trong ngày 20_400L
            repeat(UserPreferences.DEFAULT_AI_SENTENCE_LIMIT) { index ->
                val res = repo.consumeSentenceGradingQuota()
                assertTrue(res.isSuccess)
                assertEquals(UserPreferences.DEFAULT_AI_SENTENCE_LIMIT - 1 - index, res.getOrThrow())
            }
            // Lượt thứ 21 bị chặn
            assertTrue(repo.consumeSentenceGradingQuota().isFailure)

            // Gọi reset cùng ngày 20_400L không được hồi lại hạn mức
            repo.resetDailyQuotasIfNeeded(20_400L)
            assertEquals(0, repo.getUserPreferences().aiAutoFillRemainingToday)
            assertEquals(0, repo.getUserPreferences().aiSentenceGradingRemainingToday)

            // Bước qua mốc 4:00 sáng sang ngày học mới 20_401L -> tự hồi đầy 30 & 20 lượt
            currentLearningDay = 20_401L
            assertEquals(
                UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT,
                repo.getUserPreferences().aiAutoFillRemainingToday
            )
            assertEquals(
                UserPreferences.DEFAULT_AI_SENTENCE_LIMIT,
                repo.getUserPreferences().aiSentenceGradingRemainingToday
            )

            // Dùng lượt đầu tiên của ngày mới thành công (30 -> 29, 20 -> 19)
            assertEquals(29, repo.consumeAutoFillQuota().getOrThrow())
            assertEquals(19, repo.consumeSentenceGradingQuota().getOrThrow())
        } finally {
            dsScope.cancel()
        }
    }

    @Test
    fun clearSessionOnSignOut_wipesUserSessionButPreservesDeviceSettings() = runTest {
        val dsScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        try {
            val file = tempFolder.newFile("signout_test.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = dsScope,
                produceFile = { file }
            )
            val repo = UserPreferencesDataStore(dataStore) { 20_400L }

            repo.setThemeMode(ThemeMode.LIGHT)
            repo.setOnboardingCompleted(true)
            repo.updateStreak(streak = 14, isFrozen = true, lastStudyLearningDay = 20_400L, lastFreezeUsedDay = 20_399L)
            repo.consumeAutoFillQuota()
            repo.setLastSyncedAtMillis(1_700_600_000_000L)

            repo.clearSessionOnSignOut()

            val afterSignOut = repo.getUserPreferences()
            // Giữ cài đặt thiết bị
            assertEquals(ThemeMode.LIGHT, afterSignOut.themeMode)
            assertTrue(afterSignOut.isOnboardingCompleted)
            // Xóa sạch trạng thái phiên của người dùng trước
            assertEquals(0, afterSignOut.currentStreak)
            assertFalse(afterSignOut.isStreakFreezeActive)
            assertTrue(afterSignOut.hasStreakFreezeAvailable)
            assertNull(afterSignOut.lastStudyLearningDay)
            assertNull(afterSignOut.lastStreakFreezeUsedDay)
            assertEquals(UserPreferences.DEFAULT_AI_AUTO_FILL_LIMIT, afterSignOut.aiAutoFillRemainingToday)
            assertNull(afterSignOut.lastSyncedAtMillis)
        } finally {
            dsScope.cancel()
        }
    }
}
