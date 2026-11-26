package io.github.princejoseph.meld

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.github.princejoseph.meld.data.OptionUiModel
import io.github.princejoseph.meld.data.VoiceCommentUiModel
import io.github.princejoseph.meld.data.VoterIdentity
import io.github.princejoseph.meld.ui.components.SettingsSheet
import io.github.princejoseph.meld.ui.components.VoiceCommentsSection
import io.github.princejoseph.meld.ui.theme.MeldAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeldComposeUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `voice comments section allows typed fallback opinion and hides controls when locked`() {
        var addedTranscript: String? = null
        var addedOptionId: String? = "UNSET"

        val sampleOptions = listOf(
            OptionUiModel(
                id = "opt_1",
                label = "Ramen",
                sortOrder = 0,
                voteCount = 2,
                percentage = 100,
                fraction = 1f,
                isVotedByMe = true,
                isPendingSync = false,
                isLeading = true,
                isWinner = false,
                voters = emptyList()
            )
        )

        composeRule.setContent {
            MeldAppTheme {
                VoiceCommentsSection(
                    comments = emptyList(),
                    options = sampleOptions,
                    isRoomLocked = false,
                    onAddComment = { optId, transcript, _ ->
                        addedOptionId = optId
                        addedTranscript = transcript
                    },
                    onDeleteComment = {}
                )
            }
        }

        composeRule.onNodeWithTag("voice_comments_section").assertIsDisplayed()
        composeRule.onNodeWithTag("type_voice_comment_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Speak your thought clearly").assertExists()
        composeRule.onNodeWithTag("voice_comment_fallback_input").performTextInput("Extra chashu please!")
        composeRule.onNodeWithText("Add").assertIsDisplayed().performClick()

        assertEquals(null, addedOptionId)
        assertEquals("Extra chashu please!", addedTranscript)
    }

    @Test
    fun `voice comments section renders cards and respects locked state`() {
        var deletedId: String? = null
        val comments = listOf(
            VoiceCommentUiModel(
                id = "vc_1",
                roomId = "ROOM01",
                optionId = "opt_1",
                optionLabel = "Tacos",
                voterUid = "u1",
                voterName = "Agent 07",
                voterBadgeColorIndex = 0,
                voterAvatarStyleIndex = 0,
                transcript = "Best salsa in town",
                durationSeconds = 5,
                isMyComment = true
            )
        )

        composeRule.setContent {
            MeldAppTheme {
                VoiceCommentsSection(
                    comments = comments,
                    options = emptyList(),
                    isRoomLocked = true,
                    onAddComment = { _, _, _ -> },
                    onDeleteComment = { deletedId = it }
                )
            }
        }

        composeRule.onNodeWithTag("voice_comment_vc_1").assertIsDisplayed()
        composeRule.onNodeWithText("\"Best salsa in town\"").assertIsDisplayed()
        composeRule.onNodeWithText("On: Tacos").assertIsDisplayed()
        composeRule.onNodeWithTag("record_voice_comment_button").assertDoesNotExist()
        composeRule.onNodeWithTag("type_voice_comment_button").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Delete comment").performClick()
        assertEquals("vc_1", deletedId)
    }

    @Test
    fun `settings sheet unified studio exposes profile modes, tabs, history, theme, and language`() {
        var savedStdAvatar = -1
        var savedStdColor = -1
        var savedAnonAvatar = -1
        var savedAnonColor = -1
        var savedMask = ""

        val identity = VoterIdentity(
            uid = "u1",
            displayName = "Alex",
            badgeColorIndex = 0,
            avatarStyleIndex = 0,
            anonymousAvatarStyleIndex = 0,
            anonymousBadgeColorIndex = 4,
            anonymousMaskName = "Cipher"
        )

        composeRule.setContent {
            MeldAppTheme {
                SettingsSheet(
                    identity = identity,
                    isDarkTheme = false,
                    hapticsEnabled = true,
                    recentRoomsCount = 2,
                    currentLanguageCode = "en",
                    onSaveStandardProfile = { _, avatar, color ->
                        savedStdAvatar = avatar
                        savedStdColor = color
                    },
                    onSaveAnonymousBadge = { avatar, color, mask ->
                        savedAnonAvatar = avatar
                        savedAnonColor = color
                        savedMask = mask
                    },
                    onToggleDarkTheme = {},
                    onToggleHaptics = {},
                    onOpenHistory = {},
                    onOpenLanguageSelector = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("anonymous_badge_studio_card").assertIsDisplayed()
        composeRule.onNodeWithText("My Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Anonymous Mask").assertIsDisplayed().performClick()

        composeRule.onNodeWithText("Shape").assertIsDisplayed()
        composeRule.onNodeWithText("Palette").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Vermilion palette").assertExists()
        composeRule.onNodeWithText("Pattern").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Diagonal Stripes").assertExists()
        composeRule.onNodeWithText("Eyes").assertExists()
        composeRule.onNodeWithText("Gear").assertExists()
        composeRule.onNodeWithText("Frame").assertExists()

        composeRule.onNodeWithTag("settings_history_row").assertExists()
        composeRule.onNodeWithTag("settings_theme_row").assertExists()
        composeRule.onNodeWithTag("settings_language_row").assertExists()

        composeRule.onNodeWithTag("anon_shuffle_button").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        assertTrue(savedStdAvatar >= 0)
        assertTrue(savedStdColor >= 0)
        assertTrue(savedAnonAvatar >= 0)
        assertTrue(savedAnonColor >= 0)
        assertTrue(savedMask.isNotBlank())
    }
}
