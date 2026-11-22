package dev.tacuchi.hope.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.domain.model.HopeWithAssets
import dev.tacuchi.hope.domain.model.playingHopeId
import dev.tacuchi.hope.domain.usecase.DeleteHopeUseCase
import dev.tacuchi.hope.domain.usecase.ObserveHopesUseCase
import dev.tacuchi.hope.domain.usecase.ObservePlaybackUseCase
import dev.tacuchi.hope.domain.usecase.ToggleHopePlaybackUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Una tarjeta de la biblioteca. */
data class LibraryItem(val hope: HopeWithAssets) {
    val id: Long get() = hope.hope.id
    val title: String get() = hope.hope.title
    val durationMs: Long get() = hope.durationMs
    val linkUrl: String? get() = hope.hope.linkUrl
    val coverFile: File? get() = hope.cover?.file
}

data class LibraryUiState(
    /** Nulo mientras se lee la base por primera vez. */
    val items: List<LibraryItem>? = null,
    val playingId: Long? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeHopes: ObserveHopesUseCase,
    observePlayback: ObservePlaybackUseCase,
    private val togglePlayback: ToggleHopePlaybackUseCase,
    private val deleteHope: DeleteHopeUseCase,
) : ViewModel() {

    val state: StateFlow<LibraryUiState> = combine(observeHopes(), observePlayback()) { list, playback ->
        LibraryUiState(items = list.map(::LibraryItem), playingId = playback.playingHopeId())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    /** Tocar una hope la reproduce o la pausa. */
    fun togglePlay(item: LibraryItem) = togglePlayback(item.hope)

    /** Deja de sonar y borra la hope; sus assets quedan en Assets. */
    fun delete(item: LibraryItem) {
        viewModelScope.launch { deleteHope(item.id) }
    }
}
