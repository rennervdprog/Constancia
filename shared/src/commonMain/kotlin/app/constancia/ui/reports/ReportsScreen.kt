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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import app.constancia.model.isoToAppDay
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
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
    d.minus(DatePeriod(days = d.dayOfWeek.isoDayNumber - 1))

private fun isDueOn(habit: Habit, date: LocalDate, activeFrom: LocalDate?): Boolean {
    if (habit.paused) return false
    if (activeFrom != null && date < activeFrom) return false
    return when (habit.frequencyType) {
        FrequencyType.DAILY -> true
        FrequencyType.WEEKLY_X -> true
        FrequencyType.DAYS_OF_WEEK ->
            habit.daysOfWeek.isEmpty() ||
                isoToAppDay(date.dayOfWeek.isoDayNumber) in habit.daysOfWeek
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
        val pts = points.filter { isoToAppDay(it.date.dayOfWeek.isoDayNumber) == appDay }
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

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Relatórios",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "Constância · sua evolução em números",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPeriod.values().forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { period = p },
                    label = { Text(p.label) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        if (checkins.isEmpty()) {
            EmptyState()
        } else {
            KpiGrid(data)
            Spacer(Modifier.height(24.dp))

            EvolutionSection(
                data = data,
                mode = chartMode,
                onModeChange = { chartMode = it },
            )
            Spacer(Modifier.height(24.dp))

            HeatmapSection(data)
            Spacer(Modifier.height(24.dp))

            HabitPerformanceSection(data.habits)
            Spacer(Modifier.height(24.dp))

            WeekdayPatternSection(data)
            Spacer(Modifier.height(24.dp))

            MoodSection(data)
            Spacer(Modifier.height(24.dp))

            InsightsSection(data.insights)
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val csv = buildExportCsv(habits, checkins)
                            shareTextFile("constancia-checkins.csv", csv, "text/csv")
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Exportar CSV")
                }
                OutlinedButton(
                    onClick = { showSummary = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Copiar resumo")
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showSummary) {
        AlertDialog(
            onDismissRequest = { showSummary = false },
            title = { Text("Resumo do período") },
            text = {
                SelectionContainer {
                    Text(summaryText, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showSummary = false }) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Fechar")
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Componentes
// ---------------------------------------------------------------------------

@Composable
private fun EmptyState() {
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
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Sem dados ainda",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Complete hábitos para ver relatórios",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, hint: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
    if (hint != null) {
        Spacer(Modifier.height(2.dp))
        Text(
            hint,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun KpiGrid(data: ReportData) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.TrendingUp,
                value = pct(data.completionRate),
                label = "taxa de conclusão",
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CheckCircle,
                value = "${data.maxStreak}",
                label = "dias de sequência",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CalendarMonth,
                value = "${data.perfectDays}",
                label = "dias perfeitos",
            )
            KpiCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.BarChart,
                value = "${data.totalCheckins}",
                label = "check-ins no período",
            )
        }
    }
}

@Composable
private fun KpiCard(modifier: Modifier = Modifier, icon: ImageVector, value: String, label: String) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EvolutionSection(
    data: ReportData,
    mode: ChartMode,
    onModeChange: (ChartMode) -> Unit,
) {
    SectionHeader(Icons.Filled.BarChart, "Evolução", null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartMode.values().forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onModeChange(m) },
                label = { Text(m.label) },
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    CardBox(Modifier.fillMaxWidth()) {
        val primary = MaterialTheme.colorScheme.primary
        val avgColor = MaterialTheme.colorScheme.onSurfaceVariant
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeatmapSection(data: ReportData) {
    SectionHeader(
        Icons.Filled.DateRange,
        "Mapa de consistência",
        "Cada quadrado é um dia · ${data.period.label.lowercase()}",
    )
    val heatScroll = rememberScrollState()
    LaunchedEffect(data.period) { heatScroll.scrollTo(heatScroll.maxValue) }
    CardBox(Modifier.fillMaxWidth()) {
        val cs = MaterialTheme.colorScheme
        Row(
            Modifier.horizontalScroll(heatScroll),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val points = data.days
            if (points.isEmpty()) {
                Text(
                    "Sem dias no período.",
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            } else {
                val firstAppDay = isoToAppDay(points.first().date.dayOfWeek.isoDayNumber)
                val totalCells = (firstAppDay - 1) + points.size
                val cols = (totalCells + 6) / 7
                repeat(cols) { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(7) { r ->
                            val dayIdx = c * 7 + r - (firstAppDay - 1)
                            if (dayIdx in points.indices) {
                                val p = points[dayIdx]
                                val cellColor = when {
                                    p.due == 0 -> cs.surfaceVariant
                                    p.done == 0 -> cs.primary.copy(alpha = 0.12f)
                                    else -> cs.primary.copy(alpha = 0.12f + 0.88f * p.rate.toFloat())
                                }
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(5.dp))
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
            Text("Menos", fontSize = 11.sp, color = cs.onSurfaceVariant)
            listOf(0.12f, 0.35f, 0.6f, 0.85f, 1f).forEach { a ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(cs.primary.copy(alpha = a))
                )
            }
            Text("Mais", fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun HabitPerformanceSection(habits: List<HabitReport>) {
    SectionHeader(
        Icons.Filled.TrendingUp,
        "Desempenho por hábito",
        "Toque em um hábito para ver detalhes",
    )
    if (habits.isEmpty()) {
        Text(
            "Nenhum hábito no período.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    habits.forEach { hr ->
        HabitRow(hr)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HabitRow(hr: HabitReport) {
    var expanded by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(hr.habit.color).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    hr.habit.name.firstOrNull()?.uppercase() ?: "•",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(hr.habit.color),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    hr.habit.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = cs.onBackground,
                )
                Text(
                    goalDescription(hr.habit),
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            Text(
                pct(hr.rate),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = cs.primary,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 90f else 0f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cs.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(hr.rate.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(cs.primary)
            )
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
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

@Composable
private fun HabitDetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun WeekdayPatternSection(data: ReportData) {
    SectionHeader(
        Icons.Filled.CalendarMonth,
        "Padrão da semana",
        "Taxa de conclusão por dia",
    )
    CardBox(Modifier.fillMaxWidth()) {
        val cs = MaterialTheme.colorScheme
        val bestIdx = data.weekdayRates
            .mapIndexedNotNull { i, r -> if (r >= 0) i to r else null }
            .maxByOrNull { it.second }?.first
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            data.weekdayRates.forEachIndexed { i, r ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                    if (i == bestIdx) cs.primary
                                    else cs.primary.copy(alpha = 0.35f)
                                )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        DOW_SHORT[i],
                        fontSize = 11.sp,
                        fontWeight = if (i == bestIdx) FontWeight.Bold else FontWeight.Normal,
                        color = cs.onBackground,
                    )
                    Text(
                        if (r < 0) "–" else pct(r),
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodSection(data: ReportData) {
    SectionHeader(Icons.Filled.EmojiEmotions, "Diário & humor", null)
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MoodCard(
            modifier = Modifier.weight(1f),
            value = data.moodPerfectAvg?.let(::fmt1) ?: "–",
            label = "humor médio nos dias em que você completou tudo",
        )
        MoodCard(
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
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun MoodCard(modifier: Modifier = Modifier, value: String, label: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .border(1.dp, cs.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Icon(
            Icons.Filled.EmojiEmotions,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = cs.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun InsightsSection(insights: List<String>) {
    SectionHeader(
        Icons.Filled.Lightbulb,
        "Insights",
        "Padrões detectados nos seus dados",
    )
    val cs = MaterialTheme.colorScheme
    if (insights.isEmpty()) {
        Text(
            "Ainda não há padrões suficientes — continue registrando.",
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
        )
        return
    }
    CardBox(Modifier.fillMaxWidth()) {
        insights.forEachIndexed { i, text ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text,
                    fontSize = 14.sp,
                    color = cs.onBackground,
                    modifier = Modifier.weight(1f),
                )
            }
            if (i < insights.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}
