package app.constancia

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.constancia.data.DatabaseDriverFactory
import app.constancia.theme.ConstanciaTheme
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
    val navigator = rememberAppNavigator()
    val darkTheme by deps.prefs.darkThemeFlow().collectAsState(initial = false)

    ConstanciaTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = navigator.tab == tab,
                            onClick = { navigator.navigateTo(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
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
