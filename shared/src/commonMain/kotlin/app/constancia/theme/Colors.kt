package app.constancia.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta extraída do protótipo aprovado ("Constância").
// Tokens literais — ver DESIGN_TOKENS.md. Não usar cores "seed" genéricas.
val ConstanciaPrimaryLight = Color(0xFF0B7A55)
val ConstanciaPrimarySoftLight = Color(0xFFE2F2E9)
val ConstanciaFlameLight = Color(0xFFE86A17)
val ConstanciaBgLight = Color(0xFFF5F3EC)
val ConstanciaSurfaceLight = Color(0xFFFFFFFF)
val ConstanciaSurface2Light = Color(0xFFEFEADF)
val ConstanciaInkLight = Color(0xFF152419)
val ConstanciaMutedLight = Color(0xFF66766A)
val ConstanciaLineLight = Color(0xFFE6E1D2)
val ConstanciaTabBgLight = Color(0xFFFFFFFF).copy(alpha = 0.92f)
val ConstanciaOnPrimaryLight = Color(0xFFFFFFFF)

val ConstanciaPrimaryDark = Color(0xFF2FBF8F)
val ConstanciaPrimarySoftDark = Color(0xFF143327)
val ConstanciaFlameDark = Color(0xFFFF8A3D)
val ConstanciaBgDark = Color(0xFF0D1511)
val ConstanciaSurfaceDark = Color(0xFF17231C)
val ConstanciaSurface2Dark = Color(0xFF1F2F25)
val ConstanciaInkDark = Color(0xFFECF3ED)
val ConstanciaMutedDark = Color(0xFF93A69A)
val ConstanciaLineDark = Color(0xFF26392D)
val ConstanciaTabBgDark = Color(0xFF131E17).copy(alpha = 0.94f)
val ConstanciaOnPrimaryDark = Color(0xFF07130D)

// Tokens extras (relatórios + detalhes do protótipo).
@Immutable
data class ConstanciaExtraColors(
    val pageBg: Color,
    val pageCard: Color,
    val pageChip: Color,
    val pageLine: Color,
    val pageMuted: Color,
    val brandStrong: Color,
    val heat0: Color,
    val amber: Color,
    val amberSoft: Color,
    val red: Color,
    val redSoft: Color,
    val ink2: Color,
    val ink3: Color,
    val tabBg: Color,
    /** Cartão de progresso do Hoje: fundo INVERTIDO (ink) com conteúdo bg. */
    val progressCardBg: Color,
    val progressCardContent: Color,
)

val ConstanciaExtraLight = ConstanciaExtraColors(
    pageBg = Color(0xFFE9ECE8),
    pageCard = Color(0xFFFFFFFF),
    pageChip = Color(0xFFEEF2E7),
    pageLine = Color(0xFFE1E7E2),
    pageMuted = Color(0xFF9DB3A4),
    brandStrong = Color(0xFF5ED3AB),
    heat0 = Color(0xFFE4EAE5),
    amber = Color(0xFFF0A840),
    amberSoft = Color(0xFFFBEBD3),
    red = Color(0xFFF08A5D),
    redSoft = Color(0xFFFBE7DC),
    ink2 = Color(0xFFA9BCB1),
    ink3 = Color(0xFF7A8B82),
    tabBg = ConstanciaTabBgLight,
    progressCardBg = ConstanciaInkLight,
    progressCardContent = ConstanciaBgLight,
)

val ConstanciaExtraDark = ConstanciaExtraColors(
    pageBg = Color(0xFF0B100E),
    pageCard = Color(0xFF16211B),
    pageChip = Color(0xFF182A20),
    pageLine = Color(0xFF223129),
    pageMuted = Color(0xFF55665A),
    brandStrong = Color(0xFF095C40),
    heat0 = Color(0xFF1C2A22),
    amber = Color(0xFFD97706),
    amberSoft = Color(0xFF3A2A10),
    red = Color(0xFFC2410C),
    redSoft = Color(0xFF3A2115),
    ink2 = Color(0xFF43564D),
    ink3 = Color(0xFF6E8277),
    tabBg = ConstanciaTabBgDark,
    progressCardBg = ConstanciaInkDark,
    progressCardContent = ConstanciaBgDark,
)

val LocalConstanciaExtra = compositionLocalOf { ConstanciaExtraLight }

// Cores dos ícones de hábito (catálogo do app).
val HabitPalette = listOf(
    0xFF0B7A55, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777,
    0xFFE86A17, 0xFFCA8A04, 0xFF0891B2, 0xFF4D7C0F,
).map { Color(it) }
