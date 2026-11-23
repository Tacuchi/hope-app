package dev.tacuchi.hope.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.tacuchi.hope.domain.model.AssetNameKind
import dev.tacuchi.hope.domain.model.AssetOrigin
import dev.tacuchi.hope.domain.model.AssetType
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.CoverFilter
import dev.tacuchi.hope.domain.model.CoverFrame
import dev.tacuchi.hope.domain.model.CoverTexture
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.LinkNetwork
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel

/**
 * Una fila del catálogo. Tabla, columnas e índices son los de la versión 4 del esquema: cambiar
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
    // Desde la versión 4: recorte de la voz y efectos. Los valores por defecto son los de una hope
    // de antes: la voz entera, sin fundidos y sin efectos.
    @ColumnInfo(defaultValue = "0") val voiceTrimStartMs: Long = 0,
    val voiceTrimEndMs: Long? = null,
    @ColumnInfo(defaultValue = "0") val backgroundFadeInMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val backgroundFadeOutMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val voiceFadeInMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val voiceFadeOutMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val hopeFadeInMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val hopeFadeOutMs: Long = 0,
    @ColumnInfo(defaultValue = "'None'") val backgroundRoom: RoomType = RoomType.None,
    @ColumnInfo(defaultValue = "30") val backgroundRoomAmount: Int = RoomEffect.DEFAULT_AMOUNT,
    @ColumnInfo(defaultValue = "'None'") val voiceRoom: RoomType = RoomType.None,
    @ColumnInfo(defaultValue = "30") val voiceRoomAmount: Int = RoomEffect.DEFAULT_AMOUNT,
    @ColumnInfo(defaultValue = "'Normal'") val backgroundSpeed: SpeedLevel = SpeedLevel.Normal,
    @ColumnInfo(defaultValue = "0") val clearVoice: Boolean = false,
    /** Nulo: el fondo no baja bajo la voz. */
    val duck: DuckLevel? = null,
    @ColumnInfo(defaultValue = "'None'") val coverFilter: CoverFilter = CoverFilter.None,
    @ColumnInfo(defaultValue = "70") val coverFilterAmount: Int = CoverEffects.DEFAULT_AMOUNT,
    @ColumnInfo(defaultValue = "'None'") val coverFrame: CoverFrame = CoverFrame.None,
    @ColumnInfo(defaultValue = "'None'") val coverTexture: CoverTexture = CoverTexture.None,
    @ColumnInfo(defaultValue = "30") val coverTextureAmount: Int = CoverEffects.DEFAULT_TEXTURE_AMOUNT,
    @ColumnInfo(defaultValue = "100") val coverZoom: Int = CoverEffects.MIN_ZOOM,
    @ColumnInfo(defaultValue = "0") val coverOffsetX: Int = 0,
    @ColumnInfo(defaultValue = "0") val coverOffsetY: Int = 0,
)

/** Un par asset–hope en uso, con el título de la hope. */
data class AssetUseRow(val assetId: Long, val hopeId: Long, val title: String)
