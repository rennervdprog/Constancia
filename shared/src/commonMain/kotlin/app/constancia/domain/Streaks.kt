package app.constancia.domain

import app.constancia.model.FrequencyType
import app.constancia.model.Habit
import app.constancia.model.toLocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Cálculo de sequências (streaks). Regras:
 * - DAILY / DAYS_OF_WEEK: dias consecutivos com check-in, contando de hoje
 *   para trás (se hoje ainda não foi feito, começa de ontem).
 * - WEEKLY_X: semanas consecutivas batendo a meta semanal.
 * - QUANTITY: o dia só conta se quantity >= targetQuantity.
 */
fun currentStreak(
    habit: Habit,
    doneDates: Set<String>, // yyyy-MM-dd em que o dia contou como feito
    todayYmd: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Int {
    if (doneDates.isEmpty()) return 0
    return when (habit.frequencyType) {
        FrequencyType.WEEKLY_X -> weeklyStreak(habit, doneDates, todayYmd, timeZone)
        FrequencyType.DAILY, FrequencyType.DAYS_OF_WEEK ->
            dailyStreak(doneDates, todayYmd, timeZone)
    }
}

private fun dailyStreak(
    doneDates: Set<String>,
    todayYmd: String,
    timeZone: TimeZone,
): Int {
    var cursor = todayYmd.toLocalDate()
    // Se hoje ainda não foi feito, a sequência pode continuar de ontem.
    if (!doneDates.contains(cursor.toString())) {
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    var streak = 0
    while (doneDates.contains(cursor.toString())) {
        streak++
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return streak
}

private fun weeklyStreak(
    habit: Habit,
    doneDates: Set<String>,
    todayYmd: String,
    timeZone: TimeZone,
): Int {
    val today = todayYmd.toLocalDate()
    // Segunda-feira da semana atual.
    val isoDow = today.dayOfWeek.isoDayNumber // 1=seg .. 7=dom
    var weekStart = today.minus(DatePeriod(days = isoDow - 1))
    var streak = 0
    while (true) {
        val weekEnd = weekStart.plus(DatePeriod(days = 6))
        val doneThisWeek = doneDates.count { d ->
            val date = d.toLocalDate()
            date >= weekStart && date <= weekEnd
        }
        val isCurrentWeek = weekStart == today.minus(DatePeriod(days = isoDow - 1))
        if (doneThisWeek >= habit.weeklyTarget) {
            streak++
        } else if (isCurrentWeek && doneThisWeek > 0) {
            // Semana atual ainda em andamento: não quebra, mas não conta.
            break
        } else {
            break
        }
        weekStart = weekStart.minus(DatePeriod(days = 7))
        if (streak > 520) break // trava de segurança (~10 anos)
    }
    return streak
}

/** Dias "feitos" de um hábito a partir dos check-ins (respeita meta de quantidade). */
fun doneDatesFor(habit: Habit, checkins: List<Pair<String, Double>>): Set<String> =
    checkins.filter { (_, qty) ->
        when (habit.goalType) {
            app.constancia.model.GoalType.BINARY -> qty >= 1.0
            app.constancia.model.GoalType.QUANTITY -> qty >= habit.targetQuantity
        }
    }.map { (date, _) -> date }.toSet()

/** Quantos check-ins o hábito tem na semana atual (para WEEKLY_X). */
fun weekDoneCount(
    doneDates: Set<String>,
    todayYmd: String,
): Int {
    val today = todayYmd.toLocalDate()
    val isoDow = today.dayOfWeek.isoDayNumber
    val weekStart = today.minus(DatePeriod(days = isoDow - 1))
    val weekEnd = weekStart.plus(DatePeriod(days = 6))
    return doneDates.count { d ->
        val date = d.toLocalDate()
        date >= weekStart && date <= weekEnd
    }
}
