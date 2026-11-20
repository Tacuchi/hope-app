package dev.tacuchi.hope.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * En la versión 1 todo audio importado era «Nota de voz». Desde la 2 solo lo son los OGG u Opus:
 * los demás pasan a «Audio». Los dos tipos se vuelven a numerar por fecha, como si hubieran
 * entrado con la regla nueva. Solo cambia el nombre visible: filas y archivos quedan.
 */
class SeparateVoiceNotes : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE assets SET nameKind = 'Audio'
            WHERE nameKind = 'VoiceNote'
              AND lower(mimeType) NOT IN ('audio/ogg', 'audio/opus', 'application/ogg')
              AND fileName NOT LIKE '%.ogg' AND fileName NOT LIKE '%.oga' AND fileName NOT LIKE '%.opus'
            """.trimIndent(),
        )
        for (kind in listOf("Audio", "VoiceNote")) {
            db.execSQL(
                """
                UPDATE assets SET number = (
                    SELECT COUNT(*) FROM assets AS earlier
                    WHERE earlier.nameKind = assets.nameKind
                      AND (earlier.createdAt < assets.createdAt OR (earlier.createdAt = assets.createdAt AND earlier.id <= assets.id))
                )
                WHERE nameKind = ?
                """.trimIndent(),
                arrayOf(kind),
            )
        }
    }
}

/**
 * Versión 2 suma la tabla de hopes y separa «Nota de voz» de «Audio» (ver [SeparateVoiceNotes]).
 * Nunca hay migración destructiva: si una falla, la app no abre en lugar de borrar los assets.
 */
@Database(
    entities = [Asset::class, Hope::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = SeparateVoiceNotes::class)],
)
abstract class HopeDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao

    abstract fun hopeDao(): HopeDao

    companion object {
        fun create(context: Context, name: String = "hope.db"): HopeDatabase =
            Room.databaseBuilder(context, HopeDatabase::class.java, name).build()
    }
}
