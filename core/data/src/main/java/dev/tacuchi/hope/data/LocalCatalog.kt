package dev.tacuchi.hope.data

import android.content.Context
import dev.tacuchi.hope.domain.repository.AssetRepository
import dev.tacuchi.hope.domain.repository.HopeRepository
import java.io.File

/**
 * La base y la carpeta de assets de la app, con los nombres de la 0.1.0 («hope.db» y
 * `filesDir/assets/`). Se crea una vez por proceso; quien la usa solo ve los contratos.
 */
class LocalCatalog(context: Context) {
    private val database = HopeDatabase.create(context)
    private val assetsDir = File(context.filesDir, "assets")

    val assets: AssetRepository = RoomAssetRepository(database, assetsDir)
    val hopes: HopeRepository = RoomHopeRepository(database, assetsDir)
}
