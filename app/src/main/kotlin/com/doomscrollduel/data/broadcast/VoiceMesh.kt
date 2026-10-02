package com.doomscrollduel.data.broadcast

import android.content.Context
import android.util.Log
import com.doomscrollduel.domain.social.PeerState
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule

/** What two phones tell each other to set up a call. They travel through Firestore for a moment and are deleted when read. */
sealed interface Signal {
    data class Offer(val sdp: String) : Signal
    data class Answer(val sdp: String) : Signal
    data class Ice(val mid: String?, val index: Int, val candidate: String) : Signal
}

/**
 * One audio call to every other person in the room (a "mesh"), plus a text channel to each of them for the chat.
 *
 * Who makes the offer is decided without any talking: of two people, the one with the smaller uid offers, so two phones
 * never offer at the same time. Everything is audio only; no video, no recording, nothing is sent to a server.
 *
 * Connection: public STUN servers first; when two phones cannot reach each other directly (phone networks behind a carrier
 * NAT often cannot) the call goes through a TURN relay, see [IceServers]. Audio and chat stay end-to-end encrypted, so a relay
 * only carries bytes it cannot read.
 *
 * Chat: every line has an id and travels over each open text channel; a phone that receives a line it has not seen passes it
 * on to its other channels, so everybody gets it even when two phones could not connect to each other directly.
 */
class VoiceMesh(
    context: Context,
    private val scope: CoroutineScope,
    private val me: String,
    private val sendSignal: suspend (to: String, signal: Signal) -> Unit,
    private val onPeerState: (uid: String, state: PeerState) -> Unit,
    /** A chat line that reached this phone, from the person who wrote it (not necessarily the phone it came through). */
    private val onChat: (fromUid: String, fromName: String, text: String) -> Unit,
    private val onSpeaking: (uid: String, speaking: Boolean) -> Unit,
    /** Whether audio packets are really flowing to / from that person (so "is my voice going out?" has an answer). */
    private val onFlow: (uid: String, sending: Boolean, receiving: Boolean) -> Unit = { _, _, _ -> },
) {
    private class Peer(val uid: String, val pc: PeerConnection) {
        var channel: DataChannel? = null
        val pending = ArrayList<IceCandidate>()
        var lastOutBytes = 0.0
        var lastInBytes = 0.0
        var sending = false
        var receiving = false

        @Volatile var remoteSet = false
        val startedAt = System.currentTimeMillis()

        @Volatile var state = PeerState.CONNECTING
    }

    private val peers = ConcurrentHashMap<String, Peer>()
    private val mutex = Mutex()
    private val audioDevice: JavaAudioDeviceModule
    private val factory: PeerConnectionFactory
    private val audioSource: org.webrtc.AudioSource
    private val audioTrack: org.webrtc.AudioTrack
    private val appContext = context.applicationContext
    private var closed = false
    @Volatile private var lastOthers: Set<String> = emptySet()

    // Chat lines already seen (so a line that comes round twice is shown once) and the last few sent, for a channel that opens late.
    private val seenChat = java.util.Collections.synchronizedSet(object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            val added = super.add(element)
            if (size > SEEN_LIMIT) iterator().let { it.next(); it.remove() }
            return added
        }
    })
    private val recentChat = java.util.Collections.synchronizedList(ArrayList<Pair<Long, String>>())

    // ----- who is talking ---------------------------------------------------------------------------------------------
    // My own voice is measured from the microphone samples; other people's from the audio level WebRTC reports for what it
    // plays. Only a loudness number is looked at: the sound itself is never kept, recorded or sent anywhere extra.
    @Volatile private var muted = false
    private val lastLoud = ConcurrentHashMap<String, Long>()
    private val talking = ConcurrentHashMap<String, Boolean>()
    private var poller: kotlinx.coroutines.Job? = null
    private var pollCount = 0
    @Volatile private var micFrames = 0
    @Volatile private var micPeak = 0.0
    @Volatile private var lastRemoteLevel = 0.0

    init {
        ensureInitialized(context.applicationContext)
        audioDevice = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setSamplesReadyCallback { samples -> onMicSamples(samples) }
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioDevice).createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("mic", audioSource)
        poller = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(POLL_MS)
                peers.values.filter { it.state == PeerState.CONNECTED }.forEach { peer ->
                    peer.pc.getStats { report ->
                        var inLevel = 0.0
                        var outLevel = 0.0
                        var inBytes = 0.0
                        var outBytes = 0.0
                        report.statsMap.values.forEach { stat ->
                            val m = stat.members
                            fun num(key: String) = (m[key] as? Number)?.toDouble() ?: 0.0
                            when {
                                stat.type == "inbound-rtp" && m["kind"] == "audio" -> { inLevel = maxOf(inLevel, num("audioLevel")); inBytes += num("bytesReceived") }
                                stat.type == "outbound-rtp" && m["kind"] == "audio" -> outBytes += num("bytesSent")
                                stat.type == "media-source" && m["kind"] == "audio" -> outLevel = maxOf(outLevel, num("audioLevel"))
                            }
                        }
                        lastRemoteLevel = maxOf(lastRemoteLevel, inLevel)
                        mark(peer.uid, inLevel > SPEECH_LEVEL)
                        // My own voice also shows up as the level of the microphone source, even before the sample callback fires.
                        if (!muted && outLevel > SPEECH_LEVEL) mark(me, true)
                        val sending = outBytes > peer.lastOutBytes
                        val receiving = inBytes > peer.lastInBytes
                        peer.lastOutBytes = outBytes
                        peer.lastInBytes = inBytes
                        if (sending != peer.sending || receiving != peer.receiving) {
                            peer.sending = sending
                            peer.receiving = receiving
                            onFlow(peer.uid, sending, receiving)
                        }
                    }
                }
                mark(me, false)
                val now = System.currentTimeMillis()
                val stuck = peers.values.filter { it.state == PeerState.CONNECTING && now - it.startedAt > CONNECT_TIMEOUT_MS }
                if (stuck.isNotEmpty()) {
                    stuck.forEach { markState(it, PeerState.FAILED) }
                    reconcile(lastOthers)
                }
                if (++pollCount % 6 == 0) {
                    Log.i(TAG, "voice: mic frames=$micFrames peak=${"%.4f".format(micPeak)} remotePeak=${"%.4f".format(lastRemoteLevel)} muted=$muted")
                    micPeak = 0.0
                    lastRemoteLevel = 0.0
                }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        this.muted = muted
        audioTrack.setEnabled(!muted)
        if (muted) {
            lastLoud.remove(me)
            mark(me, false)
        }
    }

    private fun onMicSamples(samples: JavaAudioDeviceModule.AudioSamples) {
        if (muted) return
        val data = samples.data
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < data.size) {
            val value = (data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)
            sum += value.toDouble() * value
            count++
            i += 2
        }
        if (count == 0) return
        val rms = kotlin.math.sqrt(sum / count) / 32768.0
        micFrames++
        if (rms > micPeak) micPeak = rms
        mark(me, rms > SPEECH_LEVEL)
    }

    /** [loud] says the voice is above the speech level right now; it stays "talking" for a moment after, so the pulse does not flicker. */
    private fun mark(uid: String, loud: Boolean) {
        val now = System.currentTimeMillis()
        if (loud) lastLoud[uid] = now
        val speaking = now - (lastLoud[uid] ?: 0L) < HOLD_MS
        if (talking.put(uid, speaking) != speaking) onSpeaking(uid, speaking)
    }

    /** Called whenever the list of people in the room changes. Connects to new people and drops the ones who left. */
    fun reconcile(others: Set<String>) {
        lastOthers = others
        scope.launch {
            mutex.withLock {
                if (closed) return@withLock
                peers.keys.filter { it !in others }.forEach { closePeer(it) }
                // A call that failed is thrown away and tried again from scratch on the next round.
                peers.values.filter { it.state == PeerState.FAILED }.forEach { closePeer(it.uid) }
                others.filter { it != me && !peers.containsKey(it) && me < it }.forEach { offerTo(it) }
            }
        }
    }

    suspend fun onSignal(from: String, signal: Signal) {
        mutex.withLock {
            if (closed) return
            try {
                when (signal) {
                    is Signal.Offer -> {
                        // Only the smaller uid offers, so a valid offer comes from a uid smaller than mine; any other is ignored.
                        if (from > me) return
                        // Network candidates can arrive before the offer; that early peer is reused, not thrown away.
                        val early = peers[from]?.takeIf { !it.remoteSet }
                        if (early == null) closePeer(from)
                        val peer = early ?: createPeer(from, offerer = false) ?: return
                        peer.pc.setRemote(SessionDescription(SessionDescription.Type.OFFER, signal.sdp))
                        peer.remoteSet = true
                        flush(peer)
                        val answer = peer.pc.answer()
                        peer.pc.setLocal(answer)
                        sendSignal(from, Signal.Answer(answer.description))
                    }
                    is Signal.Answer -> peers[from]?.let { peer ->
                        peer.pc.setRemote(SessionDescription(SessionDescription.Type.ANSWER, signal.sdp))
                        peer.remoteSet = true
                        flush(peer)
                    }
                    is Signal.Ice -> {
                        val peer = peers[from] ?: createPeer(from, offerer = false) ?: return
                        val candidate = IceCandidate(signal.mid, signal.index, signal.candidate)
                        if (peer.remoteSet) peer.pc.addIceCandidate(candidate) else peer.pending.add(candidate)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "signal from $from failed: ${e.message}")
                peers[from]?.let { markState(it, PeerState.FAILED) }
            }
        }
    }

    /** Sends a chat line to everyone whose text channel is open. Returns how many phones it went to directly. */
    fun sendChat(text: String, myName: String): Int {
        val id = java.util.UUID.randomUUID().toString()
        seenChat.add(id)
        val json = org.json.JSONObject().put("i", id).put("u", me).put("n", myName).put("t", text).toString()
        remember(json)
        return pass(json, except = null)
    }

    private fun remember(json: String) {
        val now = System.currentTimeMillis()
        synchronized(recentChat) {
            recentChat.removeAll { now - it.first > BACKLOG_MS }
            recentChat.add(now to json)
        }
    }

    /** Puts [json] on every open text channel except the one it came from. */
    private fun pass(json: String, except: String?): Int {
        var sent = 0
        peers.values.forEach { peer ->
            if (peer.uid == except) return@forEach
            if (push(peer.channel, json)) sent++
        }
        return sent
    }

    private fun push(channel: DataChannel?, json: String): Boolean {
        if (channel == null || channel.state() != DataChannel.State.OPEN) return false
        return runCatching { channel.send(DataChannel.Buffer(ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)), false)) }.getOrDefault(false)
    }

    /** A text channel just opened: hand it the lines of the last few seconds, which it may have missed while it was opening. */
    private fun backlogTo(channel: DataChannel) {
        val now = System.currentTimeMillis()
        val lines = synchronized(recentChat) { recentChat.filter { now - it.first <= BACKLOG_MS }.map { it.second } }
        lines.forEach { push(channel, it) }
    }

    private fun onChatBytes(via: String, bytes: ByteArray) {
        val obj = runCatching { org.json.JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return
        val id = obj.optString("i")
        val origin = obj.optString("u")
        val text = obj.optString("t").take(MAX_CHAT)
        if (id.isEmpty() || origin.isEmpty() || origin == me || text.isBlank()) return
        if (!seenChat.add(id)) return
        onChat(origin, obj.optString("n").take(30), text)
        remember(obj.toString())
        pass(obj.toString(), except = via)
    }

    fun close() {
        scope.launch {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                poller?.cancel()
                peers.keys.toList().forEach { closePeer(it) }
                audioTrack.dispose()
                audioSource.dispose()
                factory.dispose()
                audioDevice.release()
            }
        }
    }

    // ----- internals --------------------------------------------------------------------------------------------------

    private suspend fun offerTo(uid: String) {
        val peer = createPeer(uid, offerer = true) ?: return
        try {
            val offer = peer.pc.offer()
            peer.pc.setLocal(offer)
            sendSignal(uid, Signal.Offer(offer.description))
        } catch (e: Exception) {
            Log.w(TAG, "offer to $uid failed: ${e.message}")
            markState(peer, PeerState.FAILED)
        }
    }

    private fun flush(peer: Peer) {
        peer.pending.forEach { peer.pc.addIceCandidate(it) }
        peer.pending.clear()
    }

    private fun createPeer(uid: String, offerer: Boolean): Peer? {
        val config = PeerConnection.RTCConfiguration(IceServers.list(appContext)).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        var created: Peer? = null
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(c: IceCandidate) {
                if (c.sdp.contains(" typ relay")) Log.i(TAG, "relay candidate ready for ${uid.take(6)}")
                scope.launch { runCatching { sendSignal(uid, Signal.Ice(c.sdpMid, c.sdpMLineIndex, c.sdp)) } }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel) {
                created?.channel = channel
                watch(uid, channel)
            }
            override fun onRenegotiationNeeded() {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                val peer = created ?: return
                markState(
                    peer,
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> PeerState.CONNECTED
                        PeerConnection.PeerConnectionState.FAILED -> PeerState.FAILED
                        else -> PeerState.CONNECTING
                    },
                )
            }
        }
        val pc = factory.createPeerConnection(config, observer) ?: return null
        val peer = Peer(uid, pc)
        created = peer
        peers[uid] = peer
        pc.addTrack(audioTrack, listOf("broadcast"))
        if (offerer) {
            pc.createDataChannel("chat", DataChannel.Init())?.let {
                peer.channel = it
                watch(uid, it)
            }
        }
        onPeerState(uid, PeerState.CONNECTING)
        return peer
    }

    private fun watch(uid: String, channel: DataChannel) {
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                if (channel.state() == DataChannel.State.OPEN) backlogTo(channel)
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                onChatBytes(uid, bytes)
            }
        })
    }

    private fun markState(peer: Peer, state: PeerState) {
        if (peer.state == state) return
        Log.i(TAG, "peer ${peer.uid.take(6)} -> $state")
        peer.state = state
        onPeerState(peer.uid, state)
    }

    private fun closePeer(uid: String) {
        val peer = peers.remove(uid) ?: return
        lastLoud.remove(uid)
        if (talking.remove(uid) == true) onSpeaking(uid, false)
        onFlow(uid, false, false)
        runCatching { peer.channel?.close() }
        runCatching { peer.pc.close() }
        runCatching { peer.pc.dispose() }
    }

    // ----- WebRTC callbacks as suspend functions ----------------------------------------------------------------------

    private open class Sdp : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    private suspend fun PeerConnection.offer(): SessionDescription = suspendCancellableCoroutine { c ->
        createOffer(
            object : Sdp() {
                override fun onCreateSuccess(p0: SessionDescription) = c.resume(p0)
                override fun onCreateFailure(p0: String?) = c.resumeWithException(IllegalStateException(p0))
            },
            MediaConstraints(),
        )
    }

    private suspend fun PeerConnection.answer(): SessionDescription = suspendCancellableCoroutine { c ->
        createAnswer(
            object : Sdp() {
                override fun onCreateSuccess(p0: SessionDescription) = c.resume(p0)
                override fun onCreateFailure(p0: String?) = c.resumeWithException(IllegalStateException(p0))
            },
            MediaConstraints(),
        )
    }

    private suspend fun PeerConnection.setLocal(description: SessionDescription): Unit = suspendCancellableCoroutine { c ->
        setLocalDescription(
            object : Sdp() {
                override fun onSetSuccess() = c.resume(Unit)
                override fun onSetFailure(p0: String?) = c.resumeWithException(IllegalStateException(p0))
            },
            description,
        )
    }

    private suspend fun PeerConnection.setRemote(description: SessionDescription): Unit = suspendCancellableCoroutine { c ->
        setRemoteDescription(
            object : Sdp() {
                override fun onSetSuccess() = c.resume(Unit)
                override fun onSetFailure(p0: String?) = c.resumeWithException(IllegalStateException(p0))
            },
            description,
        )
    }

    private companion object {
        const val TAG = "VoiceMesh"
        const val MAX_CHAT = 500
        const val POLL_MS = 350L
        const val HOLD_MS = 700L

        /** A call still "connecting" after this long is dropped and set up again. */
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val SEEN_LIMIT = 400
        const val BACKLOG_MS = 12_000L

        /** Loudness (0 to 1) above which a voice counts as speech. Background hiss is far below. */
        const val SPEECH_LEVEL = 0.012

        @Volatile private var initialized = false

        fun ensureInitialized(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
                initialized = true
            }
        }
    }
}
