package io.github.princejoseph.meld

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.princejoseph.meld.data.MIGRATION_6_7
import io.github.princejoseph.meld.data.MIGRATION_7_8
import io.github.princejoseph.meld.data.MeldDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeldMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MeldDatabase::class.java
    )

    @Test
    fun `migration 6 to 7 keeps rooms options and votes`() {
        helper.createDatabase(DB, 6).apply {
            execSQL(
                "INSERT INTO rooms (id, question, mode, allowMultiple, allowFriendsToAdd, isAnonymous, " +
                    "deadlineEpochMs, creatorToken, winnerOptionId, isLocked, createdAtEpochMs, expiresAtEpochMs) " +
                    "VALUES ('ABC123', 'Lunch?', 'QUICK_POLL', 0, 0, 0, NULL, 'tok', NULL, 0, 1, 2)"
            )
            execSQL("INSERT INTO options (id, roomId, label, sortOrder) VALUES ('o1', 'ABC123', 'Pizza', 0)")
            execSQL(
                "INSERT INTO votes (id, roomId, optionId, voterUid, voterName, voterBadgeColorIndex, " +
                    "voterAvatarStyleIndex, votedAtEpochMs) VALUES ('ABC123:o1:u1', 'ABC123', 'o1', 'u1', 'Ada', 0, 0, 3)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 7, true, MIGRATION_6_7)
        db.query("SELECT label, slotStartMs, slotEndMs FROM options WHERE id = 'o1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Pizza", c.getString(0))
            assertTrue(c.isNull(1))
            assertTrue(c.isNull(2))
        }
        db.query("SELECT ownerUid FROM rooms WHERE id = 'ABC123'").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
        }
        db.query("SELECT COUNT(*) FROM votes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    @Test
    fun `migration 7 to 8 adds ranked and weekly columns without losing votes`() {
        helper.createDatabase(DB8, 7).apply {
            execSQL(
                "INSERT INTO rooms (id, question, mode, allowMultiple, allowFriendsToAdd, isAnonymous, " +
                    "deadlineEpochMs, creatorToken, winnerOptionId, isLocked, createdAtEpochMs, expiresAtEpochMs, ownerUid) " +
                    "VALUES ('XYZ789', 'When?', 'TIME_SLOTS', 0, 0, 0, NULL, 'tok', NULL, 0, 1, 2, 'auth1')"
            )
            execSQL("INSERT INTO options (id, roomId, label, sortOrder, slotStartMs, slotEndMs) VALUES ('o1', 'XYZ789', 'Mon', 0, 10, 20)")
            execSQL(
                "INSERT INTO votes (id, roomId, optionId, voterUid, voterName, voterBadgeColorIndex, " +
                    "voterAvatarStyleIndex, votedAtEpochMs) VALUES ('XYZ789:o1:u1', 'XYZ789', 'o1', 'u1', 'Ada', 0, 0, 3)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB8, 8, true, MIGRATION_7_8)
        db.query("SELECT isRanked, repeatWeekly, seriesId, nextRoomCode, ownerUid FROM rooms WHERE id = 'XYZ789'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
            assertEquals("auth1", c.getString(4))
        }
        db.query("SELECT rank FROM votes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    private companion object {
        const val DB = "migration-test.db"
        const val DB8 = "migration-test-8.db"
    }
}
