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
 */
class SentenceGradingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

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

        var hasRetryableFailure = false

        for (sentence in targetSentences) {
            // Đã chạm trần số lần thử lại tối đa -> chuyển sang GRADE_ERROR (Mục 8.2)
            if (sentence.retryCount >= MAX_RETRY_ATTEMPTS) {
                practiceSentenceRepository.saveAiGradingResult(
                    sentenceId = sentence.id,
                    status = SentenceStatus.GRADE_ERROR,
                    errorMessage = sentence.errorMessage ?: ERROR_MAX_RETRY_VI,
                    retryCount = sentence.retryCount
                )
                continue
            }

            // Kiểm tra và trừ hạn mức 20 câu chấm AI mỗi ngày học (Mục 6.7 & 11)
            val quotaResult = userPreferencesRepository.consumeSentenceGradingQuota()
            if (quotaResult.isFailure) {
                val quotaMessage = quotaResult.exceptionOrNull()?.message
                    ?: ERROR_QUOTA_EXCEEDED_VI
                practiceSentenceRepository.saveAiGradingResult(
                    sentenceId = sentence.id,
                    status = SentenceStatus.GRADE_ERROR,
                    errorMessage = quotaMessage,
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
                    // Phân loại lỗi mạng / 429 / timeout / JSON sai định dạng bằng thông báo tiếng Việt (Mục 9.5)
                    val gradingError = GeminiAiGradingService.mapToGradingException(error)
                    val nextRetryCount = sentence.retryCount + 1
                    val canRetry = gradingError.isRetryable && nextRetryCount < MAX_RETRY_ATTEMPTS

                    if (canRetry) {
                        practiceSentenceRepository.saveAiGradingResult(
                            sentenceId = sentence.id,
                            status = SentenceStatus.PENDING_GRADE,
                            errorMessage = gradingError.message,
                            retryCount = nextRetryCount
                        )
                        hasRetryableFailure = true
                    } else {
                        practiceSentenceRepository.saveAiGradingResult(
                            sentenceId = sentence.id,
                            status = SentenceStatus.GRADE_ERROR,
                            errorMessage = gradingError.message,
                            retryCount = nextRetryCount
                        )
                    }
                }
            )
        }

        return if (hasRetryableFailure) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "memora_offline_sentence_grading_work"
        const val KEY_SENTENCE_ID = GradingScheduler.KEY_SENTENCE_ID
        const val MAX_RETRY_ATTEMPTS = PracticeSentence.MAX_RETRY_ATTEMPTS

        private const val ERROR_QUOTA_EXCEEDED_VI =
            "Bạn đã dùng hết 20 lượt chấm câu AI hôm nay (tự hồi lúc 4:00 sáng)."
        private const val ERROR_MAX_RETRY_VI =
            "Không thể chấm câu sau $MAX_RETRY_ATTEMPTS lần thử. Vui lòng bấm Chấm lại khi có mạng ổn định."

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
