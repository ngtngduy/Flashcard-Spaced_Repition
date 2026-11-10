package com.uth.memora.core.sm2

import com.uth.memora.core.domain.SentenceLocalValidator
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.model.ReviewQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2EngineTest {

    @Test
    fun `test ease factor updates for 4 buttons match specification 7_2`() {
        val initialEf = 2.5
        assertEquals(1.96, Sm2Engine.calculateNewEaseFactor(initialEf, ReviewQuality.AGAIN), 0.001)
        assertEquals(2.36, Sm2Engine.calculateNewEaseFactor(initialEf, ReviewQuality.HARD), 0.001)
        assertEquals(2.50, Sm2Engine.calculateNewEaseFactor(initialEf, ReviewQuality.GOOD), 0.001)
        assertEquals(2.60, Sm2Engine.calculateNewEaseFactor(initialEf, ReviewQuality.EASY), 0.001)

        // Không thấp hơn 1.3
        assertEquals(1.30, Sm2Engine.calculateNewEaseFactor(1.40, ReviewQuality.AGAIN), 0.001)
    }

    @Test
    fun `test consecutive GOOD reviews follow 1 - 6 - 15 - 38 - 95 days progression`() {
        var card = Card(
            id = "c1",
            deckId = "d1",
            word = "resilient",
            meaning = "kiên cường"
        )
        var day = 100L

        // Lần 1: Thẻ mới bấm Nhớ tốt -> interval = 1
        card = Sm2Engine.evaluateReview(card, ReviewQuality.GOOD, day).updatedCard
        assertEquals(CardState.REVIEW, card.state)
        assertEquals(1, card.interval)
        assertEquals(RetentionStage.LEVEL_2_SHORT_TERM, card.retentionStage)

        // Lần 2: Ôn đúng hạn bấm Nhớ tốt -> interval = 6
        day = card.dueDay!!
        card = Sm2Engine.evaluateReview(card, ReviewQuality.GOOD, day).updatedCard
        assertEquals(6, card.interval)
        assertEquals(RetentionStage.LEVEL_3_MID_TERM, card.retentionStage)

        // Lần 3: Ôn đúng hạn bấm Nhớ tốt -> interval = round(6 * 2.5) = 15
        day = card.dueDay!!
        card = Sm2Engine.evaluateReview(card, ReviewQuality.GOOD, day).updatedCard
        assertEquals(15, card.interval)
        assertEquals(RetentionStage.LEVEL_4_NEAR_MASTERED, card.retentionStage)

        // Lần 4: Ôn đúng hạn bấm Nhớ tốt -> interval = round(15 * 2.5) = 38
        day = card.dueDay!!
        card = Sm2Engine.evaluateReview(card, ReviewQuality.GOOD, day).updatedCard
        assertEquals(38, card.interval)
        assertEquals(RetentionStage.LEVEL_5_MASTERED, card.retentionStage)

        // Lần 5: Ôn đúng hạn bấm Nhớ tốt -> interval = round(38 * 2.5) = 95
        day = card.dueDay!!
        card = Sm2Engine.evaluateReview(card, ReviewQuality.GOOD, day).updatedCard
        assertEquals(95, card.interval)
    }

    @Test
    fun `test sentence local validator rules`() {
        val valid = SentenceLocalValidator.validate(
            targetWord = "resilient",
            rawSentence = "Children are remarkably resilient in difficult times."
        )
        assertTrue(valid.isValid)

        val missingCapital = SentenceLocalValidator.validate(
            targetWord = "resilient",
            rawSentence = "children are remarkably resilient."
        )
        assertFalse(missingCapital.isValid)

        val missingTarget = SentenceLocalValidator.validate(
            targetWord = "resilient",
            rawSentence = "Children are very strong in difficult times."
        )
        assertFalse(missingTarget.isValid)
    }
}
