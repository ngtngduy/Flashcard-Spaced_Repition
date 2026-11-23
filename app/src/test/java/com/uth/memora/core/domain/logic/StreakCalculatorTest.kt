package com.uth.memora.core.domain.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm thử toàn bộ tình huống chuỗi ngày học (Streak) & Khiên bảo vệ 30 ngày (M1-D5 & Mục 10.2, 11).
 */
class StreakCalculatorTest {

    @Test
    fun firstStudyEver_startsStreakAtOneWithoutConsumingFreeze() {
        val result = StreakCalculator.calculateNextStreak(
            currentStreak = 0,
            lastStudyDay = null,
            todayLearningDay = 20_400L,
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )

        assertEquals(1, result.newStreak)
        assertFalse(result.isFreezeUsed)
        assertTrue(result.isFreezeAvailable)
        assertEquals(20_400L, result.lastStudyLearningDay)
        assertNull(result.lastFreezeUsedDay)
        assertEquals(0, result.nextFreezeAvailableInDays)
    }

    @Test
    fun consecutiveDay_incrementsStreakByOne() {
        val result = StreakCalculator.calculateNextStreak(
            currentStreak = 7,
            lastStudyDay = 20_400L,
            todayLearningDay = 20_401L,
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )

        assertEquals(8, result.newStreak)
        assertFalse(result.isFreezeUsed)
        assertTrue(result.isFreezeAvailable)
        assertEquals(20_401L, result.lastStudyLearningDay)
        assertEquals(0, result.nextFreezeAvailableInDays)
    }

    @Test
    fun multipleReviewsSameLearningDay_keepsStreakUnchanged() {
        val result = StreakCalculator.calculateNextStreak(
            currentStreak = 5,
            lastStudyDay = 20_400L,
            todayLearningDay = 20_400L,
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )

        assertEquals(5, result.newStreak)
        assertFalse(result.isFreezeUsed)
        assertTrue(result.isFreezeAvailable)
        assertEquals(20_400L, result.lastStudyLearningDay)
    }

    @Test
    fun missedOneDayWithFreezeAvailable_protectsStreakAndConsumesFreezeOnMissedDay() {
        val result = StreakCalculator.calculateNextStreak(
            currentStreak = 12,
            lastStudyDay = 20_400L,
            todayLearningDay = 20_402L, // Lỡ đúng ngày 20_401L
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )

        assertEquals(13, result.newStreak)
        assertTrue(result.isFreezeUsed)
        assertFalse(result.isFreezeAvailable)
        assertEquals(20_402L, result.lastStudyLearningDay)
        assertEquals(20_401L, result.lastFreezeUsedDay)
        assertEquals(29, result.nextFreezeAvailableInDays)
    }

    @Test
    fun missedOneDayBefore30DayRecovery_resetsStreakToOne() {
        val result = StreakCalculator.calculateNextStreak(
            currentStreak = 15,
            lastStudyDay = 20_427L,
            todayLearningDay = 20_429L, // Mới qua 29 ngày kể từ lần dùng khiên 20_400L (< 30)
            isFreezeAvailable = false,
            lastFreezeUsedDay = 20_400L
        )

        assertEquals(1, result.newStreak)
        assertFalse(result.isFreezeUsed)
        assertFalse(result.isFreezeAvailable)
        assertEquals(20_400L, result.lastFreezeUsedDay)
        assertEquals(1, result.nextFreezeAvailableInDays)
    }

    @Test
    fun missedOneDayAtExact30DayRecovery_recoversFreezeAndProtectsStreak() {
        val result = StreakCalculator.calculateNextStreak(
            currentStreak = 15,
            lastStudyDay = 20_428L,
            todayLearningDay = 20_430L, // Đúng 30 ngày kể từ lần dùng khiên 20_400L (>= 30)
            isFreezeAvailable = false,
            lastFreezeUsedDay = 20_400L
        )

        assertEquals(16, result.newStreak)
        assertTrue(result.isFreezeUsed)
        assertFalse(result.isFreezeAvailable)
        assertEquals(20_429L, result.lastFreezeUsedDay)
        assertEquals(29, result.nextFreezeAvailableInDays)
    }

    @Test
    fun missedTwoOrMoreDays_resetsStreakToOneWithoutWastingAvailableFreeze() {
        val result = StreakCalculator.calculateNextStreak(
            currentStreak = 20,
            lastStudyDay = 20_400L,
            todayLearningDay = 20_403L, // Lỡ 2 ngày liên tiếp (20_401 và 20_402)
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )

        assertEquals(1, result.newStreak)
        assertFalse(result.isFreezeUsed)
        assertTrue(result.isFreezeAvailable)
        assertNull(result.lastFreezeUsedDay)
        assertEquals(0, result.nextFreezeAvailableInDays)
    }

    @Test
    fun evaluateDisplayStreak_handlesAliveProtectedAndExpiredStates() {
        val alive = StreakCalculator.evaluateDisplayStreak(
            currentStreak = 9,
            lastStudyDay = 20_400L,
            todayLearningDay = 20_401L,
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )
        assertEquals(9, alive.displayedStreak)
        assertFalse(alive.isProtectedToday)
        assertTrue(alive.isFreezeAvailable)

        val protected = StreakCalculator.evaluateDisplayStreak(
            currentStreak = 9,
            lastStudyDay = 20_400L,
            todayLearningDay = 20_402L,
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )
        assertEquals(9, protected.displayedStreak)
        assertTrue(protected.isProtectedToday)
        assertFalse(protected.isFreezeAvailable)

        val expired = StreakCalculator.evaluateDisplayStreak(
            currentStreak = 9,
            lastStudyDay = 20_400L,
            todayLearningDay = 20_403L,
            isFreezeAvailable = true,
            lastFreezeUsedDay = null
        )
        assertEquals(0, expired.displayedStreak)
        assertFalse(expired.isProtectedToday)
        assertTrue(expired.isFreezeAvailable)
    }
}
