package io.github.princejoseph.meld.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Firestore implementation of [RemoteSyncGateway]. The data model is shared with the web app
 * (see docs/DATA_MODEL.md and firestore.rules):
 *
 * rooms/{code}                     room settings + ownerUid (anonymous-auth uid of the creator)
 * rooms/{code}/options/{optionId}  label, sortOrder, slotStartMs, slotEndMs
 * rooms/{code}/ballots/{authUid}   one doc per voter: optionIds + display identity
 * rooms/{code}/comments/{id}       typed/voice opinions (metadata)
 * rooms/{code}/audio/{id}          voice-note audio as base64 (fetched on demand)
 *
 * Writes are fire-and-forget: Firestore applies them to its local cache immediately and
 * delivers them when online, so the app keeps working offline. Awaiting a write would hang
 * until the server acknowledges it.
 */
class FirestoreRemoteSyncGateway private constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : RemoteSyncGateway {

    override val isEnabled: Boolean = true

    private val _authUid = MutableStateFlow(auth.currentUser?.uid)
    override val authUid: StateFlow<String?> = _authUid.asStateFlow()

    init {
        auth.addAuthStateListener { _authUid.value = it.currentUser?.uid }
    }

    override suspend fun ensureSignedIn(): String? {
        auth.currentUser?.uid?.let { return it }
        return withTimeoutOrNull(SIGN_IN_TIMEOUT_MS) {
            runCatching { auth.signInAnonymously().await().user?.uid }
                .onFailure { Log.w(TAG, "Anonymous sign-in failed", it) }
                .getOrNull()
        }?.also { _authUid.value = it }
    }

    private fun room(roomId: String) = db.collection(ROOMS).document(roomId)

    override suspend fun pushRoom(room: RoomEntity, options: List<OptionEntity>) {
        val uid = ensureSignedIn() ?: return
        val ownerUid = room.ownerUid ?: uid
        if (ownerUid != uid) return // only the owner writes room settings
        val batch = db.batch()
        batch.set(room(room.id), roomToMap(room, ownerUid), SetOptions.merge())
        options.forEach { opt ->
            batch.set(room(room.id).collection(OPTIONS).document(opt.id), optionToMap(opt, uid), SetOptions.merge())
        }
        batch.commit().addOnFailureListener { Log.w(TAG, "pushRoom ${room.id} failed", it) }
        val seriesId = room.seriesId
        if (room.repeatWeekly && seriesId != null) {
            // Created once; later pushes are rejected by the rules (only the server advances it).
            db.collection(SERIES).document(seriesId).set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "latestCode" to seriesId,
                    "active" to true,
                    "intervalDays" to 7,
                    "nextRunAt" to room.createdAtEpochMs + 7L * 24 * 3_600_000,
                    "createdAtEpochMs" to room.createdAtEpochMs
                )
            )
        }
    }

    override suspend fun pushRoomState(room: RoomEntity) {
        val uid = ensureSignedIn() ?: return
        if (room.ownerUid != null && room.ownerUid != uid) return
        room(room.id).set(
            mapOf(
                "isLocked" to room.isLocked,
                "winnerOptionId" to room.winnerOptionId,
                "deadlineEpochMs" to room.deadlineEpochMs,
                "updatedAtEpochMs" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).addOnFailureListener { Log.w(TAG, "pushRoomState ${room.id} failed", it) }
    }

    override suspend fun pushOption(option: OptionEntity) {
        val uid = ensureSignedIn() ?: return
        room(option.roomId).collection(OPTIONS).document(option.id)
            .set(optionToMap(option, uid), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "pushOption failed", it) }
    }

    override suspend fun pushBallot(roomId: String, ballot: RemoteBallot) {
        val uid = ensureSignedIn() ?: return
        val ref = room(roomId).collection(BALLOTS).document(uid)
        val task = if (ballot.optionIds.isEmpty()) {
            ref.delete()
        } else {
            ref.set(
                mapOf(
                    "voterUid" to ballot.voterUid,
                    "voterName" to ballot.voterName,
                    "badgeColorIndex" to ballot.badgeColorIndex,
                    "avatarStyleIndex" to ballot.avatarStyleIndex,
                    "optionIds" to ballot.optionIds,
                    "updatedAtEpochMs" to ballot.updatedAtEpochMs
                )
            )
        }
        task.addOnFailureListener { Log.w(TAG, "pushBallot $roomId failed", it) }
    }

    override suspend fun clearBallots(roomId: String) {
        ensureSignedIn() ?: return
        val snapshot = runCatching { room(roomId).collection(BALLOTS).get().await() }.getOrNull() ?: return
        val batch = db.batch()
        snapshot.documents.forEach { batch.delete(it.reference) }
        batch.commit().addOnFailureListener { Log.w(TAG, "clearBallots $roomId failed", it) }
    }

    override suspend fun pushVoiceComment(comment: VoiceCommentEntity, audio: ByteArray?, audioMimeType: String?) {
        val uid = ensureSignedIn() ?: return
        val encoded = audio?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            ?.takeIf { it.length <= MAX_AUDIO_BASE64_CHARS }
        val batch = db.batch()
        val roomRef = room(comment.roomId)
        batch.set(
            roomRef.collection(COMMENTS).document(comment.id),
            mapOf(
                "authorAuthUid" to uid,
                "voterUid" to comment.voterUid,
                "voterName" to comment.voterName,
                "badgeColorIndex" to comment.voterBadgeColorIndex,
                "avatarStyleIndex" to comment.voterAvatarStyleIndex,
                "optionId" to comment.optionId,
                "transcript" to comment.transcript,
                "durationSeconds" to comment.durationSeconds,
                "createdAtEpochMs" to comment.createdAtEpochMs,
                "hasAudio" to (encoded != null),
                "audioMimeType" to if (encoded != null) audioMimeType else null
            )
        )
        if (encoded != null) {
            batch.set(
                roomRef.collection(AUDIO).document(comment.id),
                mapOf(
                    "authorAuthUid" to uid,
                    "mimeType" to (audioMimeType ?: "audio/mp4"),
                    "dataBase64" to encoded
                )
            )
        }
        batch.commit().addOnFailureListener { Log.w(TAG, "pushVoiceComment failed", it) }
    }

    override suspend fun deleteVoiceComment(roomId: String, commentId: String) {
        ensureSignedIn() ?: return
        val batch = db.batch()
        batch.delete(room(roomId).collection(COMMENTS).document(commentId))
        batch.delete(room(roomId).collection(AUDIO).document(commentId))
        batch.commit().addOnFailureListener { Log.w(TAG, "deleteVoiceComment failed", it) }
    }

    override suspend fun fetchRoom(roomId: String): RemoteRoomSnapshot? {
        ensureSignedIn() ?: return null
        return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            runCatching {
                val roomDoc = room(roomId).get().await()
                if (!roomDoc.exists()) return@runCatching null
                val options = room(roomId).collection(OPTIONS).get().await()
                val ballots = room(roomId).collection(BALLOTS).get().await()
                val comments = room(roomId).collection(COMMENTS).get().await()
                buildSnapshot(roomId, roomDoc, options, ballots, comments)
            }.onFailure { Log.w(TAG, "fetchRoom $roomId failed", it) }.getOrNull()
        }
    }

    override fun observeRoom(roomId: String): Flow<RemoteRoomSnapshot> = callbackFlow {
        if (ensureSignedIn() == null) {
            close()
            return@callbackFlow
        }
        var roomDoc: DocumentSnapshot? = null
        var options: QuerySnapshot? = null
        var ballots: QuerySnapshot? = null
        var comments: QuerySnapshot? = null

        fun emitIfReady() {
            val r = roomDoc ?: return
            val o = options ?: return
            val b = ballots ?: return
            val c = comments ?: return
            if (!r.exists()) return
            buildSnapshot(roomId, r, o, b, c)?.let { trySend(it) }
        }

        val ref = room(roomId)
        val registrations = mutableListOf<ListenerRegistration>()
        registrations += ref.addSnapshotListener { snap, err ->
            if (err != null) Log.w(TAG, "room listener", err)
            snap?.let { roomDoc = it; emitIfReady() }
        }
        registrations += ref.collection(OPTIONS).addSnapshotListener { snap, err ->
            if (err != null) Log.w(TAG, "options listener", err)
            snap?.let { options = it; emitIfReady() }
        }
        registrations += ref.collection(BALLOTS).addSnapshotListener { snap, err ->
            if (err != null) Log.w(TAG, "ballots listener", err)
            snap?.let { ballots = it; emitIfReady() }
        }
        registrations += ref.collection(COMMENTS).addSnapshotListener { snap, err ->
            if (err != null) Log.w(TAG, "comments listener", err)
            snap?.let { comments = it; emitIfReady() }
        }
        awaitClose { registrations.forEach { it.remove() } }
    }

    override suspend fun fetchAudio(roomId: String, commentId: String): Pair<ByteArray, String>? {
        ensureSignedIn() ?: return null
        return withTimeoutOrNull(FETCH_TIMEOUT_MS) {
            runCatching {
                val doc = room(roomId).collection(AUDIO).document(commentId).get().await()
                val data = doc.getString("dataBase64") ?: return@runCatching null
                Base64.decode(data, Base64.DEFAULT) to (doc.getString("mimeType") ?: "audio/mp4")
            }.getOrNull()
        }
    }

    private fun buildSnapshot(
        roomId: String,
        roomDoc: DocumentSnapshot,
        options: QuerySnapshot,
        ballots: QuerySnapshot,
        comments: QuerySnapshot
    ): RemoteRoomSnapshot? {
        val room = RoomEntity(
            id = roomId,
            question = sanitizeMeldInput(roomDoc.getString("question") ?: return null, 200),
            mode = roomDoc.getString("mode")?.takeIf { m -> RoomMode.entries.any { it.name == m } }
                ?: RoomMode.QUICK_POLL.name,
            allowMultiple = roomDoc.getBoolean("allowMultiple") ?: false,
            allowFriendsToAdd = roomDoc.getBoolean("allowFriendsToAdd") ?: false,
            isAnonymous = roomDoc.getBoolean("isAnonymous") ?: false,
            deadlineEpochMs = roomDoc.getLong("deadlineEpochMs"),
            creatorToken = "",
            winnerOptionId = roomDoc.getString("winnerOptionId"),
            isLocked = roomDoc.getBoolean("isLocked") ?: false,
            createdAtEpochMs = roomDoc.getLong("createdAtEpochMs") ?: System.currentTimeMillis(),
            expiresAtEpochMs = roomDoc.getLong("expiresAtEpochMs") ?: System.currentTimeMillis(),
            ownerUid = roomDoc.getString("ownerUid"),
            isRanked = roomDoc.getBoolean("isRanked") ?: false,
            repeatWeekly = roomDoc.getBoolean("repeatWeekly") ?: false,
            seriesId = roomDoc.getString("seriesId"),
            nextRoomCode = roomDoc.getString("nextRoomCode")
        )
        val optionEntities = options.documents.mapNotNull { d ->
            OptionEntity(
                id = d.id,
                roomId = roomId,
                label = sanitizeMeldInput(d.getString("label") ?: return@mapNotNull null, 140),
                sortOrder = (d.getLong("sortOrder") ?: 0L).toInt(),
                slotStartMs = d.getLong("slotStartMs"),
                slotEndMs = d.getLong("slotEndMs")
            )
        }
        val validOptionIds = optionEntities.map { it.id }.toSet()
        val ballotList = ballots.documents.mapNotNull { d ->
            @Suppress("UNCHECKED_CAST")
            val ids = (d.get("optionIds") as? List<Any?>).orEmpty()
                .filterIsInstance<String>()
                .filter { it in validOptionIds }
                .distinct()
            RemoteBallot(
                voterUid = d.getString("voterUid") ?: d.id,
                voterName = sanitizeMeldInput(d.getString("voterName") ?: "Guest", 28),
                badgeColorIndex = (d.getLong("badgeColorIndex") ?: 0L).toInt(),
                avatarStyleIndex = (d.getLong("avatarStyleIndex") ?: 0L).toInt(),
                // Ranked rooms: the list order is the voter's ranking (best first).
                optionIds = if (room.allowMultiple || room.isRanked) ids else ids.take(1),
                updatedAtEpochMs = d.getLong("updatedAtEpochMs") ?: 0L
            )
        }
        val commentList = comments.documents.mapNotNull { d ->
            RemoteComment(
                comment = VoiceCommentEntity(
                    id = d.id,
                    roomId = roomId,
                    optionId = d.getString("optionId")?.takeIf { it in validOptionIds },
                    voterUid = d.getString("voterUid") ?: return@mapNotNull null,
                    voterName = sanitizeMeldInput(d.getString("voterName") ?: "Guest", 28),
                    voterBadgeColorIndex = (d.getLong("badgeColorIndex") ?: 0L).toInt(),
                    voterAvatarStyleIndex = (d.getLong("avatarStyleIndex") ?: 0L).toInt(),
                    transcript = sanitizeMeldInput(d.getString("transcript") ?: "", 300),
                    durationSeconds = (d.getLong("durationSeconds") ?: 1L).toInt().coerceIn(1, 60),
                    createdAtEpochMs = d.getLong("createdAtEpochMs") ?: 0L,
                    audioFilePath = null
                ),
                hasAudio = d.getBoolean("hasAudio") ?: false,
                audioMimeType = d.getString("audioMimeType")
            )
        }
        return RemoteRoomSnapshot(room, optionEntities, ballotList, commentList)
    }

    private fun roomToMap(room: RoomEntity, ownerUid: String): Map<String, Any?> = mapOf(
        "question" to room.question,
        "mode" to room.mode,
        "allowMultiple" to room.allowMultiple,
        "allowFriendsToAdd" to room.allowFriendsToAdd,
        "isAnonymous" to room.isAnonymous,
        "deadlineEpochMs" to room.deadlineEpochMs,
        "ownerUid" to ownerUid,
        "winnerOptionId" to room.winnerOptionId,
        "isLocked" to room.isLocked,
        "createdAtEpochMs" to room.createdAtEpochMs,
        "expiresAtEpochMs" to room.expiresAtEpochMs,
        "updatedAtEpochMs" to System.currentTimeMillis(),
        "isRanked" to room.isRanked,
        "repeatWeekly" to room.repeatWeekly,
        "seriesId" to room.seriesId,
        "source" to "app",
        "schemaVersion" to SCHEMA_VERSION
    )

    private fun optionToMap(option: OptionEntity, uid: String): Map<String, Any?> = mapOf(
        "label" to option.label,
        "sortOrder" to option.sortOrder,
        "slotStartMs" to option.slotStartMs,
        "slotEndMs" to option.slotEndMs,
        "createdByUid" to uid
    )

    companion object {
        private const val TAG = "MeldSync"
        private const val ROOMS = "rooms"
        private const val OPTIONS = "options"
        private const val BALLOTS = "ballots"
        private const val COMMENTS = "comments"
        private const val AUDIO = "audio"
        private const val SERIES = "series"
        private const val SCHEMA_VERSION = 1
        private const val SIGN_IN_TIMEOUT_MS = 10_000L
        private const val FETCH_TIMEOUT_MS = 12_000L

        /** Firestore documents are capped at 1 MiB; keep audio well below it. */
        const val MAX_AUDIO_BASE64_CHARS = 700_000

        fun createOrNull(context: Context): FirestoreRemoteSyncGateway? {
            val app = FirebaseApp.getApps(context).firstOrNull()
                ?: runCatching { FirebaseApp.initializeApp(context) }.getOrNull()
                ?: return null
            return runCatching {
                FirestoreRemoteSyncGateway(FirebaseFirestore.getInstance(app), FirebaseAuth.getInstance(app))
            }.onFailure { Log.w(TAG, "Firebase unavailable, running local-only", it) }.getOrNull()
        }
    }
}
