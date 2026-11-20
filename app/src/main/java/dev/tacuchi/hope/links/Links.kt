package dev.tacuchi.hope.links

import java.net.URI
import java.net.URISyntaxException

/** La red de un enlace. Lo que no es de las tres conocidas es [Generic]: se guarda igual. */
enum class LinkNetwork {
    TikTok,
    YouTube,
    Instagram,
    Generic,
}

/** Un enlace ya validado, con la URL tal como se abre. */
data class Link(val url: String, val network: LinkNetwork)

object Links {

    /** La búsqueda fija de *hopecore* en cada red. */
    val searches: Map<LinkNetwork, String> = mapOf(
        LinkNetwork.TikTok to "https://www.tiktok.com/search?q=hopecore",
        LinkNetwork.YouTube to "https://www.youtube.com/results?search_query=hopecore",
        LinkNetwork.Instagram to "https://www.instagram.com/explore/tags/hopecore/",
    )

    private val urlInText = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    /**
     * Lo pegado en el editor como enlace, o nulo si no lo es. Se acepta sin `https://`
     * («tiktok.com/…») siempre que el sitio tenga dominio; nunca con espacios.
     */
    fun recognize(text: String): Link? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val uri = try {
            URI(withScheme)
        } catch (_: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()?.removeSuffix(".")
        if (scheme != "http" && scheme != "https") return null
        if (host == null || !host.contains('.') || host.startsWith('.')) return null
        return Link(withScheme, networkOf(host, uri.path.orEmpty()))
    }

    /** El primer enlace de un texto compartido («Mira esto https://vm.tiktok.com/…»). */
    fun firstIn(text: String): Link? =
        urlInText.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '»', '\'')?.let(::recognize)

    /** Si [host] es el dominio o uno de sus subdominios. */
    internal fun hostIs(host: String, domain: String) = host == domain || host.endsWith(".$domain")

    private fun networkOf(host: String, path: String): LinkNetwork = when {
        hostIs(host, "tiktok.com") -> LinkNetwork.TikTok
        hostIs(host, "youtube.com") || host == "youtu.be" -> LinkNetwork.YouTube
        hostIs(host, "instagram.com") && (path.startsWith("/reel/") || path.startsWith("/p/")) -> LinkNetwork.Instagram
        else -> LinkNetwork.Generic
    }
}
