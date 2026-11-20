package dev.tacuchi.hope.ui.assets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.tacuchi.hope.R
import dev.tacuchi.hope.appContainer
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.AssetType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AssetFilter(@StringRes val label: Int, @DrawableRes val icon: Int, val type: AssetType?) {
    All(R.string.assets_filter_all, R.drawable.ic_check, null),
    Audio(R.string.assets_filter_audio, R.drawable.ic_graphic_eq, AssetType.Audio),
    Image(R.string.assets_filter_image, R.drawable.ic_image, AssetType.Image),
}

data class AssetsUiState(
    val filter: AssetFilter = AssetFilter.All,
    /** Nulo mientras se lee el catálogo por primera vez. */
    val assets: List<Asset>? = null,
    val playingId: Long? = null,
)

class AssetsViewModel(
    private val repository: AssetRepository,
    private val player: AssetAudioPlayer,
) : ViewModel() {

    private val filter = MutableStateFlow(AssetFilter.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AssetsUiState> = combine(
        filter,
        filter.flatMapLatest { repository.observe(it.type) },
        player.playingId,
    ) { selected, assets, playing -> AssetsUiState(selected, assets, playing) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState())

    fun selectFilter(selected: AssetFilter) {
        filter.value = selected
    }

    fun togglePlay(asset: Asset) {
        if (asset.type != AssetType.Audio) return
        player.toggle(asset.id, repository.fileOf(asset))
    }

    fun delete(asset: Asset) {
        player.stopIfCurrent(asset.id)
        viewModelScope.launch { repository.delete(asset) }
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY]!!
                AssetsViewModel(application.appContainer.assetRepository, AssetAudioPlayer(application))
            }
        }
    }
}
