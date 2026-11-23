package dev.tacuchi.hope.preview

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.tacuchi.hope.domain.media.AssetPreview
import dev.tacuchi.hope.domain.media.PreviewPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Escucha un asset de audio en Assets o en el selector del editor. ExoPlayer se crea al primer
 * toque. Mientras suena publica su avance; al llegar al final vuelve al inicio, en pausa.
 */
class AssetAudioPlayer(private val context: Context) : AssetPreview {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(PreviewPlayback())
    override val state: StateFlow<PreviewPlayback> = _state.asStateFlow()

    private val tick = object : Runnable {
        override fun run() {
            val exo = player ?: return
            publish(exo)
            if (exo.isPlaying) handler.postDelayed(this, TICK_MS)
        }
    }

    private fun publish(exo: ExoPlayer) {
        val duration = exo.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: _state.value.durationMs
        _state.value = _state.value.copy(isPlaying = exo.isPlaying, positionMs = exo.currentPosition, durationMs = duration)
    }

    override fun toggle(id: Long, file: File, durationMs: Long?) {
        val exo = player ?: ExoPlayer.Builder(context).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (_state.value.assetId == null) return
                    publish(created)
                    handler.removeCallbacks(tick)
                    if (isPlaying) handler.post(tick)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) return
                    created.pause()
                    created.seekTo(0)
                    _state.value = _state.value.copy(isPlaying = false, positionMs = 0)
                }
            })
            player = created
        }
        val current = _state.value
        when {
            current.assetId == id && exo.isPlaying -> exo.pause()
            current.assetId == id -> exo.play()
            else -> {
                _state.value = PreviewPlayback(id, isPlaying = false, positionMs = 0, durationMs = durationMs ?: 0)
                exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                exo.prepare()
                exo.play()
            }
        }
    }

    override fun stop() {
        handler.removeCallbacks(tick)
        player?.stop()
        player?.clearMediaItems()
        _state.value = PreviewPlayback()
    }

    override fun stopIfCurrent(id: Long) {
        if (_state.value.assetId == id) stop()
    }

    override fun release() {
        handler.removeCallbacks(tick)
        player?.release()
        player = null
        _state.value = PreviewPlayback()
    }

    private companion object {
        const val TICK_MS = 100L
    }
}
