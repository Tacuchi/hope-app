package dev.tacuchi.hope.domain.model

/**
 * Una hope guarda qué assets usa y cómo mezclarlos; la mezcla ocurre al reproducir. Un asset en
 * uso no se puede borrar, y borrar la hope no borra sus assets.
 */
data class Hope(
    val id: Long = 0,
    val title: String,
    val coverAssetId: Long?,
    val backgroundAssetId: Long?,
    val voiceAssetId: Long?,
    /** De 0 a 100. */
    val backgroundVolume: Int,
    val voiceVolume: Int,
    /** Recorte del fondo, dentro del audio. [trimEndMs] nulo es hasta el final. */
    val trimStartMs: Long,
    val trimEndMs: Long?,
    /** Cuándo entra la voz, contado desde el inicio de la hope. */
    val voiceOffsetMs: Long,
    val createdAt: Long,
    /**
     * El enlace al video original: solo una referencia, nunca se descarga. El título y la URL de
     * la miniatura vienen de la vista previa, cuando la red la da.
     */
    val linkUrl: String? = null,
    val linkNetwork: LinkNetwork? = null,
    val linkTitle: String? = null,
    val linkThumbnailUrl: String? = null,
)

/** Una hope con sus assets resueltos, lista para mostrar o reproducir. */
data class HopeWithAssets(
    val hope: Hope,
    val cover: Asset?,
    val background: Asset?,
    val voice: Asset?,
) {
    val durationMs: Long
        get() = hopeDurationMs(
            backgroundMs = background?.durationMs,
            trimStartMs = hope.trimStartMs,
            trimEndMs = hope.trimEndMs,
            voiceMs = voice?.durationMs,
            voiceOffsetMs = hope.voiceOffsetMs,
        )
}

/**
 * Desde el inicio del recorte hasta que termina la última pista. El fondo dura lo que su
 * recorte; la voz, su desfase más su largo. Una pista ausente no cuenta.
 */
fun hopeDurationMs(
    backgroundMs: Long?,
    trimStartMs: Long,
    trimEndMs: Long?,
    voiceMs: Long?,
    voiceOffsetMs: Long,
): Long {
    val background = backgroundMs?.let { length ->
        val end = (trimEndMs ?: length).coerceIn(0, length)
        (end - trimStartMs.coerceIn(0, end)).coerceAtLeast(0)
    } ?: 0L
    val voice = voiceMs?.let { voiceOffsetMs.coerceAtLeast(0) + it } ?: 0L
    return maxOf(background, voice)
}
