package dev.tacuchi.hope.playback

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.model.MixPresentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import dev.tacuchi.hope.cover.CoverComposer
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.CoverFilter
import org.junit.Assert.*
import org.robolectric.annotation.GraphicsMode
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    /**
     * La sesión recibe la portada con sus efectos, compuesta en la caché privada: la notificación y
     * la pantalla de bloqueo la muestran así. Sin efectos recibe la original. La original no cambia.
     */
    @Test
    fun receivesTheCoverWithItsEffectsAndTheOriginalWithoutThem() {
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "session-cover")
        val original = File(dir, "portada.png")
        dir.mkdirs()
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 140, 160)) }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val before = original.readBytes()
        val composer = CoverComposer(File(dir, "covers"))

        val plain = MixPresentation("Respira", original)
        session.show(plain, composer.artwork(plain.cover, plain.coverEffects))
        assertEquals(Uri.fromFile(original), session.mediaMetadata.artworkUri)

        val warm = MixPresentation("Respira", original, CoverEffects(filter = CoverFilter.Warm, filterAmount = 100))
        val artwork = composer.artwork(warm.cover, warm.coverEffects)!!
        session.show(warm, artwork)
        assertEquals(Uri.fromFile(artwork), session.mediaMetadata.artworkUri)
        assertEquals(File(dir, "covers"), artwork.parentFile)
        val composed = BitmapFactory.decodeFile(artwork.path)
        val pixel = composed.getPixel(32, 24)
        // El filtro cálido sube el rojo y baja el azul de la original.
        assertTrue(Color.red(pixel) > 125 && Color.blue(pixel) < 155)
        // La misma portada con los mismos ajustes reusa la imagen; otros ajustes dan otra.
        assertEquals(artwork, composer.artwork(warm.cover, warm.coverEffects))
        assertNotEquals(artwork, composer.artwork(original, CoverEffects(filter = CoverFilter.Mono)))
        assertArrayEquals(before, original.readBytes())
        dir.deleteRecursively()
    }
}
