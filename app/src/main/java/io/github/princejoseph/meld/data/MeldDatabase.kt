package io.github.princejoseph.meld.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoomEntity::class,
        OptionEntity::class,
        VoteEntity::class,
        PendingVoteEntity::class,
        AnonymousLockEntity::class,
        VoiceCommentEntity::class
    ],
    version = 5,
    exportSchema = false
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
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
