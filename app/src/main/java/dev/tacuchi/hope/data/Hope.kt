package dev.tacuchi.hope.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.tacuchi.hope.links.LinkNetwork

/**
 * Una hope guarda qué assets usa y cómo mezclarlos; la mezcla ocurre al reproducir. Las
 * referencias impiden borrar un asset en uso también desde la base ([ForeignKey.RESTRICT]).
 */
@Entity(
    tableName = "hopes",
    foreignKeys = [
        ForeignKey(Asset::class, ["id"], ["coverAssetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(Asset::class, ["id"], ["backgroundAssetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(Asset::class, ["id"], ["voiceAssetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("coverAssetId"), Index("backgroundAssetId"), Index("voiceAssetId"), Index("createdAt")],
)
data class Hope(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val coverAssetId: Long?,
    val backgroundAssetId: Long?,
    val voiceAssetId: Long?,
    /** De 0 a 100. */
    val backgroundVolume: Int,
    val voiceVolume: Int,
    /** Recorte del fondo, dentro del audio. [trimEndMs] nulo es hasta el final. */
    val trimStartMs: Long,
    val trimEndMs: Long?,
    /** Cuándo entra la voz, contado desde el inicio de la hope. */
    val voiceOffsetMs: Long,
    val createdAt: Long,
    /**
     * El enlace al video original: solo una referencia, nunca se descarga. El título y la URL de
     * la miniatura vienen de la vista previa, cuando la red la da.
     */
    val linkUrl: String? = null,
    val linkNetwork: LinkNetwork? = null,
    val linkTitle: String? = null,
    val linkThumbnailUrl: String? = null,
)

/** Una hope con sus assets resueltos, lista para mostrar o reproducir. */
data class HopeWithAssets(
    val hope: Hope,
    val cover: Asset?,
    val background: Asset?,
    val voice: Asset?,
) {
    val durationMs: Long
        get() = hopeDurationMs(
            backgroundMs = background?.durationMs,
            trimStartMs = hope.trimStartMs,
            trimEndMs = hope.trimEndMs,
            voiceMs = voice?.durationMs,
            voiceOffsetMs = hope.voiceOffsetMs,
        )
}

/**
 * Desde el inicio del recorte hasta que termina la última pista. El fondo dura lo que su
 * recorte; la voz, su desfase más su largo. Una pista ausente no cuenta.
 */
fun hopeDurationMs(
    backgroundMs: Long?,
    trimStartMs: Long,
    trimEndMs: Long?,
    voiceMs: Long?,
    voiceOffsetMs: Long,
): Long {
    val background = backgroundMs?.let { length ->
        val end = (trimEndMs ?: length).coerceIn(0, length)
        (end - trimStartMs.coerceIn(0, end)).coerceAtLeast(0)
    } ?: 0L
    val voice = voiceMs?.let { voiceOffsetMs.coerceAtLeast(0) + it } ?: 0L
    return maxOf(background, voice)
}

/** Una hope que usa un asset, para el conteo y el aviso de borrado de Assets. */
data class AssetUse(val assetId: Long, val hopeId: Long, val title: String)
