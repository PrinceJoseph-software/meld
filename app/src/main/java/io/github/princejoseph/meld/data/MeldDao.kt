package io.github.princejoseph.meld.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("DELETE FROM voice_comments WHERE id = :commentId")
    suspend fun deleteVoiceComment(commentId: String)
}
