package app.constancia.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.constancia.AppDeps
import app.constancia.domain.buildExportCsv
import app.constancia.domain.currentStreak
import app.constancia.domain.doneDatesFor
import app.constancia.export.shareTextFile
import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.isoToAppDay
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import app.constancia.theme.constanciaExtra
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Modelos internos da tela
// ---------------------------------------------------------------------------

private enum class ReportPeriod(val days: Int, val label: String) {
    SEMANA(7, "Semana"),
    MES(30, "Mês"),
    ANO(365, "Ano"),
}

private enum class ChartMode(val label: String) {
    CONCLUSAO("Conclusão"),
    CHECKINS("Check-ins"),
}

private data class DayPoint(
    val date: LocalDate,
    val due: Int,
    val done: Int,
    val checkins: Int,
) {
    val rate: Double get() = if (due > 0) done.toDouble() / due else 0.0
    val isPerfect: Boolean get() = due > 0 && done >= due
}

private data class HabitReport(
    val habit: Habit,
    val streak: Int,
    val dueDays: Int,
    val doneDays: Int,
    val checkinCount: Int,
    val totalQuantity: Double,
) {
    val rate: Double get() = if (dueDays > 0) doneDays.toDouble() / dueDays else 0.0
}

private data class ReportData(
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DayPoint>,
    val completionRate: Double,
    val totalCheckins: Int,
    val perfectDays: Int,
    val activeDays: Int,
    val maxStreak: Int,
    val habits: List<HabitReport>,
    val weekdayRates: List<Double>, // índice 0..6 = Dom..Sáb; -1.0 = sem dados
    val moodPerfectAvg: Double?,
    val moodPendingAvg: Double?,
    val insights: List<String>,
)

private val DOW_SHORT = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
private val DOW_FULL =
    listOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")
private val MONTHS =
    listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

// ---------------------------------------------------------------------------
// Formatação (sem String.format — commonMain)
// ---------------------------------------------------------------------------

private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

private fun fmt1(v: Double): String {
    val r = (v * 10).roundToInt()
    return "${r / 10},${abs(r % 10)}"
}

private fun fmtQty(v: Double): String {
    val whole = v.roundToInt()
    return if (v == whole.toDouble()) "$whole" else fmt1(v)
}

private fun formatDay(d: LocalDate): String =
    "${d.dayOfMonth} de ${MONTHS[d.monthNumber - 1]}"

// ---------------------------------------------------------------------------
// Cálculo do relatório (função pura — tudo derivado dos dados reais)
// ---------------------------------------------------------------------------

private fun weekStartOf(d: LocalDate): LocalDate =
    d.minus(DatePeriod(days = d.isoDayNumberCompat() - 1))

private fun isDueOn(habit: Habit, date: LocalDate, activeFrom: LocalDate?): Boolean {
    if (habit.paused) return false
    if (activeFrom != null && date < activeFrom) return false
    return when (habit.frequencyType) {
        FrequencyType.DAILY -> true
        FrequencyType.WEEKLY_X -> true
        FrequencyType.DAYS_OF_WEEK ->
            habit.daysOfWeek.isEmpty() ||
                isoToAppDay(date.isoDayNumberCompat()) in habit.daysOfWeek
    }
}

// Para WEEKLY_X: o dia conta como cumprido se houve check-in nele ou se a meta
// semanal já tinha sido batida antes dele na mesma semana.
private fun satisfiedDates(habit: Habit, doneDates: Set<String>): Set<String> {
    if (habit.frequencyType != FrequencyType.WEEKLY_X || habit.weeklyTarget <= 0) {
        return doneDates
    }
    val out = doneDates.toMutableSet()
    doneDates.groupBy { weekStartOf(it.toLocalDate()) }.forEach { (ws, dates) ->
        val sorted = dates.sorted()
        if (sorted.size >= habit.weeklyTarget) {
            var d = sorted[habit.weeklyTarget - 1].toLocalDate().plus(DatePeriod(days = 1))
            val weekEnd = ws.plus(DatePeriod(days = 6))
            while (d <= weekEnd) {
                out.add(d.toYmd())
                d = d.plus(DatePeriod(days = 1))
            }
        }
    }
    return out
}

private fun rateOf(points: List<DayPoint>): Double {
    val due = points.sumOf { it.due }
    return if (due > 0) points.sumOf { it.done }.toDouble() / due else 0.0
}

private fun computeReport(
    habits: List<Habit>,
    checkins: List<Checkin>,
    diary: List<DiaryEntry>,
    period: ReportPeriod,
    timeZone: TimeZone,
): ReportData {
    val today = Clock.System.todayIn(timeZone)
    val from = today.minus(DatePeriod(days = period.days - 1))
    val dayList = buildList {
        var d = from
        while (d <= today) {
            add(d)
            d = d.plus(DatePeriod(days = 1))
        }
    }
    val checkinsByHabit = checkins.groupBy { it.habitId }
    val doneDatesByHabit = habits.associate { h ->
        val pairs = checkinsByHabit[h.id].orEmpty().map { it.date to it.quantity }
        h.id to satisfiedDates(h, doneDatesFor(h, pairs))
    }
    val activeFromByHabit = habits.associate { h ->
        h.id to runCatching { h.createdAt.take(10).toLocalDate() }.getOrNull()
    }
    fun dueOn(h: Habit, d: LocalDate): Boolean =
        isDueOn(h, d, activeFromByHabit[h.id])

    val checkinsByDate = checkins.groupBy { it.date }
    val points = dayList.map { d ->
        val ymd = d.toYmd()
        val dueHabits = habits.filter { dueOn(it, d) }
        val doneCount =
            dueHabits.count { doneDatesByHabit[it.id].orEmpty().contains(ymd) }
        DayPoint(d, dueHabits.size, doneCount, checkinsByDate[ymd].orEmpty().size)
    }

    val habitReports = habits.map { h ->
        val doneDates = doneDatesByHabit[h.id].orEmpty()
        var due = 0
        var done = 0
        dayList.forEach { d ->
            if (dueOn(h, d)) {
                due++
                if (doneDates.contains(d.toYmd())) done++
            }
        }
        val hcis = checkinsByHabit[h.id].orEmpty()
        HabitReport(
            habit = h,
            streak = currentStreak(h, doneDates, today.toYmd(), timeZone),
            dueDays = due,
            doneDays = done,
            checkinCount = hcis.size,
            totalQuantity = hcis.sumOf { it.quantity },
        )
    }.sortedByDescending { it.rate }

    val weekdayRates = (1..7).map { appDay ->
        val pts = points.filter { isoToAppDay(it.date.isoDayNumberCompat()) == appDay }
        if (pts.isEmpty()) -1.0 else rateOf(pts).let { if (pts.sumOf { p -> p.due } == 0) -1.0 else it }
    }

    val moodByDate = diary.mapNotNull { e -> e.mood?.let { m -> e.date to m } }.toMap()
    val perfectMoods = mutableListOf<Int>()
    val pendingMoods = mutableListOf<Int>()
    points.forEach { p ->
        val m = moodByDate[p.date.toYmd()]
        if (m != null) {
            if (p.isPerfect) perfectMoods.add(m) else if (p.due > 0) pendingMoods.add(m)
        }
    }

    val perfectDays = points.count { it.isPerfect }
    val insights = buildList {
        val valid = weekdayRates.mapIndexedNotNull { i, r -> if (r >= 0) i to r else null }
        if (valid.size >= 2) {
            val best = valid.maxBy { it.second }
            val worst = valid.minBy { it.second }
            if (best.first != worst.first && best.second > 0) {
                add("${DOW_FULL[best.first]} é o seu melhor dia (${pct(best.second)} de conclusão).")
                add("${DOW_FULL[worst.first]} costuma ser o dia mais difícil (${pct(worst.second)}).")
            }
        }
        val topHabit = habitReports.filter { it.dueDays > 0 }.maxByOrNull { it.streak }
        if (topHabit != null && topHabit.streak > 0) {
            add("Maior sequência: ${topHabit.habit.name} (${topHabit.streak} dias seguidos).")
        }
        if (perfectDays > 0) {
            add(
                if (perfectDays == 1) "1 dia perfeito no período."
                else "$perfectDays dias perfeitos no período."
            )
        }
        val low = habitReports.filter { it.dueDays > 0 }.minByOrNull { it.rate }
        if (low != null && habitReports.size >= 2) {
            add("${low.habit.name} teve a menor taxa (${pct(low.rate)}) — considere ajustar a meta.")
        }
        val mp = perfectMoods.average().takeIf { perfectMoods.isNotEmpty() }
        val mq = pendingMoods.average().takeIf { pendingMoods.isNotEmpty() }
        if (mp != null && mq != null && mp > mq) {
            add("Seu humor médio sobe para ${fmt1(mp)} nos dias em que você completa tudo.")
        }
        if (points.size >= 8) {
            val half = points.size / 2
            val delta = (rateOf(points.drop(half)) - rateOf(points.take(half))) * 100
            if (abs(delta) >= 3) {
                add(
                    "Sua conclusão ${if (delta > 0) "subiu" else "caiu"} " +
                        "${fmt1(abs(delta))} p.p. na segunda metade do período."
                )
            }
        }
    }.take(5)

    return ReportData(
        period = period,
        from = from,
        to = today,
        days = points,
        completionRate = rateOf(points),
        totalCheckins = checkins.size,
        perfectDays = perfectDays,
        activeDays = points.count { it.checkins > 0 },
        maxStreak = habitReports.maxOfOrNull { it.streak } ?: 0,
        habits = habitReports,
        weekdayRates = weekdayRates,
        moodPerfectAvg = perfectMoods.average().takeIf { perfectMoods.isNotEmpty() },
        moodPendingAvg = pendingMoods.average().takeIf { pendingMoods.isNotEmpty() },
        insights = insights,
    )
}

private fun goalDescription(h: Habit): String = when {
    h.goalType == GoalType.QUANTITY ->
        "Meta de ${fmtQty(h.targetQuantity)} ${h.quantityUnit}".trim()
    h.frequencyType == FrequencyType.WEEKLY_X -> "${h.weeklyTarget}× por semana"
    h.frequencyType == FrequencyType.DAYS_OF_WEEK && h.daysOfWeek.isNotEmpty() ->
        h.daysOfWeek.sorted().joinToString(", ") { DOW_SHORT[it - 1] }
    else -> "Diário"
}

private fun buildSummary(data: ReportData): String {
    val sb = StringBuilder()
    sb.append("Constância — Relatório (${data.period.label.lowercase()})\n")
    sb.append("${formatDay(data.from)} a ${formatDay(data.to)}\n\n")
    sb.append("Taxa de conclusão: ${pct(data.completionRate)}\n")
    sb.append("Sequência atual: ${data.maxStreak} dias\n")
    sb.append("Dias perfeitos: ${data.perfectDays}\n")
    sb.append("Check-ins: ${data.totalCheckins}\n")
    if (data.moodPerfectAvg != null) {
        sb.append("Humor médio (dias 100%): ${fmt1(data.moodPerfectAvg)}\n")
    }
    if (data.moodPendingAvg != null) {
        sb.append("Humor médio (dias com pendências): ${fmt1(data.moodPendingAvg)}\n")
    }
    sb.append("\nHábitos:\n")
    data.habits.forEach { hr ->
        sb.append("• ${hr.habit.name} — ${pct(hr.rate)}")
        if (hr.streak > 0) sb.append(" · ${hr.streak} dias seguidos")
        sb.append("\n")
    }
    return sb.toString()
}

// ---------------------------------------------------------------------------
// Tela
// ---------------------------------------------------------------------------

@Composable
fun ReportsScreen(deps: AppDeps, modifier: Modifier = Modifier) {
    val timeZone = TimeZone.currentSystemDefault()
    var period by remember { mutableStateOf(ReportPeriod.MES) }
    var chartMode by remember { mutableStateOf(ChartMode.CONCLUSAO) }
    var showSummary by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val diaryEntries by deps.diary.recentFlow(365).collectAsState(initial = emptyList())
    val today = remember { Clock.System.todayIn(timeZone) }
    val fromYmd = remember(period, today) {
        today.minus(DatePeriod(days = period.days - 1)).toYmd()
    }
    val toYmd = remember(today) { today.toYmd() }
    val checkins by remember(period) {
        deps.checkins.checkinsInRangeFlow(fromYmd, toYmd)
    }.collectAsState(initial = emptyList())

    val data = remember(period, habits, checkins, diaryEntries) {
        computeReport(habits, checkins, diaryEntries, period, timeZone)
    }
    val summaryText = remember(data) { buildSummary(data) }
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Relatórios",
            style = MaterialTheme.typography.headlineSmall,
            color = cs.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Constância · sua evolução em números",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        PeriodSegmented(period = period, onSelect = { period = it })
        Spacer(Modifier.height(8.dp))
        Text(
            periodLabel(period) + " · ${formatDay(data.from)} a ${formatDay(data.to)}",
            style = MaterialTheme.typography.bodySmall,
            color = extra.ink3,
        )
        Spacer(Modifier.height(12.dp))

        if (checkins.isEmpty()) {
            EmptyState()
        } else {
            KpiGrid(data)
            Spacer(Modifier.height(12.dp))

            EvolutionSection(
                data = data,
                mode = chartMode,
                onModeChange = { chartMode = it },
            )
            Spacer(Modifier.height(12.dp))

            HeatmapSection(data)
            Spacer(Modifier.height(12.dp))

            HabitPerformanceSection(data.habits)
            Spacer(Modifier.height(12.dp))

            WeekdayPatternSection(data)
            Spacer(Modifier.height(12.dp))

            MoodSection(data)
            Spacer(Modifier.height(12.dp))

            InsightsSection(data.insights)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryReportButton(
                    onClick = {
                        scope.launch {
                            val csv = buildExportCsv(habits, checkins)
                            shareTextFile("constancia-checkins.csv", csv, "text/csv")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Download,
                    text = "Exportar CSV",
                )
                SecondaryReportButton(
                    onClick = { showSummary = true },
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.ContentCopy,
                    text = "Copiar resumo",
                )
            }
            Spacer(Modifier.height(18.dp))
        }
    }

    if (showSummary) {
        AlertDialog(
            onDismissRequest = { showSummary = false },
            containerColor = extra.pageCard,
            titleContentColor = cs.onBackground,
            textContentColor = cs.onBackground,
            title = {
                Text("Resumo do período", style = MaterialTheme.typography.titleMedium)
            },
            text = {
                SelectionContainer {
                    Text(summaryText, fontSize = 13.sp)
                }
            },
            confirmButton = {
                PrimaryReportButton(
                    onClick = { showSummary = false },
                    icon = Icons.Filled.Close,
                    text = "Fechar",
                )
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------

private fun periodLabel(p: ReportPeriod): String = when (p) {
    ReportPeriod.SEMANA -> "Últimos 7 dias"
    ReportPeriod.MES -> "Últimos 30 dias"
    ReportPeriod.ANO -> "Últimos 12 meses"
}

@Composable
private fun EmptyState() {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Insights,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = extra.ink3.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Sem dados ainda",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Complete hábitos para ver relatórios",
            style = MaterialTheme.typography.bodyMedium,
            color = extra.ink2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PeriodSegmented(period: ReportPeriod, onSelect: (ReportPeriod) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(extra.pageChip)
            .padding(4.dp),
    ) {
        ReportPeriod.values().forEach { p ->
            val selected = p == period
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (selected) Modifier.shadow(2.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .background(if (selected) extra.pageCard else Color.Transparent)
                    .clickable { onSelect(p) }
                    .padding(vertical = 9.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) cs.onSurface else extra.ink2,
                )
            }
        }
    }
}

@Composable
private fun CardTitle(title: String, hint: String?) {
    val extra = MaterialTheme.constanciaExtra
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    if (hint != null) {
        Spacer(Modifier.height(2.dp))
        Text(
            hint,
            style = MaterialTheme.typography.bodySmall,
            color = extra.ink3,
        )
    }
}

@Composable
private fun CardBox(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val extra = MaterialTheme.constanciaExtra
    Column(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(extra.pageCard)
            .border(1.dp, extra.pageLine, RoundedCornerShape(corner))
            .padding(contentPadding),
        content = content,
    )
}

@Composable
private fun KpiGrid(data: ReportData) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(Modifier.weight(1f), pct(data.completionRate), "taxa de conclusão")
            KpiCard(Modifier.weight(1f), "${data.maxStreak}", "dias de sequência")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(Modifier.weight(1f), "${data.perfectDays}", "dias perfeitos")
            KpiCard(Modifier.weight(1f), "${data.totalCheckins}", "check-ins no período")
        }
    }
}

@Composable
private fun KpiCard(modifier: Modifier = Modifier, value: String, label: String) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(extra.pageCard)
            .border(1.dp, extra.pageLine, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.8.sp),
            color = extra.ink3,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 29.sp),
            color = cs.onBackground,
        )
    }
}

@Composable
private fun EvolutionSection(
    data: ReportData,
    mode: ChartMode,
    onModeChange: (ChartMode) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val primary = cs.primary
    val avgColor = extra.amber
    CardBox(Modifier.fillMaxWidth()) {
        CardTitle("Evolução", null)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChartMode.values().forEach { m ->
                ReportChip(
                    selected = mode == m,
                    label = m.label,
                    onClick = { onModeChange(m) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val values = data.days.map { p ->
                if (mode == ChartMode.CONCLUSAO) p.rate * 100.0 else p.checkins.toDouble()
            }
            val n = values.size
            if (n == 0) return@Canvas
            val maxV = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtLeast(1.5f)
            val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (v / maxV * size.height * 0.9).toFloat()
                if (h > 0.5f) {
                    val x = i * slot + (slot - barW) / 2f
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barW, h),
                        cornerRadius = radius,
                    )
                }
            }
            val avg = values.average()
            val ay = size.height - (avg / maxV * size.height * 0.9).toFloat()
            drawLine(
                color = avgColor,
                start = Offset(0f, ay),
                end = Offset(size.width, ay),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "— média do período",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = extra.amber,
        )
    }
}

@Composable
private fun ReportChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val shape = CircleShape
    Box(
        Modifier
            .clip(shape)
            .border(1.5.dp, if (selected) cs.primary else extra.pageLine, shape)
            .background(if (selected) cs.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeatmapSection(data: ReportData) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    CardBox(
        Modifier.fillMaxWidth(),
        corner = 24.dp,
        contentPadding = PaddingValues(top = 18.dp, bottom = 14.dp, start = 16.dp, end = 16.dp),
    ) {
        CardTitle(
            "Mapa de consistência",
            "Cada quadrado é um dia · ${data.period.label.lowercase()}",
        )
        Spacer(Modifier.height(12.dp))
        val heatScroll = rememberScrollState()
        LaunchedEffect(data.period) { heatScroll.scrollTo(heatScroll.maxValue) }
        Row(
            Modifier.horizontalScroll(heatScroll),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val points = data.days
            if (points.isEmpty()) {
                Text(
                    "Sem dias no período.",
                    fontSize = 13.sp,
                    color = extra.ink3,
                )
            } else {
                val firstAppDay = isoToAppDay(points.first().date.isoDayNumberCompat())
                val totalCells = (firstAppDay - 1) + points.size
                val cols = (totalCells + 6) / 7
                repeat(cols) { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(7) { r ->
                            val dayIdx = c * 7 + r - (firstAppDay - 1)
                            if (dayIdx in points.indices) {
                                val p = points[dayIdx]
                                val cellColor = when {
                                    p.due == 0 -> extra.heat0
                                    p.done == 0 -> cs.primary.copy(alpha = 0.12f)
                                    else -> cs.primary.copy(alpha = 0.12f + 0.88f * p.rate.toFloat())
                                }
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(cellColor)
                                )
                            } else {
                                Spacer(Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Menos", fontSize = 11.sp, color = extra.ink3)
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(extra.heat0)
            )
            listOf(0.3f, 0.55f, 0.8f, 1f).forEach { a ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(cs.primary.copy(alpha = a))
                )
            }
            Text("Mais", fontSize = 11.sp, color = extra.ink3)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(extra.pageLine)
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            HeatMeta(
                Modifier.weight(1f),
                "${data.activeDays} de ${data.days.size}",
                "dias ativos no período",
            )
            HeatMeta(Modifier.weight(1f), "${data.perfectDays}", "dias perfeitos")
            HeatMeta(Modifier.weight(1f), "${data.maxStreak}", "recorde de sequência")
        }
    }
}

@Composable
private fun HeatMeta(modifier: Modifier, value: String, label: String) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 17.sp),
            color = cs.onBackground,
        )
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = extra.ink3,
        )
    }
}

@Composable
private fun HabitPerformanceSection(habits: List<HabitReport>) {
    val extra = MaterialTheme.constanciaExtra
    CardBox(Modifier.fillMaxWidth()) {
        CardTitle("Desempenho por hábito", "Toque em um hábito para ver detalhes")
        Spacer(Modifier.height(6.dp))
        if (habits.isEmpty()) {
            Text(
                "Nenhum hábito no período.",
                fontSize = 13.sp,
                color = extra.ink3,
            )
        } else {
            habits.forEachIndexed { i, hr ->
                if (i > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(extra.pageLine)
                    )
                }
                HabitRow(hr)
            }
        }
    }
}

@Composable
private fun HabitRow(hr: HabitReport) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val habitColor = Color(hr.habit.color)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(habitColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    hr.habit.name.firstOrNull()?.uppercase() ?: "•",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = habitColor,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    hr.habit.name,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp,
                    color = cs.onBackground,
                )
                Text(
                    goalDescription(hr.habit),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = extra.ink3,
                )
            }
            Text(
                pct(hr.rate),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp),
                color = cs.onBackground,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = extra.ink3,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(extra.pageChip)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(hr.rate.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(99.dp))
                    .background(habitColor)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(extra.pageChip)
                    .padding(12.dp),
            ) {
                HabitDetailLine("Sequência atual", "${hr.streak} dias")
                HabitDetailLine("Dias concluídos", "${hr.doneDays} de ${hr.dueDays}")
                HabitDetailLine("Check-ins", "${hr.checkinCount}")
                if (hr.habit.goalType == GoalType.QUANTITY) {
                    val unit = hr.habit.quantityUnit.trim()
                    val total = fmtQty(hr.totalQuantity) + if (unit.isNotEmpty()) " $unit" else ""
                    HabitDetailLine("Total no período", total)
                }
                if (hr.habit.frequencyType == FrequencyType.WEEKLY_X) {
                    HabitDetailLine("Meta semanal", "${hr.habit.weeklyTarget}× por semana")
                }
            }
        }
    }
}

@Composable
private fun HabitDetailLine(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = extra.ink3,
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
        )
    }
}

@Composable
private fun WeekdayPatternSection(data: ReportData) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    CardBox(Modifier.fillMaxWidth()) {
        CardTitle("Padrão da semana", "Taxa de conclusão por dia")
        Spacer(Modifier.height(14.dp))
        val bestIdx = data.weekdayRates
            .mapIndexedNotNull { i, r -> if (r >= 0) i to r else null }
            .maxByOrNull { it.second }?.first
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            data.weekdayRates.forEachIndexed { i, r ->
                val isBest = i == bestIdx
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (r < 0) "–" else pct(r),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = extra.ink3,
                    )
                    Spacer(Modifier.height(7.dp))
                    Box(
                        Modifier.height(110.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val frac = if (r < 0) 0f else r.toFloat().coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .width(30.dp)
                                .fillMaxHeight(if (frac <= 0f) 0.02f else frac)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isBest) cs.primary
                                    else cs.primary.copy(alpha = 0.35f)
                                )
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        DOW_SHORT[i],
                        fontSize = 11.sp,
                        fontWeight = if (isBest) FontWeight.Bold else FontWeight.Normal,
                        color = if (isBest) extra.brandStrong else extra.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodSection(data: ReportData) {
    val extra = MaterialTheme.constanciaExtra
    CardBox(Modifier.fillMaxWidth()) {
        CardTitle("Diário & humor", null)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoodBox(
                modifier = Modifier.weight(1f),
                value = data.moodPerfectAvg?.let(::fmt1) ?: "–",
                label = "humor médio nos dias em que você completou tudo",
            )
            MoodBox(
                modifier = Modifier.weight(1f),
                value = data.moodPendingAvg?.let(::fmt1) ?: "–",
                label = "humor médio nos dias com hábitos pendentes",
            )
        }
        if (data.moodPerfectAvg == null && data.moodPendingAvg == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Registre seu humor no diário para ver a correlação.",
                fontSize = 12.sp,
                color = extra.ink3,
            )
        }
    }
}

@Composable
private fun MoodBox(modifier: Modifier = Modifier, value: String, label: String) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(extra.pageChip)
            .padding(13.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
            color = cs.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = extra.ink2,
        )
    }
}

@Composable
private fun InsightsSection(insights: List<String>) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    CardBox(Modifier.fillMaxWidth()) {
        CardTitle("Insights", "Padrões detectados nos seus dados")
        Spacer(Modifier.height(4.dp))
        if (insights.isEmpty()) {
            Text(
                "Ainda não há padrões suficientes — continue registrando.",
                fontSize = 13.sp,
                color = extra.ink3,
            )
        } else {
            insights.forEachIndexed { i, text ->
                if (i > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(cs.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = extra.ink2,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryReportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(15.dp))
            .background(cs.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = cs.onPrimary,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                ),
                color = cs.onPrimary,
            )
        }
    }
}

@Composable
private fun SecondaryReportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    text: String,
) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.5.dp, extra.pageLine, shape)
            .background(extra.pageCard)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                ),
                color = cs.onSurface,
            )
        }
    }
}
