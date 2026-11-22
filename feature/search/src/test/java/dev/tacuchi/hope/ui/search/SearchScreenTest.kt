package dev.tacuchi.hope.ui.search

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.ui.theme.HopeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun opened(network: Int): Intent {
        compose.setContent { HopeTheme { SearchScreen(onBack = {}) } }
        compose.onNodeWithText(app.getString(network)).performClick()
        return shadowOf(app).nextStartedActivity
    }

    @Test
    fun tiktokOpensTheSearchInsideItsApp() {
        val started = opened(R.string.search_tiktok)

        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("tiktok://search?keyword=hopecore", started.dataString)
    }

    @Test
    fun youtubeOpensItsWebSearch() {
        assertEquals("https://www.youtube.com/results?search_query=hopecore", opened(R.string.search_youtube).dataString)
    }

    @Test
    fun instagramOpensTheHopecoreTag() {
        assertEquals("https://www.instagram.com/explore/tags/hopecore/", opened(R.string.search_instagram).dataString)
    }
}
