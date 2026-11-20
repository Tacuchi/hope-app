package dev.tacuchi.hope.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class LinksTest {

    private fun networkOf(text: String) = Links.recognize(text)?.network

    @Test
    fun recognizesEachNetworkIncludingShortLinksShortsAndReels() {
        val expected = mapOf(
            "https://www.tiktok.com/@usuario/video/7311111111111111111" to LinkNetwork.TikTok,
            "https://vm.tiktok.com/ZMabcdEf/" to LinkNetwork.TikTok,
            "https://vt.tiktok.com/ZSabcdEf/" to LinkNetwork.TikTok,
            "https://www.youtube.com/watch?v=jNQXAC9IVRw" to LinkNetwork.YouTube,
            "https://m.youtube.com/watch?v=jNQXAC9IVRw" to LinkNetwork.YouTube,
            "https://youtu.be/jNQXAC9IVRw?si=abc" to LinkNetwork.YouTube,
            "https://youtube.com/shorts/abcdEFGhijk" to LinkNetwork.YouTube,
            "https://www.instagram.com/reel/C1abcDEFghi/?igsh=xyz" to LinkNetwork.Instagram,
            "https://instagram.com/p/C1abcDEFghi/" to LinkNetwork.Instagram,
        )
        expected.forEach { (url, network) -> assertEquals(url, network, networkOf(url)) }
    }

    @Test
    fun anyOtherWebAddressIsAGenericLink() {
        assertEquals(LinkNetwork.Generic, networkOf("https://vimeo.com/76979871"))
        assertEquals(LinkNetwork.Generic, networkOf("http://ejemplo.pe/video"))
        // Un perfil de Instagram no es un video: queda genérico. Un dominio parecido tampoco es la red.
        assertEquals(LinkNetwork.Generic, networkOf("https://www.instagram.com/usuario/"))
        assertEquals(LinkNetwork.Generic, networkOf("https://nottiktok.com/video"))
    }

    @Test
    fun aLinkPastedWithoutTheSchemeOpensWithHttps() {
        val link = Links.recognize("  tiktok.com/@usuario/video/1 ")
        assertEquals(Link("https://tiktok.com/@usuario/video/1", LinkNetwork.TikTok), link)
    }

    @Test
    fun textThatIsNotALinkIsRejected() {
        listOf("", "   ", "hola", "mira esto", "ftp://ejemplo.com/a", "https://", "https://localhost/x", "youtube", "https://you tube.com")
            .forEach { assertNull(it, Links.recognize(it)) }
    }

    @Test
    fun sharedTextGivesItsFirstLinkWithoutTrailingPunctuation() {
        assertEquals(
            Link("https://vm.tiktok.com/ZMabcdEf/", LinkNetwork.TikTok),
            Links.firstIn("Mira este video: https://vm.tiktok.com/ZMabcdEf/. Y este https://youtu.be/x"),
        )
        assertEquals(LinkNetwork.YouTube, Links.firstIn("«Respira» (https://youtu.be/jNQXAC9IVRw)")?.network)
        assertNull(Links.firstIn("Sin enlace aquí"))
    }

    @Test
    fun eachSearchOpensHopecoreOnItsOwnNetworkOverHttps() {
        val domains = mapOf(
            LinkNetwork.TikTok to "www.tiktok.com",
            LinkNetwork.YouTube to "www.youtube.com",
            LinkNetwork.Instagram to "www.instagram.com",
        )
        assertEquals(domains.keys, Links.searches.keys)
        Links.searches.forEach { (network, url) ->
            val uri = URI(url)
            assertEquals("https", uri.scheme)
            assertEquals(domains.getValue(network), uri.host)
            assertTrue(url, url.contains("hopecore"))
        }
    }
}
