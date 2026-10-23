package app.constancia.domain

import app.constancia.model.HabitCycle
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Item 2: ciclos/temporadas do hábito.
 * Um ciclo é uma janela [startDate, endDate] com meta de dias feitos
 * (targetDays). Bater a meta conclui o ciclo — que fica permanente no
 * histórico — e o app oferece o próximo. Falhar um dia dentro do ciclo
 * NÃO apaga ciclos já concluídos: a celebração é por temporada, não
 * só pelo streak infinito.
 */
enum class CycleState {
    /** Dentro da janela, meta ainda não batida. */
    ACTIVE,

    /** Meta batida (completedAt preenchido ou progresso >= meta). */
    COMPLETED,

    /** Janela acabou sem bater a meta. */
    EXPIRED,
}

/** Dias feitos dentro da janela do ciclo. */
fun cycleDoneDays(cycle: HabitCycle, doneDates: Set<String>): Int {
    val start = cycle.startDate.toLocalDate()
    val end = cycle.endDate.toLocalDate()
    return doneDates.count { ymd ->
        val d = ymd.toLocalDate()
        d >= start && d <= end
    }
}

/** A meta do ciclo foi batida com estes dias feitos? */
fun isCycleComplete(cycle: HabitCycle, doneDates: Set<String>): Boolean =
    cycle.completedAt != null || cycleDoneDays(cycle, doneDates) >= cycle.targetDays

fun cycleState(cycle: HabitCycle, doneDates: Set<String>, todayYmd: String): CycleState =
    when {
        isCycleComplete(cycle, doneDates) -> CycleState.COMPLETED
        todayYmd.toLocalDate() > cycle.endDate.toLocalDate() -> CycleState.EXPIRED
        else -> CycleState.ACTIVE
    }

/**
 * Sequência (streak) DENTRO do ciclo: dias consecutivos feitos contando
 * de hoje (ou ontem) para trás, sem sair da janela e perdoando os dias
 * de [forgivenDates] (pausas e pulos). É a sequência (streak) dentro do ciclo.
 */
fun cycleStreak(
    cycle: HabitCycle,
    doneDates: Set<String>,
    forgivenDates: Set<String>,
    todayYmd: String,
): Int {
    val start = cycle.startDate.toLocalDate()
    val end = cycle.endDate.toLocalDate()
    var cursor = minOf(todayYmd.toLocalDate(), end)
    if (cursor < start) return 0
    // Se o dia de referência ainda não foi feito, a sequência pode
    // continuar do dia anterior (mesma regra do streak geral).
    if (!doneDates.contains(cursor.toString())) {
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    var streak = 0
    while (cursor >= start) {
        val key = cursor.toString()
        if (doneDates.contains(key)) {
            streak++
        } else if (!forgivenDates.contains(key)) {
            break
        }
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return streak
}

/** Janela do próximo ciclo: começa em [startYmd] e dura [targetDays] dias. */
fun nextCycleWindow(startYmd: String, targetDays: Int): Pair<String, String> {
    val start = startYmd.toLocalDate()
    val end = start.plus(DatePeriod(days = (targetDays - 1).coerceAtLeast(0)))
    return start.toYmd() to end.toYmd()
}
