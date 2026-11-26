package io.github.princejoseph.meld.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v8: ranked-choice + weekly recurring rooms. */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `rooms` ADD COLUMN `isRanked` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `rooms` ADD COLUMN `repeatWeekly` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `rooms` ADD COLUMN `seriesId` TEXT")
        db.execSQL("ALTER TABLE `rooms` ADD COLUMN `nextRoomCode` TEXT")
        db.execSQL("ALTER TABLE `votes` ADD COLUMN `rank` INTEGER NOT NULL DEFAULT 0")
    }
}

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
    version = 8,
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
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    // Only pre-release development schemas (v1-v5) may be dropped.
                    .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
