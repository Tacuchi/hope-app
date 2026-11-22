package dev.tacuchi.hope.data

import android.content.Context
import androidx.core.content.edit
import dev.tacuchi.hope.domain.model.Appearance
import dev.tacuchi.hope.domain.model.Palette
import dev.tacuchi.hope.domain.model.ThemeMode
import dev.tacuchi.hope.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La apariencia en `SharedPreferences` («appearance»), con las mismas claves y valores desde la
 * 0.1.0. Se lee una vez al crearse y cada cambio se guarda enseguida.
 */
class PreferencesAppearanceRepository(context: Context) : AppearanceRepository {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(load())

    override val appearance: StateFlow<Appearance> = current.asStateFlow()

    override fun save(value: Appearance) {
        preferences.edit {
            putString("theme", value.theme.name)
            putString("palette", value.palette.name)
        }
        current.value = value
    }

    private fun load() = Appearance(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: ThemeMode.System,
        Palette.entries.firstOrNull { it.name == preferences.getString("palette", null) } ?: Palette.Hope,
    )
}
