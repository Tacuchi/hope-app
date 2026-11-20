package dev.tacuchi.hope.ui.common

import android.content.ActivityNotFoundException
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.links.LinkNetwork
import dev.tacuchi.hope.links.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** ActivityNotFoundException es de la plataforma: corre con Robolectric. */
@RunWith(AndroidJUnit4::class)
class OpenLinkTest {

    @Test
    fun tiktokSearchTriesItsAppFirstAndFallsBackToTheWeb() {
        val tiktok = Links.searchTargets(LinkNetwork.TikTok)
        assertEquals(listOf("tiktok://search?keyword=hopecore", "https://www.tiktok.com/search?q=hopecore"), tiktok)

        val tried = mutableListOf<String>()
        val opened = openFirst(tiktok) { url ->
            tried += url
            if (url.startsWith("tiktok://")) throw ActivityNotFoundException()
        }

        assertTrue(opened)
        assertEquals(tiktok, tried)
    }

    @Test
    fun withTheAppInstalledTheWebIsNotOpened() {
        val tried = mutableListOf<String>()
        openFirst(Links.searchTargets(LinkNetwork.TikTok)) { tried += it }
        assertEquals(listOf("tiktok://search?keyword=hopecore"), tried)
    }

    @Test
    fun youtubeAndInstagramOpenOnlyTheirWebSearch() {
        assertEquals(listOf(Links.searches.getValue(LinkNetwork.YouTube)), Links.searchTargets(LinkNetwork.YouTube))
        assertEquals(listOf(Links.searches.getValue(LinkNetwork.Instagram)), Links.searchTargets(LinkNetwork.Instagram))
    }

    @Test
    fun whenNothingOpensItSaysSo() {
        assertFalse(openFirst(listOf("tiktok://x", "https://x.com")) { throw ActivityNotFoundException() })
    }
}
