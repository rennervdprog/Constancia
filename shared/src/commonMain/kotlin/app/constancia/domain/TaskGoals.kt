package app.constancia.domain

import app.constancia.model.GoalPause
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

/** Expande as pausas da meta em dias "perdoados" (yyyy-MM-dd). */
fun goalForgivenDates(pauses: List<GoalPause>, todayYmd: String): Set<String> {
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
