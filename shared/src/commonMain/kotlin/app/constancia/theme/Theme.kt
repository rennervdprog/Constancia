package app.constancia.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = ConstanciaPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = ConstanciaPrimarySoftLight,
    onPrimaryContainer = ConstanciaPrimaryLight,
    secondary = ConstanciaFlameLight,
    background = ConstanciaBgLight,
    onBackground = ConstanciaInkLight,
    surface = ConstanciaSurfaceLight,
    onSurface = ConstanciaInkLight,
    surfaceVariant = ConstanciaBgLight,
    onSurfaceVariant = ConstanciaMutedLight,
    outline = ConstanciaLineLight,
)

private val DarkScheme = darkColorScheme(
    primary = ConstanciaPrimaryDark,
    onPrimary = Color(0xFF07130D),
    primaryContainer = ConstanciaPrimarySoftDark,
    onPrimaryContainer = ConstanciaPrimaryDark,
    secondary = ConstanciaFlameDark,
    background = ConstanciaBgDark,
    onBackground = ConstanciaInkDark,
    surface = ConstanciaSurfaceDark,
    onSurface = ConstanciaInkDark,
    surfaceVariant = ConstanciaBgDark,
    onSurfaceVariant = ConstanciaMutedDark,
    outline = ConstanciaLineDark,
)

@Composable
fun ConstanciaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
