package dev.tacuchi.hope

import android.content.Context
import android.content.Intent
import dev.tacuchi.hope.data.AppearanceRepository
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.HopeDatabase
import dev.tacuchi.hope.data.HopeRepository
import dev.tacuchi.hope.importing.AudioExtractor
import dev.tacuchi.hope.importing.ImportStaging
import dev.tacuchi.hope.importing.MediaInspector
import dev.tacuchi.hope.links.LinkPreviews
import dev.tacuchi.hope.playback.MixPlayer
import dev.tacuchi.hope.playback.PlaybackService
import dev.tacuchi.hope.recording.AudioOutputs
import dev.tacuchi.hope.recording.MediaRecorderCapture
import dev.tacuchi.hope.recording.VoiceRecorder
import java.io.File

/** Inyección manual: lo que vive mientras vive la app se crea una sola vez aquí. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appearanceRepository by lazy { AppearanceRepository(appContext) }

    val database: HopeDatabase by lazy { HopeDatabase.create(appContext) }

    val assetRepository: AssetRepository by lazy {
        AssetRepository(database, File(appContext.filesDir, "assets"))
    }

    val hopeRepository: HopeRepository by lazy { HopeRepository(database) }

    val staging: ImportStaging by lazy {
        ImportStaging(appContext.contentResolver, File(appContext.filesDir, "import-tmp"))
    }

    val inspector: MediaInspector by lazy { MediaInspector() }

    val audioExtractor: AudioExtractor by lazy { AudioExtractor(appContext) }

    /** El único reproductor de hopes, para el editor y la biblioteca. */
    val mixPlayer: MixPlayer by lazy {
        MixPlayer(appContext, null) {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        }
    }

    val voiceRecorder: VoiceRecorder by lazy {
        VoiceRecorder(MediaRecorderCapture(appContext), File(appContext.filesDir, "recording-tmp"))
    }

    val audioOutputs: AudioOutputs by lazy { AudioOutputs(appContext) }

    val linkPreviews: LinkPreviews by lazy { LinkPreviews() }
}
