package dev.tacuchi.hope.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.tacuchi.hope.appContainer
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.HopeRepository
import dev.tacuchi.hope.data.HopeWithAssets
import dev.tacuchi.hope.playback.MixPlayback
import dev.tacuchi.hope.playback.MixPlayer
import dev.tacuchi.hope.playback.libraryKey
import dev.tacuchi.hope.playback.toMixSpec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Una tarjeta de la biblioteca. */
data class LibraryItem(val hope: HopeWithAssets, val coverFile: File?) {
    val id: Long get() = hope.hope.id
    val title: String get() = hope.hope.title
    val durationMs: Long get() = hope.durationMs
}

data class LibraryUiState(
    /** Nulo mientras se lee la base por primera vez. */
    val items: List<LibraryItem>? = null,
    val playingId: Long? = null,
)

class LibraryViewModel(
    private val hopes: HopeRepository,
    private val assets: AssetRepository,
    private val player: MixPlayer,
) : ViewModel() {

    val state: StateFlow<LibraryUiState> = combine(hopes.observe(), player.state) { list, playback ->
        LibraryUiState(
            items = list.map { LibraryItem(it, it.cover?.let(assets::fileOf)) },
            playingId = playback.playingHopeId(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Tocar una hope la reproduce o la pausa. */
    fun togglePlay(item: LibraryItem) {
        player.toggle(libraryKey(item.id), item.hope.toMixSpec(assets::fileOf))
    }

    /** Borra la hope; sus assets quedan en Assets. */
    fun delete(item: LibraryItem) {
        player.stop(libraryKey(item.id))
        viewModelScope.launch { hopes.delete(item.id) }
    }

    private fun MixPlayback.playingHopeId(): Long? =
        if (isPlaying) key?.removePrefix("hope-")?.toLongOrNull()?.takeIf { key == libraryKey(it) } else null

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                LibraryViewModel(container.hopeRepository, container.assetRepository, container.mixPlayer)
            }
        }
    }
}
