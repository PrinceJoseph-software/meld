package io.github.princejoseph.meld.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v7: slot timestamps on options (locale-independent time slots) and room ownerUid for sync. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `options` ADD COLUMN `slotStartMs` INTEGER")
        db.execSQL("ALTER TABLE `options` ADD COLUMN `slotEndMs` INTEGER")
        db.execSQL("ALTER TABLE `rooms` ADD COLUMN `ownerUid` TEXT")
    }
}

@Database(
    entities = [
        RoomEntity::class,
        OptionEntity::class,
        VoteEntity::class,
        PendingVoteEntity::class,
        AnonymousLockEntity::class,
        VoiceCommentEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class MeldDatabase : RoomDatabase() {
    abstract fun meldDao(): MeldDao

    companion object {
        @Volatile
        private var instance: MeldDatabase? = null

        fun getInstance(context: Context): MeldDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MeldDatabase::class.java,
                    "meld_decisions.db"
                )
                    .addMigrations(MIGRATION_6_7)
                    // Only pre-release development schemas (v1-v5) may be dropped.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
