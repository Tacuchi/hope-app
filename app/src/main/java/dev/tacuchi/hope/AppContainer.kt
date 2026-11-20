package dev.tacuchi.hope

import android.content.Context
import dev.tacuchi.hope.data.AssetRepository
import dev.tacuchi.hope.data.HopeDatabase
import dev.tacuchi.hope.importing.AudioExtractor
import dev.tacuchi.hope.importing.ImportStaging
import dev.tacuchi.hope.importing.MediaInspector
import java.io.File

/** Inyección manual: lo que vive mientras vive la app se crea una sola vez aquí. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: HopeDatabase by lazy { HopeDatabase.create(appContext) }

    val assetRepository: AssetRepository by lazy {
        AssetRepository(database, File(appContext.filesDir, "assets"))
    }

    val staging: ImportStaging by lazy {
        ImportStaging(appContext.contentResolver, File(appContext.filesDir, "import-tmp"))
    }

    val inspector: MediaInspector by lazy { MediaInspector() }

    val audioExtractor: AudioExtractor by lazy { AudioExtractor(appContext) }
}
