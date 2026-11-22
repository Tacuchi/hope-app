package dev.tacuchi.hope.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkNetwork
import dev.tacuchi.hope.domain.model.LinkPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.UnknownHostException

/** org.json es de la plataforma: corre con Robolectric. */
@RunWith(AndroidJUnit4::class)
class OEmbedLinkPreviewsTest {

    /** Respuesta real del oEmbed de YouTube, guardada para no depender de la red. */
    private val sample = javaClass.getResource("/oembed-youtube.json")!!.readText()
    private val youtube = Link("https://youtu.be/jNQXAC9IVRw", LinkNetwork.YouTube)

    @Test
    fun youtubeGivesTitleAndThumbnailFromItsOEmbed() = runBlocking {
        var asked: String? = null
        val preview = OEmbedLinkPreviews { url -> asked = url; sample }.previewOf(youtube)

        assertEquals(LinkPreview(youtube.url, "Me at the zoo", "https://i.ytimg.com/vi/jNQXAC9IVRw/hqdefault.jpg"), preview)
        assertEquals("https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fyoutu.be%2FjNQXAC9IVRw", asked)
    }

    @Test
    fun withoutConnectionOrWithAnErrorThereIsNoPreview() = runBlocking {
        assertNull(OEmbedLinkPreviews { throw UnknownHostException("www.youtube.com") }.previewOf(youtube))
        assertNull(OEmbedLinkPreviews { throw IOException("HTTP 404") }.previewOf(youtube))
        assertNull(OEmbedLinkPreviews { "<html>no es json</html>" }.previewOf(youtube))
        assertNull(OEmbedLinkPreviews { """{"thumbnail_url":"https://i.ytimg.com/x.jpg"}""" }.previewOf(youtube))
    }

    @Test
    fun otherNetworksNeverAskTheNetwork() = runBlocking {
        val previews = OEmbedLinkPreviews { error("no debe consultar la red") }
        listOf(LinkNetwork.TikTok, LinkNetwork.Instagram, LinkNetwork.Generic).forEach { network ->
            assertNull(previews.previewOf(Link("https://ejemplo.com/v", network)))
        }
    }
}
