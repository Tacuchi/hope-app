package dev.tacuchi.hope.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.R
import dev.tacuchi.hope.ui.theme.HopeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun privacyPolicyOpensThePublishedPage() {
        compose.setContent { HopeTheme { SettingsScreen(onBack = {}, onLicenses = {}) } }

        compose.onNodeWithText(context.getString(R.string.privacy_policy)).performScrollTo().performClick()

        val started = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://tacuchi.github.io/hope-app/privacidad/", started.dataString)
    }
}
