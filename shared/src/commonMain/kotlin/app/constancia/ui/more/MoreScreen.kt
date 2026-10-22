package app.constancia.ui.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.data.SyncState
import app.constancia.domain.buildExportCsv
import app.constancia.domain.goalForgivenDates
import app.constancia.domain.parseTimeOrNull
import app.constancia.notify.NotificationScheduler
import app.constancia.domain.buildExportJson
import app.constancia.export.shareTextFile
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.TimeTextField
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Aba Mais (protótipo "Roxo Profundo" aprovado): cartão de perfil com a
 * sequência em chama, o cartão Pro em gradiente roxo com preço e o menu
 * "Atalhos" em botões com tiles suaves — Diário, Relatórios detalhados,
 * Desafios, Sincronização e Ajustes (que expande tema e exportação).
 */
@Composable
fun MoreScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
    onOpenSection: (app.constancia.ui.navigation.MaisSection) -> Unit = {},
) {
    val today = remember { todayYmd() }
    val scope = rememberCoroutineScope()
    val extra = MaterialTheme.constanciaExtra
    val scheme = MaterialTheme.colorScheme
    val darkTheme by deps.prefs.darkThemeFlow().collectAsState(initial = false)
    val isPro by deps.prefs.isProFlow().collectAsState(initial = false)
    val syncStatus by deps.sync.statusFlow().collectAsState(initial = null)
    val statuses by deps.habits.todayStatusFlow().collectAsState(initial = emptyList())
    var syncMsg by remember { mutableStateOf<String?>(null) }
    var exportMsg by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(true) }
    // Itens 2 e 3: metas de tarefas e Daily Review.
    val dailyGoal by deps.prefs.taskDailyGoalFlow().collectAsState(initial = 0)
    val weeklyGoal by deps.prefs.taskWeeklyGoalFlow().collectAsState(initial = 0)
    val goalPauses by deps.tasks.goalPausesFlow()
        .collectAsState(initial = emptyList())
    val goalPausedToday = goalForgivenDates(goalPauses, today).contains(today)
    val reviewEnabled by deps.prefs.dailyReviewEnabledFlow()
        .collectAsState(initial = false)
    val reviewTime by deps.prefs.dailyReviewTimeFlow()
        .collectAsState(initial = "08:00")
    // Fase C (R6.2): lembrete automático das tarefas com horário.
    val autoReminderMinutes by deps.prefs.autoReminderMinutesFlow()
        .collectAsState(initial = -1)
    // Fase D: dias de descanso fixos da meta (R10.1) e modo férias (R13.4).
    val goalIgnoreWeekdays by deps.prefs.goalIgnoreWeekdaysFlow()
        .collectAsState(initial = emptySet())
    val vacationMode by deps.prefs.vacationModeFlow()
        .collectAsState(initial = false)
    var reviewTimeText by remember { mutableStateOf<String?>(null) }

    val bestStreak = statuses.maxOfOrNull { it.currentStreak } ?: 0
    val syncSubtitle = if (syncStatus?.state == SyncState.CLOUD_SOON) {
        "em breve"
    } else {
        syncStatus?.lastSyncLabel ?: "em breve"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("mais_list"),
        contentPadding = PaddingValues(
            start = 20.dp, top = 10.dp, end = 20.dp, bottom = 118.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Column {
                Text("Mais", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tudo o resto do Constância",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
            }
        }

        // Cartão de perfil do protótipo.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(scheme.surface)
                    .border(1.dp, extra.lineSoft, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF5B21B6),
                                    Color(0xFF38106E),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(27.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Conta Constância",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (isPro) "Plano Pro" else "Plano gratuito",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (bestStreak > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(extra.tintFlame)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Whatshot,
                            contentDescription = null,
                            tint = scheme.secondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = bestStreak.toString() +
                                if (bestStreak == 1) " dia" else " dias",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = scheme.secondary,
                        )
                    }
                }
            }
        }

        // Cartão Pro em gradiente do protótipo.
        item {
            ProCard(isPro = isPro, onClick = onOpenPaywall)
        }

        item {
            Text(
                text = "Atalhos",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 2.dp, top = 8.dp),
            )
        }
        item {
            MenuButton(
                icon = Icons.Filled.MenuBook,
                tint = extra.tintSun,
                iconColor = extra.sunColor,
                title = "Diário",
                subtitle = "Humor, energia, sono e a pergunta do dia",
                tag = "mais_diario",
                onClick = {
                    onOpenSection(app.constancia.ui.navigation.MaisSection.DIARIO)
                },
            )
        }
        item {
            MenuButton(
                icon = Icons.Filled.BarChart,
                tint = scheme.primaryContainer,
                iconColor = scheme.primary,
                title = "Relatórios detalhados",
                subtitle = "Padrões, raio-X e ano em revista",
                tag = "mais_progresso",
                onClick = {
                    onOpenSection(app.constancia.ui.navigation.MaisSection.PROGRESSO)
                },
            )
        }
        item {
            MenuButton(
                icon = Icons.Filled.EmojiEvents,
                tint = extra.tintFlame,
                iconColor = scheme.secondary,
                title = "Desafios",
                subtitle = "21 e 30 dias, no seu ritmo",
                tag = "mais_desafios",
                onClick = {
                    onOpenSection(app.constancia.ui.navigation.MaisSection.DESAFIOS)
                },
            )
        }
        item {
            Column {
                MenuButton(
                    icon = Icons.Filled.Sync,
                    tint = extra.tintAqua,
                    iconColor = extra.aquaColor,
                    title = "Sincronização",
                    subtitle = syncSubtitle,
                    tag = "mais_sync",
                    onClick = { syncMsg = "Disponível em breve" },
                )
                if (syncMsg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        syncMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        item {
            MenuButton(
                icon = Icons.Filled.Settings,
                tint = extra.tintBlue,
                iconColor = extra.blueColor,
                title = "Ajustes",
                subtitle = "Tema, dados e conta",
                tag = "mais_ajustes",
                onClick = { showSettings = !showSettings },
            )
        }

        if (showSettings) {
            item {
                SettingRow {
                    IconTile(
                        icon = Icons.Filled.Palette,
                        tint = extra.tintBlue,
                        iconColor = extra.blueColor,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aparência", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tema escuro",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = darkTheme,
                        onCheckedChange = {
                            scope.launch { deps.prefs.setDarkTheme(it) }
                        },
                    )
                }
            }
            item {
                StackCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Filled.Whatshot,
                            tint = extra.tintFlame,
                            iconColor = scheme.secondary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Metas de tarefas",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Quantos itens concluir por dia e por " +
                                    "semana — sem pontos, só a sua sequência.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    GoalStepperRow(
                        label = "Meta diária",
                        value = dailyGoal,
                        suffix = if (dailyGoal == 0) {
                            "desligada"
                        } else if (dailyGoal == 1) {
                            "tarefa/dia"
                        } else {
                            "tarefas/dia"
                        },
                        onChange = { value ->
                            scope.launch { deps.prefs.setTaskDailyGoal(value) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    GoalStepperRow(
                        label = "Meta semanal",
                        value = weeklyGoal,
                        suffix = if (weeklyGoal == 0) {
                            "desligada"
                        } else if (weeklyGoal == 1) {
                            "tarefa/semana"
                        } else {
                            "tarefas/semana"
                        },
                        onChange = { value ->
                            scope.launch { deps.prefs.setTaskWeeklyGoal(value) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pausar a meta",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Dias de pausa não quebram a sequência.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = goalPausedToday,
                            onCheckedChange = { paused ->
                                scope.launch {
                                    deps.tasks.setGoalPaused(paused, today)
                                }
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Dias de descanso da meta",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Nesses dias a meta não conta nem quebra a " +
                            "sequência — sem precisar pausar toda semana.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    IsoWeekdayChips(
                        selected = goalIgnoreWeekdays,
                        onToggle = { day ->
                            scope.launch {
                                deps.prefs.setGoalIgnoreWeekdays(
                                    if (day in goalIgnoreWeekdays) {
                                        goalIgnoreWeekdays - day
                                    } else {
                                        goalIgnoreWeekdays + day
                                    },
                                )
                            }
                        },
                    )
                }
            }
            item {
                StackCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Filled.WbSunny,
                            tint = extra.tintSun,
                            iconColor = extra.sunColor,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Modo férias",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Pausa a meta e todos os hábitos de uma " +
                                    "vez. As sequências ficam protegidas " +
                                    "até você voltar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = vacationMode,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    deps.setVacationMode(enabled, today)
                                }
                            },
                        )
                    }
                }
            }
            item {
                StackCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Filled.Notifications,
                            tint = extra.tintSun,
                            iconColor = extra.sunColor,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Daily Review",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Um resumo do seu dia, todo dia, no " +
                                    "horário que você escolher.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = reviewEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    deps.prefs.setDailyReviewEnabled(enabled)
                                    if (enabled) {
                                        parseTimeOrNull(reviewTime)?.let { t ->
                                            NotificationScheduler()
                                                .scheduleDailyReview(
                                                    t.hour, t.minute,
                                                )
                                        }
                                    } else {
                                        NotificationScheduler()
                                            .cancelDailyReview()
                                    }
                                }
                            },
                        )
                    }
                    if (reviewEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Horário",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            TimeTextField(
                                value = reviewTimeText ?: reviewTime,
                                onValueChange = { value ->
                                    reviewTimeText = value
                                    val parsed = app.constancia.domain
                                        .parseTimeOrNull(value)
                                    if (parsed != null && value.length == 5) {
                                        val hhmm = parsed.hour.toString()
                                            .padStart(2, '0') + ":" +
                                            parsed.minute.toString()
                                                .padStart(2, '0')
                                        scope.launch {
                                            deps.prefs
                                                .setDailyReviewTime(hhmm)
                                            NotificationScheduler()
                                                .scheduleDailyReview(
                                                    parsed.hour, parsed.minute,
                                                )
                                        }
                                    }
                                },
                                modifier = Modifier.width(100.dp),
                            )
                        }
                    }
                }
            }
            item {
                StackCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Filled.Notifications,
                            tint = extra.tintSun,
                            iconColor = extra.sunColor,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Lembrete automático",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Tarefa nova COM horário ganha um " +
                                    "lembrete sozinha, mesmo sem você " +
                                    "mexer no campo de lembrete.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        listOf(
                            "Desligado" to -1,
                            "Na hora" to 0,
                            "10 min" to 10,
                            "30 min" to 30,
                            "1 h antes" to 60,
                        ).forEach { (label, value) ->
                            val selected = autoReminderMinutes == value
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(99.dp))
                                    .background(
                                        if (selected) {
                                            scheme.primaryContainer
                                        } else {
                                            scheme.surfaceVariant
                                        },
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) {
                                            scheme.primary
                                        } else {
                                            scheme.outline
                                        },
                                        RoundedCornerShape(99.dp),
                                    )
                                    .clickable {
                                        scope.launch {
                                            deps.prefs
                                                .setAutoReminderMinutes(value)
                                        }
                                    }
                                    .padding(
                                        horizontal = 11.dp,
                                        vertical = 8.dp,
                                    ),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme
                                        .typography.labelLarge,
                                    color = if (selected) {
                                        scheme.onPrimaryContainer
                                    } else {
                                        scheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                StackCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(
                            icon = Icons.Filled.Download,
                            tint = extra.tintAqua,
                            iconColor = extra.aquaColor,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Exportar meus dados",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Seus hábitos e seu diário são seus — leve quando quiser.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = "Exportar JSON",
                            onClick = {
                                scope.launch {
                                    exportMsg = doExport(deps, today, csv = false)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "Exportar CSV",
                            onClick = {
                                scope.launch {
                                    exportMsg = doExport(deps, today, csv = true)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (exportMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            exportMsg ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Cartão Pro da aba Mais (.pro-card do protótipo): gradiente roxo,
 *  círculo decorativo, selo, título, texto e a linha de preço com o
 *  botão branco "Conhecer". */
@Composable
private fun ProCard(isPro: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF4B1D96), Color(0xFF2A0A54)),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(360f, 300f),
                ),
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 50.dp, y = (-60).dp)
                .size(150.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFC4A6F5).copy(alpha = 0.18f)),
        )
        Column(modifier = Modifier.padding(19.dp)) {
            Text(
                text = "CONSTÂNCIA PRO",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.12.em,
                ),
                color = Color(0xFFC4A6F5),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isPro) {
                    "Você já é Pro"
                } else {
                    "Relatórios sem limite de tempo"
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp),
                color = Color.White,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "Padrões cruzados, raio-X de atrasos e o ano em " +
                    "revista — sem recorte de 30 dias.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = Color(0xFFDCCFFB),
                modifier = Modifier.widthIn(max = 270.dp),
            )
            if (!isPro) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "R$ 119,90/ano",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "R$ 191,88",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                        ),
                        color = Color(0xFFA78BDB),
                        textDecoration = TextDecoration.LineThrough,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color.White)
                            .clickable(onClick = onClick)
                            .padding(horizontal = 11.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Conhecer",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = Color(0xFF2A0A54),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Tile de ícone suave 40dp raio 13 (menu e ajustes). */
@Composable
private fun IconTile(icon: ImageVector, tint: Color, iconColor: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Botão do menu "Atalhos" do protótipo: surface raio 18, tile de ícone
 *  suave, título, subtítulo e a seta em lavanda. */
@Composable
private fun MenuButton(
    icon: ImageVector,
    tint: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surface)
            .border(1.dp, extra.lineSoft, RoundedCornerShape(18.dp))
            .testTag(tag)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, tint = tint, iconColor = iconColor)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFA08FC0),
            modifier = Modifier.size(20.dp),
        )
    }
}

private suspend fun doExport(deps: AppDeps, today: String, csv: Boolean): String {
    return try {
        val habits = deps.habits.habitsFlow().first()
        val checkins = deps.checkins.checkinsInRangeFlow(today.take(4) + "-01-01", today).first()
        val tasks = deps.tasks.tasksForDateFlow(today).first()
        val diary = deps.diary.recentFlow(365).first()
        val (fileName, content, mime) = if (csv) {
            Triple("constancia.csv", buildExportCsv(habits, checkins), "text/csv")
        } else {
            Triple("constancia.json", buildExportJson(habits, checkins, tasks, diary), "application/json")
        }
        shareTextFile(fileName, content, mime)
        "Pronto — escolha onde salvar ou enviar"
    } catch (t: Throwable) {
        "Não foi possível exportar agora"
    }
}

/** Linha de ajuste: surface, borda line-soft, raio 18, padding 15/16. */
@Composable
private fun SettingRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.constanciaExtra.lineSoft,
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** Cartão empilhado dos ajustes: raio 24, padding 16. */
@Composable
private fun StackCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.constanciaExtra.lineSoft,
                RoundedCornerShape(24.dp),
            )
            .padding(16.dp),
        content = content,
    )
}

/** Botão secundário: borda 1.5dp, raio 12dp, fundo bg, texto ink 13/700. */
@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Linha de ajuste com stepper − / + (metas de tarefas, item 2). */
/** Letras dos dias na ordem ISO (1=segunda .. 7=domingo). */
private val ISO_DAY_LETTERS = listOf("S", "T", "Q", "Q", "S", "S", "D")

/**
 * Fase D (R10.1): seletor de dias da semana em círculos de 40dp —
 * mesmo desenho dos dias do formulário de hábito. [selected] guarda
 * dias ISO (1=segunda .. 7=domingo); tocar alterna o dia.
 */
@Composable
private fun IsoWeekdayChips(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        (1..7).forEach { isoDay ->
            val isSelected = isoDay in selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (isSelected) scheme.primary else scheme.surface,
                        CircleShape,
                    )
                    .then(
                        if (isSelected) Modifier
                        else Modifier.border(
                            BorderStroke(1.5.dp, scheme.outline),
                            CircleShape,
                        ),
                    )
                    .clickable { onToggle(isoDay) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ISO_DAY_LETTERS[isoDay - 1],
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) scheme.onPrimary
                    else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GoalStepperRow(
    label: String,
    value: Int,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(scheme.surfaceVariant)
                .clickable(enabled = value > 0) {
                    onChange((value - 1).coerceAtLeast(0))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 11.dp),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(scheme.primaryContainer)
                .clickable { onChange(value + 1) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer,
            )
        }
        Text(
            text = suffix,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp).widthIn(min = 74.dp),
        )
    }
}
