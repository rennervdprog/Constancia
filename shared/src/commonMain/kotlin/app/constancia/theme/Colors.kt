package app.constancia.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// Paleta do desenho final aprovado (protótipo "Roxo Profundo").
// Marca: roxo profundo predominante sobre off-white quente; chama do streak
// em âmbar (único acento quente). Cores com significado (humor, projetos,
// prioridades, cor de cada hábito) NÃO viram roxo — ver DiaryLogic/
// TaskProject/priorityColor. Tokens literais — ver DESIGN_TOKENS.md.
val ConstanciaPrimaryLight = Color(0xFF4B1D96)
val ConstanciaPrimarySoftLight = Color(0xFFEBE2FA)
val ConstanciaFlameLight = Color(0xFFE86A17)
val ConstanciaBgLight = Color(0xFFFBF8F3)
val ConstanciaSurfaceLight = Color(0xFFFFFFFF)
val ConstanciaSurface2Light = Color(0xFFFAF6FD)
val ConstanciaInkLight = Color(0xFF221238)
val ConstanciaMutedLight = Color(0xFF7A6B90)
val ConstanciaLineLight = Color(0xFFE2D8F0)
val ConstanciaTabBgLight = Color(0xFFFFFFFF).copy(alpha = 0.92f)
val ConstanciaOnPrimaryLight = Color(0xFFFFFFFF)

// Tema escuro refeito em roxo (não é inversão): fundo roxo-noite profundo,
// superfícies em violeta escuro e primário lavanda para contraste.
val ConstanciaPrimaryDark = Color(0xFFB79CFF)
val ConstanciaPrimarySoftDark = Color(0xFF2E1A56)
val ConstanciaFlameDark = Color(0xFFFF8A3D)
val ConstanciaBgDark = Color(0xFF160A2C)
val ConstanciaSurfaceDark = Color(0xFF22123F)
val ConstanciaSurface2Dark = Color(0xFF2B1850)
val ConstanciaInkDark = Color(0xFFF4EFFC)
val ConstanciaMutedDark = Color(0xFFAB9AC6)
val ConstanciaLineDark = Color(0xFF3B2566)
val ConstanciaTabBgDark = Color(0xFF1D0E38).copy(alpha = 0.94f)
val ConstanciaOnPrimaryDark = Color(0xFF1E0740)

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
    /** Cartão "Seu dia" do Hoje: fundo roxo profundo no claro; no escuro o
     *  cartão inverte para lavanda com conteúdo roxo-noite. */
    val progressCardBg: Color,
    val progressCardContent: Color,
)

val ConstanciaExtraLight = ConstanciaExtraColors(
    pageBg = Color(0xFFF1EAF8),
    pageCard = Color(0xFFFFFFFF),
    pageChip = Color(0xFFF1EAF8),
    pageLine = Color(0xFFE2D8F0),
    pageMuted = Color(0xFFA08BB8),
    brandStrong = Color(0xFF7C3AED),
    heat0 = Color(0xFFEAE2F4),
    amber = Color(0xFFF0A840),
    amberSoft = Color(0xFFFBEBD3),
    red = Color(0xFFD92D20),
    redSoft = Color(0xFFFDECEA),
    ink2 = Color(0xFF4A3A63),
    ink3 = Color(0xFF7A6B90),
    tabBg = ConstanciaTabBgLight,
    progressCardBg = Color(0xFF2A0A54),
    progressCardContent = Color(0xFFFBF8F3),
)

val ConstanciaExtraDark = ConstanciaExtraColors(
    pageBg = Color(0xFF120722),
    pageCard = Color(0xFF22123F),
    pageChip = Color(0xFF2B1850),
    pageLine = Color(0xFF3B2566),
    pageMuted = Color(0xFF6E5A94),
    brandStrong = Color(0xFFA78BFA),
    heat0 = Color(0xFF2E1C52),
    amber = Color(0xFFD97706),
    amberSoft = Color(0xFF3A2A10),
    red = Color(0xFFF97066),
    redSoft = Color(0xFF3D1524),
    ink2 = Color(0xFFC9BCE4),
    ink3 = Color(0xFF8F7DB3),
    tabBg = ConstanciaTabBgDark,
    progressCardBg = Color(0xFFEBE2FA),
    progressCardContent = Color(0xFF2A0A54),
)

val LocalConstanciaExtra = compositionLocalOf { ConstanciaExtraLight }

// Cores dos ícones de hábito (catálogo do app) — cores com significado,
// uma por hábito, como no protótipo (Treino verde, Meditar azul-petróleo,
// Acordar âmbar, Leitura roxo...). NÃO são a cor da marca.
val HabitPalette = listOf(
    0xFF0B7A55, 0xFF0E7C86, 0xFFD97706, 0xFF7C3AED,
    0xFFDB2777, 0xFF2563EB, 0xFF6D3BD1, 0xFFE86A17,
).map { Color(it) }
