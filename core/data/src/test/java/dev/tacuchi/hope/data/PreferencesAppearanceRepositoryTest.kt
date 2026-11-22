package dev.tacuchi.hope.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.domain.model.Appearance
import dev.tacuchi.hope.domain.model.Palette
import dev.tacuchi.hope.domain.model.ThemeMode
import dev.tacuchi.hope.domain.repository.AppearanceRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesAppearanceRepositoryTest {
    @Test
    fun newInstallationUsesSystemAndHopeThenRestoresSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository: AppearanceRepository = PreferencesAppearanceRepository(context)
        assertEquals(Appearance(ThemeMode.System, Palette.Hope), repository.appearance.value)
        val chosen = Appearance(ThemeMode.Dark, Palette.Coral)
        repository.save(chosen)
        assertEquals(chosen, repository.appearance.value)
        assertEquals(chosen, PreferencesAppearanceRepository(context).appearance.value)
    }

    @Test
    fun keepsTheKeysAndValuesOfThePreviousVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit()
            .putString("theme", "Light").putString("palette", "Violet").commit()

        assertEquals(Appearance(ThemeMode.Light, Palette.Violet), PreferencesAppearanceRepository(context).appearance.value)
    }
}
