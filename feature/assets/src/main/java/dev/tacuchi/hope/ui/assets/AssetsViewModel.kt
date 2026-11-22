package dev.tacuchi.hope.ui.assets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.usecase.DeleteAssetUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetUsesUseCase
import dev.tacuchi.hope.domain.usecase.ObserveAssetsUseCase
import dev.tacuchi.hope.domain.usecase.ObserveHopesUseCase
import dev.tacuchi.hope.domain.usecase.ReleaseAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.StopAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.ToggleAssetPreviewUseCase
import dev.tacuchi.hope.domain.usecase.UseAssetInHopeUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.tacuchi.hope.ui.R as UiR

enum class AssetFilter(@StringRes val label: Int, @DrawableRes val icon: Int, val type: AssetType?) {
    All(R.string.assets_filter_all, UiR.drawable.ic_check, null),
    Audio(UiR.string.assets_filter_audio, UiR.drawable.ic_graphic_eq, AssetType.Audio),
    Image(UiR.string.assets_filter_image, UiR.drawable.ic_image, AssetType.Image),
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
    observeAssets: ObserveAssetsUseCase,
    observeUses: ObserveAssetUsesUseCase,
    observeHopes: ObserveHopesUseCase,
    observePreview: ObserveAssetPreviewUseCase,
    private val togglePreview: ToggleAssetPreviewUseCase,
    private val stopPreview: StopAssetPreviewUseCase,
    private val releasePreview: ReleaseAssetPreviewUseCase,
    private val deleteAsset: DeleteAssetUseCase,
    private val useAssetInHope: UseAssetInHopeUseCase,
) : ViewModel() {

    private val filter = MutableStateFlow(AssetFilter.All)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<AssetsUiState> = combine(
        filter,
        filter.flatMapLatest { observeAssets(it.type) },
        observePreview(),
        observeUses(),
        observeHopes(),
    ) { selected, assets, playing, uses, saved ->
        AssetsUiState(selected, assets, playing, uses, saved.map { HopeChoice(it.hope.id, it.hope.title) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetsUiState())

    fun selectFilter(selected: AssetFilter) {
        filter.value = selected
    }

    /** Escucha un audio o lo pausa; las imágenes no suenan. */
    fun togglePlay(asset: Asset) = togglePreview(asset)

    /** La pantalla ya no ofrece borrar un asset en uso; el catálogo igual lo rechaza. */
    fun delete(asset: Asset) {
        stopPreview(asset)
        viewModelScope.launch { deleteAsset(asset) }
    }

    /** Pone el asset en una hope guardada y avisa cuando quedó, para abrirla. */
    fun useInHope(asset: Asset, hopeId: Long, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            useAssetInHope(hopeId, asset)
            onDone(hopeId)
        }
    }

    override fun onCleared() {
        releasePreview()
    }
}
