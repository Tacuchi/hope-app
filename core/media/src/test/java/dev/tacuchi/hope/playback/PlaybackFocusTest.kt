package dev.tacuchi.hope.playback

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class PlaybackFocusTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val audio = shadowOf(context.getSystemService(AudioManager::class.java))
    private var pauses = 0
    private val focus = PlaybackFocus(context) { pauses++ }

    @Test
    fun otherAudioAndCallsPauseTheMix() {
        assertTrue(focus.acquire())
        val listener = audio.lastAudioFocusRequest.listener
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        assertEquals(3, pauses)
        focus.abandon()
        assertNotNull(audio.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun unplugPausesOnlyWhileHoldingFocus() {
        assertTrue(focus.acquire())
        val noisy = Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        focus.abandon()
        context.sendBroadcast(noisy)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, pauses)
    }

    @Test
    fun deniedFocusPreventsPlayback() {
        audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        assertFalse(focus.acquire())
    }
}
