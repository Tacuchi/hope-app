package dev.tacuchi.hope.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.domain.model.Fades
import dev.tacuchi.hope.domain.model.WaveState
import dev.tacuchi.hope.domain.model.Waveform
import dev.tacuchi.hope.ui.common.formatDuration
import dev.tacuchi.hope.ui.theme.TimeStyle
import kotlin.math.abs
import kotlin.math.roundToLong

private val WaveHeight = 120.dp
private val BarStep = 5.dp

/**
 * La onda de una pista con su recorte: la onda entera del archivo, el tramo elegido resaltado y lo
 * de afuera atenuado (el audio original no cambia), dos asas con su tiempo encima, la envolvente
 * de los fundidos y el cabezal mientras suena. Las asas se arrastran; cada posición se convierte a
 * tiempo del archivo. Si la onda se está calculando, se muestra un esqueleto; si falló, el aviso
 * con «Reintentar».
 */
@Composable
internal fun TrimWave(
    wave: WaveState?,
    lengthMs: Long,
    startMs: Long,
    endMs: Long,
    fades: Fades,
    speed: Float,
    playheadMs: Long?,
    description: String,
    onTrim: (Long, Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val currentTrim by rememberUpdatedState(Triple(startMs, endMs, lengthMs))
    val currentOnTrim by rememberUpdatedState(onTrim)
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(36.dp)) {
            if (lengthMs > 0) {
                val widthPx = constraints.maxWidth.toFloat()
                TimeChip(formatDuration(startMs), widthPx * startMs / lengthMs, widthPx)
                TimeChip(formatDuration(endMs), widthPx * endMs / lengthMs, widthPx)
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().height(WaveHeight)) {
            Box {
                val band = colors.primary
                val dim = colors.onSurfaceVariant.copy(alpha = 0.55f)
                val highlight = colors.secondaryContainer.copy(alpha = 0.55f)
                val handle = colors.primary
                val envelope = colors.onSurface.copy(alpha = 0.55f)
                val head = colors.onSurface
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = description }
                        .pointerInput(lengthMs) {
                            if (lengthMs <= 0) return@pointerInput
                            var dragging = 0
                            detectHorizontalDragGestures(
                                onDragStart = { at ->
                                    val (start, end, length) = currentTrim
                                    val startX = size.width * start.toFloat() / length
                                    val endX = size.width * end.toFloat() / length
                                    dragging = if (abs(at.x - startX) <= abs(at.x - endX)) 1 else 2
                                },
                                onDragEnd = { dragging = 0 },
                                onHorizontalDrag = { change, _ ->
                                    val (start, end, length) = currentTrim
                                    val ms = (change.position.x / size.width * length).roundToLong().coerceIn(0, length)
                                    val snapped = ms / DRAG_SNAP_MS * DRAG_SNAP_MS
                                    if (dragging == 1) currentOnTrim(snapped, end) else currentOnTrim(start, snapped)
                                },
                            )
                        },
                ) {
                    if (lengthMs <= 0) return@Canvas
                    val startX = size.width * startMs / lengthMs
                    val endX = size.width * endMs / lengthMs
                    drawRect(highlight, Offset(startX, 0f), Size(endX - startX, size.height))
                    when (wave) {
                        is WaveState.Ready -> drawBars(wave.wave, startX, endX, band, dim, lengthMs)
                        WaveState.Loading, null -> drawSkeleton(dim)
                        WaveState.Failed -> Unit
                    }
                    drawFadeEnvelope(fades, speed, startMs, endMs, lengthMs, envelope)
                    playheadMs?.let { ms ->
                        val x = size.width * ms.coerceIn(0, lengthMs) / lengthMs
                        drawLine(head, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                    }
                    listOf(startX, endX).forEach { x -> drawHandle(x, handle, colors.surfaceContainerHigh) }
                }
                if (wave is WaveState.Failed) {
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.editor_wave_failed), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.editor_wave_retry)) }
                    }
                } else if (wave == WaveState.Loading) {
                    Text(
                        stringResource(R.string.editor_wave_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    )
                }
            }
        }
        if (lengthMs > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                (0..4).forEach { i ->
                    Text(
                        formatDuration(lengthMs * i / 4),
                        style = TimeStyle.small,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = when (i) {
                            0 -> androidx.compose.ui.text.style.TextAlign.Start
                            4 -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        },
                    )
                }
            }
        }
    }
}

/** Al arrastrar, los cortes caen en décimas de segundo. */
private const val DRAG_SNAP_MS = 100L

@Composable
private fun TimeChip(text: String, centerPx: Float, widthPx: Float) {
    val density = LocalDensity.current
    val chipWidth = with(density) { 56.dp.toPx() }
    val left = (centerPx - chipWidth / 2).coerceIn(0f, (widthPx - chipWidth).coerceAtLeast(0f))
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.offset(x = with(density) { left.toDp() }).padding(top = 2.dp),
    ) {
        Box(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), contentAlignment = Alignment.Center) { Text(text, style = TimeStyle.small) }
    }
}

private fun DrawScope.drawBars(wave: Waveform, startX: Float, endX: Float, band: Color, dim: Color, lengthMs: Long) {
    val step = BarStep.toPx()
    val maxPeak = (wave.peaks.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    val middle = size.height / 2
    var x = step / 2
    while (x < size.width) {
        val fromMs = ((x - step / 2) / size.width * lengthMs).toLong()
        val toMs = ((x + step / 2) / size.width * lengthMs).toLong()
        var peak = 0f
        var ms = fromMs
        while (ms <= toMs) {
            peak = maxOf(peak, wave.peakAt(ms))
            ms += (lengthMs / Waveform.BINS).coerceAtLeast(1)
        }
        val half = (peak / maxPeak).coerceIn(0.03f, 1f) * (middle - 8.dp.toPx())
        val color = if (x in startX..endX) band else dim
        drawLine(color, Offset(x, middle - half), Offset(x, middle + half), 3.dp.toPx(), StrokeCap.Round)
        x += step
    }
}

private fun DrawScope.drawSkeleton(color: Color) {
    val step = BarStep.toPx()
    val middle = size.height / 2
    var x = step / 2
    var i = 0
    while (x < size.width) {
        val half = (0.25f + 0.2f * ((i * 37) % 7) / 7f) * middle
        drawLine(color.copy(alpha = 0.3f), Offset(x, middle - half), Offset(x, middle + half), 3.dp.toPx(), StrokeCap.Round)
        x += step
        i++
    }
}

/** Los fundidos sobre el tramo, en tiempos del archivo: un fundido de la hope a velocidad lenta abarca menos archivo. */
private fun DrawScope.drawFadeEnvelope(fades: Fades, speed: Float, startMs: Long, endMs: Long, lengthMs: Long, color: Color) {
    if (fades.count == 0) return
    val span = (endMs - startMs).coerceAtLeast(1)
    val fadeIn = (fades.inMs * speed).toLong().coerceIn(0, span)
    val fadeOut = (fades.outMs * speed).toLong().coerceIn(0, span)
    fun x(ms: Long) = size.width * ms / lengthMs
    val top = 8.dp.toPx()
    val bottom = size.height - 8.dp.toPx()
    val path = Path().apply {
        moveTo(x(startMs), if (fadeIn > 0) bottom else top)
        lineTo(x(startMs + fadeIn), top)
        lineTo(x(endMs - fadeOut), top)
        lineTo(x(endMs), if (fadeOut > 0) bottom else top)
    }
    drawPath(path, color, style = Stroke(2.dp.toPx()))
}

private fun DrawScope.drawHandle(x: Float, color: Color, inner: Color) {
    drawLine(color, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
    val w = 18.dp.toPx()
    val h = 40.dp.toPx()
    val top = size.height / 2 - h / 2
    drawRoundRect(color, Offset(x - w / 2, top), Size(w, h), CornerRadius(w / 2))
    drawRoundRect(inner, Offset(x - w / 2 + 2.dp.toPx(), top + 2.dp.toPx()), Size(w - 4.dp.toPx(), h - 4.dp.toPx()), CornerRadius(w / 2))
    listOf(-3.dp.toPx(), 3.dp.toPx()).forEach { dx ->
        drawLine(color, Offset(x + dx, top + 12.dp.toPx()), Offset(x + dx, top + h - 12.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
    }
}

/** Una pista en la línea de tiempo de la hope: su onda recortada, dónde empieza y cuánto dura. */
internal data class Lane(
    val wave: Waveform?,
    val fileStartMs: Long,
    val fileEndMs: Long,
    val startInHopeMs: Long,
    val lengthInHopeMs: Long,
    /** La ganancia de 0 a 1 en un instante de la hope (fundidos y bajada), para dibujar su envolvente. */
    val gainAt: (Long) -> Double,
)

/**
 * La mezcla en carriles: la hope completa con sus fundidos, el fondo con su bajada bajo la voz y
 * la voz desde su entrada, con puntadas hasta ella. Con [playheadMs], el cabezal. Misma escala
 * para todos: posición = segundo / duración.
 */
@Composable
internal fun MixLanes(
    durationMs: Long,
    hopeGainAt: ((Long) -> Double)?,
    background: Lane?,
    voice: Lane?,
    playheadMs: Long?,
    description: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val laneHeight = if (compact) 36.dp else 56.dp
    val hopeHeight = if (compact) 0.dp else 40.dp
    val gap = 8.dp
    val primary = colors.primary
    val ink = colors.onSurface
    val dim = colors.onSurfaceVariant
    val fill = colors.secondaryContainer
    val lane = colors.surfaceContainerHighest
    Canvas(
        modifier
            .fillMaxWidth()
            .height(hopeHeight + laneHeight * 2 + gap * (if (compact) 1 else 2))
            .semantics { contentDescription = description },
    ) {
        if (durationMs <= 0) return@Canvas
        fun x(ms: Long) = size.width * ms.coerceIn(0, durationMs) / durationMs
        var top = 0f
        if (!compact && hopeGainAt != null) {
            val h = hopeHeight.toPx()
            val path = Path().apply {
                moveTo(0f, top + h)
                var ms = 0L
                val step = (durationMs / 120).coerceAtLeast(1)
                while (ms <= durationMs) {
                    lineTo(x(ms), top + h - (h - 4.dp.toPx()) * hopeGainAt(ms).toFloat())
                    ms += step
                }
                lineTo(size.width, top + h)
                close()
            }
            drawPath(path, fill)
            drawPath(path, primary, style = Stroke(2.dp.toPx()))
            top += h + gap.toPx()
        }
        listOf(background, voice).forEachIndexed { index, track ->
            val h = laneHeight.toPx()
            drawRoundRect(lane, Offset(0f, top), Size(size.width, h), CornerRadius(12.dp.toPx()))
            if (track != null && track.lengthInHopeMs > 0) {
                val left = x(track.startInHopeMs)
                val right = x(track.startInHopeMs + track.lengthInHopeMs)
                if (index == 1 && track.startInHopeMs > 0) {
                    drawLine(
                        dim, Offset(4.dp.toPx(), top + h / 2), Offset(left - 4.dp.toPx(), top + h / 2), 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    )
                }
                drawRoundRect(fill, Offset(left, top), Size(right - left, h), CornerRadius(12.dp.toPx()))
                drawRoundRect(primary, Offset(left, top), Size(right - left, h), CornerRadius(12.dp.toPx()), style = Stroke(2.dp.toPx()))
                val middle = top + h / 2
                val step = BarStep.toPx()
                var bx = left + step
                while (bx < right - step / 2) {
                    val hopeMs = ((bx / size.width) * durationMs).toLong()
                    val fraction = (hopeMs - track.startInHopeMs).toDouble() / track.lengthInHopeMs
                    val fileMs = track.fileStartMs + (fraction * (track.fileEndMs - track.fileStartMs)).toLong()
                    val peak = track.wave?.let { w -> (w.peakAt(fileMs) / (w.peaks.maxOrNull() ?: 1f).coerceAtLeast(0.0001f)) } ?: 0.4f
                    val half = peak.coerceIn(0.05f, 1f) * (h / 2 - 6.dp.toPx())
                    drawLine(if (index == 0) primary else ink, Offset(bx, middle - half), Offset(bx, middle + half), 2.dp.toPx(), StrokeCap.Round)
                    bx += step
                }
                // La envolvente de la pista: fundidos y, en el fondo, la bajada bajo la voz.
                val envelope = Path()
                var ms = track.startInHopeMs
                val end = track.startInHopeMs + track.lengthInHopeMs
                val envStep = (track.lengthInHopeMs / 80).coerceAtLeast(1)
                envelope.moveTo(x(ms), top + h - 4.dp.toPx() - (h - 8.dp.toPx()) * track.gainAt(ms).toFloat())
                while (ms <= end) {
                    envelope.lineTo(x(ms), top + h - 4.dp.toPx() - (h - 8.dp.toPx()) * track.gainAt(ms).toFloat())
                    ms += envStep
                }
                drawPath(envelope, ink.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
            }
            top += h + gap.toPx()
        }
        playheadMs?.let {
            drawLine(ink, Offset(x(it), 0f), Offset(x(it), size.height), 2.dp.toPx())
        }
    }
}

/** La regla de tiempo bajo los carriles. */
@Composable
internal fun TimeRuler(durationMs: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        (0..4).forEach { i ->
            Text(
                formatDuration(durationMs * i / 4),
                style = TimeStyle.small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = when (i) {
                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                    4 -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                },
            )
        }
    }
}
