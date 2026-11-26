package io.github.princejoseph.meld.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/** One voter's complete ballot in a room (the server stores one document per voter). */
data class RemoteBallot(
    val voterUid: String,
    val voterName: String,
    val badgeColorIndex: Int,
    val avatarStyleIndex: Int,
    val optionIds: List<String>,
    val updatedAtEpochMs: Long
)

/** A voice/typed opinion as stored remotely; audio bytes are fetched lazily. */
data class RemoteComment(
    val comment: VoiceCommentEntity,
    val hasAudio: Boolean,
    val audioMimeType: String?
)

/** Server state of one room. `room.creatorToken` is always blank: ownership is `ownerUid`. */
data class RemoteRoomSnapshot(
    val room: RoomEntity,
    val options: List<OptionEntity>,
    val ballots: List<RemoteBallot>,
    val comments: List<RemoteComment>
)

/**
 * Cloud sync for rooms. Local Room tables remain the app's source of truth for the UI;
 * this gateway pushes local mutations and mirrors remote state back in.
 *
 * The Firestore implementation is offline-first: writes made without a connection are
 * queued by the Firestore SDK and delivered when the device reconnects.
 */
interface RemoteSyncGateway {
    /** False when no backend is configured (no google-services.json): the app runs local-only. */
    val isEnabled: Boolean

    /** Firebase anonymous-auth uid once signed in. */
    val authUid: StateFlow<String?>

    suspend fun ensureSignedIn(): String?
    suspend fun pushRoom(room: RoomEntity, options: List<OptionEntity>)
    suspend fun pushRoomState(room: RoomEntity)
    suspend fun pushOption(option: OptionEntity)

    /** Writes (or, when [RemoteBallot.optionIds] is empty, deletes) this device's ballot. */
    suspend fun pushBallot(roomId: String, ballot: RemoteBallot)
    suspend fun clearBallots(roomId: String)
    suspend fun pushVoiceComment(comment: VoiceCommentEntity, audio: ByteArray?, audioMimeType: String?)
    suspend fun deleteVoiceComment(roomId: String, commentId: String)
    suspend fun fetchRoom(roomId: String): RemoteRoomSnapshot?
    fun observeRoom(roomId: String): Flow<RemoteRoomSnapshot>
    suspend fun fetchAudio(roomId: String, commentId: String): Pair<ByteArray, String>?
}

/** Local-only mode: everything is stored on the device and nothing leaves it. */
object NoOpRemoteSyncGateway : RemoteSyncGateway {
    override val isEnabled: Boolean = false
    override val authUid: StateFlow<String?> = MutableStateFlow(null)
    override suspend fun ensureSignedIn(): String? = null
    override suspend fun pushRoom(room: RoomEntity, options: List<OptionEntity>) = Unit
    override suspend fun pushRoomState(room: RoomEntity) = Unit
    override suspend fun pushOption(option: OptionEntity) = Unit
    override suspend fun pushBallot(roomId: String, ballot: RemoteBallot) = Unit
    override suspend fun clearBallots(roomId: String) = Unit
    override suspend fun pushVoiceComment(comment: VoiceCommentEntity, audio: ByteArray?, audioMimeType: String?) = Unit
    override suspend fun deleteVoiceComment(roomId: String, commentId: String) = Unit
    override suspend fun fetchRoom(roomId: String): RemoteRoomSnapshot? = null
    override fun observeRoom(roomId: String): Flow<RemoteRoomSnapshot> = emptyFlow()
    override suspend fun fetchAudio(roomId: String, commentId: String): Pair<ByteArray, String>? = null
}

/**
 * Returns the process-wide gateway: Firestore when Firebase is configured
 * (app/google-services.json present at build time), otherwise local-only.
 */
object RemoteSyncGatewayFactory {
    @Volatile
    private var instance: RemoteSyncGateway? = null

    fun create(context: Context): RemoteSyncGateway {
        return instance ?: synchronized(this) {
            instance ?: (FirestoreRemoteSyncGateway.createOrNull(context.applicationContext)
                ?: NoOpRemoteSyncGateway).also { instance = it }
        }
    }
}
