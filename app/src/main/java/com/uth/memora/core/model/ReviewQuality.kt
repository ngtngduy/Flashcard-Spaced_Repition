package com.uth.memora.core.model

/**
 * Quy ước 4 nút đánh giá ánh xạ sang chất lượng nhớ q của thuật toán SM-2 (Mục 7.2).
 *
 * Thuật toán SM-2 gốc dùng thang 0..5. Memora rút gọn còn 4 nút để giảm tải nhận thức:
 * - Chưa nhớ (q = 1): Gom nhóm Quên (q < 3), giảm EF -0.54
 * - Khó (q = 3): Nhớ chật vật, giảm nhẹ EF -0.14
 * - Nhớ tốt (q = 4): Nhớ tiêu chuẩn, giữ nguyên EF (0.00)
 * - Dễ (q = 5): Nhớ tức thì, tăng EF +0.10
 */
enum class ReviewQuality(
    val q: Int,
    val labelVi: String,
    val efDelta: Double
) {
    AGAIN(q = 1, labelVi = "Chưa nhớ", efDelta = -0.54),
    HARD(q = 3, labelVi = "Khó", efDelta = -0.14),
    GOOD(q = 4, labelVi = "Nhớ tốt", efDelta = 0.00),
    EASY(q = 5, labelVi = "Dễ", efDelta = +0.10);

    companion object {
        fun fromQ(q: Int): ReviewQuality = entries.firstOrNull { it.q == q } ?: GOOD
    }
}
