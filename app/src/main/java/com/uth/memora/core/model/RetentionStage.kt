package com.uth.memora.core.model

/**
 * Phân loại 6 cấp độ ghi nhớ của thẻ (Retention Stages - Mục 7.5).
 * Tính tự động từ trạng thái và khoảng cách ngày ôn (interval) của thẻ.
 * Không trùng tên với 4 nút bấm đánh giá.
 */
enum class RetentionStage(
    val level: Int,
    val titleVi: String,
    val intervalDescriptionVi: String
) {
    LEVEL_0_UNLEARNED(
        level = 0,
        titleVi = "Cấp 0 – Chưa học",
        intervalDescriptionVi = "0 ngày"
    ),
    LEVEL_1_STARTING(
        level = 1,
        titleVi = "Cấp 1 – Khởi động",
        intervalDescriptionVi = "Bước phút (1m, 10m)"
    ),
    LEVEL_2_SHORT_TERM(
        level = 2,
        titleVi = "Cấp 2 – Ghi nhớ ngắn hạn",
        intervalDescriptionVi = "1 – 3 ngày"
    ),
    LEVEL_3_MID_TERM(
        level = 3,
        titleVi = "Cấp 3 – Ghi nhớ trung hạn",
        intervalDescriptionVi = "4 – 10 ngày"
    ),
    LEVEL_4_NEAR_MASTERED(
        level = 4,
        titleVi = "Cấp 4 – Gần thuần thục",
        intervalDescriptionVi = "11 – 20 ngày"
    ),
    LEVEL_5_MASTERED(
        level = 5,
        titleVi = "Cấp 5 – Thành thạo",
        intervalDescriptionVi = "Từ 21 ngày trở lên"
    );

    companion object {
        /**
         * Tính cấp độ ghi nhớ dựa trên trạng thái vòng đời và khoảng cách ngày ôn.
         */
        fun fromCard(state: CardState, intervalDays: Int): RetentionStage {
            return when (state) {
                CardState.NEW -> LEVEL_0_UNLEARNED
                CardState.LEARNING -> LEVEL_1_STARTING
                CardState.REVIEW -> when {
                    intervalDays <= 3 -> LEVEL_2_SHORT_TERM
                    intervalDays <= 10 -> LEVEL_3_MID_TERM
                    intervalDays <= 20 -> LEVEL_4_NEAR_MASTERED
                    else -> LEVEL_5_MASTERED
                }
            }
        }
    }
}
