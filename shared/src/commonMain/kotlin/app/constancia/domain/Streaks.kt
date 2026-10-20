package app.constancia.domain

import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.PauseInterval
import app.constancia.model.toLocalDate
import app.constancia.model.isoDayNumberCompat
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Cálculo de sequências (streaks). Regras:
 * - DAILY / DAYS_OF_WEEK: dias consecutivos com check-in, contando de hoje
 *   para trás (se hoje ainda não foi feito, começa de ontem).
 * - WEEKLY_X: semanas consecutivas batendo a meta semanal. A semana atual,
 *   se em andamento (algum dia feito, meta ainda não batida), não quebra
 *   nem conta — as semanas anteriores seguem valendo.
 * - QUANTITY: o dia só conta se quantity >= targetQuantity.
 * - PAUSA: dias dentro de um intervalo de pausa são "perdoados" — não contam
 *   como feitos, mas também não quebram a sequência (streak congelado).
 */
fun currentStreak(
    habit: Habit,
    doneDates: Set<String>, // yyyy-MM-dd em que o dia contou como feito
    todayYmd: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    forgivenDates: Set<String> = emptySet(), // yyyy-MM-dd perdoados (pausas)
): Int {
    if (doneDates.isEmpty() && forgivenDates.isEmpty()) return 0
    return when (habit.frequencyType) {
        FrequencyType.WEEKLY_X -> weeklyStreak(habit, doneDates, forgivenDates, todayYmd)
        FrequencyType.DAILY, FrequencyType.DAYS_OF_WEEK ->
            dailyStreak(doneDates, forgivenDates, todayYmd)
    }
}

private fun dailyStreak(
    doneDates: Set<String>,
    forgivenDates: Set<String>,
    todayYmd: String,
): Int {
    var cursor = todayYmd.toLocalDate()
    // Se hoje ainda não foi feito, a sequência pode continuar de ontem.
    if (!doneDates.contains(cursor.toString())) {
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    var streak = 0
    var guard = 0
    while (guard++ < 3700) {
        val key = cursor.toString()
        if (doneDates.contains(key)) {
            streak++
        } else if (!forgivenDates.contains(key)) {
            break
        }
        // Dia perdoado (pausa): pula sem contar e sem quebrar.
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return streak
}

private fun weeklyStreak(
    habit: Habit,
    doneDates: Set<String>,
    forgivenDates: Set<String>,
    todayYmd: String,
): Int {
    val today = todayYmd.toLocalDate()
    // Segunda-feira da semana atual.
    val isoDow = today.isoDayNumberCompat() // 1=seg .. 7=dom
    val thisWeekStart = today.minus(DatePeriod(days = isoDow - 1))
    var weekStart = thisWeekStart
    var streak = 0
    var guard = 0
    while (guard++ < 520) { // trava de segurança (~10 anos)
        val weekEnd = weekStart.plus(DatePeriod(days = 6))
        fun inWeek(ymd: String): Boolean {
            val date = ymd.toLocalDate()
            return date >= weekStart && date <= weekEnd
        }
        // Dias perdoados (pausa) contam como cumpridos para a meta semanal.
        val doneThisWeek = doneDates.count(::inWeek) + forgivenDates.count(::inWeek)
        val isCurrentWeek = weekStart == thisWeekStart
        if (doneThisWeek >= habit.weeklyTarget) {
            streak++
            weekStart = weekStart.minus(DatePeriod(days = 7))
        } else if (isCurrentWeek && doneThisWeek > 0) {
            // Semana atual ainda em andamento: não quebra nem conta —
            // segue avaliando as semanas anteriores.
            weekStart = weekStart.minus(DatePeriod(days = 7))
        } else {
            break
        }
    }
    return streak
}

/** Dias "feitos" de um hábito a partir dos check-ins (respeita meta de quantidade). */
fun doneDatesFor(habit: Habit, checkins: List<Pair<String, Double>>): Set<String> =
    checkins.filter { (_, qty) ->
        when (habit.goalType) {
            GoalType.BINARY -> qty >= 1.0
            GoalType.QUANTITY -> qty >= habit.targetQuantity
        }
    }.map { (date, _) -> date }.toSet()

/**
 * Expande intervalos de pausa em um conjunto de datas "perdoadas" (yyyy-MM-dd).
 * Intervalo aberto (endDate null) vai até hoje.
 */
fun forgivenDates(intervals: List<PauseInterval>, todayYmd: String): Set<String> {
    val today = todayYmd.toLocalDate()
    val out = mutableSetOf<String>()
    for (iv in intervals) {
        var d = iv.startDate.toLocalDate()
        val end = iv.endDate?.toLocalDate() ?: today
        var guard = 0
        while (d <= end && guard++ < 3700) {
            out.add(d.toString())
            d = d.plus(DatePeriod(days = 1))
        }
    }
    return out
}

/** Quantos check-ins o hábito tem na semana atual (para WEEKLY_X). */
fun weekDoneCount(
    doneDates: Set<String>,
    todayYmd: String,
): Int {
    val today = todayYmd.toLocalDate()
    val isoDow = today.isoDayNumberCompat()
    val weekStart = today.minus(DatePeriod(days = isoDow - 1))
    val weekEnd = weekStart.plus(DatePeriod(days = 6))
    return doneDates.count { d ->
        val date = d.toLocalDate()
        date >= weekStart && date <= weekEnd
    }
}

/**
 * Maior sequência histórica (recorde), para os cartões da aba Hábitos.
 * - DAILY / DAYS_OF_WEEK: maior corrida de dias consecutivos feitos
 *   (dias perdoados por pausa não contam, mas também não quebram).
 * - WEEKLY_X: maior corrida de semanas consecutivas batendo a meta.
 */
fun bestStreak(
    habit: Habit,
    doneDates: Set<String>,
    forgivenDates: Set<String> = emptySet(),
): Int {
    if (doneDates.isEmpty()) return 0
    return when (habit.frequencyType) {
        FrequencyType.WEEKLY_X -> bestWeeklyStreak(habit, doneDates, forgivenDates)
        FrequencyType.DAILY, FrequencyType.DAYS_OF_WEEK -> {
            val sorted = doneDates.map { it.toLocalDate() }.sorted()
            var best = 1
            var run = 1
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val cur = sorted[i]
                val gap = cur.toEpochDays() - prev.toEpochDays()
                if (gap == 1L) {
                    run++
                } else if (gap > 1L) {
                    // O buraco só não quebra se TODOS os dias dele forem perdoados.
                    var d = prev.plus(DatePeriod(days = 1))
                    var allForgiven = true
                    while (d < cur) {
                        if (!forgivenDates.contains(d.toString())) {
                            allForgiven = false
                            break
                        }
                        d = d.plus(DatePeriod(days = 1))
                    }
                    run = if (allForgiven) run + 1 else 1
                }
                if (run > best) best = run
            }
            best
        }
    }
}

private fun bestWeeklyStreak(
    habit: Habit,
    doneDates: Set<String>,
    forgivenDates: Set<String>,
): Int {
    val weeks = doneDates.map { weekStartOf(it.toLocalDate()) }.toSet().sorted()
    if (weeks.isEmpty()) return 0
    fun weekOk(ws: LocalDate): Boolean {
        val we = ws.plus(DatePeriod(days = 6))
        val count = (doneDates + forgivenDates).count { ymd ->
            val d = ymd.toLocalDate()
            d >= ws && d <= we
        }
        return count >= habit.weeklyTarget
    }
    var best = 0
    var run = 0
    var cursor = weeks.first()
    val last = weeks.last()
    var guard = 0
    while (cursor <= last && guard++ < 520) {
        if (weekOk(cursor)) {
            run++
            if (run > best) best = run
        } else {
            run = 0
        }
        cursor = cursor.plus(DatePeriod(days = 7))
    }
    return best
}
