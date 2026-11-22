package dev.tacuchi.hope.data

import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.Hope
import java.io.File

/** El asset del dominio, con su archivo resuelto dentro de [assetsDir]. */
internal fun AssetEntity.toDomain(assetsDir: File) = Asset(
    id = id,
    type = type,
    nameKind = nameKind,
    number = number,
    frameMs = frameMs,
    origin = origin,
    createdAt = createdAt,
    durationMs = durationMs,
    file = File(assetsDir, fileName),
    mimeType = mimeType,
)

internal fun Asset.toEntity() = AssetEntity(
    id = id,
    type = type,
    nameKind = nameKind,
    number = number,
    frameMs = frameMs,
    origin = origin,
    createdAt = createdAt,
    durationMs = durationMs,
    fileName = file.name,
    mimeType = mimeType,
)

internal fun HopeEntity.toDomain() = Hope(
    id = id,
    title = title,
    coverAssetId = coverAssetId,
    backgroundAssetId = backgroundAssetId,
    voiceAssetId = voiceAssetId,
    backgroundVolume = backgroundVolume,
    voiceVolume = voiceVolume,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    voiceOffsetMs = voiceOffsetMs,
    createdAt = createdAt,
    linkUrl = linkUrl,
    linkNetwork = linkNetwork,
    linkTitle = linkTitle,
    linkThumbnailUrl = linkThumbnailUrl,
)

internal fun Hope.toEntity() = HopeEntity(
    id = id,
    title = title,
    coverAssetId = coverAssetId,
    backgroundAssetId = backgroundAssetId,
    voiceAssetId = voiceAssetId,
    backgroundVolume = backgroundVolume,
    voiceVolume = voiceVolume,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    voiceOffsetMs = voiceOffsetMs,
    createdAt = createdAt,
    linkUrl = linkUrl,
    linkNetwork = linkNetwork,
    linkTitle = linkTitle,
    linkThumbnailUrl = linkThumbnailUrl,
)

internal fun AssetUseRow.toDomain() = AssetUse(assetId, hopeId, title)
