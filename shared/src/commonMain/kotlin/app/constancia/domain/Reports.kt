package app.constancia.domain

import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.isoToAppDay
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
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
// Modelos do relatório (lógica pura — 100% testável, sem Compose)
// ---------------------------------------------------------------------------

enum class ReportPeriod(val days: Int, val label: String) {
    SEMANA(7, "Semana"),
    MES(30, "Mês"),
    ANO(365, "Ano"),
}

data class DayPoint(
    val date: LocalDate,
    val due: Int,
    val done: Int,
    val checkins: Int,
) {
    val rate: Double get() = if (due > 0) done.toDouble() / due else 0.0
    val isPerfect: Boolean get() = due > 0 && done >= due
}

data class HabitReport(
    val habit: Habit,
    val streak: Int,
    val dueDays: Int,
    val doneDays: Int,
    val checkinCount: Int,
    val totalQuantity: Double,
) {
    val rate: Double get() = if (dueDays > 0) doneDays.toDouble() / dueDays else 0.0
}

data class ReportData(
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

// ---------------------------------------------------------------------------
// Formatação (sem String.format — commonMain)
// ---------------------------------------------------------------------------

fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

fun fmt1(v: Double): String {
    val r = (v * 10).roundToInt()
    return "${r / 10},${abs(r % 10)}"
}

fun fmtQty(v: Double): String {
    val whole = v.roundToInt()
    return if (v == whole.toDouble()) "$whole" else fmt1(v)
}

fun formatDay(d: LocalDate): String =
    "${d.dayOfMonth} de ${MONTHS[d.monthNumber - 1]}"

// ---------------------------------------------------------------------------
// Cálculo do relatório (função pura — tudo derivado dos dados reais)
// ---------------------------------------------------------------------------

fun weekStartOf(d: LocalDate): LocalDate =
    d.minus(DatePeriod(days = d.isoDayNumberCompat() - 1))

fun isDueOn(habit: Habit, date: LocalDate, activeFrom: LocalDate?): Boolean {
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
fun satisfiedDates(habit: Habit, doneDates: Set<String>): Set<String> {
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

fun rateOf(points: List<DayPoint>): Double {
    val due = points.sumOf { it.due }
    return if (due > 0) points.sumOf { it.done }.toDouble() / due else 0.0
}

fun computeReport(
    habits: List<Habit>,
    checkins: List<Checkin>,
    diary: List<DiaryEntry>,
    period: ReportPeriod,
    timeZone: TimeZone,
    forgivenByHabit: Map<String, Set<String>> = emptyMap(),
    todayOverride: LocalDate? = null, // só para testes: fixa o "hoje"
): ReportData {
    val today = todayOverride ?: Clock.System.todayIn(timeZone)
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
        val pairs = donePairs(checkinsByHabit[h.id].orEmpty())
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
            streak = currentStreak(h, doneDates, today.toYmd(), timeZone, forgivenByHabit[h.id].orEmpty()),
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

fun goalDescription(h: Habit): String = when {
    h.goalType == GoalType.QUANTITY ->
        "Meta de ${fmtQty(h.targetQuantity)} ${h.quantityUnit}".trim()
    h.frequencyType == FrequencyType.WEEKLY_X -> "${h.weeklyTarget}× por semana"
    h.frequencyType == FrequencyType.DAYS_OF_WEEK && h.daysOfWeek.isNotEmpty() ->
        h.daysOfWeek.sorted().joinToString(", ") { DOW_SHORT[it - 1] }
    else -> "Diário"
}

fun buildSummary(data: ReportData): String {
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
