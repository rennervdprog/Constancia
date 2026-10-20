package app.constancia.ui.habits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.constancia.AppDeps
import app.constancia.domain.DOW_SHORT
import app.constancia.domain.HeatCell
import app.constancia.domain.bestStreak
import app.constancia.domain.completionRateLast30
import app.constancia.domain.doneDatesFor
import app.constancia.domain.forgivenDates
import app.constancia.domain.heatGrid
import app.constancia.domain.isDueOn
import app.constancia.model.FrequencyType
import app.constancia.model.Habit
import app.constancia.model.HabitStatus
import app.constancia.model.PauseInterval
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.SecondaryButton
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

/**
 * Aba Hábitos (protótipo aprovado): 4 tiles de resumo e cartão por hábito
 * com streak, meta semanal e mapa de calor das últimas 18 semanas.
 */
@Composable
fun HabitsScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenReports: () -> Unit,
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
    val pausesByHabit by produceState<Map<String, List<PauseInterval>>>(emptyMap()) {
        value = deps.habits.allPauseIntervals()
    }

    val statusByHabit = statuses.associateBy { it.habit.id }
    val checkinDays30 = checkins
        .map { it.date }
        .filter { it >= todayDate.minus(DatePeriod(days = 29)).toString() }
        .distinct()
        .size
    val maxStreakStatus = statuses.maxByOrNull { it.currentStreak }
    val completion = completionRateLast30(habits, checkins, todayDate)

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("habits_list"),
        contentPadding = PaddingValues(start = 20.dp, top = 10.dp, end = 20.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Hábitos", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sequências, mapa de calor e o ritmo de cada hábito",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TileCard(modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.Whatshot,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = (maxStreakStatus?.currentStreak ?: 0).toString() + " dias",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Text(
                        text = "maior sequência atual" +
                            (maxStreakStatus?.let { " — " + it.habit.name } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TileCard(modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.FormatListBulleted,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = habits.count { !it.paused }.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Text(
                        text = "hábitos ativos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TileCard(modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = checkinDays30.toString() + " dias",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Text(
                        text = "com check-in — últimos 30 dias",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TileCard(modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = (completion * 100).toInt().toString() + "%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    )
                    Text(
                        text = "conclusão — últimos 30 dias",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                val doneDates = doneDatesFor(
                    habit,
                    habitCheckins.map { it.date to it.quantity },
                )
                val forgiven = forgivenDates(
                    pausesByHabit[habit.id].orEmpty(),
                    today,
                )
                HabitCard(
                    habit = habit,
                    status = status,
                    grid = heatGrid(habit, habitCheckins, todayDate),
                    doneCount = doneDates.size,
                    best = bestStreak(habit, doneDates, forgiven),
                    dueToday = isDueOn(
                        habit,
                        todayDate,
                        runCatching { habit.createdAt.take(10).toLocalDate() }
                            .getOrNull(),
                    ),
                    onCheck = {
                        scope.launch { deps.checkins.toggle(habit.id, today) }
                    },
                )
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            SecondaryButton(
                text = "Ver progresso geral e relatórios",
                onClick = onOpenReports,
                modifier = Modifier.fillMaxWidth(),
                tag = "open_reports",
            )
        }
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    status: HabitStatus?,
    grid: List<List<HeatCell>>,
    doneCount: Int,
    best: Int,
    dueToday: Boolean,
    onCheck: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val weekly = habit.frequencyType == FrequencyType.WEEKLY_X
    val streak = status?.currentStreak ?: 0
    val doneToday = status?.isDoneToday == true

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(scheme.surface, RoundedCornerShape(24.dp))
            .border(BorderStroke(1.dp, scheme.outline), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp)
                    .background(
                        Color(habit.color).copy(alpha = 0.16f),
                        RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    habitIcon(habit.icon),
                    contentDescription = null,
                    tint = Color(habit.color),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = frequencyLabel(habit),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            // Chip da sequência com chama.
            Row(
                modifier = Modifier
                    .background(
                        if (streak > 0) extra.amberSoft else scheme.surfaceVariant,
                        RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Whatshot,
                    contentDescription = null,
                    tint = if (streak > 0) scheme.secondary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = streak.toString() + if (weekly) " sem" else "d",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (streak > 0) scheme.secondary
                        else scheme.onSurfaceVariant,
                )
            }
            if (dueToday && !doneToday && !habit.paused) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(40.dp)
                        .background(scheme.primary, CircleShape)
                        .testTag("habit_check_" + habit.id)
                        .clickable { onCheck() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Marcar " + habit.name,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
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
        } else if (weekly) {
            val doneThisWeek = status?.weekDone ?: 0
            val target = habit.weeklyTarget
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = doneThisWeek.toString() + " de " + target.toString() +
                        " dias esta semana",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "meta flexível",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier.fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(scheme.surfaceVariant),
            ) {
                val frac = if (target > 0) {
                    doneThisWeek.toFloat() / target
                } else {
                    0f
                }
                Box(
                    modifier = Modifier.fillMaxWidth(frac.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(scheme.primary, RoundedCornerShape(999.dp)),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Meta flexível, qualquer dia conta.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // Mapa de calor: 18 colunas (semanas), 7 linhas (dom..sáb).
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            grid.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { cell ->
                        val color = when (cell) {
                            HeatCell.DONE -> scheme.primary
                            HeatCell.MISSED -> extra.heat0
                            HeatCell.OFF -> extra.pageCard
                            HeatCell.FUTURE -> Color.Transparent
                        }
                        Box(
                            modifier = Modifier.size(11.dp)
                                .background(color, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Últimas 18 semanas · " + doneCount.toString() +
                    " dias feitos · melhor sequência: " + best.toString() +
                    if (weekly) " semanas" else " dias",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    habit.paused -> ""
                    doneToday -> "feito hoje"
                    dueToday -> "pendente hoje"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = if (doneToday) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
}
