package dev.tacuchi.hope.ui.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.R

/**
 * Estructura del editor. Grabar, mezclar, reproducir y guardar llegan en fases posteriores:
 * todos sus controles se ven deshabilitados y solo Cerrar responde.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHopeScreen(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
            )
        },
        bottomBar = { EditorActions() },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CoverAndTitle()
            BackgroundTrackCard()
            VoiceTrackCard()
            OutlinedTextField(
                value = "",
                onValueChange = {},
                enabled = false,
                label = { Text(stringResource(R.string.editor_link)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_link), contentDescription = null) },
                supportingText = { Text(stringResource(R.string.editor_link_help)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CoverAndTitle() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_image),
                        contentDescription = stringResource(R.string.editor_cover),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            TextButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.editor_change_frame))
            }
        }
        OutlinedTextField(
            value = "",
            onValueChange = {},
            enabled = false,
            label = { Text(stringResource(R.string.editor_field_title)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BackgroundTrackCard() {
    TrackCard(R.drawable.ic_music_note, R.string.editor_background_track, R.string.editor_background_empty) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = {}, enabled = false) {
                Icon(painterResource(R.drawable.ic_play_arrow), stringResource(R.string.editor_play))
            }
            Spacer(Modifier.width(12.dp))
            Slider(state = remember { SliderState() }, onValueChange = {}, enabled = false, modifier = Modifier.weight(1f))
        }
        VolumeRow(percent = 30)
        HorizontalDivider()
        Text(stringResource(R.string.editor_trim), style = MaterialTheme.typography.titleSmall)
        RangeSlider(
            state = remember { RangeSliderState(0f, 1f) },
            onValueChange = {},
            enabled = false,
        )
        Row {
            val zero = stringResource(R.string.editor_zero_time)
            Text(stringResource(R.string.editor_trim_start, zero), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.editor_trim_end, zero), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VoiceTrackCard() {
    TrackCard(R.drawable.ic_mic, R.string.editor_voice_track, R.string.editor_voice_empty) {
        FilledTonalButton(onClick = {}, enabled = false) {
            Icon(painterResource(R.drawable.ic_mic), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.editor_record))
        }
        VolumeRow(percent = 100)
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.editor_voice_offset),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {}, enabled = false) {
                        Icon(painterResource(R.drawable.ic_remove), stringResource(R.string.editor_offset_less))
                    }
                    Text(stringResource(R.string.editor_zero_time), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = {}, enabled = false) {
                        Icon(painterResource(R.drawable.ic_add), stringResource(R.string.editor_offset_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun VolumeRow(percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.editor_volume), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Slider(
            state = remember(percent) { SliderState(value = percent / 100f) },
            onValueChange = {},
            enabled = false,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.editor_percent, percent), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EditorActions() {
    Column(Modifier.navigationBarsPadding()) {
        HorizontalDivider()
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(painterResource(R.drawable.ic_headphones), contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.editor_listen))
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f).height(56.dp)) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.editor_save))
            }
        }
    }
}
