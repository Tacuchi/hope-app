package dev.tacuchi.hope.playback

import dev.tacuchi.hope.domain.model.GainShape
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel

/** Los tipos de sala de la app, con el nombre que usan las mediciones. */
typealias RoomKind = RoomType

/** Los niveles de velocidad del fondo de la app. */
typealias SlowLevel = SpeedLevel

/**
 * Lo que se espera oír de una pista: el volumen por su forma, calculada aparte de la app. Cambiar
 * [volume] cambia también el volumen en vivo de lo que suena ([live]).
 */
class ExpectedGain internal constructor(private val shape: GainShape, private val live: GainEnvelope?) {
    var volume: Float = live?.volume ?: 1f
        set(value) {
            field = value
            live?.volume = value
        }

    fun shapeAt(ms: Double): Double = shape.at(ms)

    fun gainAt(ms: Double): Double = volume * shape.at(ms)
}
