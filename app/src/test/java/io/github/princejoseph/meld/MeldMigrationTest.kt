package io.github.princejoseph.meld

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import io.github.princejoseph.meld.data.MIGRATION_6_7
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

    private companion object {
        const val DB = "migration-test.db"
    }
}
