package dev.tacuchi.hope.ui.settings

import dev.tacuchi.hope.domain.model.Appearance
import dev.tacuchi.hope.domain.model.Palette
import dev.tacuchi.hope.domain.model.ThemeMode
import dev.tacuchi.hope.domain.repository.AppearanceRepository
import dev.tacuchi.hope.domain.usecase.ObserveAppearanceUseCase
import dev.tacuchi.hope.domain.usecase.SaveAppearanceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/** Guarda en memoria y cuenta cuántas veces se escribió. */
private class FakeAppearanceRepository(initial: Appearance) : AppearanceRepository {
    override val appearance = MutableStateFlow(initial)
    var writes = 0

    override fun save(value: Appearance) {
        appearance.value = value
        writes++
    }
}

class SettingsViewModelTest {

    private fun viewModel(repository: AppearanceRepository) =
        SettingsViewModel(ObserveAppearanceUseCase(repository), SaveAppearanceUseCase(repository))

    @Test
    fun startsWithTheSavedSelection() {
        val repository = FakeAppearanceRepository(Appearance(ThemeMode.Dark, Palette.Coral))

        val vm = viewModel(repository)

        assertEquals(Appearance(ThemeMode.Dark, Palette.Coral), vm.appearance.value)
        assertEquals(0, repository.writes)
    }

    @Test
    fun changingTheThemeKeepsThePaletteAndPersists() {
        val repository = FakeAppearanceRepository(Appearance(ThemeMode.System, Palette.Lime))
        val vm = viewModel(repository)

        vm.setTheme(ThemeMode.Light)

        assertEquals(Appearance(ThemeMode.Light, Palette.Lime), vm.appearance.value)
        assertEquals(Appearance(ThemeMode.Light, Palette.Lime), repository.appearance.value)
        assertEquals(1, repository.writes)
    }

    @Test
    fun changingThePaletteKeepsTheThemeAndPersists() {
        val repository = FakeAppearanceRepository(Appearance(ThemeMode.Dark, Palette.Hope))
        val vm = viewModel(repository)

        vm.setPalette(Palette.Violet)

        assertEquals(Appearance(ThemeMode.Dark, Palette.Violet), vm.appearance.value)
        assertEquals(Appearance(ThemeMode.Dark, Palette.Violet), repository.appearance.value)
        // Otra pantalla, como al volver a abrir la app, trae lo elegido.
        assertEquals(Appearance(ThemeMode.Dark, Palette.Violet), viewModel(repository).appearance.value)
    }
}
