package io.github.princejoseph.meld.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MeldDao {
    @Transaction
    @Query("SELECT * FROM rooms WHERE id = :roomId")
    fun observeRoom(roomId: String): Flow<RoomWithOptionsAndVotes?>

    @Transaction
    @Query("SELECT * FROM rooms ORDER BY createdAtEpochMs DESC LIMIT 50")
    fun observeRecentRooms(): Flow<List<RoomWithOptionsAndVotes>>

    @Transaction
    @Query("SELECT * FROM rooms ORDER BY createdAtEpochMs DESC LIMIT 20")
    suspend fun getRecentRoomsOnce(): List<RoomWithOptionsAndVotes>

    @Query("SELECT * FROM pending_votes WHERE roomId = :roomId ORDER BY createdAtEpochMs ASC")
    fun observePendingVotesForRoom(roomId: String): Flow<List<PendingVoteEntity>>

    @Query("SELECT * FROM pending_votes ORDER BY createdAtEpochMs ASC")
    fun observeAllPendingVotes(): Flow<List<PendingVoteEntity>>

    @Query("SELECT * FROM pending_votes ORDER BY createdAtEpochMs ASC")
    suspend fun getAllPendingVotesOnce(): List<PendingVoteEntity>

    @Query("SELECT * FROM rooms WHERE id = :roomId")
    suspend fun getRoomOnce(roomId: String): RoomEntity?

    @Query("SELECT * FROM options WHERE roomId = :roomId ORDER BY sortOrder ASC")
    suspend fun getOptionsOnce(roomId: String): List<OptionEntity>

    @Query("SELECT * FROM votes WHERE roomId = :roomId")
    suspend fun getVotesOnce(roomId: String): List<VoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOptions(options: List<OptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOption(option: OptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVote(vote: VoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingVote(pendingVote: PendingVoteEntity)

    @Query("DELETE FROM pending_votes WHERE id = :pendingId")
    suspend fun deletePendingVoteById(pendingId: String)

    @Query("DELETE FROM pending_votes WHERE roomId = :roomId")
    suspend fun clearPendingVotesForRoom(roomId: String)

    @Query("DELETE FROM votes WHERE roomId = :roomId AND optionId = :optionId AND voterUid = :voterUid")
    suspend fun deleteVote(roomId: String, optionId: String, voterUid: String)

    @Query("DELETE FROM votes WHERE roomId = :roomId AND voterUid = :voterUid")
    suspend fun deleteVotesByVoterInRoom(roomId: String, voterUid: String)

    @Query("DELETE FROM votes WHERE roomId = :roomId")
    suspend fun clearRoomVotes(roomId: String)

    @Query("UPDATE rooms SET isLocked = :isLocked, winnerOptionId = :winnerOptionId WHERE id = :roomId")
    suspend fun updateRoomLock(roomId: String, isLocked: Boolean, winnerOptionId: String?)

    @Query("UPDATE rooms SET isLocked = :isLocked, winnerOptionId = :winnerOptionId, deadlineEpochMs = :deadlineEpochMs WHERE id = :roomId")
    suspend fun updateRoomDeadlineAndLock(
        roomId: String,
        isLocked: Boolean,
        winnerOptionId: String?,
        deadlineEpochMs: Long?
    )

    @Query("DELETE FROM rooms WHERE id = :roomId")
    suspend fun deleteRoom(roomId: String)

    @Query(
        "UPDATE votes SET voterName = :newName, voterBadgeColorIndex = :colorIndex, voterAvatarStyleIndex = :avatarIndex WHERE voterUid = :voterUid"
    )
    suspend fun updateVoterProfileAcrossVotes(
        voterUid: String,
        newName: String,
        colorIndex: Int,
        avatarIndex: Int
    )

    @Transaction
    suspend fun createRoomWithOptions(room: RoomEntity, options: List<OptionEntity>) {
        insertRoom(room)
        insertOptions(options)
    }

    @Query("SELECT * FROM anonymous_locks WHERE lockKey = :lockKey LIMIT 1")
    suspend fun getAnonymousLock(lockKey: String): AnonymousLockEntity?

    @Query("SELECT * FROM anonymous_locks WHERE roomId = :roomId")
    fun observeAnonymousLocksForRoom(roomId: String): Flow<List<AnonymousLockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnonymousLock(lock: AnonymousLockEntity)

    @Query("DELETE FROM anonymous_locks WHERE roomId = :roomId")
    suspend fun clearAnonymousLocksForRoom(roomId: String)

    @Transaction
    suspend fun toggleSingleChoiceVote(
        roomId: String,
        optionId: String,
        voterUid: String,
        alreadySelected: Boolean,
        newVote: VoteEntity
    ) {
        deleteVotesByVoterInRoom(roomId, voterUid)
        if (!alreadySelected) {
            insertVote(newVote)
        }
    }

    @Query("SELECT * FROM voice_comments WHERE roomId = :roomId ORDER BY createdAtEpochMs ASC")
    fun observeVoiceCommentsForRoom(roomId: String): Flow<List<VoiceCommentEntity>>

    @Query("SELECT COUNT(*) FROM voice_comments WHERE roomId = :roomId")
    suspend fun getVoiceCommentCountOnce(roomId: String): Int

    @Query("SELECT * FROM voice_comments WHERE id = :commentId LIMIT 1")
    suspend fun getVoiceCommentByIdOnce(commentId: String): VoiceCommentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoiceComment(comment: VoiceCommentEntity)

    @Query("SELECT audioFilePath FROM voice_comments WHERE roomId = :roomId AND audioFilePath IS NOT NULL")
    suspend fun getVoiceAudioPathsForRoom(roomId: String): List<String>

    @Query("DELETE FROM voice_comments WHERE id = :commentId")
    suspend fun deleteVoiceComment(commentId: String)

    // ---- Cloud sync ----------------------------------------------------------------------

    // @Upsert (UPDATE-or-INSERT) is required here: REPLACE would delete the parent row first
    // and CASCADE-delete its options, votes and comments.
    @Upsert
    suspend fun upsertRoom(room: RoomEntity)

    @Upsert
    suspend fun upsertOptions(options: List<OptionEntity>)

    @Upsert
    suspend fun upsertVoiceComments(comments: List<VoiceCommentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVotes(votes: List<VoteEntity>)

    @Query("SELECT * FROM votes WHERE roomId = :roomId AND voterUid = :voterUid ORDER BY rank ASC, votedAtEpochMs ASC")
    suspend fun getVotesByVoterOnce(roomId: String, voterUid: String): List<VoteEntity>

    /**
     * Ranked rooms: add [newVote] at the end of the voter's ranking, or (isRemoval) take the
     * option out and close the gap so ranks stay 0..n-1.
     */
    @Transaction
    suspend fun applyRankedVote(roomId: String, optionId: String, voterUid: String, isRemoval: Boolean, newVote: VoteEntity) {
        val current = getVotesByVoterOnce(roomId, voterUid)
        val kept = current.filter { it.optionId != optionId }
        val ordered = if (isRemoval) kept else kept + newVote
        deleteVotesByVoterInRoom(roomId, voterUid)
        insertVotes(ordered.mapIndexed { idx, v -> v.copy(rank = idx) })
    }

    @Query("SELECT * FROM voice_comments WHERE roomId = :roomId")
    suspend fun getVoiceCommentsOnce(roomId: String): List<VoiceCommentEntity>

    @Query("UPDATE voice_comments SET audioFilePath = :path WHERE id = :commentId")
    suspend fun setVoiceCommentAudioPath(commentId: String, path: String)

    @Query("UPDATE rooms SET ownerUid = :ownerUid WHERE id = :roomId")
    suspend fun setRoomOwnerUid(roomId: String, ownerUid: String)

    @Query("DELETE FROM voice_comments WHERE roomId = :roomId AND id NOT IN (:keepIds) AND voterUid != :myUid")
    suspend fun deleteOtherCommentsNotIn(roomId: String, keepIds: List<String>, myUid: String)

    @Query("DELETE FROM voice_comments WHERE roomId = :roomId AND voterUid != :myUid")
    suspend fun deleteAllOtherComments(roomId: String, myUid: String)

    /**
     * Mirrors server state into the local tables in one transaction. Votes are replaced
     * wholesale (the server is authoritative; this device's queued votes live in
     * pending_votes and are layered on top). Local-only fields (creator token, audio paths)
     * are preserved.
     */
    @Transaction
    suspend fun applyRemoteRoom(
        room: RoomEntity,
        options: List<OptionEntity>,
        votes: List<VoteEntity>,
        comments: List<VoiceCommentEntity>,
        myUid: String
    ) {
        val local = getRoomOnce(room.id)
        // A deadline that has passed closes the room on every device. If this device already
        // finalized it locally, keep that result until the owner's device publishes its own.
        val deadlinePassed = room.deadlineEpochMs?.let { System.currentTimeMillis() >= it } == true
        val keepLocalClose = deadlinePassed && !room.isLocked && local?.isLocked == true
        upsertRoom(
            room.copy(
                creatorToken = local?.creatorToken ?: "",
                ownerUid = room.ownerUid ?: local?.ownerUid,
                isLocked = room.isLocked || keepLocalClose,
                winnerOptionId = if (keepLocalClose) local?.winnerOptionId else room.winnerOptionId
            )
        )
        upsertOptions(options)
        clearRoomVotes(room.id)
        insertVotes(votes)
        val localPaths = getVoiceCommentsOnce(room.id).associate { it.id to it.audioFilePath }
        upsertVoiceComments(comments.map { it.copy(audioFilePath = localPaths[it.id] ?: it.audioFilePath) })
        if (comments.isEmpty()) {
            deleteAllOtherComments(room.id, myUid)
        } else {
            deleteOtherCommentsNotIn(room.id, comments.map { it.id }, myUid)
        }
    }
}
