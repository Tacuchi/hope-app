package dev.tacuchi.hope.ui.assets

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.tacuchi.hope.R
import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.AssetNameKind
import dev.tacuchi.hope.data.AssetOrigin
import dev.tacuchi.hope.data.AssetType
import dev.tacuchi.hope.ui.common.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** «Audio de video 1», «Nota de voz 2», «Audio 3», «Grabación 1», «Imagen 3» o «Fotograma 0:14». */
@Composable
fun assetName(asset: Asset): String = when (asset.nameKind) {
    AssetNameKind.Frame -> stringResource(R.string.asset_name_frame, formatDuration(asset.frameMs ?: 0L))
    AssetNameKind.AudioFromVideo -> stringResource(R.string.asset_name_audio_from_video, asset.number ?: 0)
    AssetNameKind.VoiceNote -> stringResource(R.string.asset_name_voice_note, asset.number ?: 0)
    AssetNameKind.Image -> stringResource(R.string.asset_name_image, asset.number ?: 0)
    AssetNameKind.Audio -> stringResource(R.string.asset_name_audio, asset.number ?: 0)
    AssetNameKind.Recording -> stringResource(R.string.asset_name_recording, asset.number ?: 0)
}

/** «Audio · WhatsApp · 18 nov · 0:52»: tipo, origen, fecha y, en audios, duración. */
@Composable
fun assetSubtitle(asset: Asset): String {
    val parts = buildList {
        add(stringResource(if (asset.type == AssetType.Audio) R.string.assets_filter_audio else R.string.assets_filter_image))
        add(stringResource(originLabel(asset.origin)))
        add(formatShortDate(asset.createdAt))
        asset.durationMs?.let { add(formatDuration(it)) }
    }
    return parts.joinToString(stringResource(R.string.assets_subtitle_separator))
}

@StringRes
fun originLabel(origin: AssetOrigin): Int = when (origin) {
    AssetOrigin.WhatsApp -> R.string.origin_whatsapp
    AssetOrigin.Telegram -> R.string.origin_telegram
    AssetOrigin.TikTok -> R.string.origin_tiktok
    AssetOrigin.Gallery -> R.string.origin_gallery
    AssetOrigin.Files -> R.string.origin_files
    AssetOrigin.Shared -> R.string.origin_shared
    AssetOrigin.Recording -> R.string.origin_recording
}

private val spanish: Locale = Locale.forLanguageTag("es")
private val sameYear = DateTimeFormatter.ofPattern("d MMM", spanish)
private val otherYear = DateTimeFormatter.ofPattern("d MMM yyyy", spanish)

/** «18 nov», con el año solo si no es el actual. */
fun formatShortDate(epochMs: Long, zone: ZoneId = ZoneId.systemDefault(), nowMs: Long = System.currentTimeMillis()): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val formatter = if (date.year == today.year) sameYear else otherYear
    return date.format(formatter).replace(".", "")
}
