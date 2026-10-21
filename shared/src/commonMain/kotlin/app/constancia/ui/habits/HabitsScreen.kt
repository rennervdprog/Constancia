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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.constancia.domain.DOW_SHORT
import app.constancia.domain.HeatCell
import app.constancia.domain.completionRateLast30
import app.constancia.domain.heatGrid
import app.constancia.domain.isDueOn
import app.constancia.model.FrequencyType
import app.constancia.model.Habit
import app.constancia.model.HabitStatus
import app.constancia.model.Session
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.todayYmd
import app.constancia.theme.constanciaExtra
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
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
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
                    onCheck = {
                        scope.launch { deps.checkins.toggle(habit.id, today) }
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
    onCheck: () -> Unit,
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
                            HeatCell.DONE -> habitColor
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
    }
}
