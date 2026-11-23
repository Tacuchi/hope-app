package dev.tacuchi.hope.ui.editor

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tacuchi.hope.ui.common.DISABLED_ALPHA
import dev.tacuchi.hope.ui.theme.TimeStyle
import kotlin.math.roundToInt
import dev.tacuchi.hope.ui.R as UiR

/** El nombre de cada paso. */
internal fun EditorStep.label(): Int = when (this) {
    EditorStep.Cover -> R.string.step_cover
    EditorStep.Background -> R.string.step_background
    EditorStep.Voice -> R.string.step_voice
    EditorStep.Mix -> R.string.step_mix
    EditorStep.Review -> R.string.step_review
}

/**
 * Indicador de paso «hilván»: cinco tramos con su etiqueta centrada debajo. Un paso hecho es un
 * tramo lleno con ✓ y se puede tocar; el actual, una cinta más gruesa que se aclara hacia el
 * centro; los que faltan, puntadas discontinuas.
 */
@Composable
internal fun StepIndicator(current: EditorStep, reached: EditorStep, onStep: (EditorStep) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        EditorStep.entries.forEach { step ->
            val isCurrent = step == current
            val done = !isCurrent && step.ordinal <= reached.ordinal
            val name = stringResource(step.label())
            val state = stringResource(
                when {
                    isCurrent -> R.string.step_current
                    done -> R.string.step_done
                    else -> R.string.step_pending
                },
                name,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = isCurrent, enabled = done, role = Role.Tab) { onStep(step) }
                    .semantics(mergeDescendants = true) { contentDescription = state },
            ) {
                val ribbon = colors.primary
                val light = colors.onPrimary
                val stitch = colors.outline
                Canvas(Modifier.fillMaxWidth().height(10.dp)) {
                    val y = size.height / 2
                    when {
                        isCurrent -> {
                            val brush = Brush.horizontalGradient(listOf(ribbon, lerpColor(ribbon, light, 0.55f), ribbon))
                            drawRoundRect(brush, Offset(0f, y - 4.dp.toPx()), Size(size.width, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
                        }
                        done -> drawLine(ribbon, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), 4.dp.toPx(), StrokeCap.Round)
                        else -> drawLine(
                            stitch, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (done) {
                        Icon(painterResource(UiR.drawable.ic_check), null, tint = colors.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun lerpColor(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color, t: Float) =
    androidx.compose.ui.graphics.lerp(a, b, t)

/** Una tarjeta de la superficie del editor. */
@Composable
internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** El botón «Efectos» de cada paso, con el resumen de lo activo. */
@Composable
internal fun EffectsButton(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        border = BorderStroke(1.5.dp, colors.outline),
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(16.dp), color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(painterResource(icon), null) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(painterResource(UiR.drawable.ic_expand_less), null, tint = colors.onSurfaceVariant)
        }
    }
}

/** Una sección plegable de una hoja «Efectos»: nombre y resumen a la derecha; abierta muestra sus controles. */
@Composable
internal fun EffectSection(
    title: String,
    summary: String,
    @DrawableRes icon: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            val action = stringResource(if (expanded) R.string.effects_collapse else R.string.effects_expand, title)
            Surface(
                onClick = onToggle,
                color = colors.surfaceContainerHigh,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.semantics { stateDescription = summary; contentDescription = "$title. $action" },
            ) {
                Row(Modifier.padding(horizontal = 16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(icon), null, tint = colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(if (expanded) UiR.drawable.ic_expand_less else UiR.drawable.ic_expand_more), null, tint = colors.onSurfaceVariant)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            }
        }
    }
}

/** Una opción tipo chip: la elegida lleva fondo de contenedor y ✓. */
@Composable
internal fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
        border = if (selected) null else BorderStroke(1.5.dp, colors.outline),
        modifier = modifier
            .heightIn(min = 48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (selected) {
                Icon(painterResource(UiR.drawable.ic_check), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Tres (o más) segmentos, con el elegido en el color principal. */
@Composable
internal fun <T> LevelSelector(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> Pair<String, String?>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, colors.outline),
        modifier = modifier.fillMaxWidth().alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Row(Modifier.selectableGroup()) {
            options.forEach { option ->
                val isSelected = option == selected
                val (title, detail) = label(option)
                Surface(
                    color = if (isSelected) colors.primary else colors.surfaceContainerHigh,
                    contentColor = if (isSelected) colors.onPrimary else colors.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton) { onSelect(option) },
                ) {
                    Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        detail?.let { Text(it, style = TimeStyle.small) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** Volumen o intensidad: barra gruesa con su nivel y un asa vertical; el valor en Manrope. */
@Composable
internal fun LevelSlider(
    label: String,
    percent: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(painterResource(it), null, tint = colors.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
            }
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.editor_percent, percent), style = TimeStyle.medium)
        }
        val sliderState = remember { SliderState(percent / 100f) }
        // El borrador manda: el asa sigue al valor guardado.
        SideEffect { if (sliderState.value != percent / 100f) sliderState.value = percent / 100f }
        Slider(
            state = sliderState,
            onValueChange = { value: Float -> onChange((value * 100).roundToInt()) },
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label },
            thumb = { _: SliderState ->
                Box(
                    Modifier
                        .size(width = 6.dp, height = 36.dp)
                        .alpha(if (enabled) 1f else DISABLED_ALPHA)
                        .then(Modifier.padding(0.dp)),
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawRoundRect(colors.primary, cornerRadius = CornerRadius(3.dp.toPx()))
                    }
                }
            },
            track = { state: SliderState ->
                val fraction = state.value.coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(colors.outlineVariant, cornerRadius = radius)
                    drawRoundRect(colors.primary, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                }
            },
            colors = SliderDefaults.colors(),
        )
    }
}

/** Interruptor con colores de rol, con título y explicación. */
@Composable
internal fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .selectable(selected = checked, enabled = enabled, role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.primary,
                uncheckedThumbColor = colors.outline,
                uncheckedTrackColor = colors.surfaceContainerHighest,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}

/** Botón redondo de 48 dp con ícono. */
@Composable
internal fun RoundIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
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
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), null) }
    }
}
