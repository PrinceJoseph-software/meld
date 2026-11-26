package io.github.princejoseph.meld

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.princejoseph.meld.data.IdentityStore
import io.github.princejoseph.meld.data.MeldDatabase
import io.github.princejoseph.meld.data.MeldRepository
import io.github.princejoseph.meld.data.NewOptionSpec
import io.github.princejoseph.meld.data.OptionEntity
import io.github.princejoseph.meld.data.RemoteBallot
import io.github.princejoseph.meld.data.RemoteComment
import io.github.princejoseph.meld.data.RemoteRoomSnapshot
import io.github.princejoseph.meld.data.RemoteSyncGateway
import io.github.princejoseph.meld.data.RoomEntity
import io.github.princejoseph.meld.data.RoomMode
import io.github.princejoseph.meld.data.VoiceCommentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In-memory stand-in for Firestore: one shared "server" that several devices talk to. */
private class FakeServer {
    val rooms = mutableMapOf<String, RoomEntity>()
    val options = mutableMapOf<String, MutableMap<String, OptionEntity>>()
    val ballots = mutableMapOf<String, MutableMap<String, RemoteBallot>>()
    val comments = mutableMapOf<String, MutableMap<String, RemoteComment>>()
    val version = MutableStateFlow(0)

    fun snapshot(roomId: String): RemoteRoomSnapshot? = synchronized(this) {
        val room = rooms[roomId] ?: return null
        RemoteRoomSnapshot(
            room = room.copy(creatorToken = ""),
            options = options[roomId].orEmpty().values.toList(),
            ballots = ballots[roomId].orEmpty().values.toList(),
            comments = comments[roomId].orEmpty().values.toList()
        )
    }

    fun bump() { version.value += 1 }
}

private class FakeGateway(private val server: FakeServer, uid: String) : RemoteSyncGateway {
    override val isEnabled = true
    override val authUid: StateFlow<String?> = MutableStateFlow(uid)
    private val me = uid
    override suspend fun ensureSignedIn() = me
    override suspend fun pushRoom(room: RoomEntity, options: List<OptionEntity>) {
        synchronized(server) {
            val existing = server.rooms[room.id]
            if (existing != null && existing.ownerUid != me) return // rules: owner only
            server.rooms[room.id] = room.copy(ownerUid = room.ownerUid ?: me, creatorToken = "")
            val map = server.options.getOrPut(room.id) { mutableMapOf() }
            options.forEach { map[it.id] = it }
        }
        server.bump()
    }
    override suspend fun pushRoomState(room: RoomEntity) {
        synchronized(server) {
            val existing = server.rooms[room.id] ?: return
            if (existing.ownerUid != me) return
            server.rooms[room.id] = existing.copy(isLocked = room.isLocked, winnerOptionId = room.winnerOptionId)
        }
        server.bump()
    }
    override suspend fun pushOption(option: OptionEntity) {
        synchronized(server) { server.options.getOrPut(option.roomId) { mutableMapOf() }[option.id] = option }
        server.bump()
    }
    override suspend fun pushBallot(roomId: String, ballot: RemoteBallot) {
        synchronized(server) {
            val map = server.ballots.getOrPut(roomId) { mutableMapOf() }
            if (ballot.optionIds.isEmpty()) map.remove(me) else map[me] = ballot
        }
        server.bump()
    }
    override suspend fun clearBallots(roomId: String) {
        synchronized(server) { server.ballots[roomId]?.clear() }
        server.bump()
    }
    override suspend fun pushVoiceComment(comment: VoiceCommentEntity, audio: ByteArray?, audioMimeType: String?) {
        synchronized(server) {
            server.comments.getOrPut(comment.roomId) { mutableMapOf() }[comment.id] =
                RemoteComment(comment.copy(audioFilePath = null), audio != null, audioMimeType)
        }
        server.bump()
    }
    override suspend fun deleteVoiceComment(roomId: String, commentId: String) {
        synchronized(server) { server.comments[roomId]?.remove(commentId) }
        server.bump()
    }
    override suspend fun fetchRoom(roomId: String) = server.snapshot(roomId)
    override fun observeRoom(roomId: String): Flow<RemoteRoomSnapshot> =
        server.version.map { server.snapshot(roomId) }.filterNotNull()
    override suspend fun fetchAudio(roomId: String, commentId: String): Pair<ByteArray, String>? = null
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudSyncTest {

    private suspend fun eventually(check: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!check()) delay(20) }
    }

    @Test
    fun `room created on one device can be joined, voted on and closed from another`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FakeServer()
        val identity = IdentityStore(context)

        val dbA = Room.inMemoryDatabaseBuilder(context, MeldDatabase::class.java).allowMainThreadQueries().build()
        val dbB = Room.inMemoryDatabaseBuilder(context, MeldDatabase::class.java).allowMainThreadQueries().build()
        val deviceA = MeldRepository(dbA.meldDao(), identity, remoteSync = FakeGateway(server, "auth-A"))
        val deviceB = MeldRepository(dbB.meldDao(), identity, remoteSync = FakeGateway(server, "auth-B"))

        // Device A creates a time-slot room; it reaches the server with A as owner and slot times intact.
        val start = 1_800_000_000_000L
        val roomId = deviceA.createRoom(
            question = "When do we meet?",
            mode = RoomMode.TIME_SLOTS,
            options = listOf(NewOptionSpec("Thu 2pm", start), NewOptionSpec("Fri 10am", start + 86_400_000L)),
            allowMultiple = false,
            allowFriendsToAdd = true
        )
        eventually { server.rooms[roomId] != null && server.options[roomId].orEmpty().size == 2 }
        assertEquals("auth-A", server.rooms[roomId]!!.ownerUid)
        assertEquals(start, server.options[roomId]!!.values.minByOrNull { it.sortOrder }!!.slotStartMs)

        // Device B has never seen the room: join by code fetches it from the server.
        assertFalse(deviceB.roomExists(roomId))
        assertTrue(deviceB.fetchRemoteRoom(roomId))
        val joined = deviceB.observeRoom(roomId).first()!!
        assertEquals(2, joined.options.size)
        assertFalse("B is not the creator", joined.isCreator)
        assertNotNull(joined.options.first().slotStartMs)

        // B votes; the ballot goes to the server, and A sees it after mirroring.
        val thursday = joined.options.first { it.label == "Thu 2pm" }.id
        deviceB.toggleVote(roomId, thursday, isCurrentlyOnline = true)
        eventually { server.ballots[roomId].orEmpty().containsKey("auth-B") }
        deviceA.mirrorRemoteRoom(roomId).take(1).toList()
        val seenByA = deviceA.observeRoom(roomId).first()!!
        assertEquals(1, seenByA.totalVotes)
        assertEquals(thursday, seenByA.options.first { it.voteCount == 1 }.id)
        assertTrue("A keeps creator rights after mirroring", seenByA.isCreator)

        // B cannot close the room (not the owner); A can, and B sees it closed.
        deviceB.crownWinner(roomId)
        assertFalse(server.rooms[roomId]!!.isLocked)
        deviceA.crownWinner(roomId)
        eventually { server.rooms[roomId]!!.isLocked }
        deviceB.mirrorRemoteRoom(roomId).take(1).toList()
        val closedOnB = deviceB.observeRoom(roomId).first()!!
        assertTrue(closedOnB.isLocked)
        assertEquals(thursday, closedOnB.winnerOptionId)

        dbA.close()
        dbB.close()
    }

    @Test
    fun `ranked ballots keep their order across devices and runoff decides`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val server = FakeServer()
        val identity = IdentityStore(context)
        val dbA = Room.inMemoryDatabaseBuilder(context, MeldDatabase::class.java).allowMainThreadQueries().build()
        val dbB = Room.inMemoryDatabaseBuilder(context, MeldDatabase::class.java).allowMainThreadQueries().build()
        val deviceA = MeldRepository(dbA.meldDao(), identity, remoteSync = FakeGateway(server, "auth-A"))
        val deviceB = MeldRepository(dbB.meldDao(), identity, remoteSync = FakeGateway(server, "auth-B"))

        val roomId = deviceA.createRoom(
            question = "Offsite city?",
            mode = RoomMode.QUICK_POLL,
            options = listOf(NewOptionSpec("Lisbon"), NewOptionSpec("Oslo"), NewOptionSpec("Rome")),
            allowMultiple = false,
            allowFriendsToAdd = false,
            isRanked = true
        )
        eventually { server.rooms[roomId]?.isRanked == true && server.options[roomId].orEmpty().size == 3 }
        val ids = server.options[roomId]!!.values.associate { it.label to it.id }

        // Device A ranks Rome > Oslo; one vote is queued offline and flushed later.
        deviceA.toggleVote(roomId, ids.getValue("Rome"), isCurrentlyOnline = true)
        deviceA.toggleVote(roomId, ids.getValue("Oslo"), isCurrentlyOnline = false)
        val queued = deviceA.observeRoom(roomId).first()!!
        assertEquals(1, queued.options.first { it.label == "Rome" }.myRank)
        assertEquals(2, queued.options.first { it.label == "Oslo" }.myRank)
        deviceA.flushPendingVotes()
        eventually { server.ballots[roomId]?.get("auth-A")?.optionIds?.size == 2 }
        assertEquals(listOf(ids["Rome"], ids["Oslo"]), server.ballots[roomId]!!["auth-A"]!!.optionIds)

        // Two other voters (written straight to the server): Lisbon, Oslo.
        synchronized(server) {
            server.ballots[roomId]!!["auth-X"] = RemoteBallot("x", "X", 0, 0, listOf(ids.getValue("Lisbon")), 1)
            server.ballots[roomId]!!["auth-Y"] = RemoteBallot("y", "Y", 0, 0, listOf(ids.getValue("Oslo")), 1)
        }
        server.bump()

        // Device B joins and sees the same runoff result: Rome eliminated, its ballot moves to Oslo.
        assertTrue(deviceB.fetchRemoteRoom(roomId))
        val onB = deviceB.observeRoom(roomId).first()!!
        assertTrue(onB.isRanked)
        assertEquals("Oslo", onB.options.first().label)
        assertEquals(2, onB.options.first().voteCount)

        deviceA.mirrorRemoteRoom(roomId).take(1).toList()
        deviceA.crownWinner(roomId)
        eventually { server.rooms[roomId]!!.isLocked }
        assertEquals(ids["Oslo"], server.rooms[roomId]!!.winnerOptionId)

        dbA.close()
        dbB.close()
    }
}
