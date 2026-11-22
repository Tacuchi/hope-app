package dev.tacuchi.hope.ui.settings

import android.os.Build
import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import dev.tacuchi.hope.domain.model.Appearance
import dev.tacuchi.hope.domain.model.Palette
import dev.tacuchi.hope.domain.model.ThemeMode
import dev.tacuchi.hope.ui.common.openLink
import dev.tacuchi.hope.ui.theme.fixedScheme
import dev.tacuchi.hope.ui.R as UiR

/** La política de privacidad, publicada con GitHub Pages desde `docs/privacidad/` del repo. */
const val PRIVACY_POLICY_URL = "https://tacuchi.github.io/hope-app/privacidad/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBar(title: Int, onBack: () -> Unit) {
    TopAppBar(title = { Text(stringResource(title)) }, navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(painterResource(UiR.drawable.ic_arrow_back), stringResource(UiR.string.action_back))
        }
    })
}

/** Las licencias que AboutLibraries genera al compilar la app; [librariesRes] es su recurso. */
@Composable
fun LicensesScreen(@RawRes librariesRes: Int, onBack: () -> Unit) {
    val libraries by produceLibraries(librariesRes)
    Scaffold(topBar = { SettingsBar(R.string.licenses, onBack) }) { padding ->
        LibrariesContainer(libraries, Modifier.padding(padding).fillMaxSize())
    }
}

@Composable
fun SettingsScreen(
    appearance: Appearance,
    onThemeChange: (ThemeMode) -> Unit,
    onPaletteChange: (Palette) -> Unit,
    onBack: () -> Unit,
    onLicenses: () -> Unit,
    /** La versión de la app instalada, como la muestra «Acerca de». */
    versionName: String,
) {
    Scaffold(topBar = { SettingsBar(UiR.string.settings, onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, theme ->
                    SegmentedButton(selected = theme == appearance.theme,
                        onClick = { onThemeChange(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)) {
                        Text(stringResource(when (theme) {
                            ThemeMode.System -> R.string.theme_system
                            ThemeMode.Light -> R.string.theme_light
                            ThemeMode.Dark -> R.string.theme_dark
                        }))
                    }
                }
            }
            Text(stringResource(R.string.palette), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Palette.entries.filter { it != Palette.Dynamic || Build.VERSION.SDK_INT >= 31 }.forEach { palette ->
                    val color = if (palette == Palette.Dynamic && Build.VERSION.SDK_INT >= 31)
                        dynamicLightColorScheme(LocalContext.current).primary else fixedScheme(palette, false).primary
                    FilterChip(selected = palette == appearance.palette,
                        onClick = { onPaletteChange(palette) },
                        leadingIcon = { Box(Modifier.size(18.dp).background(color, CircleShape)) },
                        label = { Text(stringResource(when (palette) {
                            Palette.Hope -> UiR.string.app_name
                            Palette.Dynamic -> R.string.palette_dynamic
                            Palette.Lime -> R.string.palette_lime
                            Palette.Coral -> R.string.palette_coral
                            Palette.Violet -> R.string.palette_violet
                        })) })
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.app_version, versionName))
            TextButton(onClick = onLicenses) { Text(stringResource(R.string.licenses)) }
            val context = LocalContext.current
            TextButton(onClick = { openLink(context, PRIVACY_POLICY_URL) }) {
                Text(stringResource(R.string.privacy_policy))
            }
        }
    }
}
