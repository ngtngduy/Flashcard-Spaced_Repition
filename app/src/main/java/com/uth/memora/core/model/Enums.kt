package com.uth.memora.core.model

/**
 * Danh sách loại từ cố định (Mục 11).
 */
enum class PartOfSpeech(val labelVi: String, val abbr: String) {
    NOUN("Danh từ", "n"),
    VERB("Động từ", "v"),
    ADJECTIVE("Tính từ", "adj"),
    ADVERB("Trạng từ", "adv"),
    PREPOSITION("Giới từ", "prep"),
    CONJUNCTION("Liên từ", "conj"),
    OTHER("Khác", "other")
}

/**
 * Trạng thái đồng bộ đám mây của bản ghi (Mục 8.3 & 10.1).
 */
enum class SyncStatus(val labelVi: String) {
    SYNCED("Đã đồng bộ"),
    PENDING_SYNC("Chờ đồng bộ"),
    SYNC_ERROR("Lỗi đồng bộ")
}

/**
 * Trạng thái của câu luyện tập với AI (Mục 8.2).
 */
enum class SentenceStatus(val labelVi: String) {
    /** Đã qua kiểm tra cục bộ, chờ mạng hoặc chờ gửi */
    PENDING_GRADE("Chờ chấm"),

    /** Đã gửi lên dịch vụ AI, đang chờ phản hồi (Hiển thị Shimmer Loading) */
    GRADING("Đang chấm"),

    /** Đã nhận điểm, nhận xét và câu sửa từ AI */
    GRADED("Đã chấm"),

    /** Thử lại tối đa 3 lần không thành công hoặc kết quả sai định dạng */
    GRADE_ERROR("Lỗi chấm")
}

/**
 * Chế độ của một phiên học Flashcard (Mục 6.5 & 6.6, Figma 2104:23 vs 2101:15).
 */
enum class StudyMode(val labelVi: String) {
    /** Học từ mới: mặt sau có 2 nút "Học lại" và "Đã nhớ" */
    NEW_CARDS("Học từ mới"),

    /** Ôn tập đến hạn: mặt sau có 4 nút SM-2 ("Chưa nhớ", "Khó", "Nhớ tốt", "Dễ") */
    DUE_REVIEW("Ôn tập")
}
