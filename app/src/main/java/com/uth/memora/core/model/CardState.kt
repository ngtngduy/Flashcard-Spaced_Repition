package com.uth.memora.core.model

/**
 * Trạng thái vòng đời của thẻ (Mục 5 & Mục 8.1).
 * Dùng để điều phối hàng đợi trong phiên học.
 */
enum class CardState(val displayNameVi: String) {
    /** Thẻ mới: Chưa từng hiển thị trong phiên học nào */
    NEW("Thẻ mới"),

    /** Thẻ đang học: Đã hiển thị nhưng chưa tốt nghiệp, hoặc vừa bị bấm Chưa nhớ */
    LEARNING("Thẻ đang học"),

    /** Thẻ đã học: Đã vượt qua lần ôn đầu và có lịch ôn tính theo ngày */
    REVIEW("Thẻ đã học")
}
