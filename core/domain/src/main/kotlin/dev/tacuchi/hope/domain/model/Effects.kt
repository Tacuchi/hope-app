package dev.tacuchi.hope.domain.model

import kotlin.math.pow

/**
 * Fundido de entrada y de salida, en milisegundos. Cero es sin fundido. Son rampas lineales de
 * amplitud que se acotan al largo de lo que funden.
 */
data class Fades(val inMs: Long = 0, val outMs: Long = 0) {
    val count: Int get() = (if (inMs > 0) 1 else 0) + (if (outMs > 0) 1 else 0)

    companion object {
        /** Lo que mueve cada toque de − y +. */
        const val STEP_MS = 500L
    }
}

/**
 * Tipos de sala. [t60Seconds] es el tiempo nominal en que la cola baja 60 dB; [damping] apaga
 * antes los agudos; [preDelayMs] separa la señal seca de su cola; [wetAtFull] deja la cola 3 dB
 * por debajo de la señal seca con «Cuánto se nota» al 100 %, para que los tres tipos se noten
 * parecido.
 */
enum class RoomType(val t60Seconds: Double, val damping: Double, val preDelayMs: Double, val wetAtFull: Double) {
    None(0.0, 0.0, 0.0, 0.0),

    /** «Cuarto»: cola de unos 0,4 s. */
    Room(0.5, 0.45, 5.0, 2.81),

    /** «Sala»: cola de unos 1,2 s. */
    Hall(1.5, 0.30, 20.0, 2.24),

    /** «Iglesia»: cola de unos 3 s. */
    Church(3.5, 0.15, 35.0, 1.61),
}

/** Sala de una pista: el tipo y cuánto se nota, de 0 a 100. */
data class RoomEffect(val type: RoomType = RoomType.None, val amount: Int = DEFAULT_AMOUNT) {
    val isOn: Boolean get() = type != RoomType.None && amount > 0

    companion object {
        const val DEFAULT_AMOUNT = 30
    }
}

/** Velocidad del fondo; el tono baja con ella, como un disco más lento. */
enum class SpeedLevel(val speed: Float) {
    /** «Normal 1×». */
    Normal(1f),

    /** «Lento 0,9×». */
    Slow(0.9f),

    /** «Más lento 0,8×». */
    Slower(0.8f),
}

/** Cuánto baja el fondo mientras suena la voz. */
enum class DuckLevel(val db: Double) {
    /** «Poco». */
    Little(-6.0),

    /** «Medio». */
    Medium(-12.0),

    /** «Mucho». */
    Much(-18.0);

    /** Proporción de amplitud. */
    val level: Double get() = 10.0.pow(db / 20)

    companion object {
        /** La bajada termina justo cuando entra la voz: el fondo ya está abajo en la primera sílaba. */
        const val ATTACK_MS = 200L

        /** La subida empieza cuando termina la voz y es más lenta, para que no se note el regreso. */
        const val RELEASE_MS = 500L
    }
}

data class BackgroundEffects(
    val fades: Fades = Fades(),
    val room: RoomEffect = RoomEffect(),
    val speed: SpeedLevel = SpeedLevel.Normal,
) {
    val isEmpty: Boolean get() = fades.count == 0 && !room.isOn && speed == SpeedLevel.Normal
}

data class VoiceEffects(
    val fades: Fades = Fades(),
    val room: RoomEffect = RoomEffect(),
    val clearVoice: Boolean = false,
) {
    val isEmpty: Boolean get() = fades.count == 0 && !room.isOn && !clearVoice
}

/** Los efectos de la hope completa. [duck] nulo: el fondo no baja bajo la voz. */
data class HopeEffects(
    val fades: Fades = Fades(),
    val duck: DuckLevel? = null,
) {
    val isEmpty: Boolean get() = fades.count == 0 && duck == null
}
