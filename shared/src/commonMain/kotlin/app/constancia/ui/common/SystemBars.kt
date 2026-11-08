package app.constancia.ui.common

import androidx.compose.runtime.Composable

/**
 * Define a aparência dos ícones das barras do sistema (status e
 * navegação). O MainActivity usa SystemBarStyle.auto, que segue o
 * tema do SISTEMA: num aparelho em modo escuro, os ícones nascem
 * claros — mas a faixa atrás deles é a cor de fundo do app (clara
 * no tema claro), então os ícones somem ("barra de status em
 * branco", relato aberto desde 2026-10-22). Quem manda nesta
 * decisão é o tema do APP, não o do sistema.
 */
@Composable
expect fun SystemBarsAppearance(darkIcons: Boolean)
