package dev.tacuchi.hope.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.domain.model.CoverEffects
import dev.tacuchi.hope.domain.model.CoverFilter
import dev.tacuchi.hope.domain.model.CoverFrame
import dev.tacuchi.hope.domain.model.CoverTexture
import dev.tacuchi.hope.domain.model.DuckLevel
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.RoomType
import dev.tacuchi.hope.domain.model.SpeedLevel
import dev.tacuchi.hope.ui.common.CoverImage
import dev.tacuchi.hope.ui.common.ListenButton
import dev.tacuchi.hope.ui.common.TimeStepper
import dev.tacuchi.hope.ui.common.assetName
import dev.tacuchi.hope.ui.common.formatDuration
import dev.tacuchi.hope.ui.R as UiR

/** El marco común de las hojas «Efectos»: título, cierre, contenido y «Cancelar» o «Aceptar». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsSheetFrame(
    title: String,
    subtitle: String?,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                RoundIconButton(UiR.drawable.ic_close, stringResource(UiR.string.action_close), onCancel)
            }
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            footer?.let { Column(Modifier.padding(horizontal = 16.dp), content = it) }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text(stringResource(R.string.effects_cancel), style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Text(stringResource(R.string.effects_accept), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Hoja «Efectos» del fondo, de la voz o de la hope completa. Cada control cambia lo que se está
 * probando; «Escuchar con efectos» lo hace sonar antes de cerrar. «Aceptar» lo pasa al borrador.
 */
@Composable
internal fun AudioEffectsSheet(
    state: EditorUiState,
    sheet: EffectsSheet,
    onBackground: ((dev.tacuchi.hope.domain.model.BackgroundEffects) -> dev.tacuchi.hope.domain.model.BackgroundEffects) -> Unit,
    onVoice: ((dev.tacuchi.hope.domain.model.VoiceEffects) -> dev.tacuchi.hope.domain.model.VoiceEffects) -> Unit,
    onHope: ((dev.tacuchi.hope.domain.model.HopeEffects) -> dev.tacuchi.hope.domain.model.HopeEffects) -> Unit,
    onListen: () -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
    onGoToVoice: () -> Unit,
) {
    val draft = state.draft
    val trying = draft.trying(sheet).toMixSpec()
    val (title, subtitle) = when (sheet.part) {
        EffectsPart.Background -> stringResource(R.string.effects_title_background) to draft.background?.let {
            stringResource(R.string.effects_subtitle_track, assetName(it), formatDuration(draft.trimStartMs), formatDuration(draft.backgroundEndMs))
        }
        EffectsPart.Voice -> stringResource(R.string.effects_title_voice) to draft.voice?.let {
            stringResource(R.string.effects_subtitle_track, assetName(it), formatDuration(draft.voiceTrimStartMs), formatDuration(draft.voiceEndMs))
        }
        else -> stringResource(R.string.effects_title_hope) to stringResource(R.string.effects_subtitle_hope, formatDuration(trying.durationMs))
    }
    val (playingMode, listenDuration, listenSubtitle) = when (sheet.part) {
        EffectsPart.Background -> Triple(EditorPlayback.Background, trying.backgroundInHopeMs, R.string.effects_only_background)
        EffectsPart.Voice -> Triple(EditorPlayback.Voice, trying.voiceSpanMs, R.string.effects_only_voice)
        else -> Triple(EditorPlayback.Mix, trying.durationMs, R.string.effects_whole_mix)
    }
    val playing = state.playback == playingMode
    EffectsSheetFrame(
        title = title,
        subtitle = subtitle,
        onCancel = onCancel,
        onAccept = onAccept,
        footer = {
            ListenButton(
                title = stringResource(if (playing) R.string.effects_listening else R.string.effects_listen),
                subtitle = stringResource(listenSubtitle),
                playing = playing,
                positionMs = state.positionMs,
                durationMs = listenDuration,
                onClick = onListen,
                enabled = listenDuration > 0,
            )
        },
    ) {
        when (sheet.part) {
            EffectsPart.Background -> {
                val effects = sheet.background
                FadesSection(effects.fades, trying.backgroundInHopeMs, initiallyOpen = false) { fades -> onBackground { it.copy(fades = fades) } }
                RoomSection(effects.room, initiallyOpen = true) { room -> onBackground { it.copy(room = room) } }
                var open by rememberSaveable { mutableStateOf(true) }
                EffectSection(
                    stringResource(R.string.effects_speed),
                    "${stringResource(effects.speed.label())} · ${stringResource(effects.speed.factor())}",
                    UiR.drawable.ic_slow_motion_video,
                    open,
                    { open = !open },
                ) {
                    LevelSelector(
                        options = SpeedLevel.entries,
                        selected = effects.speed,
                        label = { stringResource(it.label()) to stringResource(it.factor()) },
                        onSelect = { speed -> onBackground { it.copy(speed = speed) } },
                    )
                    Text(
                        if (effects.speed == SpeedLevel.Normal) {
                            stringResource(R.string.effects_speed_hint_normal)
                        } else {
                            stringResource(R.string.effects_speed_hint, formatDuration(trying.backgroundInHopeMs), formatDuration(trying.backgroundSpanMs))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            EffectsPart.Voice -> {
                val effects = sheet.voice
                FadesSection(effects.fades, trying.voiceSpanMs, initiallyOpen = true) { fades -> onVoice { it.copy(fades = fades) } }
                RoomSection(effects.room, initiallyOpen = false) { room -> onVoice { it.copy(room = room) } }
                var open by rememberSaveable { mutableStateOf(true) }
                EffectSection(
                    stringResource(R.string.effects_clear_voice),
                    stringResource(if (effects.clearVoice) R.string.effects_on else R.string.effects_off),
                    UiR.drawable.ic_record_voice_over,
                    open,
                    { open = !open },
                ) {
                    SwitchRow(
                        stringResource(R.string.effects_clear_voice),
                        stringResource(R.string.effects_clear_voice_hint),
                        effects.clearVoice,
                        { on -> onVoice { it.copy(clearVoice = on) } },
                    )
                }
            }
            else -> {
                val effects = sheet.hope
                val hasVoice = draft.voice != null
                FadesSection(effects.fades, trying.durationMs, initiallyOpen = true) { fades -> onHope { it.copy(fades = fades) } }
                var open by rememberSaveable { mutableStateOf(true) }
                EffectSection(
                    stringResource(R.string.effects_duck),
                    effects.duck?.takeIf { hasVoice }?.let { stringResource(it.label()) } ?: stringResource(R.string.effects_off),
                    UiR.drawable.ic_volume_down,
                    open,
                    { open = !open },
                ) {
                    SwitchRow(
                        stringResource(R.string.effects_duck),
                        stringResource(if (hasVoice) R.string.effects_duck_hint else R.string.effects_duck_needs_voice),
                        checked = hasVoice && effects.duck != null,
                        onChange = { on -> onHope { it.copy(duck = if (on) it.duck ?: DuckLevel.Medium else null) } },
                        enabled = hasVoice,
                    )
                    if (!hasVoice) {
                        TextButton(onClick = onGoToVoice) { Text(stringResource(R.string.effects_go_to_voice)) }
                    } else {
                        LevelSelector(
                            options = DuckLevel.entries,
                            selected = effects.duck,
                            label = { stringResource(it.label()) to null },
                            onSelect = { level -> onHope { it.copy(duck = level) } },
                            enabled = effects.duck != null,
                        )
                        val spec = trying
                        MixLanes(
                            durationMs = spec.durationMs,
                            hopeGainAt = null,
                            background = spec.takeIf { it.background != null }?.let { mix ->
                                val shape = mix.backgroundShape()
                                Lane(null, mix.trimStartMs, draft.backgroundEndMs, 0, mix.backgroundInHopeMs) { ms -> shape.at(ms.toDouble()) }
                            },
                            voice = Lane(null, spec.voiceTrimStartMs, draft.voiceEndMs, spec.voiceStartMs, spec.voiceSpanMs) { 1.0 },
                            playheadMs = null,
                            description = stringResource(R.string.effects_duck_hint),
                            compact = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FadesSection(fades: Fades, maxMs: Long, initiallyOpen: Boolean, onChange: (Fades) -> Unit) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    EffectSection(stringResource(R.string.effects_fades), fadesSummary(fades), UiR.drawable.ic_elevation, open, { open = !open }) {
        val step = Fades.STEP_MS
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeStepper(
                label = stringResource(R.string.effects_fade_in),
                value = seconds(fades.inMs),
                onMinus = { onChange(fades.copy(inMs = (fades.inMs - step).coerceAtLeast(0))) },
                onPlus = { onChange(fades.copy(inMs = (fades.inMs + step).coerceAtMost(maxMs))) },
                minusDescription = stringResource(R.string.effects_fade_in_less),
                plusDescription = stringResource(R.string.effects_fade_in_more),
                minusEnabled = fades.inMs > 0,
                plusEnabled = fades.inMs + step <= maxMs,
                modifier = Modifier.weight(1f),
            )
            TimeStepper(
                label = stringResource(R.string.effects_fade_out),
                value = seconds(fades.outMs),
                onMinus = { onChange(fades.copy(outMs = (fades.outMs - step).coerceAtLeast(0))) },
                onPlus = { onChange(fades.copy(outMs = (fades.outMs + step).coerceAtMost(maxMs))) },
                minusDescription = stringResource(R.string.effects_fade_out_less),
                plusDescription = stringResource(R.string.effects_fade_out_more),
                minusEnabled = fades.outMs > 0,
                plusEnabled = fades.outMs + step <= maxMs,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RoomSection(room: dev.tacuchi.hope.domain.model.RoomEffect, initiallyOpen: Boolean, onChange: (dev.tacuchi.hope.domain.model.RoomEffect) -> Unit) {
    var open by rememberSaveable { mutableStateOf(initiallyOpen) }
    EffectSection(stringResource(R.string.effects_room), roomSummary(room), UiR.drawable.ic_surround_sound, open, { open = !open }) {
        Text(stringResource(R.string.effects_room_type), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoomType.entries.forEach { type ->
                OptionChip(stringResource(type.label()), selected = room.type == type, onClick = { onChange(room.copy(type = type)) })
            }
        }
        LevelSlider(
            label = stringResource(R.string.effects_room_amount),
            percent = room.amount,
            onChange = { onChange(room.copy(amount = it)) },
            enabled = room.type != RoomType.None,
        )
    }
}

private enum class CoverTab { Filter, Frame, Texture, Framing }

/**
 * Hoja «Efectos» de portada: la vista previa con todos los efectos (mantener muestra la
 * original), cuatro pestañas con un punto en las que tienen un efecto activo, y cada opción en
 * miniatura con la portada real. Nada cambia la imagen original.
 */
@Composable
internal fun CoverEffectsSheet(
    cover: java.io.File?,
    effects: CoverEffects,
    onChange: ((CoverEffects) -> CoverEffects) -> Unit,
    onCancel: () -> Unit,
    onAccept: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(CoverTab.Filter) }
    var holding by remember { mutableStateOf(false) }
    EffectsSheetFrame(stringResource(R.string.effects_title_cover), null, onCancel, onAccept) {
        val colors = MaterialTheme.colorScheme
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO)
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    })
                },
        ) {
            if (cover != null) CoverImage(cover, effects, Modifier.fillMaxSize(), showOriginal = holding)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.inverseSurface.copy(alpha = 0.85f),
                contentColor = colors.inverseOnSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(UiR.drawable.ic_compare), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(if (holding) R.string.cover_showing_original else R.string.cover_hold_original),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        CoverTabs(tab, effects) { tab = it }
        when (tab) {
            CoverTab.Filter -> {
                OptionThumbnails(CoverFilter.entries, effects.filter, { stringResource(it.label()) }, { effects.copy(filter = it) }, cover) { f ->
                    onChange { it.copy(filter = f) }
                }
                if (effects.filter != CoverFilter.None) {
                    LevelSlider(stringResource(R.string.cover_filter_amount), effects.filterAmount, { v -> onChange { it.copy(filterAmount = v) } })
                }
            }
            CoverTab.Frame -> OptionThumbnails(CoverFrame.entries, effects.frame, { stringResource(it.label()) }, { effects.copy(frame = it) }, cover) { f ->
                onChange { it.copy(frame = f) }
            }
            CoverTab.Texture -> {
                OptionThumbnails(CoverTexture.entries, effects.texture, { stringResource(it.label()) }, { effects.copy(texture = it) }, cover) { t ->
                    onChange { it.copy(texture = t) }
                }
                if (effects.texture != CoverTexture.None) {
                    LevelSlider(stringResource(R.string.cover_texture_amount), effects.textureAmount, { v -> onChange { it.copy(textureAmount = v) } })
                }
            }
            CoverTab.Framing -> FramingControls(effects, onChange)
        }
        val parts = coverParts(effects)
        Text(
            if (parts.isEmpty()) stringResource(R.string.cover_none_active) else stringResource(R.string.cover_active, parts.joinToString(", ").lowercase()),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

/** La forma de la portada en el editor y la biblioteca. */
internal const val COVER_RATIO = 16f / 10f

@Composable
private fun CoverTabs(selected: CoverTab, effects: CoverEffects, onSelect: (CoverTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(6.dp).selectableGroup()) {
            CoverTab.entries.forEach { tab ->
                val active = when (tab) {
                    CoverTab.Filter -> effects.hasFilter
                    CoverTab.Frame -> effects.frame != CoverFrame.None
                    CoverTab.Texture -> effects.hasTexture
                    CoverTab.Framing -> effects.hasFraming
                }
                val name = stringResource(
                    when (tab) {
                        CoverTab.Filter -> R.string.cover_tab_filter
                        CoverTab.Frame -> R.string.cover_tab_frame
                        CoverTab.Texture -> R.string.cover_tab_texture
                        CoverTab.Framing -> R.string.cover_tab_framing
                    },
                )
                val isSelected = tab == selected
                val description = if (active) stringResource(R.string.cover_tab_active, name) else name
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) colors.secondaryContainer else colors.surfaceContainerHigh,
                    contentColor = if (isSelected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, role = Role.Tab) { onSelect(tab) }
                        .semantics { contentDescription = description },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Box(Modifier.padding(top = 4.dp).size(6.dp).clip(CircleShape).then(if (active) Modifier.background(colors.primary) else Modifier))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> OptionThumbnails(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    preview: (T) -> CoverEffects,
    cover: java.io.File?,
    onSelect: (T) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Column(
                Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (isSelected) 3.dp else 1.dp, if (isSelected) colors.primary else colors.outline),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                ) {
                    Box {
                        if (cover != null) CoverImage(cover, preview(option), Modifier.fillMaxSize(), maxSidePx = 160)
                        if (isSelected) {
                            Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp)) {
                                Icon(painterResource(UiR.drawable.ic_check), null, Modifier.padding(3.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Encuadre accesible: zoom con − y +, cuatro flechas y «Centrar», todos de 48 dp. */
@Composable
private fun FramingControls(effects: CoverEffects, onChange: ((CoverEffects) -> CoverEffects) -> Unit) {
    TimeStepper(
        label = stringResource(R.string.cover_zoom),
        value = stringResource(R.string.cover_zoom_value, effects.zoom),
        onMinus = { onChange { it.zoomedBy(-1) } },
        onPlus = { onChange { it.zoomedBy(1) } },
        minusDescription = stringResource(R.string.cover_zoom_less),
        plusDescription = stringResource(R.string.cover_zoom_more),
        minusEnabled = effects.zoom > CoverEffects.MIN_ZOOM,
        plusEnabled = effects.zoom < CoverEffects.MAX_ZOOM,
        modifier = Modifier.fillMaxWidth(),
    )
    val canMove = effects.hasFraming
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundIconButton(UiR.drawable.ic_arrow_upward, stringResource(R.string.cover_move_up), { onChange { it.movedBy(0, -1) } }, canMove && effects.offsetY > -100)
            Row {
                RoundIconButton(UiR.drawable.ic_arrow_back, stringResource(R.string.cover_move_left), { onChange { it.movedBy(-1, 0) } }, canMove && effects.offsetX > -100)
                Spacer(Modifier.size(48.dp))
                RoundIconButton(UiR.drawable.ic_arrow_forward, stringResource(R.string.cover_move_right), { onChange { it.movedBy(1, 0) } }, canMove && effects.offsetX < 100)
            }
            RoundIconButton(UiR.drawable.ic_arrow_downward, stringResource(R.string.cover_move_down), { onChange { it.movedBy(0, 1) } }, canMove && effects.offsetY < 100)
        }
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange { it.centered() } }, enabled = effects.hasFraming || effects.offsetX != 0 || effects.offsetY != 0, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(UiR.drawable.ic_filter_center_focus), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.cover_center))
            }
            Text(stringResource(R.string.cover_framing_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
