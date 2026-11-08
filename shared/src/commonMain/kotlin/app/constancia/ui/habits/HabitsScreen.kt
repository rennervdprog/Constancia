package app.constancia.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.CycleState
import app.constancia.domain.DOW_SHORT
import app.constancia.domain.HeatCell
import app.constancia.domain.fmtQty
import app.constancia.domain.completionRateLast30
import app.constancia.domain.cycleDoneDays
import app.constancia.domain.cycleState
import app.constancia.domain.cycleStreak
import app.constancia.domain.doneDatesFor
import app.constancia.domain.donePairs
import app.constancia.domain.heatGrid
import app.constancia.domain.heatGridStart
import app.constancia.domain.isDueOn
import app.constancia.domain.skippedDatesFor
import app.constancia.model.Checkin
import app.constancia.model.CheckinStatus
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.isoToAppDay
import app.constancia.model.habitActiveFrom
import app.constancia.model.HabitCycle
import app.constancia.model.HabitRecord
import app.constancia.model.HabitStatus
import app.constancia.model.Session
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.SegmentedRow
import app.constancia.ui.common.TileCard
import app.constancia.ui.common.habitColorOf
import app.constancia.ui.home.NewHabitSheet
import app.constancia.ui.common.habitIcon
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Rótulo da frequência a partir dos campos do hábito. */
fun frequencyLabel(habit: Habit): String = when (habit.frequencyType) {
    FrequencyType.DAILY -> "Todos os dias"
    FrequencyType.WEEKLY_X -> habit.weeklyTarget.toString() + "x por semana"
    FrequencyType.DAYS_OF_WEEK -> habit.daysOfWeek
        .mapNotNull { DOW_SHORT.getOrNull(it - 1) }
        .joinToString(", ")
        .ifBlank { "Dias fixos" }
}

/** Frequência no formato do protótipo: "Todo dia", "Seg, qua e sex". */
private fun frequencyShort(habit: Habit): String = when (habit.frequencyType) {
    FrequencyType.DAILY -> "Todo dia"
    FrequencyType.WEEKLY_X -> habit.weeklyTarget.toString() + "x por semana"
    FrequencyType.DAYS_OF_WEEK -> habit.daysOfWeek
        .mapNotNull { DOW_SHORT.getOrNull(it - 1) }
        .joinToString(", ") { it.lowercase() }
        .replaceFirstChar { it.uppercase() }
        .ifBlank { "Dias fixos" }
}

private fun sessionSuffix(habit: Habit): String = " · sessão da " + when (habit.session) {
    Session.MANHA -> "manhã"
    Session.TARDE -> "tarde"
    Session.NOITE -> "noite"
}

/**
 * Dias de descanso visíveis no cartão ("· folga qua"). Antes eram
 * invisíveis em toda a UI: um hábito com folga no dia simplesmente
 * sumia da tela Hoje sem explicação (reteste 2026-10-21, bug nº 1).
 */
private fun restDaysSuffix(habit: Habit): String =
    if (habit.frequencyType == FrequencyType.WEEKLY_X ||
        habit.ignoreWeekdays.isEmpty()
    ) {
        ""
    } else {
        " · folga " + habit.ignoreWeekdays.sorted()
            .joinToString(", ") { iso ->
                DOW_SHORT.getOrNull(isoToAppDay(iso) - 1)?.lowercase() ?: ""
            }
    }

/** Meta de dias da semana atual, conforme o tipo de frequência. */
private fun weekTargetOf(habit: Habit): Int = when (habit.frequencyType) {
    FrequencyType.WEEKLY_X -> habit.weeklyTarget
    FrequencyType.DAYS_OF_WEEK -> habit.daysOfWeek.size.coerceAtLeast(1)
    FrequencyType.DAILY -> 7
}

/**
 * Aba Hábitos (protótipo "Roxo Profundo" aprovado): título + frase da
 * marca, 4 KPIs em grade 2×2 com tiles de ícone, e o cartão por hábito
 * com a cor própria dele — sequência e meta da semana na goalbar e o
 * mapa de calor das últimas 18 semanas em células flexíveis.
 */
@Composable
fun HabitsScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onCreateHabit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val today = remember { todayYmd() }
    val todayDate = remember { today.toLocalDate() }
    // Bug nº 4 (reteste 2026-10-21): hábito em edição (folha preenchida).
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    val rangeFrom = remember {
        todayDate.minus(DatePeriod(days = 400)).toString()
    }
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    // M4: arquivados ganham seção própria com restaurar/excluir —
    // arquivar deixou de ser um caminho sem volta.
    val archived by deps.habits.archivedFlow().collectAsState(initial = emptyList())
    var archivedDeleteId by remember { mutableStateOf<String?>(null) }
    val statuses by deps.habits.todayStatusFlow().collectAsState(initial = emptyList())
    val checkins by deps.checkins.checkinsInRangeFlow(rangeFrom, today)
        .collectAsState(initial = emptyList())
    // Itens 2 e 3: ciclos e log de todos os hábitos.
    val allCycles by deps.habits.allCyclesFlow().collectAsState(initial = emptyList())
    val allRecords by deps.checkins.allRecordsFlow()
        .collectAsState(initial = emptyList())
    // Fase C: lembretes (vários por hábito) de todos os hábitos.
    val allHabitReminders by deps.habits.allHabitRemindersFlow()
        .collectAsState(initial = emptyList())

    val statusByHabit = statuses.associateBy { it.habit.id }
    // B3: "dias com check-in" conta só dias com conclusão — dia só
    // de pulo não entra (mesma regra dos relatórios).
    val checkinDays30 = checkins
        .filter { it.status == CheckinStatus.DONE }
        .map { it.date }
        .filter { it >= todayDate.minus(DatePeriod(days = 29)).toString() }
        .distinct()
        .size
    val maxStreak = statuses.maxOfOrNull { it.currentStreak } ?: 0
    val completion = completionRateLast30(habits, checkins, todayDate)
    val activeCount = habits.count { !it.paused }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("habits_list"),
        contentPadding = PaddingValues(
            start = 20.dp, top = 10.dp, end = 20.dp, bottom = 118.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Column {
                Text("Hábitos", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Constância se constrói um dia de cada vez",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                KpiTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Whatshot,
                    tint = MaterialTheme.constanciaExtra.tintFlame,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    value = maxStreak.toString() +
                        if (maxStreak == 1) " dia" else " dias",
                    label = "maior sequência atual",
                )
                KpiTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.constanciaExtra.tintGreen,
                    iconColor = MaterialTheme.constanciaExtra.greenStrong,
                    value = activeCount.toString(),
                    label = "hábitos ativos",
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                KpiTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CalendarMonth,
                    tint = MaterialTheme.constanciaExtra.tintAqua,
                    iconColor = MaterialTheme.constanciaExtra.aquaColor,
                    value = checkinDays30.toString() + " dias",
                    label = "dias com check-in · 30 dias",
                )
                KpiTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.BarChart,
                    tint = MaterialTheme.constanciaExtra.tintBlue,
                    iconColor = MaterialTheme.constanciaExtra.blueColor,
                    value = (completion * 100).toInt().toString() + "%",
                    label = "conclusão · últimos 30 dias",
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 2.dp, top = 9.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Seus hábitos",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(
                    text = activeCount.toString() +
                        if (activeCount == 1) " ativo" else " ativos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (habits.isEmpty()) {
            item {
                Text(
                    text = "Nenhum hábito ainda. Toque no botão + para criar o primeiro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
        } else {
            items(habits, key = { it.id }) { habit ->
                val status = statusByHabit[habit.id]
                val habitCheckins = checkins.filter { it.habitId == habit.id }
                HabitCard(
                    habit = habit,
                    status = status,
                    grid = heatGrid(habit, habitCheckins, todayDate),
                    todayRow = todayDate.isoDayNumberCompat() % 7,
                    todayDate = todayDate,
                    dueToday = isDueOn(
                        habit,
                        todayDate,
                        habitActiveFrom(habit.createdAt),
                    ),
                    habitCheckins = habitCheckins,
                    cycles = allCycles.filter { it.habitId == habit.id },
                    records = allRecords.filter { it.habitId == habit.id },
                    habitReminders = allHabitReminders
                        .filter { it.habitId == habit.id },
                    todayYmdStr = today,
                    onCheck = {
                        scope.launch {
                            // M1 (varredura): em hábito de quantidade,
                            // o círculo NÃO usa o toggle binário (que
                            // apagava o progresso parcial do dia):
                            // incompleto -> completa a meta; feito ->
                            // desmarca limpando o registro.
                            if (habit.goalType == GoalType.QUANTITY) {
                                if (status?.isDoneToday == true) {
                                    deps.checkins.remove(habit.id, today)
                                } else {
                                    deps.checkins.setQuantity(
                                        habit.id, today, habit.targetQuantity,
                                    )
                                }
                            } else {
                                deps.checkins.toggle(habit.id, today)
                            }
                        }
                    },
                    onSetQuantity = { qty ->
                        scope.launch {
                            deps.checkins.setQuantity(habit.id, today, qty)
                        }
                    },
                    onPauseToggle = {
                        scope.launch {
                            deps.habits.setPaused(habit.id, !habit.paused)
                        }
                    },
                    onArchive = {
                        scope.launch { deps.habits.archive(habit.id) }
                    },
                    onDelete = {
                        scope.launch { deps.habits.delete(habit.id) }
                    },
                    onSkip = {
                        scope.launch { deps.checkins.skip(habit.id, today) }
                    },
                    onUnskip = {
                        scope.launch { deps.checkins.unskip(habit.id, today) }
                    },
                    onStartCycle = { days ->
                        scope.launch { deps.habits.startCycle(habit.id, days, today) }
                    },
                    onSaveRecord = { content, emoji ->
                        scope.launch {
                            deps.checkins.saveRecord(habit.id, today, content, emoji)
                        }
                    },
                    onEdit = { editingHabit = habit },
                    onSetHabitReminders = { times ->
                        scope.launch {
                            deps.habits.setHabitReminders(habit.id, times)
                        }
                    },
                )
            }
        }

        if (archived.isNotEmpty()) {
            item {
                Text(
                    text = "Arquivados (" + archived.size + ")",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            items(archived, key = { "arch-" + it.id }) { habit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(15.dp),
                        )
                        .border(
                            1.dp,
                            MaterialTheme.constanciaExtra.lineSoft,
                            RoundedCornerShape(15.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Restaurar",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch {
                                    deps.habits.unarchive(habit.id)
                                }
                            }
                            .testTag("habit_unarchive_" + habit.id)
                            .padding(8.dp),
                    )
                    Text(
                        text = "Excluir",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { archivedDeleteId = habit.id }
                            .padding(8.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(15.dp),
                    )
                    .clickable(onClick = onCreateHabit)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.constanciaExtra.ink2,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Criar novo hábito",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = MaterialTheme.constanciaExtra.ink2,
                )
            }
        }
    }

    // Bug nº 4 do reteste 2026-10-21: editar hábito (folha preenchida,
    // update no mesmo id).
    editingHabit?.let { habit ->
        NewHabitSheet(
            deps = deps,
            onDismiss = { editingHabit = null },
            editing = habit,
        )
    }

    // M4: confirmação de exclusão a partir da seção de arquivados.
    archivedDeleteId?.let { id ->
        val target = archived.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { archivedDeleteId = null },
            title = { Text("Excluir hábito?") },
            text = {
                Text(
                    "O hábito \"" + (target?.name ?: "") + "\" e todo o " +
                        "histórico de check-ins dele são apagados. " +
                        "Não dá para desfazer.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        archivedDeleteId = null
                        scope.launch { deps.habits.delete(id) }
                    },
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { archivedDeleteId = null }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

/** KPI da aba Hábitos (.kpi do protótipo): tile de ícone suave 34dp,
 *  valor Sora 23/800 e legenda 12/700 em muted. */
@Composable
private fun KpiTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color,
    iconColor: Color,
    value: String,
    label: String,
) {
    TileCard(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(19.dp),
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    status: HabitStatus?,
    grid: List<List<HeatCell>>,
    todayRow: Int,
    todayDate: LocalDate,
    dueToday: Boolean,
    habitCheckins: List<Checkin>,
    cycles: List<HabitCycle>,
    records: List<HabitRecord>,
    habitReminders: List<app.constancia.model.HabitReminder>,
    todayYmdStr: String,
    onCheck: () -> Unit,
    onSetQuantity: (Double) -> Unit,
    onPauseToggle: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onStartCycle: (Int) -> Unit,
    onSaveRecord: (String, String?) -> Unit,
    onSetHabitReminders: (List<String>) -> Unit,
    onEdit: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val habitColor = habitColorOf(habit.color)
    val weekly = habit.frequencyType == FrequencyType.WEEKLY_X
    val streak = status?.currentStreak ?: 0
    val doneToday = status?.isDoneToday == true
    val weekDone = status?.weekDone ?: 0
    val weekTarget = weekTargetOf(habit)
    // M4 (varredura): menu de acoes por habito (pausar/arquivar/excluir
    // existiam no repositorio sem nenhuma entrada na interface).
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Redesenho 2026-11-01: mapa e ações moram nos detalhes do cartão.
    var expanded by remember(habit.id) { mutableStateOf(false) }
    // R13.2 (Fase D): desde quando a sequência vem + o recorde —
    // calculado aqui porque o rodapé do cartão o exibe.
    val caption = status?.streakInfo?.let {
        app.constancia.domain.streakCaption(it)
    }

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(1.dp, extra.lineSoft, RoundedCornerShape(24.dp))
            .padding(17.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(habitColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    habitIcon(habit.icon),
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = frequencyShort(habit) + sessionSuffix(habit) +
                        restDaysSuffix(habit),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!habit.paused && (dueToday || doneToday)) {
                Box(
                    modifier = Modifier.size(38.dp)
                        .then(
                            if (doneToday) {
                                Modifier.shadow(
                                    elevation = 7.dp,
                                    shape = CircleShape,
                                    spotColor = scheme.primary.copy(alpha = 0.45f),
                                    ambientColor = scheme.primary.copy(alpha = 0.45f),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clip(CircleShape)
                        .background(
                            if (doneToday) scheme.primary else Color.Transparent,
                        )
                        .then(
                            if (doneToday) Modifier
                            else Modifier.border(
                                2.5.dp, extra.checkBorder, CircleShape,
                            ),
                        )
                        .testTag("habit_check_" + habit.id)
                        .clickable { onCheck() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (doneToday) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Marcar " + habit.name,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            // M4: menu de acoes do habito.
            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Ações de " + habit.name,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { menuOpen = true }
                        .testTag("habit_menu_" + habit.id)
                        .padding(9.dp),
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    // Redesenho 2026-11-01: Editar mora no menu (o
                    // cabeçalho do cartão respirou).
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        modifier = Modifier.testTag("habit_edit_" + habit.id),
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(if (habit.paused) "Retomar" else "Pausar")
                        },
                        onClick = { menuOpen = false; onPauseToggle() },
                    )
                    DropdownMenuItem(
                        text = { Text("Arquivar") },
                        onClick = { menuOpen = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Excluir") },
                        onClick = { menuOpen = false; confirmDelete = true },
                    )
                }
            }
        }

        // M1: em habito de quantidade, o progresso de hoje se ajusta
        // aqui (antes so existia na Hoje; o circulo apagava o parcial).
        if (!habit.paused && habit.goalType == GoalType.QUANTITY &&
            (dueToday || doneToday)
        ) {
            val currentQty = status?.todayCheckin?.quantity ?: 0.0
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Hoje",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                CardQtyButton(
                    text = "−",
                    tag = "habit_qty_dec_" + habit.id,
                    onClick = {
                        onSetQuantity(
                            (currentQty - habit.quantityStep)
                                .coerceAtLeast(0.0),
                        )
                    },
                )
                Text(
                    text = fmtQty(currentQty) + " de " +
                        fmtQty(habit.targetQuantity) + " " + habit.quantityUnit,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
                CardQtyButton(
                    text = "+",
                    tag = "habit_qty_inc_" + habit.id,
                    onClick = {
                        onSetQuantity(
                            (currentQty + habit.quantityStep)
                                .coerceAtMost(habit.targetQuantity),
                        )
                    },
                )
            }
        }

        if (habit.paused) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Em pausa — sequência protegida.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = scheme.primary,
            )
        } else {
            // Goalbar do protótipo: sequência em chama + meta da semana,
            // barra de 8dp na cor do próprio hábito.
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    text = when {
                        weekly && streak == 1 -> "1 semana de sequência"
                        weekly -> streak.toString() + " semanas de sequência"
                        streak == 1 -> "1 dia de sequência"
                        else -> streak.toString() + " dias de sequência"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = scheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "meta da semana · " + weekDone.toString() +
                        " de " + weekTarget.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(scheme.outline),
            ) {
                val frac = if (weekTarget > 0) {
                    weekDone.toFloat() / weekTarget
                } else {
                    0f
                }
                Box(
                    modifier = Modifier.fillMaxWidth(frac.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(habitColor, RoundedCornerShape(99.dp)),
                )
            }
            // A legenda da sequência (desde/recorde) desceu para o
            // rodapé do cartão no redesenho 2026-11-01.
        }

        // Redesenho 2026-11-01: na frente do cartão, a semana atual
        // em bolinhas (a última coluna da grade) — leitura num olhar.
        // O mapa completo desceu para os detalhes.
        if (!habit.paused) {
            grid.lastOrNull()?.let { currentWeek ->
                Spacer(Modifier.height(13.dp))
                WeekStrip(
                    week = currentWeek,
                    todayRow = todayRow,
                    habitColor = habitColor,
                )
            }
        }

        // Rodapé do cartão: legenda da sequência + abre/fecha detalhes.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = caption ?: "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                ),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .testTag("habit_details_" + habit.id)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (expanded) "Recolher" else "Detalhes",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = extra.lineSoft)
            HeatmapBlock(
                grid = grid,
                todayRow = todayRow,
                todayDate = todayDate,
                habitColor = habitColor,
            )
        }

        if (expanded && !habit.paused) {
            // Item 4: pular hoje (registro que protege a sequência) —
            // no redesenho, vira a primeira linha de ação dos detalhes.
            val skippedToday = status?.todayCheckin?.status == CheckinStatus.SKIPPED
            if (dueToday || doneToday || skippedToday) {
                if (skippedToday) {
                    DetailActionRow(
                        icon = Icons.Filled.SkipNext,
                        title = "Pulado hoje",
                        value = "sequência protegida",
                        actionText = "Desfazer",
                        onAction = onUnskip,
                    )
                } else if (!doneToday) {
                    DetailActionRow(
                        icon = Icons.Filled.SkipNext,
                        title = "Não vai dar hoje?",
                        value = "pular protege a sequência",
                        actionText = "Pular hoje",
                        onAction = onSkip,
                    )
                }
            }

            // Item 2: ciclo/temporada do hábito.
            HabitCycleSection(
                habit = habit,
                cycles = cycles,
                habitCheckins = habitCheckins,
                todayYmdStr = todayYmdStr,
                onStartCycle = onStartCycle,
            )

            // Item 3: log do hábito (nota + emoji do dia).
            HabitLogSection(
                records = records,
                todayYmdStr = todayYmdStr,
                onSaveRecord = onSaveRecord,
            )

            // Fase C: lembretes múltiplos do hábito (um alarme/dia
            // por horário, mesmo sistema das tarefas).
            HabitRemindersSection(
                reminders = habitReminders,
                onSetReminders = onSetHabitReminders,
            )
        }
    }

    // M4: excluir pede confirmação (apaga o histórico junto).
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir hábito?") },
            text = {
                Text(
                    "O hábito \"" + habit.name + "\" e todo o histórico " +
                        "de check-ins dele são apagados. Não dá para desfazer.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDelete() },
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}

private val WEEK_LETTERS = listOf("D", "S", "T", "Q", "Q", "S", "S")

private val MONTH_ABBR = listOf(
    "jan", "fev", "mar", "abr", "mai", "jun",
    "jul", "ago", "set", "out", "nov", "dez",
)

/**
 * Redesenho 2026-11-01: a semana atual do hábito em 7 bolinhas com a
 * letra do dia — feito = cor cheia com ✓, parcial/pulado = tons da
 * cor, hoje = anel da marca por cima do estado do dia.
 */
@Composable
private fun WeekStrip(
    week: List<HeatCell>,
    todayRow: Int,
    habitColor: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(modifier = Modifier.fillMaxWidth()) {
        week.forEachIndexed { rowIdx, cell ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = WEEK_LETTERS.getOrElse(rowIdx) { "" },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val bg = when (cell) {
                    HeatCell.DONE -> habitColor
                    HeatCell.PARTIAL -> habitColor.copy(alpha = 0.45f)
                    HeatCell.SKIPPED -> habitColor.copy(alpha = 0.18f)
                    HeatCell.MISSED -> extra.heatEmpty
                    HeatCell.OFF -> extra.heatEmpty.copy(alpha = 0.45f)
                    HeatCell.FUTURE -> Color.Transparent
                }
                Box(
                    modifier = Modifier.size(26.dp)
                        .clip(CircleShape)
                        .background(bg)
                        .then(
                            when {
                                rowIdx == todayRow -> Modifier.border(
                                    2.dp, scheme.primary, CircleShape,
                                )
                                cell == HeatCell.FUTURE -> Modifier.border(
                                    1.5.dp, extra.lineSoft, CircleShape,
                                )
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (cell == HeatCell.DONE) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    } else if (cell == HeatCell.SKIPPED) {
                        Text(
                            text = "–",
                            style = MaterialTheme.typography.labelLarge,
                            color = habitColor,
                        )
                    }
                }
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(
            text = "semana atual",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
            ),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "hoje · anel roxo",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.5.sp,
            ),
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Redesenho 2026-11-01: o mapa de calor legível — meses no topo,
 * letras dos dias na lateral, legenda dos tons embaixo e "hoje" com
 * o anel da marca (a borda escura antiga parecia defeito).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeatmapBlock(
    grid: List<List<HeatCell>>,
    todayRow: Int,
    todayDate: LocalDate,
    habitColor: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Seu histórico",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "últimas " + grid.size.toString() + " semanas",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
    }
    if (grid.isEmpty()) return
    val cols = grid.size
    val start = heatGridStart(todayDate, cols)
    // Mês de cada coluna (pelo domingo da semana dela): o rótulo
    // entra na primeira coluna de cada mês.
    val monthCols = (0 until cols).map { col ->
        start.plus(DatePeriod(days = col * 7)).monthNumber
    }
    val gap = 3.dp
    val labelW = 14.dp
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        val cell = (maxWidth - labelW - gap - gap * (cols - 1)) / cols
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(13.dp)) {
                monthCols.forEachIndexed { col, month ->
                    if (col == 0 || month != monthCols[col - 1]) {
                        Text(
                            text = MONTH_ABBR[month - 1],
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.offset(
                                x = labelW + gap + (cell + gap) * col,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            (0..6).forEach { rowIdx ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = if (rowIdx == 0) 0.dp else gap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(labelW).height(cell),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = WEEK_LETTERS[rowIdx],
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(gap))
                    grid.forEachIndexed { colIdx, week ->
                        val cellState = week[rowIdx]
                        val color = when (cellState) {
                            // Cor do próprio hábito (semântica), não a
                            // da marca. Parcial = progresso sem bater
                            // a meta; pulado = tom bem claro.
                            HeatCell.DONE -> habitColor
                            HeatCell.PARTIAL -> habitColor.copy(alpha = 0.45f)
                            HeatCell.SKIPPED -> habitColor.copy(alpha = 0.18f)
                            HeatCell.MISSED -> extra.heatEmpty
                            HeatCell.OFF -> extra.heatEmpty
                            HeatCell.FUTURE -> Color.Transparent
                        }
                        val isTodayCell =
                            colIdx == grid.lastIndex && rowIdx == todayRow
                        Box(
                            modifier = Modifier
                                .padding(
                                    start = if (colIdx == 0) 0.dp else gap,
                                )
                                .size(cell)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                                .then(
                                    if (isTodayCell) {
                                        Modifier.border(
                                            2.dp, scheme.primary,
                                            RoundedCornerShape(3.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
    // Legenda: cada tom do mapa explicado.
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(habitColor, "feito")
        LegendItem(habitColor.copy(alpha = 0.45f), "parcial")
        LegendItem(habitColor.copy(alpha = 0.18f), "pulado")
        LegendItem(extra.heatEmpty, "não feito")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.5.dp, scheme.primary, RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "hoje",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                ),
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = scheme.onSurfaceVariant,
        )
    }
}

/**
 * Redesenho 2026-11-01: linha de ação compacta dos detalhes do
 * cartão — ícone em tile, título + valor curto e a ação à direita.
 * Substitui os parágrafos de explicação das seções antigas.
 */
@Composable
private fun DetailActionRow(
    icon: ImageVector,
    title: String,
    value: String?,
    actionText: String?,
    onAction: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
                color = scheme.onSurface,
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                    ),
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(4.dp),
            )
        }
    }
}

/** Botão +/− do ajuste de quantidade no cartão (M1). */
@Composable
private fun CardQtyButton(text: String, tag: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(scheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.onSurface,
        )
    }
}

private val CYCLE_DAY_OPTIONS = listOf(7, 21, 30, 66, 90)

/**
 * Seção de ciclo do cartão do hábito: progresso do ciclo ativo com a
 * sequência dentro dele; celebração + "próximo ciclo" quando conclui;
 * convite discreto quando não há ciclo. Ciclos concluídos ficam
 * contados aqui — são o histórico permanente do hábito.
 */
@Composable
private fun HabitCycleSection(
    habit: Habit,
    cycles: List<HabitCycle>,
    habitCheckins: List<Checkin>,
    todayYmdStr: String,
    onStartCycle: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val habitColor = habitColorOf(habit.color)
    val doneDates = doneDatesFor(habit, donePairs(habitCheckins))
    val skipped = skippedDatesFor(habitCheckins)
    val sorted = cycles.sortedBy { it.startDate }
    val completedCount = sorted.count { it.completedAt != null }
    val active = sorted.lastOrNull {
        cycleState(it, doneDates, todayYmdStr) == CycleState.ACTIVE
    }
    val latest = sorted.lastOrNull()
    var showDialog by remember(habit.id) { mutableStateOf(false) }

    // Redesenho 2026-11-01: cabeçalho compacto de linha de ação —
    // o valor resume o estado e o "Iniciar ciclo" mora aqui quando
    // não há ciclo nenhum.
    val noCycle = active == null && latest == null
    DetailActionRow(
        icon = Icons.Filled.Loop,
        title = "Ciclo",
        value = when {
            completedCount > 0 ->
                "🏆 " + completedCount.toString() +
                    if (completedCount == 1) {
                        " ciclo concluído"
                    } else {
                        " ciclos concluídos"
                    }
            noCycle -> "sem ciclo ativo"
            active != null -> "ciclo ativo"
            else -> null
        },
        actionText = if (noCycle) "Iniciar ciclo" else null,
        onAction = if (noCycle) ({ showDialog = true }) else null,
    )
    if (!noCycle) Spacer(Modifier.height(8.dp))
    when {
        active != null -> {
            val doneInCycle = cycleDoneDays(active, doneDates)
            val streakInCycle = cycleStreak(active, doneDates, skipped, todayYmdStr)
            val end = active.endDate.toLocalDate()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = doneInCycle.toString() + " de " +
                        active.targetDays.toString() + " dias",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "termina " + end.dayOfMonth.toString() + "/" +
                        end.monthNumber.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(scheme.outline),
            ) {
                val frac = if (active.targetDays > 0) {
                    doneInCycle.toFloat() / active.targetDays
                } else {
                    0f
                }
                Box(
                    modifier = Modifier.fillMaxWidth(frac.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(habitColor, RoundedCornerShape(99.dp)),
                )
            }
            if (streakInCycle > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "sequência no ciclo · " + streakInCycle.toString() +
                        if (streakInCycle == 1) " dia" else " dias",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        latest != null && latest.completedAt != null -> {
            // Celebração: o ciclo fechou e fica no histórico.
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.primaryContainer)
                    .padding(13.dp),
            ) {
                Text(
                    text = "🎉 Ciclo concluído!",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = scheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Você completou o ciclo de " +
                        latest.targetDays.toString() +
                        " dias. Ele fica guardado no seu histórico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(9.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.primary)
                        .clickable { onStartCycle(latest.targetDays) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = "Começar próximo ciclo",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onPrimary,
                    )
                }
            }
        }
        latest != null -> {
            // Último ciclo expirou sem bater a meta.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "O ciclo terminou sem bater a meta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Tentar de novo",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onStartCycle(latest.targetDays) }
                        .padding(4.dp),
                )
            }
        }
        // Sem ciclo nenhum: nada abaixo do cabeçalho — o "Iniciar
        // ciclo" já mora nele (redesenho 2026-11-01).
        else -> Unit
    }

    if (showDialog) {
        var selectedIdx by remember(habit.id) {
            mutableStateOf(CYCLE_DAY_OPTIONS.indexOf(30).coerceAtLeast(0))
        }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = scheme.surface,
            title = {
                Text("Novo ciclo", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column {
                    Text(
                        text = "Quantos dias de meta neste ciclo? A temporada " +
                            "começa hoje e dura o mesmo número de dias.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    SegmentedRow(
                        options = CYCLE_DAY_OPTIONS.map { it.toString() },
                        selectedIndex = selectedIdx,
                        onSelect = { selectedIdx = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onStartCycle(CYCLE_DAY_OPTIONS[selectedIdx])
                    },
                ) { Text("Começar ciclo") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

private val RECORD_EMOJIS =
    listOf("💧", "🏃", "📖", "🧘", "💪", "😴", "🍎", "✍️", "🎯", "🌿")

/**
 * Seção do log do hábito: nota + emoji de hoje e os registros recentes.
 * O dado fica em tabela própria (habit_record) — a aba Padrões poderá
 * cruzar humor por hábito a partir dele.
 */
@Composable
private fun HabitLogSection(
    records: List<HabitRecord>,
    todayYmdStr: String,
    onSaveRecord: (String, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val todayRecord = records.firstOrNull { it.date == todayYmdStr }
    val recent = records.filter { it.date != todayYmdStr }.take(3)
    var showDialog by remember { mutableStateOf(false) }

    // Redesenho 2026-11-01: cabeçalho compacto de linha de ação.
    DetailActionRow(
        icon = Icons.Filled.MenuBook,
        title = "Diário do hábito",
        value = if (todayRecord == null && recent.isEmpty()) {
            "nenhuma nota hoje"
        } else {
            null
        },
        actionText = if (todayRecord != null) "Editar nota" else "Anotar hoje",
        onAction = { showDialog = true },
    )
    if (todayRecord != null &&
        (todayRecord.content.isNotBlank() || todayRecord.emoji != null)
    ) {
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (todayRecord.emoji != null) {
                Text(text = todayRecord.emoji, fontSize = 17.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = todayRecord.content,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface,
            )
        }
    }
    // Sem nota hoje e sem recentes: o cabeçalho já diz "nenhuma nota
    // hoje" — o parágrafo antigo saiu no redesenho 2026-11-01.
    recent.forEach { record ->
        Spacer(Modifier.height(6.dp))
        val d = record.date.toLocalDate()
        Text(
            text = d.dayOfMonth.toString() + "/" + d.monthNumber.toString() +
                (record.emoji?.let { " · $it" } ?: "") +
                if (record.content.isNotBlank()) {
                    " · " + record.content.take(60)
                } else {
                    ""
                },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }

    if (showDialog) {
        var text by remember { mutableStateOf(todayRecord?.content ?: "") }
        var emoji by remember { mutableStateOf(todayRecord?.emoji) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = scheme.surface,
            title = {
                Text("Diário do hábito", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Column {
                    // Duas fileiras de 5: cabe em 320dp sem estourar.
                    RECORD_EMOJIS.chunked(5).forEach { emojiRow ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            emojiRow.forEach { e ->
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (emoji == e) {
                                                scheme.primaryContainer
                                            } else {
                                                scheme.surfaceVariant
                                            },
                                        )
                                        .clickable {
                                            emoji = if (emoji == e) null else e
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(text = e, fontSize = 16.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { if (it.length <= 140) text = it },
                        label = { Text("Como foi hoje?") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        onSaveRecord(text.trim(), emoji)
                    },
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

/**
 * Fase C — seção de lembretes do cartão do hábito: a LISTA de horários
 * em que o app chama todo dia. Editar abre um diálogo com os horários
 * atuais (toque remove) e um campo para adicionar novos; salvar grava
 * a lista e reagenda os alarmes. Hábito deixa de ter lembrete único.
 */
@Composable
private fun HabitRemindersSection(
    reminders: List<app.constancia.model.HabitReminder>,
    onSetReminders: (List<String>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var showDialog by remember { mutableStateOf(false) }
    val times = reminders.map { it.time }.sorted()

    // Redesenho 2026-11-01: cabeçalho compacto de linha de ação —
    // os horários viram o valor curto da linha.
    DetailActionRow(
        icon = Icons.Filled.Notifications,
        title = "Lembretes",
        value = if (times.isEmpty()) {
            "sem lembrete"
        } else {
            "todo dia às " + times.joinToString(" e às ")
        },
        actionText = if (times.isEmpty()) "Adicionar" else "Editar",
        onAction = { showDialog = true },
    )

    if (showDialog) {
        var draft by remember { mutableStateOf(times) }
        var timeText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = scheme.surface,
            title = {
                Text(
                    "Lembretes do hábito",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Um toque em cada horário, todo dia. " +
                            "Toque num horário para remover.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    if (draft.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            draft.forEach { time ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(scheme.primaryContainer)
                                        .clickable { draft = draft - time }
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 7.dp,
                                        ),
                                ) {
                                    Text(
                                        text = "$time ×",
                                        style = MaterialTheme
                                            .typography.labelLarge,
                                        color = scheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = { value ->
                                timeText = value
                                    .filter { c -> c.isDigit() || c == ':' }
                                    .take(5)
                            },
                            placeholder = { Text("HH:mm") },
                            singleLine = true,
                            modifier = Modifier.width(104.dp),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Adicionar",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val parsed = app.constancia.domain
                                        .parseTimeOrNull(timeText)
                                    if (parsed != null) {
                                        val hhmm = parsed.hour.toString()
                                            .padStart(2, '0') + ":" +
                                            parsed.minute.toString()
                                                .padStart(2, '0')
                                        if (hhmm !in draft) {
                                            draft = (draft + hhmm).sorted()
                                        }
                                        timeText = ""
                                    }
                                }
                                .padding(4.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSetReminders(draft)
                        showDialog = false
                    },
                ) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar")
                }
            },
        )
    }
}
