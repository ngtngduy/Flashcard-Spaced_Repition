package com.uth.memora.integration.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.uth.memora.MemoraApplication
import com.uth.memora.core.model.PracticeSentence
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.integration.ai.GeminiAiGradingService
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * ============================================================================
 * KHU VỰC CỦA THÀNH VIÊN 3: HÀNG ĐỢI CHẤM CÂU NGOẠI TUYẾN (Mục 6.8, 8.2 & 9.5)
 * ============================================================================
 * Tác vụ nền WorkManager tự động chạy khi thiết bị có mạng trở lại:
 * - Đọc `sentence_id` từ `inputData`; nếu không truyền thì xử lý toàn bộ câu đang `PENDING_GRADE`.
 * - Kiểm tra hạn mức 20 câu/ngày qua `UserPreferencesRepository.consumeSentenceGradingQuota()`.
 * - Chấm câu bằng `AiGradingService` và lưu kết quả qua `PracticeSentenceRepository` (không import `core/database`).
 * - Tự lập lịch thử lại qua `GradingScheduler` theo mốc lùi 15s → 60s → 5 phút (tối đa 3 lần thử).
 */
class SentenceGradingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val gradingScheduler: GradingScheduler by lazy {
        GradingScheduler(applicationContext)
    }

    override suspend fun doWork(): Result {
        val container = (applicationContext as? MemoraApplication)?.container
            ?: return Result.failure()

        val practiceSentenceRepository = container.practiceSentenceRepository
        val cardRepository = container.cardRepository
        val userPreferencesRepository = container.userPreferencesRepository
        val aiGradingService = container.aiGradingService

        val requestedSentenceId = inputData
            .getString(KEY_SENTENCE_ID)
            ?.takeIf { it.isNotBlank() }

        // Lấy danh sách câu đang chờ chấm từ repository (Mục 8.2)
        val pendingSentences = try {
            practiceSentenceRepository.getPendingSentences()
                .filter { it.status == SentenceStatus.PENDING_GRADE }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
        }

        val targetSentences = if (requestedSentenceId != null) {
            pendingSentences.filter { it.id == requestedSentenceId }
        } else {
            pendingSentences
        }

        if (targetSentences.isEmpty()) {
            return Result.success()
        }

        var hasUnrecoverableFailure = false

        for (sentence in targetSentences) {
            // Chặn sớm nếu dữ liệu retryCount đã vượt ngưỡng thử lại tối đa (Mục 8.2)
            if (sentence.retryCount > MAX_RETRY_ATTEMPTS) {
                practiceSentenceRepository.saveAiGradingResult(
                    sentenceId = sentence.id,
                    status = SentenceStatus.GRADE_ERROR,
                    errorMessage = ERROR_GRADE_FAILED_VI,
                    retryCount = MAX_RETRY_ATTEMPTS
                )
                hasUnrecoverableFailure = true
                continue
            }

            // Kiểm tra và trừ hạn mức 20 câu chấm AI mỗi ngày học (Mục 6.7, 9.5 & 11).
            // Nếu đã hết lượt hôm nay -> giữ nguyên PENDING_GRADE kèm thông báo cho ngày học sau (tự hồi lúc 4:00 sáng).
            val quotaResult = userPreferencesRepository.consumeSentenceGradingQuota()
            if (quotaResult.isFailure) {
                practiceSentenceRepository.saveAiGradingResult(
                    sentenceId = sentence.id,
                    status = SentenceStatus.PENDING_GRADE,
                    errorMessage = ERROR_QUOTA_EXCEEDED_VI,
                    retryCount = sentence.retryCount
                )
                continue
            }

            // Lấy nghĩa tiếng Việt từ thẻ tương ứng để cung cấp ngữ cảnh cho AI (Mục 6.7)
            val card = cardRepository.getCardById(sentence.cardId)
            val targetWord = card?.word?.takeIf { it.isNotBlank() } ?: sentence.targetWord
            val meaning = card?.meaning.orEmpty()

            val evaluationResult = try {
                aiGradingService.evaluateSentence(
                    targetWord = targetWord,
                    meaningVi = meaning,
                    userSentence = sentence.sentence
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                kotlin.Result.failure(e)
            }

            evaluationResult.fold(
                onSuccess = { evaluation ->
                    // Lưu kết quả chấm điểm AI và chuyển câu sang trạng thái GRADED (Mục 6.7 & 8.2)
                    practiceSentenceRepository.saveAiGradingResult(
                        sentenceId = sentence.id,
                        status = SentenceStatus.GRADED,
                        score = evaluation.score,
                        cefrLevel = evaluation.cefrLevel,
                        feedbackVi = evaluation.feedbackVi,
                        grammarAnalysis = evaluation.grammarAnalysis,
                        correctedSentence = evaluation.correctedSentence,
                        naturalAlternative = evaluation.naturalAlternative,
                        errorMessage = null,
                        retryCount = sentence.retryCount
                    )
                },
                onFailure = { error ->
                    // Phân loại lỗi mạng / 429 / timeout / JSON sai định dạng / thiếu API key (Mục 9.5)
                    val gradingError = GeminiAiGradingService.mapToGradingException(error)
                    val canRetry = gradingError.isRetryable && sentence.retryCount < MAX_RETRY_ATTEMPTS

                    if (canRetry) {
                        // Lưu lại PENDING_GRADE với retryCount tăng thêm 1 rồi tự hẹn chạy lại theo lịch lùi (Mục 8.2)
                        val delaySeconds = retryDelaySecondsForAttempt(sentence.retryCount)
                        val nextRetryCount = sentence.retryCount + 1
                        practiceSentenceRepository.saveAiGradingResult(
                            sentenceId = sentence.id,
                            status = SentenceStatus.PENDING_GRADE,
                            errorMessage = gradingError.message,
                            retryCount = nextRetryCount
                        )
                        gradingScheduler.enqueueSentence(
                            sentenceId = sentence.id,
                            initialDelaySeconds = delaySeconds,
                            replaceExisting = true
                        )
                    } else {
                        // Đã chạm retryCount >= 3 hoặc lỗi không thể phục hồi (thiếu/sai API key) -> GRADE_ERROR (Mục 8.2 & 9.5)
                        practiceSentenceRepository.saveAiGradingResult(
                            sentenceId = sentence.id,
                            status = SentenceStatus.GRADE_ERROR,
                            errorMessage = ERROR_GRADE_FAILED_VI,
                            retryCount = sentence.retryCount
                        )
                        hasUnrecoverableFailure = true
                    }
                }
            )
        }

        return if (hasUnrecoverableFailure) Result.failure() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "memora_offline_sentence_grading_work"
        const val KEY_SENTENCE_ID = GradingScheduler.KEY_SENTENCE_ID
        const val MAX_RETRY_ATTEMPTS = PracticeSentence.MAX_RETRY_ATTEMPTS

        const val RETRY_DELAY_ATTEMPT_0_SECONDS = 15L
        const val RETRY_DELAY_ATTEMPT_1_SECONDS = 60L
        const val RETRY_DELAY_ATTEMPT_2_SECONDS = 300L

        const val ERROR_GRADE_FAILED_VI =
            "Chưa thể chấm câu lúc này, bạn bấm Chấm lại nhé."
        const val ERROR_QUOTA_EXCEEDED_VI =
            "Bạn đã dùng hết 20 lượt chấm câu AI hôm nay. Câu được giữ Chờ chấm cho ngày học sau (tự hồi lúc 4:00 sáng)."

        /**
         * Tính thời gian chờ (giây) theo lịch lùi 15s → 60s → 5 phút trước lần thử lại kế tiếp (Mục 8.2 & 9.5).
         * - attempt 0 -> 15 giây
         * - attempt 1 -> 60 giây
         * - attempt 2 -> 300 giây (5 phút)
         */
        fun retryDelaySecondsForAttempt(retryCount: Int): Long = when (retryCount) {
            0 -> RETRY_DELAY_ATTEMPT_0_SECONDS
            1 -> RETRY_DELAY_ATTEMPT_1_SECONDS
            2 -> RETRY_DELAY_ATTEMPT_2_SECONDS
            else -> if (retryCount < 0) {
                RETRY_DELAY_ATTEMPT_0_SECONDS
            } else {
                RETRY_DELAY_ATTEMPT_2_SECONDS
            }
        }

        /**
         * Đăng ký hàng đợi chờ mạng để tự động chấm toàn bộ các câu đang ở trạng thái Chờ chấm (Mục 8.2).
         */
        fun enqueueWhenNetworkAvailable(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SentenceGradingWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15L,
                    TimeUnit.SECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
