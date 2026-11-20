package dev.tacuchi.hope.importing

/** Lo que se sabe de un archivo después de abrirlo. */
data class MediaInspection(
    val isImage: Boolean,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val durationMs: Long?,
)

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

val ImportDecision.isLong: Boolean
    get() {
        val duration = when (this) {
            is ImportDecision.Audio -> durationMs
            is ImportDecision.VideoWithAudio -> durationMs
            is ImportDecision.VideoWithoutAudio -> durationMs
            else -> null
        }
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
