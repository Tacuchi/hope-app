package dev.tacuchi.hope.domain.model

import java.io.File

/** Lo que hace falta para que suene una hope: sus archivos, sus ajustes y sus efectos. */
data class MixSpec(
    val background: File? = null,
    val backgroundLengthMs: Long? = null,
    /** De 0 a 100. */
    val backgroundVolume: Int = 100,
    val trimStartMs: Long = 0,
    val trimEndMs: Long? = null,
    val voice: File? = null,
    val voiceLengthMs: Long? = null,
    val voiceVolume: Int = 100,
    val voiceOffsetMs: Long = 0,
    val voiceTrimStartMs: Long = 0,
    val voiceTrimEndMs: Long? = null,
    val backgroundEffects: BackgroundEffects = BackgroundEffects(),
    val voiceEffects: VoiceEffects = VoiceEffects(),
    val hopeEffects: HopeEffects = HopeEffects(),
    val presentation: MixPresentation = MixPresentation(),
) {
    val durationMs: Long
        get() = hopeDurationMs(
            backgroundMs = if (background != null) backgroundLengthMs else null,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            voiceMs = if (voice != null) voiceLengthMs else null,
            voiceOffsetMs = voiceOffsetMs,
            voiceTrimStartMs = voiceTrimStartMs,
            voiceTrimEndMs = voiceTrimEndMs,
            backgroundSpeed = backgroundEffects.speed.speed,
        )

    /** Lo que dura el fondo ya recortado, en el archivo (sin contar la velocidad). */
    val backgroundSpanMs: Long
        get() = backgroundLengthMs?.takeIf { background != null }?.let { trimmedLengthMs(it, trimStartMs, trimEndMs) } ?: 0L

    /** Lo que dura el fondo en la hope: su recorte dividido por su velocidad. */
    val backgroundInHopeMs: Long get() = backgroundSpanMs.inHope(backgroundEffects.speed.speed)

    /** Lo que dura la voz ya recortada. */
    val voiceSpanMs: Long
        get() = voiceLengthMs?.takeIf { voice != null }?.let { trimmedLengthMs(it, voiceTrimStartMs, voiceTrimEndMs) } ?: 0L

    val voiceStartMs: Long get() = voiceOffsetMs.coerceAtLeast(0)
    val voiceEndMs: Long get() = voiceStartMs + voiceSpanMs

    /** El fondo baja solo si hay fondo y voz. */
    val ducks: Boolean get() = hopeEffects.duck != null && background != null && voice != null && voiceSpanMs > 0

    /**
     * La ganancia del fondo en la hope, sin el volumen: sus fundidos, la bajada bajo la voz y el
     * fundido de la hope completa (el fondo empieza en 0, así su tiempo es el de la hope).
     */
    fun backgroundShape(): GainShape = GainShape(
        lengthMs = backgroundInHopeMs,
        fades = backgroundEffects.fades,
        startInHopeMs = 0,
        hopeLengthMs = durationMs,
        hopeFades = hopeEffects.fades,
        dips = if (ducks) {
            val level = checkNotNull(hopeEffects.duck)
            listOf(Dip(voiceStartMs, voiceEndMs, level.level, DuckLevel.ATTACK_MS, DuckLevel.RELEASE_MS))
        } else {
            emptyList()
        },
    )

    /** La ganancia de la voz, sin el volumen: sus fundidos y el fundido de la hope completa. */
    fun voiceShape(): GainShape = GainShape(
        lengthMs = voiceSpanMs,
        fades = voiceEffects.fades,
        startInHopeMs = voiceStartMs,
        hopeLengthMs = durationMs,
        hopeFades = hopeEffects.fades,
    )

    /** Lo mismo salvo los volúmenes: se puede cambiar el volumen sin cortar lo que suena. */
    fun sameTimeline(other: MixSpec): Boolean =
        copy(backgroundVolume = other.backgroundVolume, voiceVolume = other.voiceVolume, presentation = other.presentation) == other
}

/** Un tramo en el que la ganancia baja a [level], con rampas lineales que terminan en [startMs] y empiezan en [endMs]. */
data class Dip(val startMs: Long, val endMs: Long, val level: Double, val attackMs: Long, val releaseMs: Long)

/**
 * La forma de la ganancia de una pista a lo largo de su duración en la hope ([lengthMs]), sin el
 * volumen: su fundido de entrada y de salida, sus bajadas (en tiempo de la hope) y el fundido de la
 * hope completa. Cada rampa es lineal y se acota al largo de lo que funde: la de la pista a la
 * pista, la de la hope a la hope. Una rampa de cero deja la ganancia en 1. Las que se pisan se
 * multiplican.
 */
data class GainShape(
    val lengthMs: Long,
    val fades: Fades = Fades(),
    /** Dónde empieza la pista en la hope. */
    val startInHopeMs: Long = 0,
    val hopeLengthMs: Long = lengthMs,
    val hopeFades: Fades = Fades(),
    val dips: List<Dip> = emptyList(),
) {
    private val fadeInMs = fades.inMs.coerceIn(0, lengthMs)
    private val fadeOutMs = fades.outMs.coerceIn(0, lengthMs)
    private val hopeFadeInMs = hopeFades.inMs.coerceIn(0, hopeLengthMs)
    private val hopeFadeOutMs = hopeFades.outMs.coerceIn(0, hopeLengthMs)

    /** Si nunca deja de valer 1. */
    val isUnity: Boolean get() = fadeInMs == 0L && fadeOutMs == 0L && hopeFadeInMs == 0L && hopeFadeOutMs == 0L && dips.isEmpty()

    /**
     * Donde la forma deja de valer 1, contado desde el inicio de la pista: el inicio de cada
     * fundido de salida y de cada rampa de bajada.
     */
    val unityEndsMs: List<Long> = buildList {
        if (fadeOutMs > 0) add(lengthMs - fadeOutMs)
        if (hopeFadeOutMs > 0) add(hopeLengthMs - hopeFadeOutMs - startInHopeMs)
        dips.forEach { add(it.startMs - it.attackMs - startInHopeMs) }
    }.map { it.coerceAtLeast(0) }.sorted()

    /** La ganancia en [ms] desde el inicio de la pista. */
    fun at(ms: Double): Double {
        var gain = 1.0
        if (fadeInMs > 0 && ms < fadeInMs) gain *= (ms / fadeInMs).coerceIn(0.0, 1.0)
        if (fadeOutMs > 0 && ms > lengthMs - fadeOutMs) gain *= ((lengthMs - ms) / fadeOutMs).coerceIn(0.0, 1.0)
        val hopeMs = ms + startInHopeMs
        if (hopeFadeInMs > 0 && hopeMs < hopeFadeInMs) gain *= (hopeMs / hopeFadeInMs).coerceIn(0.0, 1.0)
        if (hopeFadeOutMs > 0 && hopeMs > hopeLengthMs - hopeFadeOutMs) {
            gain *= ((hopeLengthMs - hopeMs) / hopeFadeOutMs).coerceIn(0.0, 1.0)
        }
        for (dip in dips) gain *= dipAt(dip, hopeMs)
        return gain
    }

    private fun dipAt(dip: Dip, ms: Double): Double {
        val attackStart = (dip.startMs - dip.attackMs).toDouble()
        val releaseEnd = (dip.endMs + dip.releaseMs).toDouble()
        return when {
            ms <= attackStart || ms >= releaseEnd -> 1.0
            ms < dip.startMs -> 1.0 - (1.0 - dip.level) * (ms - attackStart) / dip.attackMs
            ms <= dip.endMs -> dip.level
            else -> dip.level + (1.0 - dip.level) * (ms - dip.endMs) / dip.releaseMs
        }
    }
}

/** Título y portada con los que se muestra lo que suena, por ejemplo en la notificación. */
data class MixPresentation(
    val title: String = "",
    val cover: File? = null,
    val coverEffects: CoverEffects = CoverEffects(),
)

/** Qué está sonando. [key] identifica quién lo pidió: una hope de la biblioteca o el editor. */
data class MixPlayback(
    val key: String? = null,
    val presentation: MixPresentation = MixPresentation(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

/** Cómo suena una hope guardada. El editor arma el mismo [MixSpec] con sus ajustes. */
fun HopeWithAssets.toMixSpec(): MixSpec = MixSpec(
    presentation = MixPresentation(hope.title, cover?.file, hope.coverEffects),
    background = background?.file,
    backgroundLengthMs = background?.durationMs,
    backgroundVolume = hope.backgroundVolume,
    trimStartMs = hope.trimStartMs,
    trimEndMs = hope.trimEndMs,
    voice = voice?.file,
    voiceLengthMs = voice?.durationMs,
    voiceVolume = hope.voiceVolume,
    voiceOffsetMs = hope.voiceOffsetMs,
    voiceTrimStartMs = hope.voiceTrimStartMs,
    voiceTrimEndMs = hope.voiceTrimEndMs,
    backgroundEffects = hope.backgroundEffects,
    voiceEffects = hope.voiceEffects,
    hopeEffects = hope.hopeEffects,
)

/** Clave con la que suena una hope de la biblioteca. */
fun libraryKey(hopeId: Long) = "hope-$hopeId"

/** La hope de la biblioteca que está sonando; nulo si suena otra cosa o nada. */
fun MixPlayback.playingHopeId(): Long? =
    if (isPlaying) key?.removePrefix("hope-")?.toLongOrNull()?.takeIf { key == libraryKey(it) } else null
