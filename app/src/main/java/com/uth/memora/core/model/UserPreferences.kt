package com.uth.memora.core.model

/**
 * Chế độ giao diện người dùng (Mục 13 & Settings Figma 13:2).
 */
enum class ThemeMode(val labelVi: String) {
    SYSTEM("Hệ thống"),
    LIGHT("Sáng"),
    DARK("Tối")
}

/**
 * Cài đặt ứng dụng và hạn mức người dùng lưu trong DataStore (Mục 11, 13).
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyNewCardsLimit: Int = DEFAULT_DAILY_NEW_CARDS,
    val reminderHour: Int = DEFAULT_REMINDER_HOUR,
    val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
    val isReminderEnabled: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val isOnboardingCompleted: Boolean = false,
    val currentStreak: Int = 0,
    val isStreakFreezeActive: Boolean = false,
    val hasStreakFreezeAvailable: Boolean = true,
    val lastStudyLearningDay: Long? = null,
    val lastStreakFreezeUsedDay: Long? = null,
    val aiAutoFillRemainingToday: Int = DEFAULT_AI_AUTO_FILL_LIMIT,
    val aiSentenceGradingRemainingToday: Int = DEFAULT_AI_SENTENCE_LIMIT,
    val lastQuotaResetEpochDay: Long = 0L,
    val lastSyncedAtMillis: Long? = null
) {
    companion object {
        const val MIN_DAILY_NEW_CARDS = 5
        const val MAX_DAILY_NEW_CARDS = 50
        const val DEFAULT_DAILY_NEW_CARDS = 20

        const val DEFAULT_REMINDER_HOUR = 20
        const val DEFAULT_REMINDER_MINUTE = 0

        const val DEFAULT_AI_AUTO_FILL_LIMIT = 30
        const val DEFAULT_AI_SENTENCE_LIMIT = 20
    }
}
