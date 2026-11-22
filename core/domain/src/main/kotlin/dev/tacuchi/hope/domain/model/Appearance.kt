package dev.tacuchi.hope.domain.model

enum class ThemeMode { System, Light, Dark }

enum class Palette { Hope, Dynamic, Lime, Coral, Violet }

/** Cómo se ve la app. Una instalación nueva abre con Sistema y Hope. */
data class Appearance(val theme: ThemeMode = ThemeMode.System, val palette: Palette = Palette.Hope)
