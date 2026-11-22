package dev.tacuchi.hope.domain.media

import java.io.File

/** Una toma de voz a un archivo temporal privado. */
interface VoiceRecorder {
    val isRecording: Boolean

    fun start()

    /** Termina la toma. Nulo si no hay toma o quedó vacía (y entonces no deja archivo). */
    fun finish(): File?

    /** Suelta el micrófono y borra la toma en curso. */
    fun discard()

    /** Borra una toma ya terminada que no pasó a ser asset. */
    fun discardTake(take: File)

    /** Restos de una toma que el sistema cortó. */
    fun discardLeftovers()
}

/** Las salidas de audio conectadas. */
interface AudioOutputs {
    fun headphonesConnected(): Boolean
}
