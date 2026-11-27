package com.uth.memora.core.data

import androidx.room.withTransaction
import com.uth.memora.core.database.MemoraDatabase
import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.database.mapper.toDomain
import com.uth.memora.core.database.mapper.toEntity
import com.uth.memora.core.domain.AuthRepository
import com.uth.memora.core.domain.DeckRepository
import com.uth.memora.core.model.Card
import com.uth.memora.core.model.CardState
import com.uth.memora.core.model.Deck
import com.uth.memora.core.model.SyncStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID

/**
 * Hiện thực [DeckRepository] trên Room Database (Mục 5, Mục 6.3, Mục 10.1 & Mục 11).
 *
 * Bảo đảm mọi thao tác ghi đều cập nhật `updatedAtMillis` và đặt `syncStatus = SyncStatus.PENDING_SYNC`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomDeckRepository(
    private val database: MemoraDatabase? = null,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val authRepository: AuthRepository? = null,
    private val prebuiltDeckSeeder: PrebuiltDeckSeeder? = null,
    private val ownerIdProvider: suspend () -> String = {
        authRepository?.getCurrentUser()?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID
    },
    private val transactionRunner: suspend (suspend () -> Unit) -> Unit = { block ->
        val db = database
        if (db != null) {
            db.withTransaction { block() }
        } else {
            block()
        }
    }
) : DeckRepository {

    override fun observeActiveDecks(): Flow<List<Deck>> {
        val ownerFlow: Flow<String> = authRepository
            ?.observeCurrentUser()
            ?.map { user -> user?.uid?.takeIf { it.isNotBlank() } ?: Deck.GUEST_OWNER_ID }
            ?.distinctUntilChanged()
            ?: flow { emit(ownerIdProvider()) }

        return ownerFlow.flatMapLatest { ownerId ->
            deckDao.observeActiveDecks(ownerId).map { entities ->
                entities.map { it.toDomain() }
            }
        }
    }

    override fun observePrebuiltDecks(): Flow<List<Deck>> =
        deckDao.observePrebuiltDecks()
            .onStart { prebuiltDeckSeeder?.seedIfNeeded() }
            .map { entities ->
                entities.map { it.toDomain() }
            }

    override suspend fun getDeckById(deckId: String): Deck? {
        val cleanId = deckId.trim()
        if (cleanId.isEmpty()) return null
        return deckDao.getDeckById(cleanId)?.toDomain()
    }

    override suspend fun createDeck(name: String, description: String): Result<Deck> = runCatching {
        val cleanName = name.trim()
        val cleanDescription = description.trim()
        validateDeckInput(cleanName, cleanDescription)

        val ownerId = ownerIdProvider()
        val activeDecks = deckDao.observeActiveDecks(ownerId).first()
        if (activeDecks.size >= Deck.MAX_DECKS_PER_USER) {
            throw IllegalStateException(
                "Bạn đã đạt giới hạn tối đa ${Deck.MAX_DECKS_PER_USER} bộ từ."
            )
        }

        val isDuplicate = activeDecks.any { existing ->
            existing.name.trim().equals(cleanName, ignoreCase = true)
        } || deckDao.getDeckByName(ownerId, cleanName) != null

        if (isDuplicate) {
            throw IllegalArgumentException("Tên bộ từ \"$cleanName\" đã tồn tại.")
        }

        val now = System.currentTimeMillis()
        val newDeck = Deck(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            name = cleanName,
            description = cleanDescription,
            sourcePrebuiltId = null,
            createdAtMillis = now,
            updatedAtMillis = now,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_SYNC
        )
        deckDao.upsertDeck(newDeck.toEntity())
        newDeck
    }

    override suspend fun updateDeck(
        deckId: String,
        name: String,
        description: String
    ): Result<Deck> = runCatching {
        val cleanDeckId = deckId.trim()
        val existingEntity = deckDao.getDeckById(cleanDeckId)
            ?: throw NoSuchElementException("Không tìm thấy bộ từ.")

        val cleanName = name.trim()
        val cleanDescription = description.trim()
        validateDeckInput(cleanName, cleanDescription)

        val activeDecks = deckDao.observeActiveDecks(existingEntity.ownerId).first()
        val isDuplicate = activeDecks.any { other ->
            other.id != cleanDeckId && other.name.trim().equals(cleanName, ignoreCase = true)
        }
        if (isDuplicate) {
            throw IllegalArgumentException("Tên bộ từ \"$cleanName\" đã tồn tại.")
        }

        val updatedDeck = existingEntity.toDomain().copy(
            name = cleanName,
            description = cleanDescription,
            updatedAtMillis = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_SYNC
        )
        deckDao.upsertDeck(updatedDeck.toEntity())
        updatedDeck
    }

    override suspend fun softDeleteDeck(deckId: String): Result<Unit> = runCatching {
        val cleanDeckId = deckId.trim()
        if (cleanDeckId.isEmpty()) {
            throw IllegalArgumentException("Mã bộ từ không hợp lệ.")
        }
        deckDao.getDeckById(cleanDeckId)
            ?: throw NoSuchElementException("Không tìm thấy bộ từ.")

        val now = System.currentTimeMillis()
        transactionRunner {
            deckDao.softDeleteDeck(
                deckId = cleanDeckId,
                updatedAtMillis = now,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            cardDao.softDeleteCardsByDeck(
                deckId = cleanDeckId,
                updatedAtMillis = now,
                syncStatus = SyncStatus.PENDING_SYNC
            )
        }
    }

    override suspend fun importPrebuiltDeck(prebuiltDeckId: String): Result<Deck> = runCatching {
        val cleanPrebuiltId = prebuiltDeckId.trim()
        if (cleanPrebuiltId.isEmpty()) {
            throw IllegalArgumentException("Mã bộ từ có sẵn không hợp lệ.")
        }
        prebuiltDeckSeeder?.seedIfNeeded()

        val ownerId = ownerIdProvider()
        val existingImported = deckDao.getDeckBySourcePrebuiltId(ownerId, cleanPrebuiltId)
        if (existingImported != null && !existingImported.isDeleted) {
            return@runCatching existingImported.toDomain()
        }

        val activeDecks = deckDao.observeActiveDecks(ownerId).first()
        if (activeDecks.size >= Deck.MAX_DECKS_PER_USER) {
            throw IllegalStateException(
                "Bạn đã đạt giới hạn tối đa ${Deck.MAX_DECKS_PER_USER} bộ từ."
            )
        }

        val templateDeck = deckDao.getDeckById(cleanPrebuiltId)
            ?: throw NoSuchElementException("Không tìm thấy bộ từ có sẵn.")

        val now = System.currentTimeMillis()
        val newDeckId = UUID.randomUUID().toString()
        val candidateName = resolveUniqueImportedDeckName(templateDeck.name, activeDecks.map { it.name })
        val importedDeck = Deck(
            id = newDeckId,
            ownerId = ownerId,
            name = candidateName,
            description = templateDeck.description,
            sourcePrebuiltId = cleanPrebuiltId,
            createdAtMillis = now,
            updatedAtMillis = now,
            isDeleted = false,
            syncStatus = SyncStatus.PENDING_SYNC
        )

        val templateCards = cardDao.observeCardsByDeck(cleanPrebuiltId).first()
        val copiedCards = templateCards.mapIndexed { index, cardEntity ->
            cardEntity.copy(
                id = UUID.randomUUID().toString(),
                deckId = newDeckId,
                ownerId = ownerId,
                state = CardState.NEW,
                easeFactor = Card.INITIAL_EASE_FACTOR,
                repetitions = 0,
                interval = 0,
                lapses = 0,
                dueDay = null,
                dueAtMillis = null,
                firstSeenDay = null,
                lastReviewedAtMillis = null,
                createdAtMillis = now + index,
                updatedAtMillis = now + index,
                isDeleted = false,
                syncStatus = SyncStatus.PENDING_SYNC
            )
        }

        transactionRunner {
            deckDao.upsertDeck(importedDeck.toEntity())
            if (copiedCards.isNotEmpty()) {
                cardDao.upsertCards(copiedCards)
            }
        }
        importedDeck
    }

    private fun resolveUniqueImportedDeckName(
        baseName: String,
        existingNames: List<String>
    ): String {
        val trimmedBase = baseName.trim().take(Deck.MAX_NAME_LENGTH)
        if (existingNames.none { it.trim().equals(trimmedBase, ignoreCase = true) }) {
            return trimmedBase
        }
        var suffixIndex = 2
        while (true) {
            val suffix = " ($suffixIndex)"
            val maxBaseLen = (Deck.MAX_NAME_LENGTH - suffix.length).coerceAtLeast(1)
            val candidate = trimmedBase.take(maxBaseLen) + suffix
            if (existingNames.none { it.trim().equals(candidate, ignoreCase = true) }) {
                return candidate
            }
            suffixIndex++
        }
    }

    private fun validateDeckInput(cleanName: String, cleanDescription: String) {
        if (cleanName.isEmpty() || cleanName.length > Deck.MAX_NAME_LENGTH) {
            throw IllegalArgumentException(
                "Tên bộ từ phải từ 1 đến ${Deck.MAX_NAME_LENGTH} ký tự."
            )
        }
        if (cleanDescription.length > Deck.MAX_DESCRIPTION_LENGTH) {
            throw IllegalArgumentException(
                "Mô tả bộ từ tối đa ${Deck.MAX_DESCRIPTION_LENGTH} ký tự."
            )
        }
    }
}
