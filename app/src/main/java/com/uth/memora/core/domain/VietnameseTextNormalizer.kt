package com.uth.memora.core.domain

import java.text.Normalizer

/**
 * Bộ chuẩn hóa chuỗi phục vụ tìm kiếm từ vựng hoặc nghĩa tiếng Việt
 * không phân biệt chữ hoa/thường và dấu (Mục 6.9 & Mục 11).
 *
 * Ví dụ: "Kiên cường" -> "kien cuong"
 */
object VietnameseTextNormalizer {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")

    fun normalizeForSearch(input: String): String {
        if (input.isBlank()) return ""
        val nfd = Normalizer.normalize(input.trim().lowercase(), Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(nfd, "")
            .replace('đ', 'd')
            .replace(Regex("\\s+"), " ")
    }

    fun matchesQuery(targetText: String, query: String): Boolean {
        val normalizedQuery = normalizeForSearch(query)
        if (normalizedQuery.isEmpty()) return true
        val normalizedTarget = normalizeForSearch(targetText)
        return normalizedTarget.contains(normalizedQuery)
    }
}
