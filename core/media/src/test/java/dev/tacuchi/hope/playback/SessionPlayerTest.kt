package dev.tacuchi.hope.playback

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.model.MixPresentation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class SessionPlayerTest {
    private val source = object : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf(MediaItemData.Builder("source").setDurationUs(60_000_000).build()))
            .setPlaybackState(Player.STATE_READY)
            .build()
    }
    private var stopped = false
    private val session = SessionPlayer(source, "Nueva hope", {}, {}) { stopped = true }

    @Test
    fun offersPositionPauseAndStopForASingleMix() {
        assertTrue(session.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertTrue(session.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
        for (command in listOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD)) {
            assertFalse(session.isCommandAvailable(command))
        }
        session.stop()
        assertTrue(stopped)
    }

    @Test
    fun publishesTheCurrentTitleCoverAndDuration() {
        val cover = File("cover.png")
        session.show(MixPresentation("Mi mezcla", cover))
        assertEquals("Mi mezcla", session.mediaMetadata.title)
        assertEquals(Uri.fromFile(cover), session.mediaMetadata.artworkUri)
        assertEquals(60_000L, session.duration)
        assertTrue(session.isCurrentMediaItemSeekable)
        session.show(MixPresentation())
        assertEquals("Nueva hope", session.mediaMetadata.title)
        assertNull(session.mediaMetadata.artworkUri)
    }
}
