package dev.tacuchi.hope.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }
enum class Palette { Hope, Dynamic, Lime, Coral, Violet }

data class Appearance(val theme: ThemeMode = ThemeMode.System, val palette: Palette = Palette.Hope)

/** Dónde queda guardada la apariencia entre aperturas de la app. */
interface AppearanceStore {
    fun load(): Appearance
    fun save(value: Appearance)
}

/** La apariencia en `SharedPreferences` («appearance»), con las mismas claves desde la 0.1.0. */
class PreferencesAppearanceStore(context: Context) : AppearanceStore {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    override fun load() = Appearance(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: ThemeMode.System,
        Palette.entries.firstOrNull { it.name == preferences.getString("palette", null) } ?: Palette.Hope,
    )

    override fun save(value: Appearance) {
        preferences.edit {
            putString("theme", value.theme.name)
            putString("palette", value.palette.name)
        }
    }
}

/** La apariencia elegida: se lee una vez del almacén y cada cambio se guarda enseguida. */
class AppearanceRepository(private val store: AppearanceStore) {
    private val current = MutableStateFlow(store.load())
    val appearance: StateFlow<Appearance> = current.asStateFlow()

    fun save(value: Appearance) {
        store.save(value)
        current.value = value
    }
}
