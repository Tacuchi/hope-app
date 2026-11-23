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
    /** Recorte de la voz, dentro de su audio. [voiceTrimEndMs] nulo es hasta el final. */
    val voiceTrimStartMs: Long = 0,
    val voiceTrimEndMs: Long? = null,
    /** Sin efectos, una hope suena y se ve como en la versión anterior. */
    val backgroundEffects: BackgroundEffects = BackgroundEffects(),
    val voiceEffects: VoiceEffects = VoiceEffects(),
    val hopeEffects: HopeEffects = HopeEffects(),
    val coverEffects: CoverEffects = CoverEffects(),
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
            voiceTrimStartMs = hope.voiceTrimStartMs,
            voiceTrimEndMs = hope.voiceTrimEndMs,
            backgroundSpeed = hope.backgroundEffects.speed.speed,
        )
}

/**
 * Desde el inicio de la hope hasta que termina la última pista. El fondo dura su recorte dividido
 * por su velocidad; la voz, su desfase más su recorte. Una pista ausente no cuenta.
 */
fun hopeDurationMs(
    backgroundMs: Long?,
    trimStartMs: Long,
    trimEndMs: Long?,
    voiceMs: Long?,
    voiceOffsetMs: Long,
    voiceTrimStartMs: Long = 0,
    voiceTrimEndMs: Long? = null,
    backgroundSpeed: Float = 1f,
): Long {
    val background = backgroundMs?.let { trimmedLengthMs(it, trimStartMs, trimEndMs).inHope(backgroundSpeed) } ?: 0L
    val voice = voiceMs?.let { voiceOffsetMs.coerceAtLeast(0) + trimmedLengthMs(it, voiceTrimStartMs, voiceTrimEndMs) } ?: 0L
    return maxOf(background, voice)
}

/** Lo que dura un tramo de un audio de [lengthMs], con sus cortes acotados al archivo. */
fun trimmedLengthMs(lengthMs: Long, startMs: Long, endMs: Long?): Long {
    val end = (endMs ?: lengthMs).coerceIn(0, lengthMs)
    return (end - startMs.coerceIn(0, end)).coerceAtLeast(0)
}

/** Lo que dura en la hope un tramo que suena a [speed]. */
fun Long.inHope(speed: Float): Long = if (speed == 1f) this else Math.round(this / speed.toDouble())
