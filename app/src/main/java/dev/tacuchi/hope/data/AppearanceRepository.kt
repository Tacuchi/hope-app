package dev.tacuchi.hope.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { System, Light, Dark }
enum class Palette { Hope, Dynamic, Lime, Coral, Violet }

data class Appearance(val theme: ThemeMode = ThemeMode.System, val palette: Palette = Palette.Hope)

class AppearanceRepository(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(Appearance(
        ThemeMode.entries.firstOrNull { it.name == preferences.getString("theme", null) } ?: ThemeMode.System,
        Palette.entries.firstOrNull { it.name == preferences.getString("palette", null) } ?: Palette.Hope,
    ))
    val appearance = current.asStateFlow()

    fun save(value: Appearance) {
        preferences.edit {
            putString("theme", value.theme.name)
            putString("palette", value.palette.name)
        }
        current.value = value
    }
}
