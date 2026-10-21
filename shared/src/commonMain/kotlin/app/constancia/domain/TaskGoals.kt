package app.constancia.domain

import app.constancia.model.GoalPause
import app.constancia.model.StreakInfo
import app.constancia.model.TaskItem
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Item 2 — metas diária/semanal de tarefas concluídas, SEM pontos nem
 * Karma (decisão do Renner): só contagem, progresso e sequência (streak),
 * com modo pausa que congela a sequência sem quebrar — a mesma filosofia
 * das pausas de hábito (domain/Streaks.kt), reaproveitada aqui para o
 * histórico de conclusões das tarefas.
 *
 * A data de conclusão de cada tarefa é `completedAt` (schema v4); para
 * tarefas concluídas antes da v4 (sem completedAt), vale o dia a que a
 * tarefa pertencia (`date`, que acompanha o prazo).
 */

/** Quantas tarefas foram concluídas em cada dia (yyyy-MM-dd -> total). */
fun completionCountsByDay(tasks: List<TaskItem>): Map<String, Int> =
    tasks.filter { it.done }
        .groupingBy { task -> task.completedAt?.take(10) ?: task.date }
        .eachCount()

/**
 * Expande as pausas da meta em dias "perdoados" (yyyy-MM-dd).
 *
 * R10.1 (Fase D): [ignoreWeekdays] (ISO 1=seg..7=dom) são os dias de
 * descanso FIXOS da meta — entram como perdoados em toda a janela que
 * o streak pode atravessar (a mesma trava de 3700 dias das pausas),
 * então o fim de semana fora da meta não exige abrir pausa toda
 * sexta-feira. Sem o parâmetro, o comportamento é o de sempre (só
 * pausas) — é assim que a tela de ajustes lê o estado do interruptor
 * "Pausar a meta".
 */
fun goalForgivenDates(
    pauses: List<GoalPause>,
    todayYmd: String,
    ignoreWeekdays: Set<Int> = emptySet(),
): Set<String> {
    val today = todayYmd.toLocalDate()
    val out = mutableSetOf<String>()
    for (pause in pauses) {
        var d = pause.startDate.toLocalDate()
        val end = pause.endDate?.toLocalDate() ?: today
        var guard = 0
        while (d <= end && guard++ < 3700) {
            out.add(d.toYmd())
            d = d.plus(DatePeriod(days = 1))
        }
    }
    if (ignoreWeekdays.isNotEmpty()) {
        var d = today.minus(DatePeriod(days = 3700))
        while (d <= today) {
            if (d.isoDayNumberCompat() in ignoreWeekdays) {
                out.add(d.toYmd())
            }
            d = d.plus(DatePeriod(days = 1))
        }
    }
    return out
}

/** Dias em que a meta diária foi batida (contagem >= meta). */
fun dailyGoalMetDates(
    counts: Map<String, Int>,
    dailyGoal: Int,
): Set<String> =
    if (dailyGoal <= 0) emptySet()
    else counts.filterValues { it >= dailyGoal }.keys

/**
 * Sequência atual da meta diária: dias consecutivos batendo a meta,
 * contando de hoje para trás (hoje em andamento não quebra), com dias
 * de pausa pulados sem contar e sem quebrar.
 */
fun dailyGoalStreak(
    metDates: Set<String>,
    forgivenDates: Set<String>,
    todayYmd: String,
): Int {
    if (metDates.isEmpty() && forgivenDates.isEmpty()) return 0
    var cursor = todayYmd.toLocalDate()
    if (!metDates.contains(cursor.toYmd())) {
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    var streak = 0
    var guard = 0
    while (guard++ < 3700) {
        val key = cursor.toYmd()
        if (metDates.contains(key)) {
            streak++
        } else if (!forgivenDates.contains(key)) {
            break
        }
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return streak
}

/** Segunda-feira da semana ISO de uma data. */
private fun mondayOf(date: LocalDate): LocalDate =
    date.minus(DatePeriod(days = date.isoDayNumberCompat() - 1))

/** Conclusões dentro da semana (seg..dom) que contém [day]. */
fun weekCompletionCount(counts: Map<String, Int>, day: LocalDate): Int {
    val start = mondayOf(day)
    val end = start.plus(DatePeriod(days = 6))
    return counts.entries.sumOf { (ymd, n) ->
        val d = ymd.toLocalDate()
        if (d >= start && d <= end) n else 0
    }
}

/**
 * Sequência de semanas batendo a meta semanal. A semana atual, se em
 * andamento, não quebra nem conta; uma semana com 4+ dias de pausa é
 * considerada congelada (não quebra).
 */
fun weeklyGoalStreak(
    counts: Map<String, Int>,
    weeklyGoal: Int,
    forgivenDates: Set<String>,
    today: LocalDate,
): Int {
    if (weeklyGoal <= 0) return 0
    val thisWeek = mondayOf(today)
    var weekStart = thisWeek
    var streak = 0
    var guard = 0
    while (guard++ < 520) {
        val total = weekCompletionCount(counts, weekStart)
        val weekEnd = weekStart.plus(DatePeriod(days = 6))
        val forgivenInWeek = forgivenDates.count { ymd ->
            val d = ymd.toLocalDate()
            d >= weekStart && d <= weekEnd
        }
        val isCurrent = weekStart == thisWeek
        when {
            total >= weeklyGoal -> streak++
            isCurrent && total > 0 -> Unit // em andamento: não quebra
            forgivenInWeek >= 4 -> Unit // semana congelada pela pausa
            else -> return streak
        }
        weekStart = weekStart.minus(DatePeriod(days = 7))
    }
    return streak
}

// ---------------------------------------------------------------------
// R10.2 (Fase D) — as sequências da meta como OBJETO (StreakInfo):
// a corrida atual com início/fim + o recorde histórico, nos mesmos
// moldes dos hábitos (domain/Streaks.kt habitStreakInfo).
// ---------------------------------------------------------------------

/** Maior corrida histórica de dias batendo a meta (buracos só de dias
 *  perdoados não quebram a corrida, como no bestStreak dos hábitos). */
private fun bestDailyGoalRun(
    metDates: Set<String>,
    forgivenDates: Set<String>,
): Int {
    if (metDates.isEmpty()) return 0
    val sorted = metDates.map { it.toLocalDate() }.sorted()
    var best = 1
    var run = 1
    for (i in 1 until sorted.size) {
        val prev = sorted[i - 1]
        val cur = sorted[i]
        val gap = cur.toEpochDays() - prev.toEpochDays()
        if (gap == 1) {
            run++
        } else if (gap > 1) {
            var d = prev.plus(DatePeriod(days = 1))
            var allForgiven = true
            while (d < cur) {
                if (!forgivenDates.contains(d.toYmd())) {
                    allForgiven = false
                    break
                }
                d = d.plus(DatePeriod(days = 1))
            }
            run = if (allForgiven) run + 1 else 1
        }
        if (run > best) best = run
    }
    return best
}

/**
 * Sequência diária da meta como objeto: a caminhada é a de
 * [dailyGoalStreak] (hoje em andamento não quebra; perdoados são
 * atravessados sem contar), anotando o primeiro/último dia CONTADO.
 */
fun dailyGoalStreakInfo(
    metDates: Set<String>,
    forgivenDates: Set<String>,
    todayYmd: String,
): StreakInfo {
    var count = 0
    var start: LocalDate? = null
    var end: LocalDate? = null
    if (metDates.isNotEmpty() || forgivenDates.isNotEmpty()) {
        var cursor = todayYmd.toLocalDate()
        if (!metDates.contains(cursor.toYmd())) {
            cursor = cursor.minus(DatePeriod(days = 1))
        }
        var guard = 0
        while (guard++ < 3700) {
            val key = cursor.toYmd()
            if (metDates.contains(key)) {
                count++
                if (end == null) end = cursor
                start = cursor
            } else if (!forgivenDates.contains(key)) {
                break
            }
            cursor = cursor.minus(DatePeriod(days = 1))
        }
    }
    return StreakInfo(
        count = count,
        start = start,
        end = end,
        best = bestDailyGoalRun(metDates, forgivenDates),
    )
}

/** Maior corrida histórica de semanas batendo a meta semanal; semana
 *  congelada (4+ dias perdoados) não conta nem quebra, como em
 *  [weeklyGoalStreak]. */
private fun bestWeeklyGoalRun(
    counts: Map<String, Int>,
    weeklyGoal: Int,
    forgivenDates: Set<String>,
    today: LocalDate,
): Int {
    if (weeklyGoal <= 0 || counts.isEmpty()) return 0
    val firstDay = counts.keys.minOf { it.toLocalDate() }
    var weekStart = mondayOf(firstDay)
    val thisWeek = mondayOf(today)
    var best = 0
    var run = 0
    var guard = 0
    while (weekStart <= thisWeek && guard++ < 520) {
        val total = weekCompletionCount(counts, weekStart)
        val weekEnd = weekStart.plus(DatePeriod(days = 6))
        val forgivenInWeek = forgivenDates.count { ymd ->
            val d = ymd.toLocalDate()
            d >= weekStart && d <= weekEnd
        }
        when {
            total >= weeklyGoal -> {
                run++
                if (run > best) best = run
            }
            forgivenInWeek >= 4 -> Unit // congelada: atravessa
            else -> run = 0
        }
        weekStart = weekStart.plus(DatePeriod(days = 7))
    }
    return best
}

/**
 * Sequência semanal da meta como objeto: a caminhada é a de
 * [weeklyGoalStreak]; start/end são a segunda/o domingo das semanas
 * extremas da corrida contada.
 */
fun weeklyGoalStreakInfo(
    counts: Map<String, Int>,
    weeklyGoal: Int,
    forgivenDates: Set<String>,
    today: LocalDate,
): StreakInfo {
    var count = 0
    var start: LocalDate? = null
    var end: LocalDate? = null
    if (weeklyGoal > 0) {
        val thisWeek = mondayOf(today)
        var weekStart = thisWeek
        var guard = 0
        while (guard++ < 520) {
            val total = weekCompletionCount(counts, weekStart)
            val weekEnd = weekStart.plus(DatePeriod(days = 6))
            val forgivenInWeek = forgivenDates.count { ymd ->
                val d = ymd.toLocalDate()
                d >= weekStart && d <= weekEnd
            }
            val isCurrent = weekStart == thisWeek
            when {
                total >= weeklyGoal -> {
                    count++
                    if (end == null) end = weekEnd
                    start = weekStart
                }
                isCurrent && total > 0 -> Unit // em andamento: não quebra
                forgivenInWeek >= 4 -> Unit // congelada: não quebra
                else -> break
            }
            weekStart = weekStart.minus(DatePeriod(days = 7))
        }
        // A caminhada parou sem contar nada? Sai do laço com count 0.
        if (count == 0) {
            start = null
            end = null
        }
    }
    return StreakInfo(
        count = count,
        start = start,
        end = end,
        best = bestWeeklyGoalRun(counts, weeklyGoal, forgivenDates, today),
    )
}

// ---------------------------------------------------------------------
// Item 3 — Daily Review: resumo do dia para a notificação.
// ---------------------------------------------------------------------

data class DailyReviewSummary(
    val dueToday: Int,
    val overdue: Int,
    val doneYesterday: Int,
)

/** Resumo exibido na notificação diária do Daily Review. */
fun dailyReviewSummary(tasks: List<TaskItem>, today: LocalDate): DailyReviewSummary {
    val todayYmd = today.toYmd()
    val yesterdayYmd = today.minus(DatePeriod(days = 1)).toYmd()
    val counts = completionCountsByDay(tasks)
    return DailyReviewSummary(
        dueToday = tasks.count { !it.done && it.dueDate == todayYmd },
        overdue = tasks.count {
            !it.done && it.dueDate != null && it.dueDate < todayYmd
        },
        doneYesterday = counts[yesterdayYmd] ?: 0,
    )
}

/** Título e corpo da notificação do Daily Review. */
fun dailyReviewText(summary: DailyReviewSummary): Pair<String, String> {
    val title = "Seu dia no Constância"
    val parts = mutableListOf<String>()
    parts.add(
        when (summary.dueToday) {
            0 -> "Nenhuma tarefa para hoje"
            1 -> "1 tarefa para hoje"
            else -> "${summary.dueToday} tarefas para hoje"
        },
    )
    if (summary.overdue > 0) {
        parts.add(
            if (summary.overdue == 1) "1 atrasada pedindo atenção"
            else "${summary.overdue} atrasadas pedindo atenção",
        )
    }
    if (summary.doneYesterday > 0) {
        parts.add(
            if (summary.doneYesterday == 1) "ontem você concluiu 1"
            else "ontem você concluiu ${summary.doneYesterday}",
        )
    }
    return title to parts.joinToString(" · ")
}
