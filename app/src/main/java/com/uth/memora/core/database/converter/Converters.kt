package com.uth.memora.core.database.converter

import androidx.room.TypeConverter
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.ReviewQuality
import com.uth.memora.core.model.SentenceStatus
import com.uth.memora.core.model.SyncStatus

/**
 * Bộ chuyển đổi kiểu dữ liệu cho Room Database (Mục 5 & Mục 10.1).
 *
 * Chuyển đổi an toàn toàn bộ enum nghiệp vụ và danh sách chuỗi [List<String>]
 * (ví dụ grammarAnalysis từ AI Gemini) sang dạng chuỗi JSON thuần Kotlin,
 * không phụ thuộc thư viện ngoài và hoạt động ổn định cả trên JVM Unit Test lẫn Android.
 */
class Converters {

    @TypeConverter
    fun fromCardState(value: CardState): String = value.name

    @TypeConverter
    fun toCardState(value: String?): CardState {
        if (value.isNullOrBlank()) return CardState.NEW
        return CardState.entries.firstOrNull { it.name == value } ?: CardState.NEW
    }

    @TypeConverter
    fun fromReviewQuality(value: ReviewQuality): String = value.name

    @TypeConverter
    fun toReviewQuality(value: String?): ReviewQuality {
        if (value.isNullOrBlank()) return ReviewQuality.GOOD
        return ReviewQuality.entries.firstOrNull { it.name == value } ?: ReviewQuality.GOOD
    }

    @TypeConverter
    fun fromPartOfSpeech(value: PartOfSpeech?): String? = value?.name

    @TypeConverter
    fun toPartOfSpeech(value: String?): PartOfSpeech? {
        if (value.isNullOrBlank()) return null
        return PartOfSpeech.entries.firstOrNull { it.name == value }
    }

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus {
        if (value.isNullOrBlank()) return SyncStatus.PENDING_SYNC
        return SyncStatus.entries.firstOrNull { it.name == value } ?: SyncStatus.PENDING_SYNC
    }

    @TypeConverter
    fun fromSentenceStatus(value: SentenceStatus): String = value.name

    @TypeConverter
    fun toSentenceStatus(value: String?): SentenceStatus {
        if (value.isNullOrBlank()) return SentenceStatus.PENDING_GRADE
        return SentenceStatus.entries.firstOrNull { it.name == value } ?: SentenceStatus.PENDING_GRADE
    }

    /**
     * Mã hóa danh sách chuỗi thành mảng JSON chuẩn RFC 8259 để bảo toàn dấu phẩy,
     * dấu ngoặc kép, ký tự xuống dòng và tiếng Việt có dấu (Mục 6.7).
     */
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        if (value.isNullOrEmpty()) return "[]"
        val sb = StringBuilder()
        sb.append('[')
        value.forEachIndexed { index, item ->
            if (index > 0) sb.append(',')
            appendJsonString(sb, item)
        }
        sb.append(']')
        return sb.toString()
    }

    /**
     * Giải mã mảng JSON chuỗi về [List<String>]. Trả về danh sách rỗng nếu dữ liệu null/hỏng.
     */
    @TypeConverter
    fun toStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val text = raw.trim()
        if (text.length < 2 || text.first() != '[' || text.last() != ']') {
            return emptyList()
        }

        val result = mutableListOf<String>()
        var i = 1
        val end = text.length - 1

        fun skipWhitespace() {
            while (i < end && text[i].isWhitespace()) i++
        }

        skipWhitespace()
        if (i == end) return emptyList()

        while (i < end) {
            skipWhitespace()
            if (i >= end || text[i] != '"') return emptyList()
            i++ // bỏ dấu '"' mở

            val itemBuilder = StringBuilder()
            var closed = false
            while (i < end) {
                val ch = text[i++]
                when (ch) {
                    '"' -> {
                        closed = true
                        break
                    }
                    '\\' -> {
                        if (i >= end) return emptyList()
                        when (val escaped = text[i++]) {
                            '"' -> itemBuilder.append('"')
                            '\\' -> itemBuilder.append('\\')
                            '/' -> itemBuilder.append('/')
                            'b' -> itemBuilder.append('\b')
                            'f' -> itemBuilder.append('\u000C')
                            'n' -> itemBuilder.append('\n')
                            'r' -> itemBuilder.append('\r')
                            't' -> itemBuilder.append('\t')
                            'u' -> {
                                if (i + 4 > end) return emptyList()
                                val hex = text.substring(i, i + 4)
                                val codePoint = hex.toIntOrNull(16) ?: return emptyList()
                                itemBuilder.append(codePoint.toChar())
                                i += 4
                            }
                            else -> return emptyList()
                        }
                    }
                    else -> itemBuilder.append(ch)
                }
            }
            if (!closed) return emptyList()
            result.add(itemBuilder.toString())

            skipWhitespace()
            if (i == end) break
            if (text[i] != ',') return emptyList()
            i++ // bỏ dấu ','
            skipWhitespace()
            // Không cho phép dấu phẩy thừa ở cuối mảng JSON
            if (i == end) return emptyList()
        }

        return result
    }

    private fun appendJsonString(sb: StringBuilder, text: String) {
        sb.append('"')
        for (ch in text) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u")
                        sb.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }
}
