package dev.tacuchi.hope.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.tacuchi.hope.domain.model.BackgroundEffects
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.CoverFilter
import dev.tacuchi.hope.domain.model.CoverFrame
import dev.tacuchi.hope.domain.model.CoverTexture
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.HopeEffects
import dev.tacuchi.hope.domain.model.RoomEffect
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.domain.model.VoiceEffects
import dev.tacuchi.hope.ui.R as UiR

/** «0,5 s», «2 s»: medio segundo es el paso de los fundidos. */
@Composable
internal fun seconds(ms: Long): String {
    val whole = ms / 1_000
    val tenth = (ms % 1_000) / 100
    val number = if (tenth == 0L) "$whole" else "$whole,$tenth"
    return stringResource(R.string.effects_seconds, number)
}

@Composable
private fun separator() = stringResource(UiR.string.assets_subtitle_separator)

@Composable
internal fun fadesSummary(fades: Fades): String =
    if (fades.count == 0) stringResource(R.string.effects_none) else stringResource(R.string.effects_fades_summary, seconds(fades.inMs), seconds(fades.outMs))

@Composable
private fun fadeCount(fades: Fades): String? =
    if (fades.count == 0) null else pluralStringResource(R.plurals.effects_fade_count, fades.count, fades.count)

internal fun RoomType.label(): Int = when (this) {
    RoomType.None -> R.string.room_none
    RoomType.Room -> R.string.room_room
    RoomType.Hall -> R.string.room_hall
    RoomType.Church -> R.string.room_church
}

internal fun SpeedLevel.label(): Int = when (this) {
    SpeedLevel.Normal -> R.string.speed_normal
    SpeedLevel.Slow -> R.string.speed_slow
    SpeedLevel.Slower -> R.string.speed_slower
}

internal fun SpeedLevel.factor(): Int = when (this) {
    SpeedLevel.Normal -> R.string.speed_normal_factor
    SpeedLevel.Slow -> R.string.speed_slow_factor
    SpeedLevel.Slower -> R.string.speed_slower_factor
}

internal fun DuckLevel.label(): Int = when (this) {
    DuckLevel.Little -> R.string.duck_little
    DuckLevel.Medium -> R.string.duck_medium
    DuckLevel.Much -> R.string.duck_much
}

internal fun CoverFilter.label(): Int = when (this) {
    CoverFilter.None -> R.string.filter_none
    CoverFilter.Warm -> R.string.filter_warm
    CoverFilter.Cool -> R.string.filter_cool
    CoverFilter.Mono -> R.string.filter_mono
    CoverFilter.Duotone -> R.string.filter_duotone
}

internal fun CoverFrame.label(): Int = when (this) {
    CoverFrame.None -> R.string.frame_none
    CoverFrame.Thin -> R.string.frame_thin
    CoverFrame.Polaroid -> R.string.frame_polaroid
    CoverFrame.LightTape -> R.string.frame_light_tape
}

internal fun CoverTexture.label(): Int = when (this) {
    CoverTexture.None -> R.string.texture_none
    CoverTexture.Grain -> R.string.texture_grain
    CoverTexture.Vignette -> R.string.texture_vignette
    CoverTexture.Blur -> R.string.texture_blur
}

@Composable
internal fun roomSummary(room: RoomEffect): String =
    if (!room.isOn) stringResource(R.string.room_none) else stringResource(R.string.effects_room_summary, stringResource(room.type.label()), room.amount)

/** «Sala · Lento · 2 fundidos». */
@Composable
internal fun backgroundSummary(effects: BackgroundEffects): String = join(
    listOfNotNull(
        effects.room.takeIf { it.isOn }?.let { stringResource(it.type.label()) },
        effects.speed.takeIf { it != SpeedLevel.Normal }?.let { stringResource(it.label()) },
        fadeCount(effects.fades),
    ),
)

/** «Cuarto · Voz clara · 2 fundidos». */
@Composable
internal fun voiceSummary(effects: VoiceEffects): String = join(
    listOfNotNull(
        effects.room.takeIf { it.isOn }?.let { stringResource(it.type.label()) },
        stringResource(R.string.effects_clear_voice).takeIf { effects.clearVoice },
        fadeCount(effects.fades),
    ),
)

/** «2 fundidos · Fondo baja bajo la voz». */
@Composable
internal fun hopeSummary(effects: HopeEffects, hasVoice: Boolean): String = join(
    listOfNotNull(
        fadeCount(effects.fades),
        stringResource(R.string.effects_duck_summary).takeIf { effects.duck != null && hasVoice },
    ),
)

/** «Cálido · Borde fino · Grano · Zoom 120 %». */
@Composable
internal fun coverSummary(effects: CoverEffects): String = join(coverParts(effects))

@Composable
internal fun coverParts(effects: CoverEffects): List<String> = listOfNotNull(
    effects.filter.takeIf { effects.hasFilter }?.let { stringResource(it.label()) },
    effects.frame.takeIf { it != CoverFrame.None }?.let { stringResource(it.label()) },
    effects.texture.takeIf { effects.hasTexture }?.let { stringResource(it.label()) },
    stringResource(R.string.cover_zoom_summary, effects.zoom).takeIf { effects.hasFraming },
)

@Composable
private fun join(parts: List<String>): String = if (parts.isEmpty()) stringResource(R.string.effects_none) else parts.joinToString(separator())
