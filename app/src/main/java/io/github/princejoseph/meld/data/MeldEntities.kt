package io.github.princejoseph.meld.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val id: String,
    val question: String,
    val mode: String,
    val allowMultiple: Boolean,
    val allowFriendsToAdd: Boolean,
    val isAnonymous: Boolean = false,
    val deadlineEpochMs: Long? = null,
    val creatorToken: String,
    val winnerOptionId: String? = null,
    val isLocked: Boolean = false,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    /** Server identity (Firebase anonymous-auth uid) of the creator; null for local-only rooms. */
    val ownerUid: String? = null
)

@Entity(
    tableName = "options",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("roomId")]
)
data class OptionEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val label: String,
    val sortOrder: Int,
    /** Machine-readable slot window for TIME_SLOTS rooms; the label is display-only. */
    val slotStartMs: Long? = null,
    val slotEndMs: Long? = null
)

@Entity(
    tableName = "votes",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = OptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["optionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("roomId"), Index("optionId")]
)
data class VoteEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val optionId: String,
    val voterUid: String,
    val voterName: String,
    val voterBadgeColorIndex: Int,
    val voterAvatarStyleIndex: Int = 0,
    val votedAtEpochMs: Long
)

@Entity(
    tableName = "pending_votes",
    indices = [Index("roomId"), Index("createdAtEpochMs")]
)
data class PendingVoteEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val optionId: String,
    val voterUid: String,
    val voterName: String,
    val voterBadgeColorIndex: Int,
    val voterAvatarStyleIndex: Int,
    val isRemoval: Boolean,
    val allowMultiple: Boolean,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "anonymous_locks",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("roomId")]
)
data class AnonymousLockEntity(
    @PrimaryKey val lockKey: String,
    val roomId: String,
    val networkHash: String,
    val maskedIpLabel: String,
    val lockedVoterUid: String,
    val windowStartEpochMs: Long,
    val actionCountInWindow: Int,
    val cooldownUntilMs: Long,
    val lockedAtEpochMs: Long
)

data class RoomWithOptionsAndVotes(
    @Embedded val room: RoomEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "roomId"
    )
    val options: List<OptionEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "roomId"
    )
    val votes: List<VoteEntity>
)

@Entity(
    tableName = "voice_comments",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("roomId"), Index("createdAtEpochMs")]
)
data class VoiceCommentEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val optionId: String? = null,
    val voterUid: String,
    val voterName: String,
    val voterBadgeColorIndex: Int,
    val voterAvatarStyleIndex: Int,
    val transcript: String,
    val durationSeconds: Int,
    val createdAtEpochMs: Long,
    val audioFilePath: String? = null
)

