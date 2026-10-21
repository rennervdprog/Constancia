package app.constancia.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.CycleState
import app.constancia.domain.DOW_SHORT
import app.constancia.domain.HeatCell
import app.constancia.domain.completionRateLast30
import app.constancia.domain.cycleDoneDays
import app.constancia.domain.cycleState
import app.constancia.domain.cycleStreak
import app.constancia.domain.doneDatesFor
import app.constancia.domain.heatGrid
import app.constancia.domain.isDueOn
import app.constancia.domain.skippedDatesFor
import app.constancia.model.Checkin
import app.constancia.model.CheckinStatus
import app.constancia.model.FrequencyType
import app.constancia.model.Habit
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
import app.constancia.ui.common.habitIcon
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

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
    val rangeFrom = remember {
        todayDate.minus(DatePeriod(days = 400)).toString()
    }
    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val statuses by deps.habits.todayStatusFlow().collectAsState(initial = emptyList())
    val checkins by deps.checkins.checkinsInRangeFlow(rangeFrom, today)
        .collectAsState(initial = emptyList())
    // TickTick itens 2 e 3: ciclos e log de todos os hábitos.
    val allCycles by deps.habits.allCyclesFlow().collectAsState(initial = emptyList())
    val allRecords by deps.checkins.allRecordsFlow()
        .collectAsState(initial = emptyList())

    val statusByHabit = statuses.associateBy { it.habit.id }
    val checkinDays30 = checkins
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
                    text = activeCount.toString() + " ativos · mapa de 18 semanas",
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
                    dueToday = isDueOn(
                        habit,
                        todayDate,
                        runCatching { habit.createdAt.take(10).toLocalDate() }
                            .getOrNull(),
                    ),
                    habitCheckins = habitCheckins,
                    cycles = allCycles.filter { it.habitId == habit.id },
                    records = allRecords.filter { it.habitId == habit.id },
                    todayYmdStr = today,
                    onCheck = {
                        scope.launch { deps.checkins.toggle(habit.id, today) }
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
                )
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
    dueToday: Boolean,
    habitCheckins: List<Checkin>,
    cycles: List<HabitCycle>,
    records: List<HabitRecord>,
    todayYmdStr: String,
    onCheck: () -> Unit,
    onSkip: () -> Unit,
    onUnskip: () -> Unit,
    onStartCycle: (Int) -> Unit,
    onSaveRecord: (String, String?) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val habitColor = Color(habit.color)
    val weekly = habit.frequencyType == FrequencyType.WEEKLY_X
    val streak = status?.currentStreak ?: 0
    val doneToday = status?.isDoneToday == true
    val weekDone = status?.weekDone ?: 0
    val weekTarget = weekTargetOf(habit)

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
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = frequencyShort(habit) + sessionSuffix(habit),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
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
        }

        // Mapa de calor: 18 colunas (semanas) × 7 linhas (dom..sáb),
        // células flexíveis que preenchem a largura do cartão.
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            grid.forEachIndexed { colIdx, week ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    week.forEachIndexed { rowIdx, cell ->
                        val color = when (cell) {
                            // Cor do próprio hábito (semântica), não a da marca.
                            // Parcial = progresso sem bater a meta (mais
                            // claro); pulado = registro neutro bem claro.
                            HeatCell.DONE -> habitColor
                            HeatCell.PARTIAL -> habitColor.copy(alpha = 0.45f)
                            HeatCell.SKIPPED -> habitColor.copy(alpha = 0.18f)
                            HeatCell.MISSED -> extra.heatEmpty
                            HeatCell.OFF -> extra.heatEmpty
                            HeatCell.FUTURE -> Color.Transparent
                        }
                        val isTodayCell = colIdx == grid.lastIndex && rowIdx == todayRow
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color)
                                .then(
                                    if (isTodayCell) {
                                        Modifier.border(
                                            2.dp, scheme.onSurface,
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
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "há 18 semanas",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "hoje",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = scheme.onSurfaceVariant,
            )
        }

        if (!habit.paused) {
            // TickTick item 4: pular hoje (registro que protege a sequência).
            val skippedToday = status?.todayCheckin?.status == CheckinStatus.SKIPPED
            if (dueToday || doneToday || skippedToday) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (skippedToday) {
                        Text(
                            text = "Pulado hoje — sequência protegida",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = scheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Desfazer",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onUnskip)
                                .padding(4.dp),
                        )
                    } else if (!doneToday) {
                        Text(
                            text = "Não vai dar hoje?",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "Pular hoje",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onSkip)
                                .padding(4.dp),
                        )
                    }
                }
            }

            // TickTick item 2: ciclo/temporada do hábito.
            HabitCycleSection(
                habit = habit,
                cycles = cycles,
                habitCheckins = habitCheckins,
                todayYmdStr = todayYmdStr,
                onStartCycle = onStartCycle,
            )

            // TickTick item 3: log do hábito (nota + emoji do dia).
            HabitLogSection(
                records = records,
                todayYmdStr = todayYmdStr,
                onSaveRecord = onSaveRecord,
            )
        }
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
    val habitColor = Color(habit.color)
    val doneDates = doneDatesFor(habit, habitCheckins.map { it.date to it.quantity })
    val skipped = skippedDatesFor(habitCheckins)
    val sorted = cycles.sortedBy { it.startDate }
    val completedCount = sorted.count { it.completedAt != null }
    val active = sorted.lastOrNull {
        cycleState(it, doneDates, todayYmdStr) == CycleState.ACTIVE
    }
    val latest = sorted.lastOrNull()
    var showDialog by remember(habit.id) { mutableStateOf(false) }

    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Ciclo",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (completedCount > 0) {
            Text(
                text = "🏆 " + completedCount.toString() +
                    if (completedCount == 1) " ciclo concluído" else " ciclos concluídos",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = scheme.secondary,
            )
        }
    }
    Spacer(Modifier.height(7.dp))
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
        else -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Trabalhe em temporadas curtas, com linha de chegada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Iniciar ciclo",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDialog = true }
                        .padding(4.dp),
                )
            }
        }
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

    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Diário do hábito",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (todayRecord != null) "Editar nota" else "Anotar hoje",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDialog = true }
                .padding(4.dp),
        )
    }
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
    } else if (recent.isEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Uma frase sobre o hábito hoje vira memória depois.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
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
