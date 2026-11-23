package com.uth.memora.core.domain.logic

import com.uth.memora.core.model.Card
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử logic chia chặng hàng đợi chống ngợp ReviewQueueChunker (M1-D2 & Mục 6.6, 11).
 */
class ReviewQueueChunkerTest {

    private fun createCards(count: Int): List<Card> = (1..count).map { index ->
        Card(
            id = "card-$index",
            deckId = "deck-1",
            word = "word$index",
            meaning = "nghĩa $index",
            createdAtMillis = 1_700_000_000_000L + index,
            updatedAtMillis = 1_700_000_000_000L + index
        )
    }

    @Test
    fun emptyQueue_returnsEmptyList() {
        assertTrue(ReviewQueueChunker.splitIntoChunks(emptyList()).isEmpty())
    }

    @Test
    fun upToThreshold30_keepsSingleChunkWithoutSplitting() {
        val singleCard = createCards(1)
        val chunks1 = ReviewQueueChunker.splitIntoChunks(singleCard)
        assertEquals(listOf(1), chunks1.map { it.size })
        assertEquals(singleCard, chunks1.flatten())

        val thirtyCards = createCards(30)
        val chunks30 = ReviewQueueChunker.splitIntoChunks(thirtyCards)
        assertEquals(listOf(30), chunks30.map { it.size })
        assertEquals(thirtyCards, chunks30.flatten())
    }

    @Test
    fun thirtyOneCards_mergesSmallTailIntoSecondChunk() {
        val cards = createCards(31)
        val chunks = ReviewQueueChunker.splitIntoChunks(cards)
        assertEquals(listOf(15, 16), chunks.map { it.size })
        assertEquals(cards, chunks.flatten())
    }

    @Test
    fun thirtyFourAndThirtyFiveCards_respectMinTailBoundaryOf5() {
        val cards34 = createCards(34)
        val chunks34 = ReviewQueueChunker.splitIntoChunks(cards34)
        assertEquals(listOf(15, 19), chunks34.map { it.size })
        assertEquals(cards34, chunks34.flatten())

        val cards35 = createCards(35)
        val chunks35 = ReviewQueueChunker.splitIntoChunks(cards35)
        assertEquals(listOf(15, 15, 5), chunks35.map { it.size })
        assertEquals(cards35, chunks35.flatten())
    }

    @Test
    fun fortyFiveCards_splitsEvenlyIntoThreeChunksOf15() {
        val cards = createCards(45)
        val chunks = ReviewQueueChunker.splitIntoChunks(cards)
        assertEquals(listOf(15, 15, 15), chunks.map { it.size })
        assertEquals(cards, chunks.flatten())
    }

    @Test
    fun fortySevenCards_mergesRemainder2IntoThirdChunk() {
        val cards = createCards(47)
        val chunks = ReviewQueueChunker.splitIntoChunks(cards)
        assertEquals(listOf(15, 15, 17), chunks.map { it.size })
        assertEquals(cards, chunks.flatten())
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveChunkSize_throwsIllegalArgumentException() {
        ReviewQueueChunker.splitIntoChunks(createCards(10), chunkSize = 0)
    }
}
