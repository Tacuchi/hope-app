package dev.tacuchi.hope.ui.assets

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** La preescucha de Assets, vista desde su ViewModel. */
interface AssetPreview {
    /** El asset que está sonando ahora; nulo si nada suena. */
    val playingId: StateFlow<Long?>

    /** Toca un audio: si es el que suena lo pausa, si estaba en pausa sigue, si es otro lo cambia. */
    fun toggle(id: Long, file: File)

    /** Detiene el audio si es el de [id], por ejemplo antes de borrarlo. */
    fun stopIfCurrent(id: Long)

    fun release()
}

/** Escucha un asset de audio desde Assets. ExoPlayer se crea al primer toque. */
class AssetAudioPlayer(private val context: Context) : AssetPreview {
    private var player: ExoPlayer? = null
    private var currentId: Long? = null

    private val _playingId = MutableStateFlow<Long?>(null)

    override val playingId: StateFlow<Long?> = _playingId.asStateFlow()

    override fun toggle(id: Long, file: File) {
        val exo = player ?: ExoPlayer.Builder(context).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playingId.value = if (isPlaying) currentId else null
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) created.seekTo(0)
                }
            })
            player = created
        }
        when {
            currentId == id && exo.isPlaying -> exo.pause()
            currentId == id -> exo.play()
            else -> {
                currentId = id
                exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                exo.prepare()
                exo.play()
            }
        }
    }

    override fun stopIfCurrent(id: Long) {
        if (currentId != id) return
        player?.stop()
        player?.clearMediaItems()
        currentId = null
        _playingId.value = null
    }

    override fun release() {
        player?.release()
        player = null
        currentId = null
        _playingId.value = null
    }
}
