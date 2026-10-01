package com.doomscrollduel.blocking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * While the overlay covers a reel, ask other apps to pause their sound, so the video does not keep playing
 * behind the card. Well-behaved video apps pause on losing audio focus. Best effort: some apps ignore it.
 */
class AudioFocusHolder(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null

    fun hold() {
        if (request != null) return
        val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { }
            .build()
        if (audio.requestAudioFocus(r) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) request = r
    }

    fun release() {
        request?.let { audio.abandonAudioFocusRequest(it) }
        request = null
    }
}
