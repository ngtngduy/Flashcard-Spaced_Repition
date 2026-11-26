package com.uth.memora.core.data

import android.content.Context
import androidx.room.withTransaction
import com.uth.memora.core.database.MemoraDatabase
import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.database.entity.DeckEntity
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.PartOfSpeech
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Mô hình một thẻ từ vựng mẫu đọc từ `assets/prebuilt_decks.json`.
 */
data class PrebuiltCardSeed(
    val word: String,
    val phonetic: String,
    val partOfSpeech: PartOfSpeech?,
    val meaning: String,
    val example: String
)

/**
 * Mô hình một bộ từ có sẵn đọc từ `assets/prebuilt_decks.json`.
 */
data class PrebuiltDeckSeed(
    val id: String,
    val name: String,
    val description: String,
    val cards: List<PrebuiltCardSeed>
)

/**
 * Bộ khởi tạo dữ liệu mẫu ngoại tuyến cho kho "Bộ từ có sẵn" từ `assets/prebuilt_decks.json` (Mục 6.2 & Mục 6.4).
 *
 * Hoạt động idempotent: nếu các bộ mẫu (`prebuilt_oxford_3000`, `prebuilt_ielts_core`) và thẻ của chúng
 * đã tồn tại trong Room thì bỏ qua; nếu thiếu sẽ chèn bổ sung an toàn trong transaction.
 */
class PrebuiltDeckSeeder(
    private val context: Context? = null,
    private val database: MemoraDatabase? = null,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val assetFileName: String = DEFAULT_ASSET_FILE_NAME,
    private val jsonProvider: (() -> String)? = null,
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
        val db = database
        if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }
) {

    private val seedMutex = Mutex()

    /**
     * Đọc `assets/prebuilt_decks.json` và chèn idempotent các bộ từ mẫu vào Room Database.
     *
     * @return Số lượng bộ từ mẫu hợp lệ đã được đảm bảo có mặt trong cơ sở dữ liệu.
     */
    suspend fun seedIfNeeded(): Int = seedMutex.withLock {
        val rawJson = loadRawJson()
        if (rawJson.isBlank()) return@withLock 0

        val seeds = parsePrebuiltDecksJson(rawJson)
        if (seeds.isEmpty()) return@withLock 0

        val baseTimestamp = SEED_BASE_TIMESTAMP_MILLIS
        transactionRunner {
            seeds.forEachIndexed { deckIndex, deckSeed ->
                val existingDeck = deckDao.getDeckById(deckSeed.id)
                val existingCount = cardDao.countActiveCardsInDeck(deckSeed.id)

                if (existingDeck == null || existingDeck.isDeleted) {
                    val deckTimestamp = baseTimestamp + (deckIndex * 1_000L)
                    deckDao.upsertDeck(
                        DeckEntity(
                            id = deckSeed.id,
                            ownerId = DeckDao.PREBUILT_OWNER_ID,
                            name = deckSeed.name,
                            description = deckSeed.description,
                            sourcePrebuiltId = deckSeed.id,
                            createdAtMillis = deckTimestamp,
                            updatedAtMillis = deckTimestamp,
                            isDeleted = false,
                            syncStatus = SyncStatus.SYNCED
                        )
                    )
                }

                if (existingCount < deckSeed.cards.size) {
                    val cardEntities = deckSeed.cards.mapIndexed { cardIndex, cardSeed ->
                        val cardTimestamp = baseTimestamp + (deckIndex * 1_000L) + cardIndex + 1L
                        CardEntity(
                            id = "${deckSeed.id}_card_${cardIndex + 1}",
                            deckId = deckSeed.id,
                            ownerId = DeckDao.PREBUILT_OWNER_ID,
                            word = cardSeed.word,
                            phonetic = cardSeed.phonetic,
                            partOfSpeech = cardSeed.partOfSpeech,
                            meaning = cardSeed.meaning,
                            example = cardSeed.example,
                            localImagePath = null,
                            state = CardState.NEW,
                            easeFactor = Card.INITIAL_EASE_FACTOR,
                            repetitions = 0,
                            interval = 0,
                            lapses = 0,
                            dueDay = null,
                            dueAtMillis = null,
                            firstSeenDay = null,
                            lastReviewedAtMillis = null,
                            createdAtMillis = cardTimestamp,
                            updatedAtMillis = cardTimestamp,
                            isDeleted = false,
                            syncStatus = SyncStatus.SYNCED
                        )
                    }
                    cardDao.upsertCards(cardEntities)
                }
            }
        }
        seeds.size
    }

    private fun loadRawJson(): String {
        jsonProvider?.let { provider ->
            return provider()
        }
        context?.let { ctx ->
            return runCatching {
                ctx.assets.open(assetFileName).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.getOrDefault("")
        }
        val candidateFiles = listOf(
            File("src/main/assets/$assetFileName"),
            File("app/src/main/assets/$assetFileName")
        )
        val existingFile = candidateFiles.firstOrNull { it.exists() && it.isFile }
        return existingFile?.readText(Charsets.UTF_8).orEmpty()
    }

    companion object {
        const val DEFAULT_ASSET_FILE_NAME = "prebuilt_decks.json"
        private const val SEED_BASE_TIMESTAMP_MILLIS = 1_700_000_000_000L

        /**
         * Phân tích chuỗi JSON của `prebuilt_decks.json` sang danh sách [PrebuiltDeckSeed]
         * bằng bộ đọc JSON thuần Kotlin (chạy đồng nhất trên Android Runtime và JVM Unit Test).
         */
        fun parsePrebuiltDecksJson(rawJson: String): List<PrebuiltDeckSeed> {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) return emptyList()
            val root = SimpleJsonReader(trimmed).readValue() as? List<*> ?: return emptyList()

            return root.mapNotNull { item ->
                val obj = item as? Map<*, *> ?: return@mapNotNull null
                val id = (obj["id"] as? String)?.trim().orEmpty()
                val name = (obj["name"] as? String)?.trim().orEmpty()
                val description = (obj["description"] as? String)?.trim().orEmpty()
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null

                val rawCards = obj["cards"] as? List<*> ?: emptyList<Any?>()
                val cards = rawCards.mapNotNull { cardItem ->
                    val cardObj = cardItem as? Map<*, *> ?: return@mapNotNull null
                    val word = (cardObj["word"] as? String)?.trim().orEmpty()
                    val meaning = (cardObj["meaning"] as? String)?.trim().orEmpty()
                    if (word.isEmpty() || meaning.isEmpty()) return@mapNotNull null

                    val phonetic = (cardObj["phonetic"] as? String)?.trim().orEmpty()
                    val example = (cardObj["example"] as? String)?.trim().orEmpty()
                    val posRaw = (cardObj["partOfSpeech"] as? String)?.trim()?.uppercase()
                    val partOfSpeech = PartOfSpeech.entries.firstOrNull { it.name == posRaw }

                    PrebuiltCardSeed(
                        word = word,
                        phonetic = phonetic,
                        partOfSpeech = partOfSpeech,
                        meaning = meaning,
                        example = example
                    )
                }

                PrebuiltDeckSeed(
                    id = id,
                    name = name,
                    description = description,
                    cards = cards
                )
            }
        }
    }
}

/**
 * Bộ đọc JSON thuần Kotlin nhẹ cho file cấu hình `prebuilt_decks.json`.
 */
private class SimpleJsonReader(private val input: String) {
    private var pos: Int = 0

    fun readValue(): Any? {
        skipWhitespace()
        if (pos >= input.length) return null
        return when (input[pos]) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> readString()
            else -> readLiteral()
        }
    }

    private fun readObject(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        pos++ // '{'
        skipWhitespace()
        if (pos < input.length && input[pos] == '}') {
            pos++
            return map
        }
        while (pos < input.length) {
            skipWhitespace()
            if (pos >= input.length || input[pos] != '"') break
            val key = readString()
            skipWhitespace()
            if (pos < input.length && input[pos] == ':') pos++
            val value = readValue()
            map[key] = value
            skipWhitespace()
            if (pos < input.length && input[pos] == ',') {
                pos++
                continue
            }
            if (pos < input.length && input[pos] == '}') {
                pos++
                break
            }
        }
        return map
    }

    private fun readArray(): List<Any?> {
        val list = mutableListOf<Any?>()
        pos++ // '['
        skipWhitespace()
        if (pos < input.length && input[pos] == ']') {
            pos++
            return list
        }
        while (pos < input.length) {
            list.add(readValue())
            skipWhitespace()
            if (pos < input.length && input[pos] == ',') {
                pos++
                continue
            }
            if (pos < input.length && input[pos] == ']') {
                pos++
                break
            }
        }
        return list
    }

    private fun readString(): String {
        val sb = StringBuilder()
        pos++ // '"'
        while (pos < input.length) {
            val ch = input[pos++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= input.length) break
                    when (val esc = input[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= input.length) {
                                val hex = input.substring(pos, pos + 4)
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                pos += 4
                            }
                        }
                        else -> sb.append(esc)
                    }
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun readLiteral(): Any? {
        val start = pos
        while (pos < input.length && input[pos] !in charArrayOf(',', ']', '}', ' ', '\t', '\n', '\r')) {
            pos++
        }
        return when (val token = input.substring(start, pos)) {
            "true" -> true
            "false" -> false
            "null" -> null
            else -> token.toLongOrNull() ?: token.toDoubleOrNull() ?: token
        }
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) {
            pos++
        }
    }
}
