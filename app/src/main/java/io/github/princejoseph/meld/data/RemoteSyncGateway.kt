package io.github.princejoseph.meld.data

import android.content.Context

/**
 * Abstraction for syncing local Room mutations to a remote multi-device backend.
 *
 * Production implementations (e.g. Firestore, Supabase) live in a separate module or
 * behind a build variant so the core app compiles without backend SDKs.
 */
interface RemoteSyncGateway {
    suspend fun pushRoom(room: RoomEntity, options: List<OptionEntity>)
    suspend fun pushVote(vote: VoteEntity, allowMultiple: Boolean)
    suspend fun removeVote(roomId: String, optionId: String, voterUid: String)
    suspend fun pushVoiceComment(comment: VoiceCommentEntity)
    suspend fun deleteVoiceComment(roomId: String, commentId: String)
}

/**
 * Default no-op gateway used when no remote backend is configured.
 * All mutations are stored locally and can be synced later when a backend is wired up.
 */
object NoOpRemoteSyncGateway : RemoteSyncGateway {
    override suspend fun pushRoom(room: RoomEntity, options: List<OptionEntity>) = Unit
    override suspend fun pushVote(vote: VoteEntity, allowMultiple: Boolean) = Unit
    override suspend fun removeVote(roomId: String, optionId: String, voterUid: String) = Unit
    override suspend fun pushVoiceComment(comment: VoiceCommentEntity) = Unit
    override suspend fun deleteVoiceComment(roomId: String, commentId: String) = Unit
}

/**
 * Factory that returns the appropriate [RemoteSyncGateway] for the current build.
 *
 * When Firebase dependencies are on the classpath and `google-services.json` is present,
 * this will attempt to load the Firestore-backed implementation via reflection. Otherwise
 * it falls back to [NoOpRemoteSyncGateway], keeping the app fully functional offline.
 *
 * To enable Firestore sync:
 *   1. Uncomment `firebase-bom` and `firebase-firestore` in `app/build.gradle.kts`
 *   2. Add `google-services.json` to `app/`
 *   3. Create `FirestoreRemoteSyncGateway` implementing [RemoteSyncGateway]
 */
object RemoteSyncGatewayFactory {
    fun create(context: Context): RemoteSyncGateway {
        return try {
            val clazz = Class.forName(
                "io.github.princejoseph.meld.data.FirestoreRemoteSyncGateway"
            )
            clazz.getConstructor(Context::class.java).newInstance(context) as RemoteSyncGateway
        } catch (_: Exception) {
            NoOpRemoteSyncGateway
        }
    }
}
