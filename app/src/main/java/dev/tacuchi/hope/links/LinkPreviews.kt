package dev.tacuchi.hope.links

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Lo que muestra la ficha de un enlace además de su red. La imagen nunca se guarda: solo su URL. */
data class LinkPreview(val url: String, val title: String, val thumbnailUrl: String?)

/**
 * Vista previa de un enlace. Solo YouTube la da por oEmbed sin cuenta; TikTok no trae miniatura e
 * Instagram la retiró, así que esos y los genéricos quedan con la ficha de su red.
 */
class LinkPreviews(private val fetch: suspend (String) -> String = ::httpGet) {

    /** Nulo sin conexión, si YouTube no responde o si la respuesta no sirve: queda la ficha. */
    suspend fun of(link: Link): LinkPreview? {
        if (link.network != LinkNetwork.YouTube) return null
        val endpoint = "https://www.youtube.com/oembed?format=json&url=" + URLEncoder.encode(link.url, "UTF-8")
        return try {
            parse(link.url, fetch(endpoint))
        } catch (error: IOException) {
            Log.i(TAG, "Sin vista previa: ${error.javaClass.simpleName}")
            null
        }
    }

    companion object {
        private const val TAG = "HopeLinks"
        private const val TIMEOUT_MS = 8_000

        /** Título y miniatura de una respuesta de oEmbed; nulo si falta el título. */
        fun parse(url: String, json: String): LinkPreview? = try {
            val body = JSONObject(json)
            val title = body.optString("title").trim()
            val thumbnail = body.optString("thumbnail_url").takeIf { it.startsWith("https://") }
            if (title.isEmpty()) null else LinkPreview(url, title, thumbnail)
        } catch (_: JSONException) {
            null
        }

        /** GET sin caché: nada de la respuesta queda en el teléfono. */
        private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.useCaches = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}
