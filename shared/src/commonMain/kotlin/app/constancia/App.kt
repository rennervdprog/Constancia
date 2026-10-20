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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.constancia.data.DatabaseDriverFactory
import app.constancia.model.todayYmd
import app.constancia.theme.ConstanciaTheme
import app.constancia.theme.constanciaExtra
import app.constancia.ui.challenges.ChallengesScreen
import app.constancia.ui.diary.DiaryScreen
import app.constancia.ui.habits.HabitsScreen
import app.constancia.ui.home.HomeScreen
import app.constancia.ui.home.NewHabitSheet
import app.constancia.ui.more.MoreScreen
import app.constancia.ui.navigation.AppNavigator
import app.constancia.ui.navigation.AppTab
import app.constancia.ui.navigation.MaisSection
import app.constancia.ui.navigation.rememberAppNavigator
import app.constancia.ui.paywall.PaywallScreen
import app.constancia.ui.reports.ReportsScreen
import app.constancia.ui.tasks.NewTaskSheet
import app.constancia.ui.tasks.TasksScreen

/**
 * Ponto de entrada compartilhado do app Constância.
 * Navegação (protótipo turbinado): Hoje · Tarefas · (+) · Hábitos · Mais,
 * com Diário/Progresso/Desafios dentro da Mais e o paywall por cima.
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
                    navigator = navigator,
                    onSelect = { navigator.navigateTo(it) },
                    onCreate = { navigator.showCreate = true },
                )
            },
        ) { innerPadding ->
            val screenModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            when (navigator.tab) {
                AppTab.HOJE -> HomeScreen(
                    deps,
                    screenModifier,
                    onOpenDiary = { navigator.openDiary(todayYmd()) },
                )
                AppTab.TAREFAS -> TasksScreen(
                    deps,
                    screenModifier,
                    onNewTask = { navigator.showNewTask = true },
                )
                AppTab.HABITOS -> HabitsScreen(
                    deps,
                    screenModifier,
                    onOpenReports = {
                        navigator.openMaisSection(MaisSection.PROGRESSO)
                    },
                )
                AppTab.MAIS -> when (navigator.maisSection) {
                    MaisSection.DIARIO -> DiaryScreen(
                        deps,
                        screenModifier,
                        initialDate = navigator.diaryDate,
                        onConsumedInitialDate = { navigator.diaryDate = null },
                    )
                    MaisSection.PROGRESSO -> ReportsScreen(deps, screenModifier)
                    MaisSection.DESAFIOS -> ChallengesScreen(deps, screenModifier)
                    null -> MoreScreen(
                        deps,
                        screenModifier,
                        onOpenPaywall = { navigator.showPaywall = true },
                        onOpenSection = { navigator.openMaisSection(it) },
                    )
                }
            }
        }
        if (navigator.showCreate) {
            CreateSheet(
                onCreateHabit = {
                    navigator.showCreate = false
                    navigator.showNewHabit = true
                },
                onCreateTask = {
                    navigator.showCreate = false
                    navigator.showNewTask = true
                },
                onCreateDiary = {
                    navigator.showCreate = false
                    navigator.openDiary(todayYmd())
                },
                onDismiss = { navigator.showCreate = false },
            )
        }
        if (navigator.showNewHabit) {
            NewHabitSheet(deps = deps, onDismiss = { navigator.showNewHabit = false })
        }
        if (navigator.showNewTask) {
            NewTaskSheet(deps = deps, onDismiss = { navigator.showNewTask = false })
        }
        if (navigator.showPaywall) {
            PaywallScreen(onClose = { navigator.showPaywall = false })
        }
    }
}

/** Bottom navigation do protótipo turbinado: Hoje · Tarefas · (+) · Hábitos · Mais.
 *  Fundo tab-bg translúcido, borda superior 1dp; o botão central é um círculo
 *  primary de 52dp elevado, que abre a folha de criação. */
@Composable
private fun ConstanciaBottomBar(
    navigator: AppNavigator,
    onSelect: (AppTab) -> Unit,
    onCreate: () -> Unit,
) {
    Column {
        Box(
            modifier = Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outline),
        )
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.constanciaExtra.tabBg)
                .padding(horizontal = 10.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BarTab(
                tab = AppTab.HOJE,
                selected = navigator.tab == AppTab.HOJE,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
            BarTab(
                tab = AppTab.TAREFAS,
                selected = navigator.tab == AppTab.TAREFAS,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
            // Botão central de criar.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            spotColor = MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.4f),
                            ambientColor = MaterialTheme.colorScheme.primary
                                .copy(alpha = 0.4f),
                        )
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .testTag("fab_create")
                        .clickable(onClick = onCreate),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Criar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            BarTab(
                tab = AppTab.HABITOS,
                selected = navigator.tab == AppTab.HABITOS,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
            BarTab(
                tab = AppTab.MAIS,
                selected = navigator.tab == AppTab.MAIS,
                onSelect = onSelect,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BarTab(
    tab: AppTab,
    selected: Boolean,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier
            .testTag("tab_" + tab.name)
            .clickable(onClick = { onSelect(tab) })
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
