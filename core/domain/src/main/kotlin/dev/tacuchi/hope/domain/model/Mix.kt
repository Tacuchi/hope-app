package dev.tacuchi.hope.domain.model

import java.io.File

/** Lo que hace falta para que suene una hope: sus archivos y sus ajustes. */
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
    val presentation: MixPresentation = MixPresentation(),
) {
    val durationMs: Long
        get() = hopeDurationMs(
            backgroundMs = if (background != null) backgroundLengthMs else null,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            voiceMs = if (voice != null) voiceLengthMs else null,
            voiceOffsetMs = voiceOffsetMs,
        )

    /** Lo que dura el fondo ya recortado. */
    val backgroundSpanMs: Long
        get() = hopeDurationMs(backgroundLengthMs.takeIf { background != null }, trimStartMs, trimEndMs, null, 0)

    /** Lo mismo salvo los volúmenes: se puede cambiar el volumen sin cortar lo que suena. */
    fun sameTimeline(other: MixSpec): Boolean =
        copy(backgroundVolume = other.backgroundVolume, voiceVolume = other.voiceVolume, presentation = other.presentation) == other
}

/** Título y portada con los que se muestra lo que suena, por ejemplo en la notificación. */
data class MixPresentation(val title: String = "", val cover: File? = null)

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
    presentation = MixPresentation(hope.title, cover?.file),
    background = background?.file,
    backgroundLengthMs = background?.durationMs,
    backgroundVolume = hope.backgroundVolume,
    trimStartMs = hope.trimStartMs,
    trimEndMs = hope.trimEndMs,
    voice = voice?.file,
    voiceLengthMs = voice?.durationMs,
    voiceVolume = hope.voiceVolume,
    voiceOffsetMs = hope.voiceOffsetMs,
)

/** Clave con la que suena una hope de la biblioteca. */
fun libraryKey(hopeId: Long) = "hope-$hopeId"

/** La hope de la biblioteca que está sonando; nulo si suena otra cosa o nada. */
fun MixPlayback.playingHopeId(): Long? =
    if (isPlaying) key?.removePrefix("hope-")?.toLongOrNull()?.takeIf { key == libraryKey(it) } else null
