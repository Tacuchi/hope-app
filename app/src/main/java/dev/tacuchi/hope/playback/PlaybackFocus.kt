package dev.tacuchi.hope.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat

/** La sesión y la pantalla comparten el mismo permiso para sonar. */
internal class PlaybackFocus(private val context: Context, private val pause: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var holding = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (holding) {
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pause()
                }
            }
        }
        .build()
    private val unplugged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (holding && intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    fun acquire(): Boolean {
        if (holding) return true
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        holding = true
        ContextCompat.registerReceiver(context, unplugged, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        return true
    }

    fun abandon() {
        if (!holding) return
        holding = false
        context.unregisterReceiver(unplugged)
        manager.abandonAudioFocusRequest(request)
    }
}
