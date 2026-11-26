package io.github.princejoseph.meld.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.princejoseph.meld.ui.theme.VoterBadgeColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.meldDataStore: DataStore<Preferences> by preferencesDataStore(name = "meld_identity")

class IdentityStore(private val context: Context) {
    private object Keys {
        val MELD_UID = stringPreferencesKey("meld_uid")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val BADGE_COLOR_INDEX = intPreferencesKey("badge_color_index")
        val AVATAR_STYLE_INDEX = intPreferencesKey("avatar_style_index")
        val DARK_THEME_OVERRIDE = booleanPreferencesKey("dark_theme_override")
        val HAS_THEME_OVERRIDE = booleanPreferencesKey("has_theme_override")
        val CREATOR_TOKENS = stringSetPreferencesKey("creator_tokens")
        val NETWORK_SALT = stringPreferencesKey("network_lock_salt")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val ANONYMOUS_AVATAR_STYLE_INDEX = intPreferencesKey("anonymous_avatar_style_index")
        val ANONYMOUS_BADGE_COLOR_INDEX = intPreferencesKey("anonymous_badge_color_index")
        val ANONYMOUS_MASK_NAME = stringPreferencesKey("anonymous_mask_name")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    val selectedLanguageFlow: Flow<String> = context.meldDataStore.data.map { prefs ->
        prefs[Keys.SELECTED_LANGUAGE] ?: "en"
    }

    val hapticsEnabledFlow: Flow<Boolean> = context.meldDataStore.data.map { prefs ->
        prefs[Keys.HAPTICS_ENABLED] ?: true
    }

    val identityFlow: Flow<VoterIdentity> = context.meldDataStore.data.map { prefs ->
        val uid = prefs[Keys.MELD_UID] ?: ""
        val name = prefs[Keys.DISPLAY_NAME] ?: ""
        val colorIdx = prefs[Keys.BADGE_COLOR_INDEX]
            ?: badgeColorIndexForUid(uid, VoterBadgeColors.size)
        val avatarIdx = prefs[Keys.AVATAR_STYLE_INDEX]
            ?: avatarStyleIndexForUid(uid)
        val anonAvatarIdx = prefs[Keys.ANONYMOUS_AVATAR_STYLE_INDEX]
            ?: AvatarSpec(chassis = 4, eyes = 4, accessory = 5, pattern = 3, frame = 1).toPacked()
        val anonColorIdx = prefs[Keys.ANONYMOUS_BADGE_COLOR_INDEX] ?: 4
        val anonMask = prefs[Keys.ANONYMOUS_MASK_NAME] ?: "Agent ${(Math.abs(uid.hashCode()) % 89 + 10)}"

        VoterIdentity(
            uid = uid,
            displayName = name,
            badgeColorIndex = colorIdx,
            avatarStyleIndex = avatarIdx,
            anonymousAvatarStyleIndex = anonAvatarIdx,
            anonymousBadgeColorIndex = anonColorIdx,
            anonymousMaskName = anonMask
        )
    }

    val darkThemeOverrideFlow: Flow<Boolean?> = context.meldDataStore.data.map { prefs ->
        if (prefs[Keys.HAS_THEME_OVERRIDE] == true) {
            prefs[Keys.DARK_THEME_OVERRIDE] ?: false
        } else {
            null
        }
    }

    val creatorRoomIdsFlow: Flow<Set<String>> = context.meldDataStore.data.map { prefs ->
        (prefs[Keys.CREATOR_TOKENS] ?: emptySet())
            .mapNotNull { entry -> entry.substringBefore(':').takeIf { it.isNotBlank() } }
            .toSet()
    }

    suspend fun ensureIdentity(): VoterIdentity {
        var resolved: VoterIdentity? = null
        context.meldDataStore.edit { prefs ->
            val existingUid = prefs[Keys.MELD_UID]
            val uid = if (existingUid.isNullOrBlank()) {
                UUID.randomUUID().toString().also { prefs[Keys.MELD_UID] = it }
            } else {
                existingUid
            }
            val colorIdx = prefs[Keys.BADGE_COLOR_INDEX]
                ?: badgeColorIndexForUid(uid, VoterBadgeColors.size).also {
                    prefs[Keys.BADGE_COLOR_INDEX] = it
                }
            val avatarIdx = prefs[Keys.AVATAR_STYLE_INDEX]
                ?: avatarStyleIndexForUid(uid).also {
                    prefs[Keys.AVATAR_STYLE_INDEX] = it
                }
            val salt = prefs[Keys.NETWORK_SALT]
            if (salt.isNullOrBlank()) {
                prefs[Keys.NETWORK_SALT] = UUID.randomUUID().toString()
            }
            val name = prefs[Keys.DISPLAY_NAME] ?: ""
            val anonAvatarIdx = prefs[Keys.ANONYMOUS_AVATAR_STYLE_INDEX]
                ?: AvatarSpec(chassis = 4, eyes = 4, accessory = 5, pattern = 3, frame = 1).toPacked()
            val anonColorIdx = prefs[Keys.ANONYMOUS_BADGE_COLOR_INDEX] ?: 4
            val anonMask = prefs[Keys.ANONYMOUS_MASK_NAME] ?: "Agent ${(Math.abs(uid.hashCode()) % 89 + 10)}"

            resolved = VoterIdentity(
                uid = uid,
                displayName = name,
                badgeColorIndex = colorIdx,
                avatarStyleIndex = avatarIdx,
                anonymousAvatarStyleIndex = anonAvatarIdx,
                anonymousBadgeColorIndex = anonColorIdx,
                anonymousMaskName = anonMask
            )
        }
        return resolved ?: identityFlow.first()
    }

    suspend fun getOrCreateNetworkSalt(): String {
        var resolved = ""
        context.meldDataStore.edit { prefs ->
            val existing = prefs[Keys.NETWORK_SALT]
            resolved = if (existing.isNullOrBlank()) {
                UUID.randomUUID().toString().also { prefs[Keys.NETWORK_SALT] = it }
            } else {
                existing
            }
        }
        return resolved
    }

    suspend fun setDisplayName(name: String) {
        val cleaned = sanitizeMeldInput(name, maxLength = 28)
        context.meldDataStore.edit { prefs ->
            prefs[Keys.DISPLAY_NAME] = cleaned
        }
    }

    suspend fun setAvatarStyleAndColor(avatarStyleIndex: Int, badgeColorIndex: Int) {
        val normalizedSpec = AvatarSpec.fromPacked(avatarStyleIndex).toPacked()
        context.meldDataStore.edit { prefs ->
            prefs[Keys.AVATAR_STYLE_INDEX] = normalizedSpec
            prefs[Keys.BADGE_COLOR_INDEX] = badgeColorIndex.coerceIn(0, VoterBadgeColors.lastIndex)
        }
    }

    suspend fun setAnonymousBadge(avatarStyleIndex: Int, badgeColorIndex: Int, maskName: String) {
        val normalizedSpec = AvatarSpec.fromPacked(avatarStyleIndex).toPacked()
        val cleanMask = sanitizeMeldInput(maskName, maxLength = 24).ifEmpty { "Incognito" }
        context.meldDataStore.edit { prefs ->
            prefs[Keys.ANONYMOUS_AVATAR_STYLE_INDEX] = normalizedSpec
            prefs[Keys.ANONYMOUS_BADGE_COLOR_INDEX] = badgeColorIndex.coerceIn(0, VoterBadgeColors.lastIndex)
            prefs[Keys.ANONYMOUS_MASK_NAME] = cleanMask
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.meldDataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.meldDataStore.edit { prefs ->
            prefs[Keys.HAS_THEME_OVERRIDE] = true
            prefs[Keys.DARK_THEME_OVERRIDE] = isDark
        }
    }

    suspend fun setLanguage(lang: String) {
        context.meldDataStore.edit { prefs ->
            prefs[Keys.SELECTED_LANGUAGE] = lang
        }
    }

    suspend fun saveCreatorToken(roomId: String, token: String) {
        val safeRoomId = sanitizeRoomCode(roomId) ?: return
        val safeToken = token.trim()
        if (safeToken.isEmpty()) return
        context.meldDataStore.edit { prefs ->
            val current = prefs[Keys.CREATOR_TOKENS]?.toMutableSet() ?: mutableSetOf()
            current.removeAll { it.startsWith("$safeRoomId:") }
            current.add("$safeRoomId:$safeToken")
            prefs[Keys.CREATOR_TOKENS] = current
        }
    }

    suspend fun hasValidCreatorToken(roomId: String, expectedToken: String): Boolean {
        val safeRoomId = sanitizeRoomCode(roomId) ?: return false
        val safeExpected = expectedToken.trim()
        if (safeExpected.isEmpty()) return false
        val tokens = context.meldDataStore.data.first()[Keys.CREATOR_TOKENS] ?: return false
        val expectedBytes = safeExpected.toByteArray(Charsets.UTF_8)
        return tokens.any { entry ->
            val sep = entry.indexOf(':')
            if (sep <= 0) return@any false
            val entryRoom = entry.substring(0, sep)
            val entryToken = entry.substring(sep + 1)
            entryRoom == safeRoomId &&
                java.security.MessageDigest.isEqual(
                    entryToken.toByteArray(Charsets.UTF_8),
                    expectedBytes
                )
        }
    }
}
