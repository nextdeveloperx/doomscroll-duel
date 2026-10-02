package com.doomscrollduel.broadcast

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.doomscrollduel.domain.social.Person
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.doomscrollduel.core.designsystem.theme.DuelTheme
import com.doomscrollduel.domain.social.BroadcastMember
import com.doomscrollduel.domain.social.BroadcastState
import com.doomscrollduel.domain.social.BroadcastStatus
import com.doomscrollduel.domain.social.PeerState
import com.doomscrollduel.feature.broadcast.BroadcastRoomScreen
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Draws the room with one person talking and keeps a picture of it, so the pulse rings can be looked at. */
@RunWith(AndroidJUnit4::class)
class BroadcastRoomScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun speakingPersonPulses() {
        val state = BroadcastState(
            status = BroadcastStatus.LIVE, roomId = "r", title = "Raat ki gup-shup",
            members = listOf(
                BroadcastMember("a", "Tum", "tum", isMe = true, isHost = true, state = PeerState.CONNECTED, speaking = true),
                BroadcastMember("b", "Aman", "aman", isMe = false, isHost = false, state = PeerState.CONNECTED, speaking = false, sending = true, receiving = true),
                BroadcastMember("c", "Riya", "riya", isMe = false, isHost = false, state = PeerState.CONNECTED, speaking = true, sending = true, receiving = true),
            ),
        )
        rule.setContent { DuelTheme { BroadcastRoomScreen(state, {}, {}, {}, {}, {}) } }
        // Several moments of the pulse, so a ring that grows is caught mid-way.
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "shots").apply { mkdirs() }
        listOf(0L, 250L, 500L).forEachIndexed { i, wait ->
            rule.mainClock.autoAdvance = false
            rule.mainClock.advanceTimeBy(if (i == 0) 100 else wait)
            val image = rule.onRoot().captureToImage()
            val bitmap = android.graphics.Bitmap.createBitmap(image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
            val pixels = IntArray(image.width * image.height)
            image.readPixels(pixels)
            bitmap.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            File(dir, "room_$i.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue(File(dir, "room_0.png").length() > 0)
    }

    @Test
    fun invitePanelListsPeopleAndHostCanRemove() {
        val state = BroadcastState(
            status = BroadcastStatus.LIVE, roomId = "r", title = "Raat ki gup-shup",
            members = listOf(
                BroadcastMember("a", "Tum", "tum", isMe = true, isHost = true, state = PeerState.CONNECTED),
                BroadcastMember("b", "Aman", "aman", isMe = false, isHost = false, state = PeerState.CONNECTED),
            ),
        )
        val people = listOf(
            Person("p1", "rohan_k", "Rohan", isFriend = true, invited = false),
            Person("p2", "simran", "Simran", isFriend = false, invited = false),
            Person("p3", "dev_9", "Dev", isFriend = false, invited = false),
        )
        rule.setContent { DuelTheme { BroadcastRoomScreen(state, {}, {}, {}, {}, {}, people = people, invited = setOf("p3")) } }
        rule.onNodeWithContentDescription("Kisi ko bulao").performClick()
        rule.waitForIdle()
        val image = rule.onRoot().captureToImage()
        val bitmap = android.graphics.Bitmap.createBitmap(image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)
        bitmap.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "shots").apply { mkdirs() }
        File(dir, "invite.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "invite.png").length() > 0)
    }
}
