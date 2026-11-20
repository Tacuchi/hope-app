package dev.tacuchi.hope.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.net.toUri
import dev.tacuchi.hope.R
import dev.tacuchi.hope.links.LinkNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Abre el enlace en la app de su red, si está instalada, o en el navegador. */
fun openLink(context: Context, url: String) = openFirst(context, listOf(url))

/**
 * Abre el primero de [urls] que alguna app reciba. Se prueba abriendo, sin consultar qué hay
 * instalado: así no hace falta declarar `<queries>`. Si ninguno abre, lo avisa.
 */
fun openFirst(context: Context, urls: List<String>) {
    val opened = openFirst(urls) { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    if (!opened) Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
}

internal fun openFirst(urls: List<String>, start: (String) -> Unit): Boolean = urls.any { url ->
    try {
        start(url)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

@StringRes
fun LinkNetwork.label(): Int = when (this) {
    LinkNetwork.TikTok -> R.string.link_network_tiktok
    LinkNetwork.YouTube -> R.string.link_network_youtube
    LinkNetwork.Instagram -> R.string.link_network_instagram
    LinkNetwork.Generic -> R.string.link_network_generic
}

@DrawableRes
fun LinkNetwork.icon(): Int = when (this) {
    LinkNetwork.TikTok -> R.drawable.ic_smart_display
    LinkNetwork.YouTube -> R.drawable.ic_play_circle
    LinkNetwork.Instagram -> R.drawable.ic_photo_camera
    LinkNetwork.Generic -> R.drawable.ic_link
}

/**
 * La miniatura de un enlace, leída de la red cada vez y solo en memoria: sin caché ni archivo.
 * Sin conexión o si falla no dibuja nada y queda lo que haya debajo.
 */
@Composable
fun RemoteThumbnail(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) { download(url) }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    }
}

private fun download(url: String): Bitmap? {
    if (!url.startsWith("https://")) return null
    val connection = try {
        URL(url).openConnection() as HttpURLConnection
    } catch (error: IOException) {
        return null
    }
    return try {
        connection.useCaches = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.inputStream.use(BitmapFactory::decodeStream)
    } catch (error: IOException) {
        Log.i("HopeLinks", "Sin miniatura: ${error.javaClass.simpleName}")
        null
    } finally {
        connection.disconnect()
    }
}
