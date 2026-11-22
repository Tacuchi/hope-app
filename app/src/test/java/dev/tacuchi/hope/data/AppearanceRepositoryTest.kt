package dev.tacuchi.hope.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceRepositoryTest {
    @Test
    fun newInstallationUsesSystemAndHopeThenRestoresSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppearanceRepository(PreferencesAppearanceStore(context))
        assertEquals(Appearance(ThemeMode.System, Palette.Hope), repository.appearance.value)
        val chosen = Appearance(ThemeMode.Dark, Palette.Coral)
        repository.save(chosen)
        assertEquals(chosen, repository.appearance.value)
        assertEquals(chosen, AppearanceRepository(PreferencesAppearanceStore(context)).appearance.value)
    }
}
