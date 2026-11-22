package com.uth.memora.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.uth.memora.core.database.converter.Converters
import com.uth.memora.core.database.dao.CardDao
import com.uth.memora.core.database.dao.DeckDao
import com.uth.memora.core.database.dao.PracticeSentenceDao
import com.uth.memora.core.database.dao.ReviewLogDao
import com.uth.memora.core.database.entity.CardEntity
import com.uth.memora.core.database.entity.DeckEntity
import com.uth.memora.core.database.entity.PracticeSentenceEntity
import com.uth.memora.core.database.entity.ReviewLogEntity

/**
 * Cơ sở dữ liệu cục bộ Room Database của Memora (version 1 - Mục 10.1).
 *
 * Lưu trữ offline-first toàn bộ bộ từ, thẻ học SM-2, lịch sử đánh giá và câu luyện tập AI.
 */
@Database(
    entities = [
        DeckEntity::class,
        CardEntity::class,
        ReviewLogEntity::class,
        PracticeSentenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MemoraDatabase : RoomDatabase() {

    abstract fun deckDao(): DeckDao

    abstract fun cardDao(): CardDao

    abstract fun reviewLogDao(): ReviewLogDao

    abstract fun practiceSentenceDao(): PracticeSentenceDao

    companion object {
        const val DATABASE_NAME = "memora.db"

        @Volatile
        private var INSTANCE: MemoraDatabase? = null

        /**
         * Trả về thể hiện duy nhất (thread-safe singleton) của [MemoraDatabase].
         */
        fun getInstance(context: Context): MemoraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoraDatabase::class.java,
                    DATABASE_NAME
                ).build().also { db ->
                    INSTANCE = db
                }
            }
        }
    }
}
