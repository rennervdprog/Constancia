package app.constancia.domain

import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Relatórios detalhados (plano aprovado): padrões cruzados com defasagem,
 * resumo do período com deltas, raio-X de atrasos e Ano em Revista.
 * Lógica pura — 100% testável, sem Compose. Tudo derivado dos dados que o
 * app já coleta (nenhum dado novo).
 *
 * Regras de honestidade estatística (do plano, modelo Bearable):
 * - correlação só aparece com amostra mínima e o n fica sempre visível;
 * - linguagem não-causal ("se associa a", nunca "faz você");
 * - sem denominador, sem métrica: toda contagem tem par ou taxa.
 */

// ---------------------------------------------------------------------------
// Utilidades
// ---------------------------------------------------------------------------

/** Data efetiva da tarefa nos relatórios: o prazo; sem prazo, o dia dela. */
fun taskEffDate(task: TaskItem): String = task.dueDate ?: task.date

private fun List<Double>.avgOrNull(): Double? =
    if (isEmpty()) null else average()

private fun daysBetween(from: LocalDate, to: LocalDate): Int =
    to.toEpochDays() - from.toEpochDays()

/** "7h05" a partir de horas decimais (7.09 -> 7h05). */
fun fmtSleep(v: Double): String {
    var h = v.toInt()
    var m = ((v - h) * 60).roundToInt()
    if (m == 60) {
        h += 1
        m = 0
    }
    return "$h" + "h" + m.toString().padStart(2, '0')
}

/** Delta em pontos percentuais com sinal: "+9 p.p." / "−4 p.p." / "0 p.p.". */
fun fmtDeltaPp(delta: Double): String {
    val r = (delta * 100).roundToInt()
    return when {
        r > 0 -> "+$r p.p."
        r < 0 -> "−${abs(r)} p.p."
        else -> "0 p.p."
    }
}

/** Variação percentual com sinal: "+12%" / "−8%". Null quando a base é 0. */
fun fmtDeltaPct(current: Double, previous: Double): String? {
    if (previous == 0.0) return null
    val r = ((current - previous) / previous * 100).roundToInt()
    return when {
        r > 0 -> "+$r%"
        r < 0 -> "−${abs(r)}%"
        else -> "0%"
    }
}

// ---------------------------------------------------------------------------
// Resumo do período (KPIs do cabeçalho + deltas vs. período anterior)
// ---------------------------------------------------------------------------

data class PeriodSummary(
    val tasksDone: Int,
    val tasksPlanned: Int,
    /** Conclusão combinada: (hábitos feitos + tarefas feitas) / (devidos + planejadas). */
    val combinedRate: Double,
    /** Delta da conclusão combinada vs. período anterior, em fração (0.09 = +9 p.p.). */
    val combinedDelta: Double?,
    val checkins: Int,
    val moodAvg: Double?,
    val sleepAvg: Double?,
)

private fun tasksInWindow(tasks: List<TaskItem>, from: LocalDate, to: LocalDate): List<TaskItem> {
    val f = from.toYmd()
    val t = to.toYmd()
    return tasks.filter { val d = taskEffDate(it); d >= f && d <= t }
}

fun moodAvgIn(diary: List<DiaryEntry>, from: LocalDate, to: LocalDate): Double? {
    val f = from.toYmd()
    val t = to.toYmd()
    return diary.filter { it.date >= f && it.date <= t }
        .mapNotNull { it.mood?.toDouble() }.avgOrNull()
}

fun sleepAvgIn(diary: List<DiaryEntry>, from: LocalDate, to: LocalDate): Double? {
    val f = from.toYmd()
    val t = to.toYmd()
    return diary.filter { it.date >= f && it.date <= t }
        .mapNotNull { it.sleepHours }.avgOrNull()
}

/**
 * Resumo do período combinando o [ReportData] (hábitos) com as tarefas e o
 * diário. [previous] é o relatório do período imediatamente anterior
 * (computeReport com todayOverride deslocado) — null quando não há.
 */
fun periodSummary(
    tasks: List<TaskItem>,
    diary: List<DiaryEntry>,
    report: ReportData,
    previous: ReportData?,
): PeriodSummary {
    val cur = tasksInWindow(tasks, report.from, report.to)
    val curDone = cur.count { it.done }
    val curDue = report.days.sumOf { it.due }
    val curHabitDone = report.days.sumOf { it.done }
    val combined = if (curDue + cur.size > 0) {
        (curHabitDone + curDone).toDouble() / (curDue + cur.size)
    } else {
        0.0
    }
    val delta = previous?.let { prev ->
        val prevTasks = tasksInWindow(tasks, prev.from, prev.to)
        val prevDone = prevTasks.count { it.done }
        val prevDue = prev.days.sumOf { it.due }
        val prevHabitDone = prev.days.sumOf { it.done }
        if (prevDue + prevTasks.size > 0) {
            combined - (prevHabitDone + prevDone).toDouble() / (prevDue + prevTasks.size)
        } else {
            null
        }
    }
    return PeriodSummary(
        tasksDone = curDone,
        tasksPlanned = cur.size,
        combinedRate = combined,
        combinedDelta = delta,
        checkins = report.totalCheckins,
        moodAvg = moodAvgIn(diary, report.from, report.to),
        sleepAvg = sleepAvgIn(diary, report.from, report.to),
    )
}

// ---------------------------------------------------------------------------
// Padrão 1 — Sono (defasagem): a noite registrada no dia D × o dia D
// ---------------------------------------------------------------------------

/** Limiar de "noite curta" do protótipo/plano: menos de 6h30. */
const val SHORT_SLEEP_HOURS = 6.5

data class SleepLagPattern(
    val shortNights: Int,
    val enoughNights: Int,
    val moodAfterShort: Double?,
    val moodAfterEnough: Double?,
    val energyAfterShort: Double?,
    val energyAfterEnough: Double?,
    /** Fração das tarefas do dia que ficaram atrasadas (não feitas, vencidas). */
    val overdueRateAfterShort: Double,
    val overdueRateAfterEnough: Double,
    /** Quantas vezes mais se atrasa após noite curta (null = sem base). */
    val overdueRatio: Double?,
    /** Sono mais recente registrado até [today] (a "noite passada"). */
    val lastSleepHours: Double?,
    val lastSleepDate: LocalDate?,
) {
    val totalNights: Int get() = shortNights + enoughNights
}

/**
 * Cruza o sono registrado em cada dia com o próprio dia: humor, energia e
 * atrasos de tarefas. Amostra mínima: 7 noites no total e 3 em cada grupo —
 * abaixo disso retorna null (um insight ausente é melhor que um falso).
 */
fun sleepLagPattern(
    diary: List<DiaryEntry>,
    tasks: List<TaskItem>,
    today: LocalDate,
): SleepLagPattern? {
    val withSleep = diary.filter { it.sleepHours != null && it.date.toLocalDate() <= today }
    if (withSleep.size < 7) return null
    val short = withSleep.filter { (it.sleepHours ?: 0.0) < SHORT_SLEEP_HOURS }
    val enough = withSleep.filter { (it.sleepHours ?: 0.0) >= SHORT_SLEEP_HOURS }
    if (short.size < 3 || enough.size < 3) return null

    fun overdueRate(days: List<DiaryEntry>): Double {
        val dates = days.map { it.date }.toSet()
        val due = tasks.filter { taskEffDate(it) in dates }
        if (due.isEmpty()) return 0.0
        val late = due.count { !it.done && taskEffDate(it) < today.toYmd() }
        return late.toDouble() / due.size
    }
    val rateShort = overdueRate(short)
    val rateEnough = overdueRate(enough)
    val last = withSleep.maxByOrNull { it.date }
    return SleepLagPattern(
        shortNights = short.size,
        enoughNights = enough.size,
        moodAfterShort = short.mapNotNull { it.mood?.toDouble() }.avgOrNull(),
        moodAfterEnough = enough.mapNotNull { it.mood?.toDouble() }.avgOrNull(),
        energyAfterShort = short.mapNotNull { it.energy?.toDouble() }.avgOrNull(),
        energyAfterEnough = enough.mapNotNull { it.energy?.toDouble() }.avgOrNull(),
        overdueRateAfterShort = rateShort,
        overdueRateAfterEnough = rateEnough,
        overdueRatio = if (rateEnough > 0.0 && rateShort > 0.0) rateShort / rateEnough else null,
        lastSleepHours = last?.sleepHours,
        lastSleepDate = last?.date?.toLocalDate(),
    )
}

// ---------------------------------------------------------------------------
// Padrão 2 — Hábito da manhã × o dia (humor, energia e tarefas)
// ---------------------------------------------------------------------------

data class MorningHabitPattern(
    val habit: Habit,
    val daysWith: Int,
    val daysWithout: Int,
    val moodWith: Double,
    val moodWithout: Double,
    val energyWith: Double?,
    val energyWithout: Double?,
    /** Taxa de conclusão de tarefas nos dias com/sem o hábito. */
    val taskRateWith: Double?,
    val taskRateWithout: Double?,
) {
    /** Diferença relativa da conclusão de tarefas: +31% -> 0.31. */
    val taskRateDelta: Double?
        get() {
            val w = taskRateWith ?: return null
            val wo = taskRateWithout ?: return null
            return if (wo > 0.0) (w - wo) / wo else null
        }
}

/**
 * Escolhe o hábito de sessão MANHÃ com mais dias feitos entre os dias com
 * registro de humor e compara os dias com/sem ele. Amostra mínima:
 * 3 dias em cada grupo e humor nos dois grupos.
 */
fun morningHabitPattern(
    diary: List<DiaryEntry>,
    habits: List<Habit>,
    checkins: List<Checkin>,
    tasks: List<TaskItem>,
    today: LocalDate,
): MorningHabitPattern? {
    val moodEntries = diary.filter { it.mood != null && it.date.toLocalDate() <= today }
    if (moodEntries.size < 6) return null
    val morning = habits.filter { it.session == Session.MANHA && !it.paused }
    var best: MorningHabitPattern? = null
    var bestScore = -1
    for (habit in morning) {
        val doneDates = doneDatesFor(
            habit,
            donePairs(checkins.filter { it.habitId == habit.id }),
        )
        val with = moodEntries.filter { doneDates.contains(it.date) }
        val without = moodEntries.filter { !doneDates.contains(it.date) }
        if (with.size < 3 || without.size < 3) continue
        val score = with.size * 10 + minOf(with.size, without.size)
        if (score <= bestScore) continue

        fun taskRate(days: List<DiaryEntry>): Double? {
            val dates = days.map { it.date }.toSet()
            val due = tasks.filter { taskEffDate(it) in dates }
            return if (due.isEmpty()) null
            else due.count { it.done }.toDouble() / due.size
        }
        bestScore = score
        best = MorningHabitPattern(
            habit = habit,
            daysWith = with.size,
            daysWithout = without.size,
            moodWith = with.map { it.mood ?: 0 }.average(),
            moodWithout = without.map { it.mood ?: 0 }.average(),
            energyWith = with.mapNotNull { it.energy?.toDouble() }.avgOrNull(),
            energyWithout = without.mapNotNull { it.energy?.toDouble() }.avgOrNull(),
            taskRateWith = taskRate(with),
            taskRateWithout = taskRate(without),
        )
    }
    return best
}

// ---------------------------------------------------------------------------
// Revisão do período — tarefas por dia da semana (esta × anterior),
// vitória e lição
// ---------------------------------------------------------------------------

data class PeriodReview(
    /** Tarefas concluídas por dia da semana, seg..dom (índice ISO-1). */
    val doneByWeekday: List<Int>,
    val prevDoneByWeekday: List<Int>,
    /** Variação do total de concluídas vs. período anterior ("+12%"). */
    val totalDeltaLabel: String?,
    val victory: String?,
    val lesson: String?,
)

private fun weekdayIndex(date: LocalDate): Int = date.isoDayNumberCompat() - 1 // 0=seg

fun periodReview(
    tasks: List<TaskItem>,
    report: ReportData,
    previous: ReportData?,
): PeriodReview {
    fun byWeekday(from: LocalDate, to: LocalDate): List<Int> {
        val counts = MutableList(7) { 0 }
        tasksInWindow(tasks, from, to).filter { it.done }.forEach {
            counts[weekdayIndex(taskEffDate(it).toLocalDate())]++
        }
        return counts
    }
    val cur = byWeekday(report.from, report.to)
    val prev = previous?.let { byWeekday(it.from, it.to) } ?: List(7) { 0 }
    val totalDelta = previous?.let {
        fmtDeltaPct(cur.sum().toDouble(), prev.sum().toDouble())
    }

    val bestIdx = cur.indices.maxByOrNull { cur[it] }
    val victory = if (bestIdx != null && cur[bestIdx] > 0) {
        "Vitória: ${WEEKDAYS_ISO_PT[bestIdx]} foi seu melhor dia " +
            "(${cur[bestIdx]} tarefas concluídas)."
    } else {
        null
    }

    // Lição: dia da semana com mais tarefas atrasadas no período.
    val todayYmd = report.to.toYmd()
    val overdueByWeekday = MutableList(7) { 0 }
    tasksInWindow(tasks, report.from, report.to)
        .filter { !it.done && taskEffDate(it) < todayYmd }
        .forEach { overdueByWeekday[weekdayIndex(taskEffDate(it).toLocalDate())]++ }
    val worstIdx = overdueByWeekday.indices.maxByOrNull { overdueByWeekday[it] }
    val lesson = if (worstIdx != null && overdueByWeekday[worstIdx] > 0 &&
        worstIdx != bestIdx
    ) {
        "Lição: tarefas de ${WEEKDAYS_ISO_PT[worstIdx]} são as que mais atrasam " +
            "(${overdueByWeekday[worstIdx]} no período) — vale proteger esse dia."
    } else {
        null
    }

    return PeriodReview(
        doneByWeekday = cur,
        prevDoneByWeekday = prev,
        totalDeltaLabel = totalDelta,
        victory = victory,
        lesson = lesson,
    )
}

// ---------------------------------------------------------------------------
// Raio-X de atrasos — onde o planejamento fura
// ---------------------------------------------------------------------------

data class OverdueSlice(val label: String, val count: Int) {
    fun share(total: Int): Double = if (total > 0) count.toDouble() / total else 0.0
}

data class OverdueXray(
    val total: Int,
    /** Faixas do dia: Manhã (<12h) · Tarde (12h–18h) · Noite (depois das 18h). */
    val byDayPart: List<OverdueSlice>,
    val topDayPart: OverdueSlice?,
    val topDayPartShare: Double,
    val byProject: List<OverdueSlice>,
    val byPriority: List<OverdueSlice>,
    /** Idade média do atraso, em dias. */
    val avgAgeDays: Double,
    /** Atraso mais antigo, em dias. */
    val oldestAgeDays: Int,
    /** Quantas das atrasadas são recorrentes. */
    val recurringCount: Int,
)

private fun dayPartOf(task: TaskItem): String {
    val time = task.dueTime
    if (!time.isNullOrBlank()) {
        val hour = time.take(2).toIntOrNull() ?: return when (task.session) {
            Session.MANHA -> "Manhã"
            Session.TARDE -> "Tarde"
            Session.NOITE -> "Noite"
        }
        return when {
            hour < 12 -> "Manhã"
            hour < 18 -> "Tarde"
            else -> "Noite"
        }
    }
    return when (task.session) {
        Session.MANHA -> "Manhã"
        Session.TARDE -> "Tarde"
        Session.NOITE -> "Noite"
    }
}

fun overdueXray(tasks: List<TaskItem>, today: LocalDate): OverdueXray {
    val todayYmd = today.toYmd()
    val overdue = tasks.filter {
        !it.done && it.dueDate != null && it.dueDate < todayYmd
    }
    fun slices(labels: List<String>, key: (TaskItem) -> String): List<OverdueSlice> =
        labels.map { label -> OverdueSlice(label, overdue.count { key(it) == label }) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }

    val parts = slices(listOf("Manhã", "Tarde", "Noite")) { dayPartOf(it) }
    val ages = overdue.map {
        daysBetween((it.dueDate ?: it.date).toLocalDate(), today).coerceAtLeast(0)
    }
    return OverdueXray(
        total = overdue.size,
        byDayPart = parts,
        topDayPart = parts.firstOrNull(),
        topDayPartShare = parts.firstOrNull()?.share(overdue.size) ?: 0.0,
        byProject = slices(TaskProject.entries.map { it.label }) {
            it.project.label
        },
        byPriority = slices(listOf("P1", "P2", "P3")) { prioCode(it.priority) },
        avgAgeDays = ages.map { it.toDouble() }.avgOrNull() ?: 0.0,
        oldestAgeDays = ages.maxOrNull() ?: 0,
        recurringCount = overdue.count { it.repeatRule.isNotBlank() },
    )
}

// ---------------------------------------------------------------------------
// Ano em Revista — fatos-âncora de um intervalo (ano ou trimestre)
// ---------------------------------------------------------------------------

data class Highlights(
    val from: LocalDate,
    val to: LocalDate,
    val totalCheckins: Int,
    val bestStreak: Int,
    val bestStreakHabit: String?,
    val tasksDone: Int,
    val tasksPlanned: Int,
    /** Humor médio por mês (índice 0=jan); null = sem registros no mês. */
    val moodByMonth: List<Double?>,
    /** Mês com maior humor médio (1..12), com pelo menos 3 registros. */
    val happiestMonth: Int?,
    val happiestMonthAvg: Double?,
    /** Dia da semana (ISO 1=seg) com mais tarefas concluídas. */
    val mostProductiveWeekday: Int?,
    val perfectDays: Int,
) {
    val taskRate: Double
        get() = if (tasksPlanned > 0) tasksDone.toDouble() / tasksPlanned else 0.0
}

fun periodHighlights(
    habits: List<Habit>,
    checkins: List<Checkin>,
    tasks: List<TaskItem>,
    diary: List<DiaryEntry>,
    from: LocalDate,
    to: LocalDate,
    report: ReportData,
): Highlights {
    val f = from.toYmd()
    val t = to.toYmd()
    val yearCheckins = checkins.filter { it.date in f..t }
    var best = 0
    var bestName: String? = null
    habits.forEach { h ->
        val done = doneDatesFor(
            h,
            donePairs(checkins.filter { it.habitId == h.id }),
        )
        val b = bestStreak(h, done, emptySet())
        if (b > best) {
            best = b
            bestName = h.name
        }
    }
    val windowTasks = tasksInWindow(tasks, from, to)
    val byMonth = (1..12).map { month ->
        diary.filter {
            it.date >= f && it.date <= t &&
                it.date.toLocalDate().monthNumber == month
        }.mapNotNull { it.mood?.toDouble() }.avgOrNull()
    }
    val monthCounts = (1..12).map { month ->
        diary.count {
            it.date >= f && it.date <= t &&
                it.date.toLocalDate().monthNumber == month && it.mood != null
        }
    }
    val happiest = (1..12)
        .filter { monthCounts[it - 1] >= 3 && byMonth[it - 1] != null }
        .maxByOrNull { byMonth[it - 1] ?: -1.0 }
    val doneByWeekday = MutableList(7) { 0 }
    windowTasks.filter { it.done }.forEach {
        doneByWeekday[weekdayIndex(taskEffDate(it).toLocalDate())]++
    }
    val prodIdx = doneByWeekday.indices.maxByOrNull { doneByWeekday[it] }
    return Highlights(
        from = from,
        to = to,
        totalCheckins = yearCheckins.size,
        bestStreak = best,
        bestStreakHabit = bestName,
        tasksDone = windowTasks.count { it.done },
        tasksPlanned = windowTasks.size,
        moodByMonth = byMonth,
        happiestMonth = happiest,
        happiestMonthAvg = happiest?.let { byMonth[it - 1] },
        mostProductiveWeekday = if (prodIdx != null && doneByWeekday[prodIdx] > 0) {
            prodIdx + 1
        } else {
            null
        },
        perfectDays = report.days.count { it.isPerfect && it.date in from..to },
    )
}

/** Intervalo do Ano em Revista: 1º de janeiro até hoje (ou 31/12). */
fun yearWindow(today: LocalDate): Pair<LocalDate, LocalDate> {
    val jan1 = LocalDate(today.year, 1, 1)
    val dec31 = LocalDate(today.year, 12, 31)
    return jan1 to minOf(today, dec31)
}

/** Intervalo do trimestre atual (versão Pro do Ano em Revista). */
fun quarterWindow(today: LocalDate): Pair<LocalDate, LocalDate> {
    val firstMonth = (today.monthNumber - 1) / 3 * 3 + 1
    val start = LocalDate(today.year, firstMonth, 1)
    val end = start.plus(DatePeriod(months = 3)).minus(DatePeriod(days = 1))
    return start to minOf(today, end)
}
