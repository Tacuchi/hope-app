package dev.tacuchi.hope.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tacuchi.hope.data.Appearance
import dev.tacuchi.hope.data.AppearanceRepository
import dev.tacuchi.hope.data.Palette
import dev.tacuchi.hope.data.ThemeMode
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * La apariencia de toda la app. Vive en la actividad: el tema que pinta la actividad y lo que
 * muestra Ajustes salen del mismo estado.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: AppearanceRepository) : ViewModel() {

    /** Ya tiene la selección guardada desde el primer valor. */
    val appearance: StateFlow<Appearance> = repository.appearance

    fun setTheme(theme: ThemeMode) = repository.save(appearance.value.copy(theme = theme))

    fun setPalette(palette: Palette) = repository.save(appearance.value.copy(palette = palette))
}
