package com.uth.memora.feature.navigation

import com.uth.memora.core.model.StudyMode

/**
 * Định nghĩa danh sách các màn hình và route điều hướng trong ứng dụng Memora.
 *
 * Khớp 100% với thiết kế Figma và phân công công việc của 3 thành viên.
 */
object MemoraRoutes {
    // Khởi động lần đầu
    const val ONBOARDING = "onboarding"

    // 5 Tab chính của Bottom Navigation
    const val HOME = "home"
    const val DECKS = "decks"
    const val PRACTICE = "practice"
    const val ANALYTICS = "analytics"
    const val SETTINGS = "settings"

    // Màn hình chi tiết bộ thẻ & chỉnh sửa thẻ
    const val DECK_DETAIL_BASE = "deck_detail"
    const val DECK_DETAIL = "deck_detail/{deckId}"
    fun deckDetail(deckId: String): String = "$DECK_DETAIL_BASE/$deckId"

    const val CARD_EDITOR_BASE = "card_editor"
    const val CARD_EDITOR = "card_editor/{deckId}?cardId={cardId}"
    fun cardEditor(deckId: String, cardId: String? = null): String {
        return if (cardId != null) "$CARD_EDITOR_BASE/$deckId?cardId=$cardId"
        else "$CARD_EDITOR_BASE/$deckId"
    }

    // Phiên học Spaced Repetition (SM-2) & Màn hình nghỉ giữa chặng
    const val STUDY_BASE = "study"
    const val STUDY = "study/{deckId}?mode={mode}"
    fun study(deckId: String, mode: StudyMode = StudyMode.DUE_REVIEW): String =
        "$STUDY_BASE/$deckId?mode=${mode.name}"

    const val INTERMISSION_BASE = "study_intermission"
    const val INTERMISSION = "study_intermission/{deckId}"
    fun intermission(deckId: String): String = "$INTERMISSION_BASE/$deckId"

    // Đăng nhập & Tài khoản
    const val AUTH = "auth"
}
