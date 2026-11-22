package dev.tacuchi.hope.domain.model

import java.io.File

/** Lo que se sabe de un archivo después de abrirlo. */
data class MediaInspection(
    val isImage: Boolean,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val durationMs: Long?,
)

/**
 * Copia de lo recibido, con lo que dijo quien lo mandó. [mimeType] es el tipo con el que se
 * guarda: el de su extensión, si no el declarado.
 */
data class StagedFile(val file: File, val declaredMime: String?, val displayName: String?, val mimeType: String)

/** El audio que salió de un video, con su duración. */
data class ExtractedAudio(val file: File, val durationMs: Long?)

/** Qué hacer con un archivo recibido. */
sealed interface ImportDecision {
    data object Image : ImportDecision
    data class Audio(val durationMs: Long?) : ImportDecision
    data class VideoWithAudio(val durationMs: Long?) : ImportDecision
    data class VideoWithoutAudio(val durationMs: Long?) : ImportDecision
    data object Unsupported : ImportDecision

    companion object {
        /** Más de esto avisa que puede tardar y ocupar espacio, sin rechazarlo. */
        const val LONG_MEDIA_MS = 10 * 60 * 1000L

        fun from(inspection: MediaInspection): ImportDecision = with(inspection) {
            when {
                isImage -> Image
                hasVideo && hasAudio -> VideoWithAudio(durationMs)
                hasVideo -> VideoWithoutAudio(durationMs)
                hasAudio -> Audio(durationMs)
                else -> Unsupported
            }
        }
    }
}

/** La duración de lo recibido, si es audio o video. */
val ImportDecision.durationMs: Long?
    get() = when (this) {
        is ImportDecision.Audio -> durationMs
        is ImportDecision.VideoWithAudio -> durationMs
        is ImportDecision.VideoWithoutAudio -> durationMs
        else -> null
    }

val ImportDecision.isLong: Boolean
    get() {
        val duration = durationMs
        return duration != null && duration > ImportDecision.LONG_MEDIA_MS
    }

/**
 * Cómo nombrar el formato que llegó en el mensaje de no compatible: la extensión del archivo si
 * la hay, si no el tipo que declaró quien lo mandó.
 */
fun receivedFormatLabel(displayName: String?, declaredMime: String?): String? {
    val extension = displayName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() && it.length <= 5 }
    return when {
        extension != null && declaredMime != null -> "${extension.uppercase()} ($declaredMime)"
        extension != null -> extension.uppercase()
        else -> declaredMime
    }
}

/**
 * Cómo nombrar un audio importado: «Nota de voz» solo si es OGG u Opus, como las notas de voz de
 * WhatsApp; cualquier otro es «Audio».
 */
fun audioNameKind(extension: String, mime: String?): AssetNameKind {
    val oggOrOpus = extension.lowercase() in setOf("ogg", "oga", "opus") ||
        mime?.lowercase() in setOf("audio/ogg", "audio/opus", "application/ogg")
    return if (oggOrOpus) AssetNameKind.VoiceNote else AssetNameKind.Audio
}

/**
 * Origen de lo compartido según el paquete que Android informa como referrer. Si no hay referrer
 * o no es una app conocida, el origen es «Compartido»: mejor genérico que inventado.
 */
object OriginResolver {

    private val knownPackages = mapOf(
        "com.whatsapp" to AssetOrigin.WhatsApp,
        "com.whatsapp.w4b" to AssetOrigin.WhatsApp,
        "org.telegram.messenger" to AssetOrigin.Telegram,
        "org.telegram.messenger.web" to AssetOrigin.Telegram,
        "org.thunderdog.challegram" to AssetOrigin.Telegram,
        "com.zhiliaoapp.musically" to AssetOrigin.TikTok,
        "com.ss.android.ugc.trill" to AssetOrigin.TikTok,
        "com.google.android.apps.photos" to AssetOrigin.Gallery,
    )

    /** [referrer] es lo que da `Activity.getReferrer()`, por ejemplo `android-app://com.whatsapp`. */
    fun fromReferrer(referrer: String?): AssetOrigin {
        val packageName = referrer
            ?.removePrefix("android-app://")
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
            ?: return AssetOrigin.Shared
        return knownPackages[packageName] ?: AssetOrigin.Shared
    }
}
