package dev.tacuchi.hope.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AssetType { Audio, Image }

/** Cómo se nombra un asset: por tipo y número, o por el momento del video en un fotograma. */
enum class AssetNameKind(val type: AssetType) {
    AudioFromVideo(AssetType.Audio),
    VoiceNote(AssetType.Audio),
    Image(AssetType.Image),
    Frame(AssetType.Image),
}

/** De dónde vino un asset. La pantalla lo traduce a un texto. */
enum class AssetOrigin { WhatsApp, Telegram, TikTok, Gallery, Files, Shared, Recording }

/**
 * Un asset del catálogo. El nombre visible no se guarda: se arma con [nameKind] y [number] o
 * [frameMs], así sigue el idioma de la app.
 */
@Entity(tableName = "assets", indices = [Index("type", "createdAt")])
data class Asset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AssetType,
    val nameKind: AssetNameKind,
    /** Número dentro de su [nameKind]; nulo en los fotogramas. */
    val number: Int?,
    /** Momento del video del que salió un fotograma. */
    val frameMs: Long?,
    val origin: AssetOrigin,
    val createdAt: Long,
    /** Solo en audios. */
    val durationMs: Long?,
    /** Nombre del archivo dentro de la carpeta privada de assets. */
    @ColumnInfo(name = "fileName") val fileName: String,
    val mimeType: String,
)
