package com.uth.memora.core.data

import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.ReviewLogDao
import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.database.entity.ReviewLogEntity
import com.uth.memora.core.database.mapper.toDomain
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.StatisticsRepository
import com.uth.memora.core.domain.UserPreferencesRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.RetentionStage
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.StudyStatistics
import com.uth.memora.core.sm2.LearningDayCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Hiện thực [StatisticsRepository] trên Room Database & DataStore (Mục 6.9 & Mục 10.2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomStatisticsRepository(
    private val reviewLogDao: ReviewLogDao,
    private val cardDao: CardDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository? = null,
    private val ownerIdProvider: suspend () -> String = {
        authRepository?.getCurrentUser()?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID
    },
    private val clockMillisProvider: () -> Long = { System.currentTimeMillis() },
    private val learningDayProvider: (Long) -> Long = { millis ->
        LearningDayCalculator.toLearningDay(millis)
    }
) : StatisticsRepository {

    override fun observeStatistics(): Flow<StudyStatistics> {
        val ownerFlow: Flow<String> = authRepository
            ?.observeCurrentUser()
            ?.map { user -> user?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID }
            ?.distinctUntilChanged()
            ?: flow { emit(ownerIdProvider()) }

        return ownerFlow.flatMapLatest { ownerId ->
            combine(
                cardDao.observeAllActiveCards(ownerId),
                reviewLogDao.observeAllValidLogs(ownerId),
                preferencesRepository.observeUserPreferences()
            ) { cardEntities, logEntities, _ ->
                val nowMillis = clockMillisProvider()
                val todayLearningDay = learningDayProvider(nowMillis)
                buildStatistics(
                    cardEntities = cardEntities,
                    logEntities = logEntities,
                    todayLearningDay = todayLearningDay,
                    nowMillis = nowMillis
                )
            }
        }
    }

    private fun buildStatistics(
        cardEntities: List<CardEntity>,
        logEntities: List<ReviewLogEntity>,
        todayLearningDay: Long,
        nowMillis: Long
    ): StudyStatistics {
        val activeCards = cardEntities.filter { !it.isDeleted }.map { it.toDomain() }
        val validLogs = logEntities.filter { !it.isUndone }

        val (reviewsLast7DaysCount, retentionRatePercent) = computeSevenDayRetention(
            validLogs = validLogs,
            todayLearningDay = todayLearningDay
        )
        val stageDistribution = computeStageDistribution(activeCards)
        val masteredCardsCount = stageDistribution[RetentionStage.LEVEL_5_MASTERED] ?: 0
        val totalStudyTimeSeconds = computeTotalStudyTimeSeconds(validLogs)

        val newCardsLearnedToday = activeCards.count { it.firstSeenDay == todayLearningDay }
        val reviewsToday = validLogs.count { it.learningDay == todayLearningDay }
        val dueCardsCount = activeCards.count { it.isDue(todayLearningDay, nowMillis) }

        return StudyStatistics(
            newCardsLearnedToday = newCardsLearnedToday,
            reviewsToday = reviewsToday,
            dueCardsCount = dueCardsCount,
            totalStudyTimeSeconds = totalStudyTimeSeconds,
            masteredCardsCount = masteredCardsCount,
            totalActiveCardsCount = activeCards.size,
            stageDistribution = stageDistribution,
            retentionRatePercent = retentionRatePercent,
            reviewsLast7DaysCount = reviewsLast7DaysCount
        )
    }

    /**
     * Phân bố số lượng thẻ theo đủ 6 cấp độ [RetentionStage] (Mục 6.9 & Mục 10.2).
     * Bảo đảm luôn trả đủ 6 khóa kể cả khi một cấp có 0 thẻ.
     */
    internal fun computeStageDistribution(activeCards: List<Card>): Map<RetentionStage, Int> {
        val countsByStage = activeCards.groupingBy { it.retentionStage }.eachCount()
        return RetentionStage.entries.associateWith { stage ->
            countsByStage[stage] ?: 0
        }
    }

    /**
     * Tổng thời gian học hợp lệ từ các lượt đánh giá chưa bị hoàn tác (Mục 10.2).
     */
    internal fun computeTotalStudyTimeSeconds(validLogs: List<ReviewLogEntity>): Long {
        return validLogs
            .filter { !it.isUndone }
            .sumOf { it.durationValidSeconds.coerceIn(1, 60).toLong() }
    }

    /**
     * Tính tổng số lượt đánh giá hợp lệ trong 7 ngày học gần nhất (`learningDay >= todayLearningDay - 6`)
     * và tỷ lệ ghi nhớ % (`quality != ReviewQuality.AGAIN`), trả về `0f` nếu chưa có lượt nào (Mục 10.2 & Figma 2212:5).
     */
    internal fun computeSevenDayRetention(
        validLogs: List<ReviewLogEntity>,
        todayLearningDay: Long
    ): Pair<Int, Float> {
        val fromLearningDay = todayLearningDay - 6L
        val recentLogs = validLogs.filter { !it.isUndone && it.learningDay >= fromLearningDay }
        if (recentLogs.isEmpty()) {
            return 0 to 0f
        }
        val passedCount = recentLogs.count { it.quality != ReviewQuality.AGAIN }
        val retentionPercent = (passedCount * 100f) / recentLogs.size.toFloat()
        return recentLogs.size to retentionPercent
    }
}
