package com.uth.memora.core.domain.logic

import com.uth.memora.core.model.Card

/**
 * Bộ chia chặng hàng đợi ôn tập chống ngợp (Anti-burnout Queue Chunking - Mục 6.6, Mục 9.2 & Mục 11).
 *
 * Quy tắc:
 * - Nếu tổng số thẻ <= [chunkThreshold] (mặc định 30 thẻ): giữ nguyên 1 chặng duy nhất.
 * - Nếu tổng số thẻ > [chunkThreshold]: chia thành các chặng [chunkSize] thẻ (mặc định 15 thẻ).
 * - Nếu chặng cuối còn ít hơn [minTailSize] thẻ (mặc định < 5 thẻ): gộp luôn vào chặng kế trước
 *   để tránh tạo chặng lẻ quá ngắn (mỗi chặng duy trì trong khoảng 15–19 thẻ).
 */
object ReviewQueueChunker {

    const val DEFAULT_CHUNK_THRESHOLD = 30
    const val DEFAULT_CHUNK_SIZE = 15
    const val DEFAULT_MIN_TAIL_SIZE = 5

    fun splitIntoChunks(
        cards: List<Card>,
        chunkThreshold: Int = DEFAULT_CHUNK_THRESHOLD,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        minTailSize: Int = DEFAULT_MIN_TAIL_SIZE
    ): List<List<Card>> {
        require(chunkThreshold >= 0) { "chunkThreshold phải >= 0" }
        require(chunkSize > 0) { "chunkSize phải > 0" }
        require(minTailSize >= 0) { "minTailSize phải >= 0" }

        if (cards.isEmpty()) return emptyList()
        if (cards.size <= chunkThreshold) return listOf(cards)

        val chunks = cards.chunked(chunkSize).toMutableList()
        if (chunks.size >= 2 && chunks.last().size < minTailSize) {
            val tail = chunks.removeAt(chunks.lastIndex)
            chunks[chunks.lastIndex] = chunks.last() + tail
        }
        return chunks
    }
}
