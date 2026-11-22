package dev.tacuchi.hope.ui.settings

import dev.tacuchi.hope.data.Appearance
import dev.tacuchi.hope.data.AppearanceRepository
import dev.tacuchi.hope.data.AppearanceStore
import dev.tacuchi.hope.data.Palette
import dev.tacuchi.hope.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guarda en memoria y cuenta cuántas veces se escribió. */
private class FakeAppearanceStore(var stored: Appearance) : AppearanceStore {
    var writes = 0
    override fun load() = stored
    override fun save(value: Appearance) {
        stored = value
        writes++
    }
}

class SettingsViewModelTest {

    @Test
    fun startsWithTheSavedSelection() {
        val store = FakeAppearanceStore(Appearance(ThemeMode.Dark, Palette.Coral))

        val vm = SettingsViewModel(AppearanceRepository(store))

        assertEquals(Appearance(ThemeMode.Dark, Palette.Coral), vm.appearance.value)
        assertEquals(0, store.writes)
    }

    @Test
    fun changingTheThemeKeepsThePaletteAndPersists() {
        val store = FakeAppearanceStore(Appearance(ThemeMode.System, Palette.Lime))
        val vm = SettingsViewModel(AppearanceRepository(store))

        vm.setTheme(ThemeMode.Light)

        assertEquals(Appearance(ThemeMode.Light, Palette.Lime), vm.appearance.value)
        assertEquals(Appearance(ThemeMode.Light, Palette.Lime), store.stored)
    }

    @Test
    fun changingThePaletteKeepsTheThemeAndPersists() {
        val store = FakeAppearanceStore(Appearance(ThemeMode.Dark, Palette.Hope))
        val vm = SettingsViewModel(AppearanceRepository(store))

        vm.setPalette(Palette.Violet)

        assertEquals(Appearance(ThemeMode.Dark, Palette.Violet), vm.appearance.value)
        assertEquals(Appearance(ThemeMode.Dark, Palette.Violet), store.stored)
        // Una nueva lectura, como al volver a abrir la app, trae lo elegido.
        assertEquals(Appearance(ThemeMode.Dark, Palette.Violet), SettingsViewModel(AppearanceRepository(store)).appearance.value)
    }
}
