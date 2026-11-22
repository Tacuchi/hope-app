package dev.tacuchi.hope.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.domain.model.Appearance
import dev.tacuchi.hope.domain.model.Palette
import dev.tacuchi.hope.domain.model.ThemeMode
import dev.tacuchi.hope.domain.usecase.ObserveAppearanceUseCase
import dev.tacuchi.hope.domain.usecase.SaveAppearanceUseCase
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * La apariencia de toda la app. Vive en la actividad: el tema que pinta la actividad y lo que
 * muestra Ajustes salen del mismo estado.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeAppearance: ObserveAppearanceUseCase,
    private val saveAppearance: SaveAppearanceUseCase,
) : ViewModel() {

    /** Ya tiene la selección guardada desde el primer valor. */
    val appearance: StateFlow<Appearance> = observeAppearance()

    fun setTheme(theme: ThemeMode) = saveAppearance(appearance.value.copy(theme = theme))

    fun setPalette(palette: Palette) = saveAppearance(appearance.value.copy(palette = palette))
}
