package dev.tacuchi.hope.domain.model

import java.io.File

enum class AssetType { Audio, Image }

/** Cómo se nombra un asset: por tipo y número, o por el momento del video en un fotograma. */
enum class AssetNameKind(val type: AssetType) {
    AudioFromVideo(AssetType.Audio),
    /** Solo audios OGG u Opus, como las notas de voz de WhatsApp. */
    VoiceNote(AssetType.Audio),
    Image(AssetType.Image),
    Frame(AssetType.Image),
    /** Cualquier otro audio importado. */
    Audio(AssetType.Audio),
    /** Una toma grabada en el editor. */
    Recording(AssetType.Audio),
}

/** De dónde vino un asset. La pantalla lo traduce a un texto. */
enum class AssetOrigin { WhatsApp, Telegram, TikTok, Gallery, Files, Shared, Recording }

/**
 * Un asset del catálogo. El nombre visible no se guarda: se arma con [nameKind] y [number] o
 * [frameMs], así sigue el idioma de la app. [file] es su archivo en el almacenamiento privado.
 */
data class Asset(
    val id: Long,
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
    val file: File,
    val mimeType: String,
)

/** Archivo ya preparado (en la carpeta temporal) que pasa a ser un asset. */
data class NewAsset(
    val file: File,
    val nameKind: AssetNameKind,
    val origin: AssetOrigin,
    val mimeType: String,
    val durationMs: Long? = null,
    val frameMs: Long? = null,
)

/** Una hope que usa un asset, para el conteo y el aviso de borrado de Assets. */
data class AssetUse(val assetId: Long, val hopeId: Long, val title: String)

/** Resultado de intentar borrar un asset. */
sealed interface AssetDeletion {
    data object Deleted : AssetDeletion

    /** No se borró: lo usan estas hopes. */
    data class InUse(val hopes: List<AssetUse>) : AssetDeletion
}
