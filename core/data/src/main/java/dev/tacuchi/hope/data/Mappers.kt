package dev.tacuchi.hope.data

import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.AssetUse
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.Hope
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.VoiceEffects
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
    voiceTrimStartMs = voiceTrimStartMs,
    voiceTrimEndMs = voiceTrimEndMs,
    backgroundEffects = BackgroundEffects(
        fades = Fades(backgroundFadeInMs, backgroundFadeOutMs),
        room = RoomEffect(backgroundRoom, backgroundRoomAmount),
        speed = backgroundSpeed,
    ),
    voiceEffects = VoiceEffects(
        fades = Fades(voiceFadeInMs, voiceFadeOutMs),
        room = RoomEffect(voiceRoom, voiceRoomAmount),
        clearVoice = clearVoice,
    ),
    hopeEffects = HopeEffects(Fades(hopeFadeInMs, hopeFadeOutMs), duck),
    coverEffects = CoverEffects(
        filter = coverFilter,
        filterAmount = coverFilterAmount,
        frame = coverFrame,
        texture = coverTexture,
        textureAmount = coverTextureAmount,
        zoom = coverZoom,
        offsetX = coverOffsetX,
        offsetY = coverOffsetY,
    ),
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
    voiceTrimStartMs = voiceTrimStartMs,
    voiceTrimEndMs = voiceTrimEndMs,
    backgroundFadeInMs = backgroundEffects.fades.inMs,
    backgroundFadeOutMs = backgroundEffects.fades.outMs,
    voiceFadeInMs = voiceEffects.fades.inMs,
    voiceFadeOutMs = voiceEffects.fades.outMs,
    hopeFadeInMs = hopeEffects.fades.inMs,
    hopeFadeOutMs = hopeEffects.fades.outMs,
    backgroundRoom = backgroundEffects.room.type,
    backgroundRoomAmount = backgroundEffects.room.amount,
    voiceRoom = voiceEffects.room.type,
    voiceRoomAmount = voiceEffects.room.amount,
    backgroundSpeed = backgroundEffects.speed,
    clearVoice = voiceEffects.clearVoice,
    duck = hopeEffects.duck,
    coverFilter = coverEffects.filter,
    coverFilterAmount = coverEffects.filterAmount,
    coverFrame = coverEffects.frame,
    coverTexture = coverEffects.texture,
    coverTextureAmount = coverEffects.textureAmount,
    coverZoom = coverEffects.zoom,
    coverOffsetX = coverEffects.offsetX,
    coverOffsetY = coverEffects.offsetY,
)

internal fun AssetUseRow.toDomain() = AssetUse(assetId, hopeId, title)
