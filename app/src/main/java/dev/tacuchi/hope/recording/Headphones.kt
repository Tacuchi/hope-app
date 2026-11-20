package dev.tacuchi.hope.recording

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Salidas que se escuchan solo por los oídos: con ellas el micrófono no capta el fondo. */
private val HeadphoneTypes = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_HEARING_AID,
    // Bluetooth LE Audio (Android 12 y 13). Son constantes: en versiones anteriores no aparecen.
    26, // AudioDeviceInfo.TYPE_BLE_HEADSET
    30, // AudioDeviceInfo.TYPE_BLE_BROADCAST
)

/** Si alguna de las salidas conectadas son audífonos. */
fun hasHeadphones(outputTypes: Collection<Int>): Boolean = outputTypes.any { it in HeadphoneTypes }

/**
 * Con audífonos el fondo suena mientras se graba, para guiar. Por el altavoz se graba en silencio:
 * si no, el micrófono grabaría el fondo y quedaría duplicado en la mezcla.
 */
fun backgroundPlaysWhileRecording(headphones: Boolean, hasBackground: Boolean): Boolean = headphones && hasBackground

/** Pregunta a la plataforma qué salidas hay. No pide permisos. */
class AudioOutputs(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun headphonesConnected(): Boolean =
        hasHeadphones(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type })
}
