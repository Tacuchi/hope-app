package dev.tacuchi.hope.recording

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dev.tacuchi.hope.domain.media.VoiceRecorder
import java.io.File
import java.util.UUID

/** Lo que graba de verdad: el micrófono a un archivo. Separado para probar sin hardware. */
interface AudioCapture {
    fun start(target: File)

    /** Cierra el archivo. Falla si la toma quedó vacía (por ejemplo, si duró casi nada). */
    fun stop()

    /** Suelta el micrófono sin importar en qué estado quedó. */
    fun release()
}

/** AAC dentro de M4A: el mismo formato que el audio extraído de los videos. */
class MediaRecorderCapture(private val context: Context) : AudioCapture {
    private var recorder: MediaRecorder? = null

    override fun start(target: File) {
        release()
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()
        recorder = created
        created.setAudioSource(MediaRecorder.AudioSource.MIC)
        created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        created.setAudioSamplingRate(SAMPLE_RATE)
        created.setAudioEncodingBitRate(BIT_RATE)
        created.setAudioChannels(1)
        created.setOutputFile(target.path)
        created.prepare()
        created.start()
    }

    override fun stop() {
        val current = recorder ?: return
        try {
            current.stop()
        } finally {
            release()
        }
    }

    override fun release() {
        recorder?.release()
        recorder = null
    }

    @Suppress("DEPRECATION")
    private fun legacyRecorder() = MediaRecorder()

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val BIT_RATE = 128_000
    }
}

/**
 * Una toma de voz. Graba a un temporal privado en [tempDir]: terminar entrega el archivo para
 * que pase a ser asset; descartar lo borra y no deja nada.
 */
class MediaVoiceRecorder(private val capture: AudioCapture, private val tempDir: File) : VoiceRecorder {
    private var current: File? = null

    override val isRecording: Boolean
        get() = current != null

    override fun start() {
        discard()
        tempDir.mkdirs()
        val target = File(tempDir, "${UUID.randomUUID()}.m4a")
        current = target
        try {
            capture.start(target)
        } catch (error: Exception) {
            discard()
            throw error
        }
    }

    override fun finish(): File? {
        val target = current ?: return null
        current = null
        return try {
            capture.stop()
            target.takeIf { it.length() > 0 } ?: run { target.delete(); null }
        } catch (_: RuntimeException) {
            target.delete()
            null
        }
    }

    override fun discard() {
        capture.release()
        current?.delete()
        current = null
    }

    override fun discardTake(take: File) {
        take.delete()
    }

    /** Restos de una toma que el sistema cortó. */
    override fun discardLeftovers() {
        if (current == null) tempDir.listFiles()?.forEach { it.delete() }
    }
}
