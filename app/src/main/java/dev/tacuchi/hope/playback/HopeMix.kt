package dev.tacuchi.hope.playback

import dev.tacuchi.hope.data.Asset
import dev.tacuchi.hope.data.HopeWithAssets
import java.io.File

/** Cómo suena una hope guardada. El editor arma el mismo [MixSpec] con sus ajustes. */
fun HopeWithAssets.toMixSpec(fileOf: (Asset) -> File): MixSpec = MixSpec(
    background = background?.let(fileOf),
    backgroundLengthMs = background?.durationMs,
    backgroundVolume = hope.backgroundVolume,
    trimStartMs = hope.trimStartMs,
    trimEndMs = hope.trimEndMs,
    voice = voice?.let(fileOf),
    voiceLengthMs = voice?.durationMs,
    voiceVolume = hope.voiceVolume,
    voiceOffsetMs = hope.voiceOffsetMs,
)

/** Clave con la que suena una hope de la biblioteca. */
fun libraryKey(hopeId: Long) = "hope-$hopeId"
