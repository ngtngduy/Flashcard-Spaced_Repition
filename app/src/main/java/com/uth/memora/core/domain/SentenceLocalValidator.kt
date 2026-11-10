package com.uth.memora.core.domain

import com.uth.memora.core.model.PracticeSentence

/**
 * Bộ kiểm tra cục bộ câu luyện tập trước khi đưa vào hàng đợi hoặc gửi AI (Mục 6.8 & Mục 11).
 *
 * Quy tắc:
 * 1. Câu không trống, có từ 3 từ trở lên, không quá 300 ký tự.
 * 2. Câu bắt đầu bằng chữ cái viết hoa và kết thúc bằng dấu kết câu (. ! ?).
 * 3. Câu có chứa từ mục tiêu, kể cả các dạng biến đổi thông thường (-s, -es, -ed, -ing, -ies, -ied).
 */
object SentenceLocalValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessageVi: String? = null
    )

    private val END_PUNCTUATIONS = setOf('.', '!', '?')

    fun validate(targetWord: String, rawSentence: String): ValidationResult {
        val sentence = rawSentence.trim()
        val cleanTarget = targetWord.trim()

        if (sentence.isEmpty()) {
            return ValidationResult(false, "Câu không được để trống.")
        }

        if (sentence.length > PracticeSentence.MAX_SENTENCE_LENGTH) {
            return ValidationResult(
                false,
                "Câu không được vượt quá ${PracticeSentence.MAX_SENTENCE_LENGTH} ký tự (hiện tại: ${sentence.length})."
            )
        }

        val words = sentence.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < PracticeSentence.MIN_WORDS) {
            return ValidationResult(
                false,
                "Câu phải có ít nhất ${PracticeSentence.MIN_WORDS} từ."
            )
        }

        val firstChar = sentence.first()
        if (!firstChar.isUpperCase()) {
            return ValidationResult(false, "Câu phải bắt đầu bằng chữ cái viết hoa.")
        }

        val lastChar = sentence.last()
        if (lastChar !in END_PUNCTUATIONS) {
            return ValidationResult(false, "Câu phải kết thúc bằng dấu chấm câu (., ! hoặc ?).")
        }

        if (!containsTargetOrInflection(sentence, cleanTarget)) {
            return ValidationResult(
                false,
                "Câu phải chứa từ mục tiêu \"$cleanTarget\" (hoặc dạng chia thì/số nhiều của từ đó)."
            )
        }

        return ValidationResult(isValid = true)
    }

    /**
     * Kiểm tra câu có chứa từ mục tiêu hoặc biến thể thông thường (-s, -es, -ed, -ing, bỏ e thêm ing/ed, đổi y thành ies/ied).
     */
    fun containsTargetOrInflection(sentence: String, targetWord: String): Boolean {
        val lowerSentence = sentence.lowercase()
        val lowerTarget = targetWord.lowercase()

        // Nếu là cụm từ nhiều chữ, kiểm tra trực tiếp hoặc biến thể từ đầu/cuối
        if (lowerTarget.contains(" ")) {
            if (lowerSentence.contains(lowerTarget)) return true
        }

        val candidateForms = buildInflectionCandidates(lowerTarget)
        val tokenizedWords = lowerSentence
            .split(Regex("[^a-zA-Z'-]+"))
            .filter { it.isNotBlank() }
            .toSet()

        return candidateForms.any { it in tokenizedWords } || lowerSentence.contains(lowerTarget)
    }

    private fun buildInflectionCandidates(base: String): Set<String> {
        val forms = mutableSetOf(
            base,
            "${base}s",
            "${base}es",
            "${base}d",
            "${base}ed",
            "${base}ing"
        )
        if (base.endsWith("e") && base.length > 2) {
            val stem = base.dropLast(1)
            forms.add("${stem}ing")
            forms.add("${stem}ed")
        }
        if (base.endsWith("y") && base.length > 2) {
            val stem = base.dropLast(1)
            forms.add("${stem}ies")
            forms.add("${stem}ied")
        }
        return forms
    }
}
