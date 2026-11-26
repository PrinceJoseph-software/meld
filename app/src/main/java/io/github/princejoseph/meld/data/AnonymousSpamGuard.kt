package io.github.princejoseph.meld.data

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

data class NetworkLockSnapshot(
    val rawIpNormalized: String,
    val maskedIpLabel: String,
    val transportLabel: String
)

sealed class AnonymousVoteGate {
    data object Allowed : AnonymousVoteGate()
    data class BlockedDuplicateNetwork(val maskedIpLabel: String) : AnonymousVoteGate()
    data class BlockedRateLimit(val retrySeconds: Int, val maskedIpLabel: String) : AnonymousVoteGate()
}

object AnonymousSpamGuard {
    private const val RATE_WINDOW_MS = 12_000L
    private const val MAX_ACTIONS_IN_WINDOW = 6
    private const val COOLDOWN_MS = 10_000L

    fun captureCurrentNetwork(context: Context): NetworkLockSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = runCatching { cm?.activeNetwork }.getOrNull()
        val linkProps = runCatching {
            activeNetwork?.let { cm?.getLinkProperties(it) }
        }.getOrNull()

        val linkAddresses = linkProps?.linkAddresses?.mapNotNull { it.address }.orEmpty()
        val chosenFromLink = linkAddresses
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?: linkAddresses.firstOrNull { !it.isLoopbackAddress }

        val resolvedAddress = chosenFromLink ?: findFallbackInterfaceAddress()
        val rawIp = resolvedAddress?.hostAddress
            ?.substringBefore('%')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "offline-local"

        val iface = linkProps?.interfaceName?.takeIf { it.isNotBlank() } ?: "net"
        return NetworkLockSnapshot(
            rawIpNormalized = rawIp,
            maskedIpLabel = maskIpForDisplay(rawIp),
            transportLabel = iface
        )
    }

    private fun findFallbackInterfaceAddress(): java.net.InetAddress? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = interfaces
                .filter { !it.isLoopback && it.isUp }
                .flatMap { it.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress }
            candidates.firstOrNull { it is Inet4Address } ?: candidates.firstOrNull()
        }.getOrNull()
    }

    fun maskIpForDisplay(rawIp: String): String {
        if (rawIp == "offline-local" || rawIp.isBlank()) return "Local Device"
        if (rawIp.contains('.')) {
            val parts = rawIp.split('.')
            if (parts.size == 4) {
                return "${parts[0]}.${parts[1]}.x.${parts[3]}"
            }
        }
        if (rawIp.contains(':')) {
            val head = rawIp.substringBefore(':').ifEmpty { "fe80" }
            val tail = rawIp.takeLast(4)
            return "$head::x$tail"
        }
        return "Net-${rawIp.take(4)}"
    }

    fun computeNetworkHash(
        roomId: String,
        rawIpNormalized: String,
        installSalt: String
    ): String {
        val payload = "${roomId.trim().uppercase()}|${rawIpNormalized.trim()}|${installSalt.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(24)
    }

    fun evaluateAnonymousVote(
        existingLock: AnonymousLockEntity?,
        voterUid: String,
        nowMs: Long
    ): AnonymousVoteGate {
        if (existingLock == null) return AnonymousVoteGate.Allowed

        if (existingLock.lockedVoterUid.isNotBlank() && existingLock.lockedVoterUid != voterUid) {
            return AnonymousVoteGate.BlockedDuplicateNetwork(existingLock.maskedIpLabel)
        }

        if (existingLock.cooldownUntilMs > nowMs) {
            val secs = (((existingLock.cooldownUntilMs - nowMs) + 999L) / 1000L).toInt().coerceAtLeast(1)
            return AnonymousVoteGate.BlockedRateLimit(secs, existingLock.maskedIpLabel)
        }

        val elapsedSinceWindow = nowMs - existingLock.windowStartEpochMs
        if (elapsedSinceWindow <= RATE_WINDOW_MS && existingLock.actionCountInWindow >= MAX_ACTIONS_IN_WINDOW) {
            return AnonymousVoteGate.BlockedRateLimit(
                retrySeconds = (COOLDOWN_MS / 1000L).toInt(),
                maskedIpLabel = existingLock.maskedIpLabel
            )
        }

        return AnonymousVoteGate.Allowed
    }

    fun nextLockRecord(
        roomId: String,
        networkHash: String,
        maskedIpLabel: String,
        voterUid: String,
        existingLock: AnonymousLockEntity?,
        nowMs: Long
    ): AnonymousLockEntity {
        val lockKey = "$roomId:$networkHash"
        if (existingLock == null) {
            return AnonymousLockEntity(
                lockKey = lockKey,
                roomId = roomId,
                networkHash = networkHash,
                maskedIpLabel = maskedIpLabel,
                lockedVoterUid = voterUid,
                windowStartEpochMs = nowMs,
                actionCountInWindow = 1,
                cooldownUntilMs = 0L,
                lockedAtEpochMs = nowMs
            )
        }

        val resetWindow = (nowMs - existingLock.windowStartEpochMs) > RATE_WINDOW_MS
        val nextCount = if (resetWindow) 1 else existingLock.actionCountInWindow + 1
        val nextWindowStart = if (resetWindow) nowMs else existingLock.windowStartEpochMs
        val nextCooldown = if (nextCount >= MAX_ACTIONS_IN_WINDOW) nowMs + COOLDOWN_MS else 0L

        return existingLock.copy(
            maskedIpLabel = maskedIpLabel,
            windowStartEpochMs = nextWindowStart,
            actionCountInWindow = nextCount,
            cooldownUntilMs = nextCooldown
        )
    }
}
