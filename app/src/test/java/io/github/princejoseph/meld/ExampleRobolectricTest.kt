package io.github.princejoseph.meld

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.princejoseph.meld.data.AvatarSpec
import io.github.princejoseph.meld.data.IdentityStore
import io.github.princejoseph.meld.data.MeldDatabase
import io.github.princejoseph.meld.data.MeldRepository
import io.github.princejoseph.meld.data.OptionUiModel
import io.github.princejoseph.meld.data.RoomDetailsUiModel
import io.github.princejoseph.meld.data.RoomMode
import io.github.princejoseph.meld.ui.components.PollCardImageExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Meld`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Meld", appName)
    }

    @Test
    fun `create room vote offline queue sync and crown winner`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MeldDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val identityStore = IdentityStore(context)
        val repo = MeldRepository(db.meldDao(), identityStore)

        val identity = repo.initIdentity()
        assertTrue(identity.uid.isNotBlank())

        val roomId = repo.createRoom(
            question = "Where are we eating tonight?",
            mode = RoomMode.QUICK_POLL,
            optionLabels = listOf("Tacos", "Ramen", "Pizza"),
            allowMultiple = false,
            allowFriendsToAdd = true
        )

        val initialRoom = repo.observeRoom(roomId).first()!!
        assertEquals(3, initialRoom.options.size)
        assertTrue(initialRoom.isCreator)
        assertFalse(initialRoom.isLocked)

        // Vote offline -> queued in outbox and shown optimistically
        val ramenId = initialRoom.options[1].id
        repo.toggleVote(roomId, ramenId, isCurrentlyOnline = false)

        val queuedRoom = repo.observeRoom(roomId).first()!!
        assertEquals(1, queuedRoom.queuedVoteCount)
        assertEquals(1, queuedRoom.totalVotes)
        assertTrue(queuedRoom.options.first().isPendingSync)

        // Flush queued votes on reconnect
        repo.flushPendingVotes()
        val syncedRoom = repo.observeRoom(roomId).first()!!
        assertEquals(0, syncedRoom.queuedVoteCount)
        assertEquals(1, syncedRoom.totalVotes)
        assertEquals(ramenId, syncedRoom.options.first().id)

        // Crown winner
        repo.crownWinner(roomId)
        val lockedRoom = repo.observeRoom(roomId).first()!!
        assertTrue(lockedRoom.isLocked)
        assertEquals(ramenId, lockedRoom.winnerOptionId)
        assertTrue(lockedRoom.options.first().isWinner)

        db.close()
    }

    @Test
    fun `anonymous badge customization updates identity and mask`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val identityStore = IdentityStore(context)
        val customSpec = AvatarSpec(chassis = 7, eyes = 2, accessory = 3, pattern = 4, frame = 2)
        val packed = customSpec.toPacked()
        identityStore.setAnonymousBadge(avatarStyleIndex = packed, badgeColorIndex = 3, maskName = "Agent Spectre")

        val identity = identityStore.identityFlow.first()
        assertEquals(packed, identity.anonymousAvatarStyleIndex)
        assertEquals(customSpec, identity.anonymousAvatarSpec)
        assertEquals(3, identity.anonymousBadgeColorIndex)
        assertEquals("Agent Spectre", identity.anonymousMaskName)
        assertEquals("Agent Spectre", identity.effectiveAnonymousName)
    }

    @Test
    fun `voice comments can be added and observed for room`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, MeldDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val identityStore = IdentityStore(context)
        val repo = MeldRepository(db.meldDao(), identityStore)
        repo.initIdentity()

        val roomId = repo.createRoom(
            question = "Which movie?",
            mode = RoomMode.QUICK_POLL,
            optionLabels = listOf("Interstellar", "Inception"),
            allowMultiple = false,
            allowFriendsToAdd = false
        )

        val roomBefore = repo.observeRoom(roomId).first()!!
        val interstellarId = roomBefore.options.first().id
        assertTrue(roomBefore.voiceComments.isEmpty())

        // Add a voice comment attached to Interstellar
        repo.addVoiceComment(
            roomId = roomId,
            optionId = interstellarId,
            transcript = "Hans Zimmer score in IMAX was breathtaking",
            durationSeconds = 6
        )

        val roomAfter = repo.observeRoom(roomId).first()!!
        assertEquals(1, roomAfter.voiceComments.size)
        val comment = roomAfter.voiceComments.first()
        assertEquals("Hans Zimmer score in IMAX was breathtaking", comment.transcript)
        assertEquals("Interstellar", comment.optionLabel)
        assertEquals(6, comment.durationSeconds)
        assertTrue(comment.isMyComment)

        // Delete voice comment
        repo.deleteVoiceComment(comment.id)
        val roomCleaned = repo.observeRoom(roomId).first()!!
        assertTrue(roomCleaned.voiceComments.isEmpty())

        // Locked room rejects new voice comments
        repo.crownWinner(roomId)
        repo.addVoiceComment(roomId, null, "Too late!", 2)
        val lockedRoom = repo.observeRoom(roomId).first()!!
        assertTrue(lockedRoom.voiceComments.isEmpty())

        db.close()
    }

    @Test
    fun `poll card image exporter renders 1080x1350 bitmap and writes png uri`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val room = RoomDetailsUiModel(
            id = "CARD99",
            question = "Where should we celebrate?",
            mode = RoomMode.QUICK_POLL,
            allowMultiple = false,
            allowFriendsToAdd = false,
            isCreator = true,
            isLocked = true,
            winnerOptionId = "o1",
            createdAtEpochMs = 1_000L,
            expiresAtEpochMs = 100_000L,
            options = listOf(
                OptionUiModel("o1", "Ramen House", 0, 4, 80, 0.8f, true, false, true, isWinner = true, voters = emptyList()),
                OptionUiModel("o2", "Taco Bar", 1, 1, 20, 0.2f, false, false, false, isWinner = false, voters = emptyList())
            ),
            totalVotes = 5,
            activeVoters = emptyList(),
            queuedVoteCount = 0,
            hasTiedLeaders = false
        )

        val bmp = PollCardImageExporter.renderCardBitmap(room)
        assertEquals(1080, bmp.width)
        assertEquals(1350, bmp.height)
        bmp.recycle()

        val file = PollCardImageExporter.exportToFile(context, room)
        assertTrue(file != null && file.exists() && file.length() > 0L)
        assertTrue(file!!.name == "meld_card_CARD99.png")
    }
}
