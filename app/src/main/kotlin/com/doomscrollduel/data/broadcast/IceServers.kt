package com.doomscrollduel.data.broadcast

import android.content.Context
import com.doomscrollduel.R
import org.webrtc.PeerConnection

/**
 * The servers that help two phones find each other for a voice call.
 *
 * STUN only tells a phone its own public address; it works on most home Wi-Fi. Phone networks behind a carrier-grade NAT
 * (very common in India) often cannot connect directly, and then the call has to go through a TURN relay. A relay is a server
 * somebody must run or rent, so its address is NOT in the code: set TURN_URLS, TURN_USERNAME and TURN_CREDENTIAL in
 * `local.properties` (or the environment on CI) and rebuild. Good starting points are the free tier of metered.ca, Cloudflare
 * Calls TURN, Twilio, or your own coturn server. The call stays encrypted end to end (DTLS-SRTP), so a relay only carries
 * bytes it cannot listen to. If a relay is used, say so in the Privacy Policy and the Data safety form.
 *
 * The shared "openrelayproject" credentials that are often copied from tutorials were tried on 2 Oct 2026 and the server
 * refuses them, so they are not used.
 */
object IceServers {
    private val stun = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun.cloudflare.com:3478",
    )

    fun list(context: Context): List<PeerConnection.IceServer> = buildList {
        stun.forEach { add(PeerConnection.IceServer.builder(it).createIceServer()) }
        val urls = context.getString(R.string.turn_urls).split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (urls.isNotEmpty()) {
            add(
                PeerConnection.IceServer.builder(urls)
                    .setUsername(context.getString(R.string.turn_username))
                    .setPassword(context.getString(R.string.turn_credential))
                    .createIceServer(),
            )
        }
    }
}
