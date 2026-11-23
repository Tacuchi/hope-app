package dev.tacuchi.hope.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.ui.R
import dev.tacuchi.hope.ui.theme.TimeStyle

/** «0:17 / 0:48». */
@Composable
fun progressText(positionMs: Long, durationMs: Long): String =
    stringResource(R.string.audio_progress, formatDuration(positionMs), formatDuration(durationMs))

private fun fraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/**
 * Botón redondo de reproducir: en reposo un ▶ contorneado; sonando, ⏸ relleno con un anillo que
 * avanza. Lo usan Assets y el selector de audio.
 */
@Composable
fun PlayRingButton(
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (playing) colors.primary else colors.surface.copy(alpha = 0f),
        contentColor = if (playing) colors.onPrimary else colors.onSurface,
        border = if (playing) null else BorderStroke(1.5.dp, colors.outline),
        modifier = modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (playing) {
                val ring = colors.primary
                val track = colors.outlineVariant
                Canvas(Modifier.size(size)) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        ring.copy(alpha = 1f), -90f, 360f * fraction(positionMs, durationMs), false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Una barra fina de avance con un punto en la cabeza. */
@Composable
fun ProgressLine(positionMs: Long, durationMs: Long, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val value = fraction(positionMs, durationMs)
    Canvas(
        modifier
            .height(8.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
    ) {
        val y = size.height / 2
        val width = 3.dp.toPx()
        drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), width, StrokeCap.Round)
        val head = size.width * value
        drawLine(colors.primary, Offset(0f, y), Offset(head, y), width, StrokeCap.Round)
        drawCircle(colors.primary, radius = 4.dp.toPx(), center = Offset(head, y))
    }
}

/**
 * El botón de escuchar con avance: en reposo ▶ y la duración; sonando ⏸, «0:12 / 0:44» y una
 * pista fina al pie que avanza. Lo usan los pasos del editor y las hojas «Efectos».
 */
@Composable
fun ListenButton(
    title: String,
    subtitle: String?,
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(36.dp),
        color = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics(mergeDescendants = true) { role = Role.Button },
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (playing) progressText(positionMs, durationMs) else formatDuration(durationMs),
                        style = TimeStyle.medium,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                if (playing) ProgressLine(positionMs, durationMs, Modifier.fillMaxWidth().padding(end = 16.dp))
            }
        }
    }
}

/**
 * Un tiempo o una duración que se mueve con − y + (cada uno de 48 dp al menos), con su etiqueta
 * encima. Se usa para el recorte, la entrada de la voz y los fundidos.
 */
@Composable
fun TimeStepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusDescription: String,
    plusDescription: String,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = TimeStyle.large, modifier = Modifier.weight(1f), maxLines = 1)
                StepButton(R.drawable.ic_remove, minusDescription, minusEnabled, onMinus)
                StepButton(R.drawable.ic_add, plusDescription, plusEnabled, onPlus)
            }
        }
    }
}

@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = null) }
    }
}

/** Lo deshabilitado se ve al 38 %, como en Material. */
const val DISABLED_ALPHA = 0.38f
