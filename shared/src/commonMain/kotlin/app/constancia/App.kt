package app.constancia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.data.DatabaseDriverFactory
import app.constancia.model.todayYmd
import app.constancia.theme.ConstanciaTheme
import app.constancia.theme.constanciaExtra
import app.constancia.ui.CreateSheet
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
 * Navegação (protótipo aprovado): Hoje · Tarefas · (+) · Hábitos · Mais,
 * com Diário/Progresso/Desafios dentro da Mais e o paywall por cima.
 * A barra inferior é FLUTUANTE (pílula destacada das bordas, botão +
 * central elevado) e fica sobre o conteúdo — as telas reservam o espaço
 * dela no fim da rolagem. O app é offline-first (SQLDelight).
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
    val allTasks by deps.tasks.allTasksFlow().collectAsState(initial = emptyList())
    val today = todayYmd()
    val overdueCount = allTasks.count {
        !it.done && it.dueDate != null && it.dueDate < today
    }

    ConstanciaTheme(darkTheme = darkTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val screenModifier = Modifier.fillMaxSize()
            when (navigator.tab) {
                AppTab.HOJE -> HomeScreen(
                    deps,
                    screenModifier,
                    onOpenDiary = { date -> navigator.openDiary(date) },
                    onOpenReports = {
                        navigator.openMaisSection(MaisSection.PROGRESSO)
                    },
                )
                AppTab.TAREFAS -> TasksScreen(deps, screenModifier)
                AppTab.HABITOS -> HabitsScreen(
                    deps,
                    screenModifier,
                    onCreateHabit = { navigator.showNewHabit = true },
                )
                AppTab.MAIS -> when (navigator.maisSection) {
                    MaisSection.DIARIO -> DiaryScreen(
                        deps,
                        screenModifier,
                        initialDate = navigator.diaryDate,
                        onConsumedInitialDate = { navigator.diaryDate = null },
                        onBack = { navigator.maisSection = null },
                    )
                    MaisSection.PROGRESSO -> ReportsScreen(
                        deps = deps,
                        modifier = screenModifier,
                        onOpenPaywall = { navigator.showPaywall = true },
                        onBack = { navigator.maisSection = null },
                    )
                    MaisSection.DESAFIOS -> ChallengesScreen(deps, screenModifier)
                    null -> MoreScreen(
                        deps,
                        screenModifier,
                        onOpenPaywall = { navigator.showPaywall = true },
                        onOpenSection = { navigator.openMaisSection(it) },
                    )
                }
            }

            ConstanciaBottomBar(
                navigator = navigator,
                overdueCount = overdueCount,
                onSelect = { navigator.navigateTo(it) },
                onCreate = { navigator.showCreate = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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

/**
 * Barra inferior FLUTUANTE do protótipo: pílula branca translúcida
 * (raio 28, sombra roxa, destacada 14dp das laterais e 12dp do fundo),
 * abas com indicador superior na selecionada, selo de atrasadas na aba
 * Tarefas e botão + central elevado (squircle 54dp em gradiente roxo,
 * −26dp). Sem blur de fundo (RenderEffect exige API 31+; o app é 24+):
 * o fundo translúcido a 86% entrega o mesmo visual.
 */
@Composable
private fun ConstanciaBottomBar(
    navigator: AppNavigator,
    overdueCount: Int,
    onSelect: (AppTab) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 12.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color(0xFF26104A).copy(alpha = 0.28f),
                ambientColor = Color(0xFF26104A).copy(alpha = 0.28f),
            )
            .clip(RoundedCornerShape(28.dp))
            .background(extra.tabBg)
            .border(
                1.dp,
                Color(0xFF26104A).copy(alpha = 0.07f),
                RoundedCornerShape(28.dp),
            )
            .padding(horizontal = 6.dp)
            .padding(top = 9.dp, bottom = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        BarTab(
            tab = AppTab.HOJE,
            selected = navigator.tab == AppTab.HOJE,
            badge = 0,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        BarTab(
            tab = AppTab.TAREFAS,
            selected = navigator.tab == AppTab.TAREFAS,
            badge = overdueCount,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        // Botão central de criar: elevado sobre a barra.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .offset(y = (-26).dp)
                    .size(54.dp)
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(19.dp),
                        spotColor = Color(0xFF38066E).copy(alpha = 0.55f),
                        ambientColor = Color(0xFF38066E).copy(alpha = 0.55f),
                    )
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF5B21B6), Color(0xFF38106E)),
                        ),
                    )
                    .testTag("fab_create")
                    .clickable(onClick = onCreate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Criar",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        BarTab(
            tab = AppTab.HABITOS,
            selected = navigator.tab == AppTab.HABITOS,
            badge = 0,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        BarTab(
            tab = AppTab.MAIS,
            selected = navigator.tab == AppTab.MAIS,
            badge = 0,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BarTab(
    tab: AppTab,
    selected: Boolean,
    badge: Int,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (selected) scheme.primary else scheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .testTag("tab_" + tab.name)
            .clickable(onClick = { onSelect(tab) })
            .padding(horizontal = 4.dp),
    ) {
        // Barrinha indicadora da aba ativa (22×3.5dp, acima do ícone).
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(3.5.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (selected) scheme.primary else Color.Transparent),
        )
        Spacer(Modifier.height(5.dp))
        Box {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = color,
                modifier = Modifier.size(23.dp),
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-5).dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD92D20))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.toString(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = color,
        )
    }
}
