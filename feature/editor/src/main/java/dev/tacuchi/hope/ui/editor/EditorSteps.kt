package dev.tacuchi.hope.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.domain.model.Asset
import dev.tacuchi.hope.domain.model.Link
import dev.tacuchi.hope.domain.model.LinkPreview
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.domain.model.WaveState
import dev.tacuchi.hope.ui.common.CoverImage
import dev.tacuchi.hope.ui.common.ListenButton
import dev.tacuchi.hope.ui.common.RemoteThumbnail
import dev.tacuchi.hope.ui.common.TimeStepper
import dev.tacuchi.hope.ui.common.assetName
import dev.tacuchi.hope.ui.common.assetSubtitle
import dev.tacuchi.hope.ui.common.formatDuration
import dev.tacuchi.hope.ui.common.icon
import dev.tacuchi.hope.ui.common.label
import dev.tacuchi.hope.ui.theme.TimeStyle
import dev.tacuchi.hope.ui.R as UiR

// Paso 1 · Portada: título, portada de Assets o importada, sus efectos y el enlace de origen.

@Composable
internal fun CoverStep(state: EditorUiState, actions: EditorActions) {
    val draft = state.draft
    val titleError = state.showErrors && draft.titleMissing
    OutlinedTextField(
        value = draft.title,
        onValueChange = actions.onTitle,
        label = { Text(stringResource(R.string.editor_field_title)) },
        isError = titleError,
        supportingText = if (titleError) {
            { Text(stringResource(R.string.editor_error_title)) }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    SectionTitle(stringResource(R.string.editor_cover)) {
        Text(stringResource(R.string.editor_cover_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    CoverPreview(state, Modifier.fillMaxWidth())
    EffectsButton(
        title = stringResource(R.string.effects),
        summary = coverSummary(draft.coverEffects),
        icon = UiR.drawable.ic_auto_awesome,
        onClick = { actions.onOpenEffects(EffectsPart.Cover) },
        enabled = draft.cover != null,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = { actions.onPick(EditorSlot.Cover) },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        ) {
            Icon(painterResource(UiR.drawable.ic_photo_library), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.editor_pick_from_assets), maxLines = 1)
        }
        OutlinedButton(onClick = actions.onImportCover, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
            Icon(painterResource(UiR.drawable.ic_upload), null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.editor_import), maxLines = 1)
        }
    }
    LinkField(draft, actions.onLink)
    draft.link?.let { link -> LinkCard(link, draft.currentPreview) { actions.onOpenLink(link.url) } }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

/** La portada con sus efectos; sin portada, el lugar vacío. Marca «Con efectos» si los hay. */
@Composable
private fun CoverPreview(state: EditorUiState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHigh, modifier = modifier.aspectRatio(COVER_RATIO)) {
        Box(contentAlignment = Alignment.Center) {
            val file = state.coverFile
            if (file != null) {
                CoverImage(file, state.draft.coverEffects, Modifier.fillMaxSize(), contentDescription = stringResource(R.string.editor_cover))
                if (!state.draft.coverEffects.isEmpty) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.inverseSurface.copy(alpha = 0.85f),
                        contentColor = colors.inverseOnSurface,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(UiR.drawable.ic_auto_awesome), null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(UiR.string.cover_with_effects), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painterResource(UiR.drawable.ic_image), null, tint = colors.primary, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.editor_cover_empty), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/** Un texto que no es enlace se marca al escribirlo, y la hope no se guarda así. */
@Composable
private fun LinkField(draft: EditorDraft, onChange: (String) -> Unit) {
    val invalid = draft.linkInvalid
    OutlinedTextField(
        value = draft.linkText,
        onValueChange = onChange,
        label = { Text(stringResource(R.string.editor_link)) },
        leadingIcon = { Icon(painterResource(UiR.drawable.ic_link), contentDescription = null) },
        isError = invalid,
        supportingText = { Text(stringResource(if (invalid) R.string.editor_link_invalid else R.string.editor_link_help)) },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * La ficha del enlace: su red y, si la red dio vista previa, título y miniatura. Tocarla abre el
 * original. La miniatura se lee de la red y no se guarda; sin conexión queda el ícono de la red.
 */
@Composable
private fun LinkCard(link: Link, preview: LinkPreview?, onOpen: () -> Unit) {
    val network = stringResource(link.network.label())
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(width = 80.dp, height = 60.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(link.network.icon()), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    preview?.thumbnailUrl?.let { RemoteThumbnail(it, Modifier.fillMaxSize()) }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(preview?.title ?: network, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (preview != null) network else link.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(painterResource(UiR.drawable.ic_open_in_new), contentDescription = stringResource(R.string.editor_link_open), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Paso 2 · Fondo: el audio del selector, su recorte sobre la onda, escuchar el tramo y sus efectos.

@Composable
internal fun BackgroundStep(state: EditorUiState, actions: EditorActions) {
    val draft = state.draft
    val background = draft.background
    if (background == null) {
        EmptyTrack(UiR.drawable.ic_music_note, stringResource(R.string.editor_background_empty)) {
            Button(onClick = { actions.onPick(EditorSlot.Background) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(painterResource(UiR.drawable.ic_music_note), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_pick_background))
            }
        }
        if (state.showErrors && draft.tracksMissing) TracksError()
        return
    }
    TrackHeader(background, UiR.drawable.ic_music_note) {
        RoundIconButton(UiR.drawable.ic_swap_horiz, stringResource(R.string.editor_change_audio), { actions.onPick(EditorSlot.Background) })
        RoundIconButton(UiR.drawable.ic_delete, stringResource(R.string.editor_remove_background), actions.onRemoveBackground)
    }
    val mix = draft.toMixSpec()
    val speed = draft.backgroundEffects.speed
    SectionTitle(stringResource(R.string.editor_trim)) {
        Text(
            if (speed == SpeedLevel.Normal) {
                stringResource(R.string.editor_trim_left, formatDuration(mix.backgroundSpanMs))
            } else {
                stringResource(R.string.editor_trim_in_hope, formatDuration(mix.backgroundSpanMs), formatDuration(mix.backgroundInHopeMs))
            },
            style = TimeStyle.small,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val playing = state.playback == EditorPlayback.Background
    TrackTrim(
        wave = state.backgroundWave,
        lengthMs = draft.backgroundLengthMs,
        startMs = draft.trimStartMs,
        endMs = draft.backgroundEndMs,
        fades = draft.backgroundEffects.fades,
        speed = speed.speed,
        // El cabezal va en tiempos del archivo: lo que avanzó la hope por su velocidad.
        playheadMs = if (playing) draft.trimStartMs + (state.positionMs * speed.speed).toLong() else null,
        description = stringResource(R.string.editor_wave_background, formatDuration(draft.trimStartMs), formatDuration(draft.backgroundEndMs)),
        onTrim = actions.onTrim,
        onRetry = actions.onRetryWaves,
    )
    ListenButton(
        title = stringResource(if (playing) R.string.editor_listening_trim else R.string.editor_listen_trim),
        subtitle = stringResource(R.string.editor_with_effects),
        playing = playing,
        positionMs = state.positionMs,
        durationMs = mix.backgroundInHopeMs,
        onClick = actions.onToggleBackground,
    )
    EffectsButton(
        title = stringResource(R.string.effects),
        summary = backgroundSummary(draft.backgroundEffects),
        icon = UiR.drawable.ic_tune,
        onClick = { actions.onOpenEffects(EffectsPart.Background) },
    )
}

// Paso 3 · Voz: grabar en «Grabando voz», su recorte, escuchar el tramo y sus efectos.

@Composable
internal fun VoiceStep(state: EditorUiState, actions: EditorActions) {
    val draft = state.draft
    val voice = draft.voice
    if (voice == null) {
        EmptyTrack(UiR.drawable.ic_mic, stringResource(R.string.editor_voice_empty), stringResource(R.string.editor_voice_empty_hint)) {
            Button(onClick = actions.onRecord, modifier = Modifier.heightIn(min = 56.dp)) {
                Icon(painterResource(UiR.drawable.ic_mic), null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_record))
            }
        }
    } else {
        TrackHeader(voice, UiR.drawable.ic_mic) {
            RoundIconButton(UiR.drawable.ic_mic, stringResource(R.string.editor_record_again), actions.onRecord)
        }
        val mix = draft.toMixSpec()
        SectionTitle(stringResource(R.string.editor_trim)) {
            Text(stringResource(R.string.editor_trim_left, formatDuration(mix.voiceSpanMs)), style = TimeStyle.small, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val playing = state.playback == EditorPlayback.Voice
        TrackTrim(
            wave = state.voiceWave,
            lengthMs = draft.voiceLengthMs,
            startMs = draft.voiceTrimStartMs,
            endMs = draft.voiceEndMs,
            fades = draft.voiceEffects.fades,
            speed = 1f,
            playheadMs = if (playing) draft.voiceTrimStartMs + state.positionMs else null,
            description = stringResource(R.string.editor_wave_voice, formatDuration(draft.voiceTrimStartMs), formatDuration(draft.voiceEndMs)),
            onTrim = actions.onVoiceTrim,
            onRetry = actions.onRetryWaves,
        )
        ListenButton(
            title = stringResource(if (playing) R.string.editor_listening_trim else R.string.editor_listen_trim),
            subtitle = stringResource(R.string.editor_with_effects),
            playing = playing,
            positionMs = state.positionMs,
            durationMs = mix.voiceSpanMs,
            onClick = actions.onToggleVoice,
        )
        EffectsButton(
            title = stringResource(R.string.effects),
            summary = voiceSummary(draft.voiceEffects),
            icon = UiR.drawable.ic_tune,
            onClick = { actions.onOpenEffects(EffectsPart.Voice) },
        )
    }
    if (state.micDenied) {
        Column {
            Text(stringResource(R.string.editor_mic_denied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = actions.onOpenSettings) { Text(stringResource(R.string.editor_open_settings)) }
        }
    }
    if (state.recordingFailed) {
        Text(stringResource(R.string.editor_recording_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** La onda con su recorte, y los tiempos de inicio y fin con − y +. */
@Composable
private fun TrackTrim(
    wave: WaveState?,
    lengthMs: Long,
    startMs: Long,
    endMs: Long,
    fades: dev.tacuchi.hope.domain.model.Fades,
    speed: Float,
    playheadMs: Long?,
    description: String,
    onTrim: (Long, Long) -> Unit,
    onRetry: () -> Unit,
) {
    TrimWave(wave, lengthMs, startMs, endMs, fades, speed, playheadMs, description, onTrim, onRetry)
    val step = EditorViewModel.TRIM_STEP_MS
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeStepper(
            label = stringResource(R.string.editor_trim_start),
            value = formatDuration(startMs),
            onMinus = { onTrim(startMs - step, endMs) },
            onPlus = { onTrim(startMs + step, endMs) },
            minusDescription = stringResource(R.string.editor_trim_start_less),
            plusDescription = stringResource(R.string.editor_trim_start_more),
            minusEnabled = startMs > 0,
            plusEnabled = startMs + EditorViewModel.MIN_SPAN_MS < endMs,
            modifier = Modifier.weight(1f),
        )
        TimeStepper(
            label = stringResource(R.string.editor_trim_end),
            value = formatDuration(endMs),
            onMinus = { onTrim(startMs, endMs - step) },
            onPlus = { onTrim(startMs, endMs + step) },
            minusDescription = stringResource(R.string.editor_trim_end_less),
            plusDescription = stringResource(R.string.editor_trim_end_more),
            minusEnabled = endMs - EditorViewModel.MIN_SPAN_MS > startMs,
            plusEnabled = endMs < lengthMs,
            modifier = Modifier.weight(1f),
        )
    }
    Text(stringResource(R.string.editor_trim_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun TrackHeader(asset: Asset, icon: Int, actions: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(assetName(asset), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(assetSubtitle(asset), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
    }
}

@Composable
private fun EmptyTrack(icon: Int, text: String, hint: String? = null, action: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, colors.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(icon), null, tint = colors.primary, modifier = Modifier.size(40.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            action()
        }
    }
}

@Composable
private fun TracksError() {
    Text(stringResource(R.string.editor_error_tracks), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

// Paso 4 · Mezcla: la vista en carriles, en qué segundo entra la voz, el volumen de cada pista, sus efectos y escuchar.

@Composable
internal fun MixStep(state: EditorUiState, actions: EditorActions) {
    val draft = state.draft
    val mix = draft.toMixSpec()
    val playing = state.playback == EditorPlayback.Mix
    Panel {
        MixLanesFor(state, compact = false, playheadMs = if (playing) state.positionMs else null)
        TimeRuler(mix.durationMs)
    }
    TimeStepper(
        label = stringResource(R.string.editor_voice_offset),
        value = formatDuration(draft.voiceOffsetMs),
        onMinus = { actions.onShiftOffset(-EditorViewModel.OFFSET_STEP_MS) },
        onPlus = { actions.onShiftOffset(EditorViewModel.OFFSET_STEP_MS) },
        minusDescription = stringResource(R.string.editor_offset_less),
        plusDescription = stringResource(R.string.editor_offset_more),
        minusEnabled = draft.voice != null && draft.voiceOffsetMs > 0,
        plusEnabled = draft.voice != null,
        modifier = Modifier.fillMaxWidth(),
    )
    LevelSlider(
        stringResource(R.string.editor_volume_background),
        draft.backgroundVolume,
        actions.onBackgroundVolume,
        icon = UiR.drawable.ic_music_note,
        enabled = draft.background != null,
    )
    LevelSlider(
        stringResource(R.string.editor_volume_voice),
        draft.voiceVolume,
        actions.onVoiceVolume,
        icon = UiR.drawable.ic_mic,
        enabled = draft.voice != null,
    )
    EffectsButton(
        title = stringResource(R.string.effects_hope_button),
        summary = hopeSummary(draft.hopeEffects, draft.voice != null),
        icon = UiR.drawable.ic_tune,
        onClick = { actions.onOpenEffects(EffectsPart.Hope) },
        enabled = !draft.tracksMissing,
    )
    ListenButton(
        title = stringResource(if (playing) R.string.editor_listening_mix else R.string.editor_listen_mix),
        subtitle = stringResource(R.string.editor_with_all_effects),
        playing = playing,
        positionMs = state.positionMs,
        durationMs = mix.durationMs,
        onClick = actions.onListen,
        enabled = !draft.tracksMissing,
    )
    if (state.showErrors && draft.tracksMissing) TracksError()
}

/** Los carriles de la mezcla del borrador, con las ondas que ya se calcularon. */
@Composable
private fun MixLanesFor(state: EditorUiState, compact: Boolean, playheadMs: Long?) {
    val draft = state.draft
    val mix = draft.toMixSpec()
    val hopeShape = dev.tacuchi.hope.domain.model.GainShape(mix.durationMs, hopeFades = mix.hopeEffects.fades)
    val backgroundShape = mix.backgroundShape()
    val voiceShape = mix.voiceShape()
    MixLanes(
        durationMs = mix.durationMs,
        hopeGainAt = { ms -> hopeShape.at(ms.toDouble()) },
        background = draft.background?.let {
            Lane((state.backgroundWave as? WaveState.Ready)?.wave, draft.trimStartMs, draft.backgroundEndMs, 0, mix.backgroundInHopeMs) { ms ->
                backgroundShape.at(ms.toDouble())
            }
        },
        voice = draft.voice?.let {
            Lane((state.voiceWave as? WaveState.Ready)?.wave, draft.voiceTrimStartMs, draft.voiceEndMs, mix.voiceStartMs, mix.voiceSpanMs) { ms ->
                voiceShape.at((ms - mix.voiceStartMs).toDouble())
            }
        },
        playheadMs = playheadMs,
        description = stringResource(
            R.string.editor_mix_description,
            formatDuration(mix.durationMs),
            formatDuration(mix.backgroundInHopeMs),
            formatDuration(mix.voiceStartMs),
        ),
        compact = compact,
    )
}

// Paso 5 · Revisar: la portada con sus efectos, la mezcla completa y cada parte con «Cambiar».

@Composable
internal fun ReviewStep(state: EditorUiState, actions: EditorActions) {
    val draft = state.draft
    val mix = draft.toMixSpec()
    val colors = MaterialTheme.colorScheme
    val effectsOn = !(draft.backgroundEffects.isEmpty && draft.voiceEffects.isEmpty && draft.hopeEffects.isEmpty && draft.coverEffects.isEmpty)
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().aspectRatio(COVER_RATIO)) {
        Box {
            state.coverFile?.let { CoverImage(it, draft.coverEffects, Modifier.fillMaxSize(), contentDescription = stringResource(R.string.editor_cover)) }
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(
                    draft.title.ifBlank { stringResource(R.string.review_untitled) },
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val duration = formatDuration(mix.durationMs)
                val subtitle = when {
                    draft.background != null && draft.voice != null -> stringResource(R.string.review_subtitle_both, duration)
                    draft.background != null -> stringResource(R.string.review_subtitle_background, duration)
                    draft.voice != null -> stringResource(R.string.review_subtitle_voice, duration)
                    else -> null
                }
                subtitle?.let {
                    Text(
                        if (effectsOn) it + stringResource(UiR.string.assets_subtitle_separator) + stringResource(R.string.review_with_effects) else it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }
    val playing = state.playback == EditorPlayback.Mix
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = actions.onListen,
                enabled = !draft.tracksMissing,
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (playing) UiR.drawable.ic_pause else UiR.drawable.ic_play_arrow),
                        contentDescription = stringResource(if (playing) R.string.review_pause else R.string.review_play),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                MixLanesFor(state, compact = true, playheadMs = if (playing) state.positionMs else null)
                Row {
                    Text(formatDuration(if (playing) state.positionMs else 0), style = TimeStyle.small, modifier = Modifier.weight(1f))
                    Text(formatDuration(mix.durationMs), style = TimeStyle.small)
                }
            }
        }
    }
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            val separator = stringResource(UiR.string.assets_subtitle_separator)
            ReviewRow(
                icon = UiR.drawable.ic_image,
                title = stringResource(R.string.review_cover_row),
                detail = listOfNotNull(draft.title.takeIf { it.isNotBlank() }, coverSummary(draft.coverEffects).takeIf { !draft.coverEffects.isEmpty }).joinToString(separator)
                    .ifBlank { stringResource(R.string.review_no_effects) },
                missing = state.showErrors && draft.titleMissing,
                onChange = { actions.onStep(EditorStep.Cover) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            ReviewRow(
                icon = UiR.drawable.ic_music_note,
                title = draft.background?.let { stringResource(R.string.review_background_row, assetName(it)) } ?: stringResource(R.string.step_background),
                detail = draft.background?.let {
                    listOf(
                        stringResource(R.string.review_trim_range, formatDuration(draft.trimStartMs), formatDuration(draft.backgroundEndMs)),
                        backgroundSummary(draft.backgroundEffects).takeIf { !draft.backgroundEffects.isEmpty } ?: stringResource(R.string.review_no_effects),
                    ).joinToString(separator)
                } ?: stringResource(R.string.review_missing),
                missing = state.showErrors && draft.tracksMissing,
                onChange = { actions.onStep(EditorStep.Background) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            ReviewRow(
                icon = UiR.drawable.ic_mic,
                title = draft.voice?.let { stringResource(R.string.review_voice_row, assetName(it)) } ?: stringResource(R.string.step_voice),
                detail = draft.voice?.let {
                    listOf(
                        stringResource(R.string.review_trim_range, formatDuration(draft.voiceTrimStartMs), formatDuration(draft.voiceEndMs)),
                        voiceSummary(draft.voiceEffects).takeIf { !draft.voiceEffects.isEmpty } ?: stringResource(R.string.review_no_effects),
                    ).joinToString(separator)
                } ?: stringResource(R.string.review_missing),
                missing = state.showErrors && draft.tracksMissing,
                onChange = { actions.onStep(EditorStep.Voice) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = colors.outlineVariant)
            ReviewRow(
                icon = UiR.drawable.ic_tune,
                title = stringResource(R.string.review_mix_row),
                detail = listOfNotNull(
                    draft.voice?.let { stringResource(R.string.editor_voice_offset) + " " + formatDuration(draft.voiceOffsetMs) },
                    stringResource(R.string.review_volumes, draft.backgroundVolume, draft.voiceVolume),
                    hopeSummary(draft.hopeEffects, draft.voice != null).takeIf { !draft.hopeEffects.isEmpty },
                ).joinToString(separator),
                missing = false,
                onChange = { actions.onStep(EditorStep.Mix) },
            )
        }
    }
    if (state.showErrors && draft.tracksMissing) TracksError()
    if (state.showErrors && draft.titleMissing) {
        Text(stringResource(R.string.editor_error_title), color = colors.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewRow(icon: Int, title: String, detail: String, missing: Boolean, onChange: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(16.dp), color = if (missing) colors.errorContainer else colors.secondaryContainer, contentColor = if (missing) colors.onErrorContainer else colors.onSecondaryContainer) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (missing) colors.error else colors.onSurfaceVariant)
        }
        val description = stringResource(R.string.review_change_part, title)
        TextButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description }) {
            Text(stringResource(R.string.review_change), style = MaterialTheme.typography.titleSmall)
            Icon(painterResource(UiR.drawable.ic_chevron_right), null)
        }
    }
}
