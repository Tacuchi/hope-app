package dev.tacuchi.hope.recording

import android.media.AudioDeviceInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadphonesTest {

    @Test
    fun wiredUsbAndBluetoothCountAsHeadphones() {
        val speaker = listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE)
        assertFalse(hasHeadphones(speaker))
        assertTrue(hasHeadphones(speaker + AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(hasHeadphones(speaker + AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertTrue(hasHeadphones(speaker + AudioDeviceInfo.TYPE_USB_HEADSET))
        assertTrue(hasHeadphones(speaker + AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(hasHeadphones(speaker + AudioDeviceInfo.TYPE_BLE_HEADSET))
        // Una llamada por Bluetooth (SCO) o un parlante Bluetooth no siempre son auriculares: SCO no cuenta.
        assertFalse(hasHeadphones(speaker + AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
    }
}
