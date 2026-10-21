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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
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
import app.constancia.domain.DOW_FULL
import app.constancia.domain.DOW_SHORT
import app.constancia.domain.MONTHS
import app.constancia.domain.DayPoint
import app.constancia.domain.HabitReport
import app.constancia.domain.Highlights
import app.constancia.domain.MONTHS_PT
import app.constancia.domain.MorningHabitPattern
import app.constancia.domain.OverdueXray
import app.constancia.domain.PeriodReview
import app.constancia.domain.PeriodSummary
import app.constancia.domain.ReportData
import app.constancia.domain.ReportPeriod
import app.constancia.domain.SleepLagPattern
import app.constancia.domain.WEEKDAYS_ISO_PT
import app.constancia.domain.buildExportCsv
import app.constancia.domain.buildSummary
import app.constancia.domain.computeReport
import app.constancia.domain.fmt1
import app.constancia.domain.fmtDeltaPp
import app.constancia.domain.fmtDeltaPct
import app.constancia.domain.fmtQty
import app.constancia.domain.fmtSleep
import app.constancia.domain.forgivenDates
import app.constancia.domain.formatDay
import app.constancia.domain.goalDescription
import app.constancia.domain.morningHabitPattern
import app.constancia.domain.overdueXray
import app.constancia.domain.pct
import app.constancia.domain.periodHighlights
import app.constancia.domain.periodReview
import app.constancia.domain.periodSummary
import app.constancia.domain.quarterWindow
import app.constancia.domain.rateOf
import app.constancia.domain.sleepLagPattern
import app.constancia.domain.yearWindow
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
import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.isoToAppDay
import app.constancia.theme.constanciaExtra
import app.constancia.ui.common.habitIcon
import app.constancia.ui.common.moodColor
import app.constancia.ui.tasks.priorityColor
import kotlin.math.roundToInt
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
// Modelos e cálculos em domain/Reports.kt (lógica pura, testável)
// ---------------------------------------------------------------------------

private enum class ChartMode(val label: String) {
    CONCLUSAO("Conclusão"),
    CHECKINS("Check-ins"),
}

// Tela
// ---------------------------------------------------------------------------

@Composable
fun ReportsScreen(
    deps: AppDeps,
    modifier: Modifier = Modifier,
    onOpenPaywall: () -> Unit = {},
) {
    val timeZone = TimeZone.currentSystemDefault()
    var period by remember { mutableStateOf(ReportPeriod.MES) }
    var chartMode by remember { mutableStateOf(ChartMode.CONCLUSAO) }
    var showSummary by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val habits by deps.habits.habitsFlow().collectAsState(initial = emptyList())
    val diaryEntries by deps.diary.allEntriesFlow().collectAsState(initial = emptyList())
    val allTasks by deps.tasks.allTasksFlow().collectAsState(initial = emptyList())
    val isPro by deps.prefs.isProFlow().collectAsState(initial = false)
    val today = remember { Clock.System.todayIn(timeZone) }
    // Janela dobrada: período atual + anterior (deltas) no mesmo fluxo.
    val fromYmd = remember(period, today) {
        today.minus(DatePeriod(days = period.days * 2 - 1)).toYmd()
    }
    val toYmd = remember(today) { today.toYmd() }
    val checkins by remember(period) {
        deps.checkins.checkinsInRangeFlow(fromYmd, toYmd)
    }.collectAsState(initial = emptyList())
    // Dias perdoados por pausas (streak congelado) — recarregados quando os hábitos mudam.
    var forgivenByHabit by remember { mutableStateOf(emptyMap<String, Set<String>>()) }
    LaunchedEffect(habits) {
        forgivenByHabit = if (habits.isEmpty()) emptyMap()
        else deps.habits.allPauseIntervals().mapValues { (_, ivs) -> forgivenDates(ivs, toYmd) }
    }

    val curFrom = remember(period, today) {
        today.minus(DatePeriod(days = period.days - 1))
    }
    val curCheckins = remember(checkins, curFrom) {
        checkins.filter { it.date >= curFrom.toYmd() }
    }
    val prevCheckins = remember(checkins, curFrom) {
        checkins.filter { it.date < curFrom.toYmd() }
    }
    val data = remember(period, habits, curCheckins, diaryEntries, forgivenByHabit) {
        computeReport(habits, curCheckins, diaryEntries, period, timeZone, forgivenByHabit)
    }
    val prevData = remember(period, habits, prevCheckins, diaryEntries, forgivenByHabit) {
        computeReport(
            habits, prevCheckins, diaryEntries, period, timeZone, forgivenByHabit,
            todayOverride = today.minus(DatePeriod(days = period.days)),
        )
    }
    val summary = remember(allTasks, diaryEntries, data, prevData) {
        periodSummary(allTasks, diaryEntries, data, prevData)
    }
    val review = remember(allTasks, data, prevData) {
        periodReview(allTasks, data, prevData)
    }
    val sleepPattern = remember(diaryEntries, allTasks) {
        sleepLagPattern(diaryEntries, allTasks, today)
    }
    // Padrões e destaques não dependem do período selecionado: usam uma
    // janela larga própria (400 dias, como a aba Hábitos).
    val wideFrom = remember(today) { today.minus(DatePeriod(days = 400)).toYmd() }
    val wideCheckins by remember(wideFrom) {
        deps.checkins.checkinsInRangeFlow(wideFrom, toYmd)
    }.collectAsState(initial = emptyList())
    val morningPattern = remember(diaryEntries, habits, wideCheckins, allTasks) {
        morningHabitPattern(diaryEntries, habits, wideCheckins, allTasks, today)
    }
    val xray = remember(allTasks) { overdueXray(allTasks, today) }
    val yearHl = remember(habits, wideCheckins, allTasks, diaryEntries, data) {
        val (yFrom, yTo) = yearWindow(today)
        periodHighlights(habits, wideCheckins, allTasks, diaryEntries, yFrom, yTo, data)
    }
    val quarterHl = remember(habits, wideCheckins, allTasks, diaryEntries, data) {
        val (qFrom, qTo) = quarterWindow(today)
        periodHighlights(habits, wideCheckins, allTasks, diaryEntries, qFrom, qTo, data)
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
            "tarefas × hábitos × diário, cruzados",
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

        if (checkins.isEmpty() && allTasks.isEmpty() && diaryEntries.isEmpty()) {
            EmptyState()
        } else {
            KpiGrid(summary = summary, data = data, period = period)
            Spacer(Modifier.height(12.dp))

            PatternsSection(
                isPro = isPro,
                morning = morningPattern,
                sleep = sleepPattern,
                onOpenPaywall = onOpenPaywall,
            )
            Spacer(Modifier.height(12.dp))

            ReviewSection(review = review, period = period)
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

            XraySection(xray = xray, isPro = isPro, onOpenPaywall = onOpenPaywall)
            Spacer(Modifier.height(12.dp))

            YearReviewSection(
                year = yearHl,
                quarter = quarterHl,
                isPro = isPro,
                today = today,
            )
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
private fun KpiGrid(summary: PeriodSummary, data: ReportData, period: ReportPeriod) {
    val periodWord = when (period) {
        ReportPeriod.SEMANA -> "na semana"
        ReportPeriod.MES -> "no mês"
        ReportPeriod.ANO -> "no ano"
    }
    val conclusionLabel = buildString {
        append("de conclusão")
        summary.combinedDelta?.let { append(" · " + fmtDeltaPp(it) + " vs. anterior") }
    }
    val moodLabel = buildString {
        append("humor médio")
        summary.sleepAvg?.let { append(" · sono médio " + fmtSleep(it)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                Modifier.weight(1f),
                "${summary.tasksDone}",
                "tarefas concluídas $periodWord",
            )
            KpiCard(Modifier.weight(1f), pct(summary.combinedRate), conclusionLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(Modifier.weight(1f), "${data.totalCheckins}", "check-ins de hábitos")
            KpiCard(
                Modifier.weight(1f),
                summary.moodAvg?.let(::fmt1) ?: "–",
                moodLabel,
            )
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
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 29.sp),
            color = cs.onBackground,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = extra.ink3,
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
                // Ícone de verdade do hábito (igual ao card da home),
                // não a inicial do nome.
                Icon(
                    habitIcon(hr.habit.icon),
                    contentDescription = null,
                    tint = habitColor,
                    modifier = Modifier.size(20.dp),
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

// ---------------------------------------------------------------------------
// Relatórios detalhados (plano aprovado): padrões cruzados, revisão,
// raio-X de atrasos e Ano em Revista.
// Divisão Free × Pro: KPIs, gráfico básico, heatmap e a revisão-resumo são
// free; cruzamentos com defasagem, raio-X detalhado e a revista do
// trimestre são Pro.
// ---------------------------------------------------------------------------

@Composable
private fun ProLockCard(title: String, teaser: String, onOpenPaywall: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    CardBox(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    teaser,
                    style = MaterialTheme.typography.bodySmall,
                    color = extra.ink3,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryReportButton(
            onClick = onOpenPaywall,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Lock,
            text = "Disponível no Constância Pro",
        )
    }
}

@Composable
private fun PatternStat(value: String, label: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp),
            color = cs.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = extra.ink3,
        )
    }
}

@Composable
private fun PatternsSection(
    isPro: Boolean,
    morning: MorningHabitPattern?,
    sleep: SleepLagPattern?,
    onOpenPaywall: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    if (!isPro) {
        ProLockCard(
            title = "Padrões cruzados",
            teaser = "Como sono, hábitos da manhã e carga de tarefas se associam ao " +
                "seu humor, à sua energia e aos seus atrasos — com a amostra " +
                "sempre visível. Só no Constância.",
            onOpenPaywall = onOpenPaywall,
        )
        return
    }
    CardBox(Modifier.fillMaxWidth(), corner = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Padrões cruzados",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onBackground,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(cs.primaryContainer)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    "só no Constância",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = cs.primary,
                )
            }
        }
        if (morning == null && sleep == null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Registre humor, energia e sono por alguns dias: o cruzamento " +
                    "só aparece quando a amostra sustenta — sem palpite.",
                fontSize = 13.sp,
                color = extra.ink3,
            )
        }
        if (morning != null) {
            Spacer(Modifier.height(14.dp))
            MorningPatternBlock(morning)
        }
        if (sleep != null) {
            if (morning != null) {
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(1.dp).background(extra.pageLine)
                )
                Spacer(Modifier.height(14.dp))
            }
            SleepPatternBlock(sleep)
        }
    }
}

@Composable
private fun MorningPatternBlock(p: MorningHabitPattern) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val habitName = p.habit.name
    val lower = habitName.lowercase()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Insights,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "Hábitos × humor",
            style = MaterialTheme.typography.labelLarge,
            color = cs.primary,
        )
    }
    Spacer(Modifier.height(8.dp))
    val diff = p.moodWith - p.moodWithout
    Text(
        when {
            diff > 0.3 -> "$habitName de manhã muda o seu dia"
            diff < -0.3 -> "Seu humor cai nos dias de $lower de manhã"
            else -> "$habitName × humor, lado a lado"
        },
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
        color = cs.onBackground,
    )
    Spacer(Modifier.height(6.dp))
    val body = buildString {
        append("Nos dias com $lower de manhã, seu humor fica em ")
        append(fmt1(p.moodWith) + " contra " + fmt1(p.moodWithout) + " nos dias sem.")
        val eW = p.energyWith
        val eWo = p.energyWithout
        if (eW != null && eWo != null) {
            append(" Energia: " + fmt1(eW) + " × " + fmt1(eWo) + ".")
        }
        p.taskRateDelta?.let { d ->
            val label = fmtDeltaPct(
                p.taskRateWith ?: 0.0,
                p.taskRateWithout ?: 0.0,
            )
            if (label != null) {
                append(" E a conclusão de tarefas fica $label nesses dias.")
            }
        }
    }
    Text(body, fontSize = 13.sp, color = extra.ink2)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PatternStat(fmt1(p.moodWith), "humor · com $lower cedo", Modifier.weight(1f))
        PatternStat(fmt1(p.moodWithout), "humor · sem $lower", Modifier.weight(1f))
        val deltaLabel = fmtDeltaPct(p.taskRateWith ?: 0.0, p.taskRateWithout ?: 0.0)
        if (deltaLabel != null) {
            PatternStat(deltaLabel, "tarefas concluídas", Modifier.weight(1f))
        } else if (p.energyWith != null) {
            PatternStat(fmt1(p.energyWith ?: 0.0), "energia · com $lower", Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Base: ${p.daysWith + p.daysWithout} dias com humor registrado " +
            "(${p.daysWith} com, ${p.daysWithout} sem). Associação observada — " +
            "não é causa e efeito.",
        fontSize = 11.sp,
        color = extra.ink3,
    )
}

@Composable
private fun SleepPatternBlock(p: SleepLagPattern) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.NightsStay,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "Sono × dia seguinte",
            style = MaterialTheme.typography.labelLarge,
            color = cs.primary,
        )
    }
    Spacer(Modifier.height(8.dp))
    val moodShort = p.moodAfterShort
    val moodEnough = p.moodAfterEnough
    val goodDay = (p.overdueRatio ?: 1.0) >= 1.3 ||
        (moodShort != null && moodEnough != null && moodEnough - moodShort > 0.3)
    Text(
        if (goodDay) "Dormir cedo rende um amanhã melhor"
        else "Suas noites e seus dias, lado a lado",
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
        color = cs.onBackground,
    )
    Spacer(Modifier.height(6.dp))
    val body = buildString {
        p.overdueRatio?.let { r ->
            append("Em noites com menos de 6h30 de sono, você atrasa ")
            append(fmt1(r) + "× mais tarefas no dia seguinte. ")
        }
        if (moodShort != null && moodEnough != null) {
            append("Humor médio: " + fmt1(moodShort) + " após noite curta × ")
            append(fmt1(moodEnough) + " após noite boa.")
        }
    }
    Text(body, fontSize = 13.sp, color = extra.ink2)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (moodEnough != null) {
            PatternStat(fmt1(moodEnough), "humor · noite boa", Modifier.weight(1f))
        }
        if (moodShort != null) {
            PatternStat(fmt1(moodShort), "humor · noite curta", Modifier.weight(1f))
        }
        PatternStat(
            pct(p.overdueRateAfterShort),
            "atrasos · após noite curta",
            Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "Base: ${p.shortNights} noites curtas × ${p.enoughNights} noites boas. " +
            "Associação observada — não é causa e efeito.",
        fontSize = 11.sp,
        color = extra.ink3,
    )
}

@Composable
private fun ReviewSection(review: PeriodReview, period: ReportPeriod) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    val title = when (period) {
        ReportPeriod.SEMANA -> "Revisão da semana"
        ReportPeriod.MES -> "Revisão do mês"
        ReportPeriod.ANO -> "Revisão do ano"
    }
    val subtitle = when (period) {
        ReportPeriod.SEMANA -> "tarefas concluídas por dia · esta semana × anterior"
        ReportPeriod.MES -> "tarefas concluídas por dia · este mês × anterior"
        ReportPeriod.ANO -> "tarefas concluídas por dia · este ano × anterior"
    }
    CardBox(Modifier.fillMaxWidth(), corner = 24.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extra.ink3)
            }
            review.totalDeltaLabel?.let { label ->
                val arrow = when {
                    label.startsWith("+") -> "▲ "
                    label.startsWith("−") -> "▼ "
                    else -> ""
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(cs.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        arrow + label,
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        val labels = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
        val maxCount = maxOf(
            review.doneByWeekday.maxOrNull() ?: 0,
            review.prevDoneByWeekday.maxOrNull() ?: 0,
            1,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            labels.forEachIndexed { i, label ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.height(84.dp),
                    ) {
                        ReviewBar(
                            frac = review.prevDoneByWeekday[i].toFloat() / maxCount,
                            color = cs.primary.copy(alpha = 0.30f),
                        )
                        ReviewBar(
                            frac = review.doneByWeekday[i].toFloat() / maxCount,
                            color = cs.primary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, fontSize = 10.5.sp, color = extra.ink3)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary))
                Spacer(Modifier.width(5.dp))
                Text("este período", fontSize = 11.sp, color = extra.ink3)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(9.dp).clip(RoundedCornerShape(2.dp))
                        .background(cs.primary.copy(alpha = 0.30f))
                )
                Spacer(Modifier.width(5.dp))
                Text("anterior", fontSize = 11.sp, color = extra.ink3)
            }
        }
        review.victory?.let { v ->
            Spacer(Modifier.height(12.dp))
            ReviewLine("Vitória:", v.removePrefix("Vitória: "))
        }
        review.lesson?.let { l ->
            Spacer(Modifier.height(8.dp))
            ReviewLine("Lição:", l.removePrefix("Lição: "))
        }
        if (review.victory == null && review.lesson == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Conclua tarefas ao longo do período para ver a vitória e a " +
                    "lição da sua revisão aqui.",
                fontSize = 13.sp,
                color = extra.ink3,
            )
        }
    }
}

@Composable
private fun ReviewBar(frac: Float, color: Color) {
    Box(
        Modifier
            .width(11.dp)
            .fillMaxHeight(frac.coerceIn(0.03f, 1f))
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun ReviewLine(label: String, text: String) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = cs.primary,
        )
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 13.sp, color = extra.ink2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun XraySection(xray: OverdueXray, isPro: Boolean, onOpenPaywall: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    if (!isPro) {
        ProLockCard(
            title = "Raio-X de atrasos",
            teaser = "Onde seu planejamento fura: atrasos por projeto, " +
                "prioridade e horário, com a idade de cada atraso e as " +
                "recorrentes que sempre furam.",
            onOpenPaywall = onOpenPaywall,
        )
        return
    }
    CardBox(Modifier.fillMaxWidth(), corner = 24.dp) {
        CardTitle("Raio-X de atrasos", null)
        Spacer(Modifier.height(8.dp))
        if (xray.total == 0) {
            Text(
                "Nenhuma tarefa atrasada agora — planejamento em dia.",
                fontSize = 13.sp,
                color = extra.ink3,
            )
            return@CardBox
        }
        val top = xray.topDayPart
        if (top != null) {
            val (partLabel, window) = when (top.label) {
                "Noite" -> "à noite" to "depois das 18h"
                "Tarde" -> "à tarde" to "12h–18h"
                else -> "de manhã" to "antes das 12h"
            }
            Text(
                "Suas tarefas “morrem” $partLabel ($window): " +
                    pct(xray.topDayPartShare) + " dos atrasos são de itens " +
                    "agendados para esse período.",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = extra.ink2,
            )
            if (top.label == "Noite") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Mover 1 tarefa da noite para a manhã pode cortar os " +
                        "atrasos quase pela metade.",
                    fontSize = 13.sp,
                    color = extra.ink3,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Por projeto",
            style = MaterialTheme.typography.labelLarge,
            color = cs.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        xray.byProject.forEach { slice ->
            val project = TaskProject.entries.firstOrNull { it.label == slice.label }
            XrayRow(
                dot = project?.let { Color(it.colorHex) } ?: cs.primary,
                label = slice.label,
                count = slice.count,
                share = slice.share(xray.total),
            )
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Por prioridade",
            style = MaterialTheme.typography.labelLarge,
            color = cs.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        xray.byPriority.forEach { slice ->
            val prio = slice.label.removePrefix("P").toIntOrNull() ?: 2
            XrayRow(
                dot = priorityColor(prio),
                label = slice.label + " · " + when (prio) {
                    1 -> "alta"
                    2 -> "média"
                    else -> "baixa"
                },
                count = slice.count,
                share = slice.share(xray.total),
            )
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append("Idade média do atraso: " + fmt1(xray.avgAgeDays) + " dias")
                append(" · mais antigo: " + xray.oldestAgeDays + " dias")
                if (xray.recurringCount > 0) {
                    append(" · " + xray.recurringCount + " recorrentes entre elas")
                }
                append(".")
            },
            fontSize = 12.sp,
            color = extra.ink3,
        )
    }
}

@Composable
private fun XrayRow(dot: Color, label: String, count: Int, share: Double) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = extra.ink2,
            modifier = Modifier.width(110.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(extra.pageChip),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(99.dp))
                    .background(dot),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "$count · " + pct(share),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = cs.onBackground,
        )
    }
}

@Composable
private fun YearReviewSection(
    year: Highlights,
    quarter: Highlights,
    isPro: Boolean,
    today: LocalDate,
) {
    val cs = MaterialTheme.colorScheme
    val extra = MaterialTheme.constanciaExtra
    CardBox(Modifier.fillMaxWidth(), corner = 24.dp) {
        Text(
            "Ano em revista · " + MONTHS_PT[today.monthNumber - 1],
            style = MaterialTheme.typography.labelLarge,
            color = cs.primary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Seu ${year.from.year} em constância",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            color = cs.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Dias de sequência, tarefas vencidas, humor ao longo do ano — " +
                "montado automaticamente e pronto pra compartilhar.",
            fontSize = 13.sp,
            color = extra.ink3,
        )
        Spacer(Modifier.height(12.dp))
        if (year.bestStreak > 0) {
            HighlightFact(
                icon = Icons.Filled.Whatshot,
                tint = cs.secondary,
                text = "${year.bestStreak} dias de maior sequência" +
                    (year.bestStreakHabit?.let { " — $it" } ?: ""),
            )
        }
        if (year.tasksDone > 0) {
            HighlightFact(
                icon = Icons.Filled.Check,
                tint = cs.primary,
                text = "${year.tasksDone} tarefas concluídas · " +
                    pct(year.taskRate) + " das planejadas",
            )
        }
        if (year.totalCheckins > 0) {
            HighlightFact(
                icon = Icons.Filled.Insights,
                tint = cs.primary,
                text = "${year.totalCheckins} check-ins no ano",
            )
        }
        if (year.perfectDays > 0) {
            HighlightFact(
                icon = Icons.Filled.Check,
                tint = cs.primary,
                text = "${year.perfectDays} dias perfeitos",
            )
        }
        year.happiestMonth?.let { m ->
            HighlightFact(
                icon = Icons.Filled.WbSunny,
                tint = extra.amber,
                text = "Mês mais feliz: " + MONTHS_PT[m - 1] +
                    (year.happiestMonthAvg?.let { " (humor médio " + fmt1(it) + ")" } ?: ""),
            )
        }
        year.mostProductiveWeekday?.let { iso ->
            HighlightFact(
                icon = Icons.Filled.Insights,
                tint = cs.primary,
                text = "Dia mais produtivo: " + WEEKDAYS_ISO_PT[iso - 1],
            )
        }
        if (year.moodByMonth.any { it != null }) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Humor médio por mês",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                year.moodByMonth.forEachIndexed { i, v ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.height(52.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (v != null) {
                                val frac = ((v - 1.0) / 4.0).toFloat().coerceIn(0.12f, 1f)
                                Box(
                                    Modifier
                                        .width(16.dp)
                                        .fillMaxHeight(frac)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(moodColor(v.roundToInt().coerceIn(1, 5))),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .width(16.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(extra.heat0),
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(
                            MONTHS[i].take(1).uppercase(),
                            fontSize = 9.5.sp,
                            color = extra.ink3,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(extra.pageLine))
        Spacer(Modifier.height(12.dp))
        if (isPro) {
            Text(
                "Trimestre em revista · Pro",
                style = MaterialTheme.typography.labelLarge,
                color = cs.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${quarter.tasksDone} tarefas concluídas · " +
                    "${quarter.totalCheckins} check-ins · maior sequência de " +
                    "${quarter.bestStreak} dias neste trimestre.",
                fontSize = 13.sp,
                color = extra.ink2,
            )
        } else {
            Text(
                "No Constância Pro: versão completa do ano + revista do trimestre.",
                fontSize = 12.sp,
                color = extra.ink3,
            )
        }
    }
}

@Composable
private fun HighlightFact(
    icon: ImageVector,
    tint: Color,
    text: String,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onBackground,
        )
    }
}
