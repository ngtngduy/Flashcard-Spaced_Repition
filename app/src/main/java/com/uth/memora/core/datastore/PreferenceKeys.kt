package com.uth.memora.core.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Tập hợp các khóa lưu trữ của Jetpack DataStore Preferences (Mục 10.2, 11 & 13).
 *
 * Khớp 1-1 với các thuộc tính của [com.uth.memora.core.model.UserPreferences].
 */
object PreferenceKeys {

    // Giao diện & mục tiêu học hằng ngày
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DAILY_NEW_CARDS_LIMIT = intPreferencesKey("daily_new_cards_limit")

    // Nhắc học, âm thanh & cờ mở ứng dụng lần đầu
    val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    // Chuỗi ngày học (Streak) & Khiên bảo vệ 30 ngày (Mục 10.2 & 11)
    val CURRENT_STREAK = intPreferencesKey("current_streak")
    val IS_STREAK_FREEZE_ACTIVE = booleanPreferencesKey("is_streak_freeze_active")
    val HAS_STREAK_FREEZE_AVAILABLE = booleanPreferencesKey("has_streak_freeze_available")
    val LAST_STUDY_LEARNING_DAY = longPreferencesKey("last_study_learning_day")
    val LAST_STREAK_FREEZE_USED_DAY = longPreferencesKey("last_streak_freeze_used_day")

    // Hạn mức AI hằng ngày (30 lượt Auto-Fill, 20 câu chấm AI - Mục 6.4, 6.7 & 11)
    val AI_AUTO_FILL_REMAINING = intPreferencesKey("ai_auto_fill_remaining")
    val AI_SENTENCE_REMAINING = intPreferencesKey("ai_sentence_remaining")
    val LAST_QUOTA_RESET_EPOCH_DAY = longPreferencesKey("last_quota_reset_epoch_day")

    // Mốc thời gian đồng bộ đám mây thành công gần nhất (Mục 8.3 & 10.1)
    val LAST_SYNCED_AT_MILLIS = longPreferencesKey("last_synced_at_millis")
}
