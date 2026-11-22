package dev.tacuchi.hope.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.LinkNetwork

/**
 * Una fila del catálogo. Tabla, columnas e índices son los de la versión 3 del esquema: cambiar
 * uno exige una migración. Los enumerados se guardan por nombre.
 */
@Entity(tableName = "assets", indices = [Index("type", "createdAt")])
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: AssetType,
    val nameKind: AssetNameKind,
    val number: Int?,
    val frameMs: Long?,
    val origin: AssetOrigin,
    val createdAt: Long,
    val durationMs: Long?,
    /** Nombre del archivo dentro de la carpeta privada de assets. */
    @ColumnInfo(name = "fileName") val fileName: String,
    val mimeType: String,
)

/**
 * Una fila de hopes. Las referencias impiden borrar un asset en uso también desde la base
 * ([ForeignKey.RESTRICT]).
 */
@Entity(
    tableName = "hopes",
    foreignKeys = [
        ForeignKey(AssetEntity::class, ["id"], ["coverAssetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AssetEntity::class, ["id"], ["backgroundAssetId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(AssetEntity::class, ["id"], ["voiceAssetId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("coverAssetId"), Index("backgroundAssetId"), Index("voiceAssetId"), Index("createdAt")],
)
data class HopeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val coverAssetId: Long?,
    val backgroundAssetId: Long?,
    val voiceAssetId: Long?,
    val backgroundVolume: Int,
    val voiceVolume: Int,
    val trimStartMs: Long,
    val trimEndMs: Long?,
    val voiceOffsetMs: Long,
    val createdAt: Long,
    val linkUrl: String? = null,
    val linkNetwork: LinkNetwork? = null,
    val linkTitle: String? = null,
    val linkThumbnailUrl: String? = null,
)

/** Un par asset–hope en uso, con el título de la hope. */
data class AssetUseRow(val assetId: Long, val hopeId: Long, val title: String)
