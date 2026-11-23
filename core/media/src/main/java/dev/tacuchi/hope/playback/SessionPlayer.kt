package dev.tacuchi.hope.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import dev.tacuchi.hope.domain.model.MixPresentation
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File

@OptIn(UnstableApi::class)
internal class SessionPlayer(
    source: Player,
    private val untitled: String,
    private val resumeMix: () -> Unit,
    private val pauseMix: () -> Unit,
    private val stopMix: () -> Unit,
) : ForwardingSimpleBasePlayer(source) {
    private var metadata = MediaMetadata.EMPTY

    /**
     * Muestra el título de [presentation] y su portada: [artwork] es la imagen ya compuesta con
     * los efectos (ver [dev.tacuchi.hope.cover.CoverComposer]); sin ella, la original.
     */
    fun show(presentation: MixPresentation, artwork: File? = presentation.cover) {
        metadata = MediaMetadata.Builder().setTitle(presentation.title.ifBlank { untitled })
            .setArtworkUri(artwork?.let(Uri::fromFile)).build()
        invalidateState()
    }

    override fun handleStop(): ListenableFuture<*> {
        stopMix()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) resumeMix() else pauseMix()
        return Futures.immediateVoidFuture()
    }

    override fun getState(): State {
        val state = super.getState()
        val commands = Player.Commands.Builder().addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).build()
        val builder = state.buildUpon().setAvailableCommands(commands)
        if (player.currentTimeline.isEmpty) return builder.build()
        // CompositionPlayer publica una pista vacía aunque la mezcla admite saltos.
        val item = MediaItemData.Builder("mix")
            .setMediaItem(MediaItem.Builder().setMediaMetadata(metadata).build())
            .setIsSeekable(true)
            .setDurationUs(player.duration.takeIf { it >= 0 }?.times(1000) ?: C.TIME_UNSET)
            .build()
        return builder.setPlaylist(listOf(item)).build()
    }
}
