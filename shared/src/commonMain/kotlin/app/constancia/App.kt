package app.constancia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.constancia.data.DatabaseDriverFactory
import app.constancia.theme.ConstanciaTheme
import app.constancia.theme.constanciaExtra
import app.constancia.ui.challenges.ChallengesScreen
import app.constancia.ui.diary.DiaryScreen
import app.constancia.ui.home.HomeScreen
import app.constancia.ui.more.MoreScreen
import app.constancia.ui.navigation.AppTab
import app.constancia.ui.navigation.rememberAppNavigator
import app.constancia.ui.paywall.PaywallScreen
import app.constancia.ui.reports.ReportsScreen

/**
 * Ponto de entrada compartilhado do app Constância.
 * v1: 5 abas (Hoje, Relatórios, Diário, Desafios, Mais) + paywall (UI).
 * O app é offline-first: tudo roda no banco local (SQLDelight).
 */
@Composable
fun App(driverFactory: DatabaseDriverFactory) {
    val deps = rememberAppDeps(driverFactory)
    AppContent(deps)
}

/**
 * Conteúdo do app com dependências já montadas.
 * Separado de [App] para permitir testes de UI com banco em memória.
 */
@Composable
fun AppContent(deps: AppDeps) {
    val navigator = rememberAppNavigator()
    val darkTheme by deps.prefs.darkThemeFlow().collectAsState(initial = false)

    ConstanciaTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                ConstanciaBottomBar(
                    selected = navigator.tab,
                    onSelect = { navigator.navigateTo(it) },
                )
            },
        ) { innerPadding ->
            val screenModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            when (navigator.tab) {
                AppTab.HOJE -> HomeScreen(deps, screenModifier)
                AppTab.RELATORIOS -> ReportsScreen(deps, screenModifier)
                AppTab.DIARIO -> DiaryScreen(deps, screenModifier)
                AppTab.DESAFIOS -> ChallengesScreen(deps, screenModifier)
                AppTab.MAIS -> MoreScreen(
                    deps,
                    screenModifier,
                    onOpenPaywall = { navigator.showPaywall = true },
                )
            }
        }
        if (navigator.showPaywall) {
            PaywallScreen(onClose = { navigator.showPaywall = false })
        }
    }
}

/** Bottom navigation customizada do protótipo: fundo tab-bg translúcido,
 *  borda superior 1dp, item = coluna (gap 3dp) com ícone 22dp + label 11sp/700.
 *  Selecionado = primary, não selecionado = onSurfaceVariant. Sem pill. */
@Composable
private fun ConstanciaBottomBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    Column {
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.constanciaExtra.tabBg)
                // tabbar do protótipo: padding 8px 10px 20px (topo/lados/fundo).
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp, bottom = 20.dp),
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                        .testTag("tab_" + tab.name)
                        .clickable(onClick = { onSelect(tab) })
                        // tab do protótipo: padding 4px 10px, gap 3px.
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = color,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
        }
    }
}
