package app.constancia.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import app.constancia.shared.generated.resources.Res
import app.constancia.shared.generated.resources.font.manrope_bold
import app.constancia.shared.generated.resources.font.manrope_extrabold
import app.constancia.shared.generated.resources.font.manrope_regular
import app.constancia.shared.generated.resources.font.manrope_semibold
import app.constancia.shared.generated.resources.font.sora_bold
import app.constancia.shared.generated.resources.font.sora_extrabold

// Tipografia do protótipo: Sora (títulos/destaques) + Manrope (todo o resto).
@Composable
private fun manropeFamily() = FontFamily(
    Font(Res.font.manrope_regular, FontWeight.Normal),
    Font(Res.font.manrope_semibold, FontWeight.SemiBold),
    Font(Res.font.manrope_bold, FontWeight.Bold),
    Font(Res.font.manrope_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun soraFamily() = FontFamily(
    Font(Res.font.sora_bold, FontWeight.Bold),
    Font(Res.font.sora_extrabold, FontWeight.ExtraBold),
)

@Composable
private fun constanciaTypography(): Typography {
    val manrope = manropeFamily()
    val sora = soraFamily()
    return Typography(
        // Saudação do Hoje ("Boa noite"): Sora 25/800.
        displaySmall = TextStyle(
            fontFamily = sora, fontWeight = FontWeight.ExtraBold,
            fontSize = 25.sp, letterSpacing = (-0.25).sp,
        ),
        // Título de tela ("Relatórios"): Sora 23/800.
        headlineSmall = TextStyle(
            fontFamily = sora, fontWeight = FontWeight.ExtraBold, fontSize = 23.sp,
        ),
        // Botão primário: Sora 16/700.
        titleLarge = TextStyle(
            fontFamily = sora, fontWeight = FontWeight.Bold, fontSize = 16.sp,
        ),
        // Títulos de card: Manrope 800 15sp.
        titleMedium = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = 15.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp,
        ),
        // Notas e subtítulos: Manrope 600 12.5sp.
        bodySmall = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp,
        ),
        // Linha de data, chips: Manrope 700 13sp.
        labelLarge = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        ),
        // Abas da bottom nav: Manrope 700 11sp.
        labelMedium = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        ),
    )
}

private val ConstanciaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

private val LightScheme = lightColorScheme(
    primary = ConstanciaPrimaryLight,
    onPrimary = ConstanciaOnPrimaryLight,
    primaryContainer = ConstanciaPrimarySoftLight,
    onPrimaryContainer = ConstanciaPrimaryLight,
    secondary = ConstanciaFlameLight,
    onSecondary = Color.White,
    tertiary = ConstanciaPrimaryLight,
    background = ConstanciaBgLight,
    onBackground = ConstanciaInkLight,
    surface = ConstanciaSurfaceLight,
    onSurface = ConstanciaInkLight,
    surfaceVariant = ConstanciaSurface2Light,
    onSurfaceVariant = ConstanciaMutedLight,
    outline = ConstanciaLineLight,
    outlineVariant = ConstanciaLineLight,
)

private val DarkScheme = darkColorScheme(
    primary = ConstanciaPrimaryDark,
    onPrimary = ConstanciaOnPrimaryDark,
    primaryContainer = ConstanciaPrimarySoftDark,
    onPrimaryContainer = ConstanciaPrimaryDark,
    secondary = ConstanciaFlameDark,
    onSecondary = Color(0xFF07130D),
    tertiary = ConstanciaPrimaryDark,
    background = ConstanciaBgDark,
    onBackground = ConstanciaInkDark,
    surface = ConstanciaSurfaceDark,
    onSurface = ConstanciaInkDark,
    surfaceVariant = ConstanciaSurface2Dark,
    onSurfaceVariant = ConstanciaMutedDark,
    outline = ConstanciaLineDark,
    outlineVariant = ConstanciaLineDark,
)

val MaterialTheme.constanciaExtra: ConstanciaExtraColors
    @Composable
    get() = LocalConstanciaExtra.current

@Composable
fun ConstanciaTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalConstanciaExtra provides if (darkTheme) ConstanciaExtraDark else ConstanciaExtraLight,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = constanciaTypography(),
            shapes = ConstanciaShapes,
            content = content,
        )
    }
}
