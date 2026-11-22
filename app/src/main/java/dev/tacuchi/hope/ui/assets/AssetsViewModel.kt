package dev.tacuchi.hope.ui.assets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.R
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.AssetUse
import dev.tacuchi.hope.data.HopeRepository
import dev.tacuchi.hope.data.AssetType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AssetFilter(@StringRes val label: Int, @DrawableRes val icon: Int, val type: AssetType?) {
    All(R.string.assets_filter_all, R.drawable.ic_check, null),
    Audio(R.string.assets_filter_audio, R.drawable.ic_graphic_eq, AssetType.Audio),
    Image(R.string.assets_filter_image, R.drawable.ic_image, AssetType.Image),
}

/** Una hope a la que se puede llevar un asset. */
data class HopeChoice(val id: Long, val title: String)

data class AssetsUiState(
    val filter: AssetFilter = AssetFilter.All,
    /** Nulo mientras se lee el catálogo por primera vez. */
    val assets: List<Asset>? = null,
    val playingId: Long? = null,
    /** Las hopes que usan cada asset, por id de asset. */
    val uses: Map<Long, List<AssetUse>> = emptyMap(),
    /** Las hopes guardadas, de la más reciente a la más antigua. */
    val hopes: List<HopeChoice> = emptyList(),
)

@HiltViewModel
class AssetsViewModel @Inject constructor(
    private val repository: AssetRepository,
    private val hopes: HopeRepository,
    private val player: AssetPreview,
) : ViewModel() {

    private val filter = MutableStateFlow(AssetFilter.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AssetsUiState> = combine(
        filter,
        filter.flatMapLatest { repository.observe(it.type) },
        player.playingId,
        hopes.observeUses(),
        hopes.observe(),
    ) { selected, assets, playing, uses, saved ->
        AssetsUiState(selected, assets, playing, uses, saved.map { HopeChoice(it.hope.id, it.hope.title) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState())

    fun selectFilter(selected: AssetFilter) {
        filter.value = selected
    }

    fun togglePlay(asset: Asset) {
        if (asset.type != AssetType.Audio) return
        player.toggle(asset.id, repository.fileOf(asset))
    }

    /** La pantalla ya no ofrece borrar un asset en uso; el repositorio igual lo rechaza. */
    fun delete(asset: Asset) {
        player.stopIfCurrent(asset.id)
        viewModelScope.launch { repository.delete(asset) }
    }

    /** Pone el asset en una hope guardada y avisa cuando quedó, para abrirla. */
    fun useInHope(asset: Asset, hopeId: Long, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            hopes.useAsset(hopeId, asset)
            onDone(hopeId)
        }
    }

    override fun onCleared() {
        player.release()
    }
}
