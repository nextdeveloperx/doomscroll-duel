package com.doomscrollduel.broadcast

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.doomscrollduel.MainActivity
import androidx.test.platform.app.InstrumentationRegistry
import com.doomscrollduel.data.broadcast.Signal
import com.doomscrollduel.data.broadcast.VoiceMesh
import com.doomscrollduel.domain.social.PeerState
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two voice engines in one process, joined by a direct in-memory "signalling" link instead of Firestore. They must find
 * each other, connect, and pass chat lines both ways; this is the part of Broadcast that can be proved without two phones.
 * (Audio itself is not asserted here: it needs a microphone and a person.)
 */
@RunWith(AndroidJUnit4::class)
class VoiceMeshLoopbackTest {
    @Test
    fun twoPeersConnectAndChat() {
        // The microphone only returns sound to an app that is on screen, so the app's own screen is open during the test.
        ActivityScenario.launch(MainActivity::class.java).use { runCall() }
    }

    private fun runCall() = runBlocking {
        delay(2_000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // "aaaa" < "bbbb", so A makes the offer.
        val a = "aaaa"
        val b = "bbbb"
        val stateAtA = ConcurrentHashMap<String, PeerState>()
        val stateAtB = ConcurrentHashMap<String, PeerState>()
        val flowAtA = ConcurrentHashMap<String, Pair<Boolean, Boolean>>()
        val chatAtA = CompletableDeferred<String>()
        val chatAtB = CompletableDeferred<String>()
        val heardAtA = ConcurrentHashMap<String, Boolean>()
        val heardAtB = ConcurrentHashMap<String, Boolean>()
        lateinit var meshA: VoiceMesh
        lateinit var meshB: VoiceMesh
        meshA = VoiceMesh(
            context = context, scope = scope, me = a,
            sendSignal = { _, signal -> scope.launch { meshB.onSignal(a, signal) } },
            onPeerState = { uid, state -> stateAtA[uid] = state },
            onChat = { _, _, text -> chatAtA.complete(text) },
            onSpeaking = { uid, on -> if (on) heardAtA[uid] = true },
            onFlow = { uid, sending, receiving -> flowAtA[uid] = sending to receiving },
        )
        meshB = VoiceMesh(
            context = context, scope = scope, me = b,
            sendSignal = { _, signal -> scope.launch { meshA.onSignal(b, signal) } },
            onPeerState = { uid, state -> stateAtB[uid] = state },
            onChat = { _, _, text -> chatAtB.complete(text) },
            onSpeaking = { uid, on -> if (on) heardAtB[uid] = true },
        )
        meshA.reconcile(setOf(b))
        meshB.reconcile(setOf(a))

        withTimeout(40_000) {
            while (stateAtA[b] != PeerState.CONNECTED || stateAtB[a] != PeerState.CONNECTED) delay(200)
        }
        withTimeout(15_000) { while (meshA.sendChat("namaste", "A") == 0) delay(200) }
        assertEquals("namaste", withTimeout(5_000) { chatAtB.await() })
        withTimeout(15_000) { while (meshB.sendChat("hello", "B") == 0) delay(200) }
        assertEquals("hello", withTimeout(5_000) { chatAtA.await() })
        // Audio packets should be flowing in both directions once connected (silence is still sent).
        withTimeout(15_000) { while (flowAtA[b]?.first != true || flowAtA[b]?.second != true) delay(300) }
        assertTrue(flowAtA[b]?.first == true && flowAtA[b]?.second == true)

        meshA.close()
        meshB.close()
        scope.cancel()
    }

    /** A and C are never connected to each other; a line written by A must still reach C through B. */
    @Test
    fun chatReachesAPhoneThatIsOnlyConnectedThroughAnother() {
        ActivityScenario.launch(MainActivity::class.java).use { runRelay() }
    }

    private fun runRelay() = runBlocking {
        delay(2_000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val a = "aaaa"; val b = "bbbb"; val c = "cccc"
        val stateAtB = ConcurrentHashMap<String, PeerState>()
        val chatAtC = CompletableDeferred<Triple<String, String, String>>()
        lateinit var meshA: VoiceMesh
        lateinit var meshB: VoiceMesh
        lateinit var meshC: VoiceMesh
        fun mesh(me: String, route: (String, Signal) -> Unit, onState: (String, PeerState) -> Unit = { _, _ -> }, onChat: (String, String, String) -> Unit = { _, _, _ -> }) =
            VoiceMesh(
                context = context, scope = scope, me = me,
                sendSignal = { to, signal -> route(to, signal) },
                onPeerState = onState, onChat = onChat, onSpeaking = { _, _ -> },
            )
        meshA = mesh(a, { to, sig -> if (to == b) scope.launch { meshB.onSignal(a, sig) } })
        meshB = mesh(
            b,
            { to, sig -> scope.launch { if (to == a) meshA.onSignal(b, sig) else if (to == c) meshC.onSignal(b, sig) } },
            onState = { uid, st -> stateAtB[uid] = st },
        )
        meshC = mesh(c, { to, sig -> if (to == b) scope.launch { meshB.onSignal(c, sig) } }, onChat = { from, name, text -> chatAtC.complete(Triple(from, name, text)) })
        meshA.reconcile(setOf(b))
        meshB.reconcile(setOf(a, c))
        meshC.reconcile(setOf(b))
        withTimeout(60_000) { while (stateAtB[a] != PeerState.CONNECTED || stateAtB[c] != PeerState.CONNECTED) delay(200) }
        withTimeout(15_000) { while (meshA.sendChat("sab ko", "Asha") == 0) delay(200) }
        val got = withTimeout(10_000) { chatAtC.await() }
        assertEquals(Triple(a, "Asha", "sab ko"), got)
        meshA.close(); meshB.close(); meshC.close()
        scope.cancel()
    }
}
