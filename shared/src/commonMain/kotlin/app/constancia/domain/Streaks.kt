package app.constancia.domain

import app.constancia.model.Checkin
import app.constancia.model.CheckinStatus
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.PauseInterval
import app.constancia.model.StreakInfo
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
 * - DESCANSO PLANEJADO (R13.1, Fase D): dias da semana em
 *   [ignoredWeekdays] (ISO 1=seg..7=dom) funcionam como perdoados
 *   permanentes na caminhada diária — o fim de semana fora do hábito
 *   não quebra nem conta. Não se aplica ao WEEKLY_X (meta semanal).
 */
fun currentStreak(
    habit: Habit,
    doneDates: Set<String>, // yyyy-MM-dd em que o dia contou como feito
    todayYmd: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    forgivenDates: Set<String> = emptySet(), // yyyy-MM-dd perdoados (pausas)
    ignoredWeekdays: Set<Int> = emptySet(), // ISO 1..7 perdoados (R13.1)
): Int {
    if (doneDates.isEmpty() && forgivenDates.isEmpty()) return 0
    return when (habit.frequencyType) {
        FrequencyType.WEEKLY_X -> weeklyStreakRun(habit, doneDates, forgivenDates, todayYmd).count
        FrequencyType.DAILY, FrequencyType.DAYS_OF_WEEK ->
            dailyStreakRun(doneDates, forgivenDates, ignoredWeekdays, todayYmd).count
    }
}

/** O dia é "perdoado"? Está numa pausa/skip ou é dia de descanso (R13.1). */
private fun isForgivenDay(
    date: LocalDate,
    forgivenDates: Set<String>,
    ignoredWeekdays: Set<Int>,
): Boolean =
    forgivenDates.contains(date.toString()) ||
        date.isoDayNumberCompat() in ignoredWeekdays

/**
 * Caminhada diária da sequência atual: quanto tem e os dias extremos
 * CONTADOS (start = o mais antigo, end = o mais recente). Dias
 * perdoados são atravessados sem contar e sem quebrar.
 */
private fun dailyStreakRun(
    doneDates: Set<String>,
    forgivenDates: Set<String>,
    ignoredWeekdays: Set<Int>,
    todayYmd: String,
): StreakInfo {
    var cursor = todayYmd.toLocalDate()
    // Se hoje ainda não foi feito, a sequência pode continuar de ontem.
    if (!doneDates.contains(cursor.toString())) {
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    var count = 0
    var start: LocalDate? = null
    var end: LocalDate? = null
    var guard = 0
    while (guard++ < 3700) {
        val key = cursor.toString()
        if (doneDates.contains(key)) {
            count++
            if (end == null) end = cursor
            start = cursor
        } else if (!isForgivenDay(cursor, forgivenDates, ignoredWeekdays)) {
            break
        }
        // Dia perdoado (pausa/descanso): pula sem contar e sem quebrar.
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return StreakInfo(count = count, start = start, end = end, best = 0)
}

/**
 * Caminhada semanal da sequência atual (WEEKLY_X): start/end são a
 * segunda-feira/o domingo das semanas extremas da corrida contada.
 */
private fun weeklyStreakRun(
    habit: Habit,
    doneDates: Set<String>,
    forgivenDates: Set<String>,
    todayYmd: String,
): StreakInfo {
    val today = todayYmd.toLocalDate()
    // Segunda-feira da semana atual.
    val isoDow = today.isoDayNumberCompat() // 1=seg .. 7=dom
    val thisWeekStart = today.minus(DatePeriod(days = isoDow - 1))
    var weekStart = thisWeekStart
    var count = 0
    var start: LocalDate? = null
    var end: LocalDate? = null
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
            count++
            if (end == null) end = weekEnd
            start = weekStart
            weekStart = weekStart.minus(DatePeriod(days = 7))
        } else if (isCurrentWeek && doneThisWeek > 0) {
            // Semana atual ainda em andamento: não quebra nem conta —
            // segue avaliando as semanas anteriores.
            weekStart = weekStart.minus(DatePeriod(days = 7))
        } else {
            break
        }
    }
    return StreakInfo(count = count, start = start, end = end, best = 0)
}

/**
 * R13.2 (Fase D): a sequência do hábito como OBJETO — a corrida atual
 * (count/start/end, pelas mesmas regras de [currentStreak]) + o recorde
 * histórico ([bestStreak]). O descanso planejado entra pelo próprio
 * hábito ([Habit.ignoreWeekdays]), salvo se outro conjunto for passado.
 */
fun habitStreakInfo(
    habit: Habit,
    doneDates: Set<String>,
    forgivenDates: Set<String> = emptySet(),
    todayYmd: String,
    ignoredWeekdays: Set<Int> = habit.ignoreWeekdays,
): StreakInfo {
    if (doneDates.isEmpty() && forgivenDates.isEmpty()) {
        return StreakInfo(0, null, null, 0)
    }
    val run = when (habit.frequencyType) {
        FrequencyType.WEEKLY_X ->
            weeklyStreakRun(habit, doneDates, forgivenDates, todayYmd)
        FrequencyType.DAILY, FrequencyType.DAYS_OF_WEEK ->
            dailyStreakRun(doneDates, forgivenDates, ignoredWeekdays, todayYmd)
    }
    return run.copy(
        best = bestStreak(habit, doneDates, forgivenDates, ignoredWeekdays),
    )
}

/** Legenda curta da sequência para os cartões: "desde 3 de out · recorde 21". */
fun streakCaption(info: StreakInfo): String? {
    if (info.count <= 0) return null
    val start = info.start ?: return null
    val base = "desde " + formatDay(start)
    return if (info.best > info.count) {
        base + " · recorde " + info.best.toString()
    } else {
        base
    }
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
 * Pares (data, quantidade) só dos check-ins DONE. O status é a fonte
 * da verdade do "feito": um registro SKIPPED nunca conta como feito,
 * qualquer que seja a quantidade gravada.
 */
fun donePairs(checkins: List<Checkin>): List<Pair<String, Double>> =
    checkins.filter { it.status == CheckinStatus.DONE }
        .map { it.date to it.quantity }

/**
 * TickTick item 4: dias PULADOS (status SKIPPED) de um hábito. Pular é
 * um registro, não uma ausência: o dia entra no conjunto de dias
 * "perdoados" do streak (não conta como feito, mas também não quebra
 * a sequência) — diferente de simplesmente não ter check-in.
 */
fun skippedDatesFor(checkins: List<Checkin>): Set<String> =
    checkins.filter { it.status == CheckinStatus.SKIPPED }
        .map { it.date }.toSet()

/** O dia está feito? (quantidade bate a meta do hábito). */
fun isGoalMet(habit: Habit, quantity: Double): Boolean = when (habit.goalType) {
    GoalType.BINARY -> quantity >= 1.0
    GoalType.QUANTITY -> quantity >= habit.targetQuantity
}

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
    ignoredWeekdays: Set<Int> = emptySet(), // ISO 1..7 perdoados (R13.1)
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
                if (gap == 1) {
                    run++
                } else if (gap > 1) {
                    // O buraco só não quebra se TODOS os dias dele forem
                    // perdoados (pausa/skip ou dia de descanso — R13.1).
                    var d = prev.plus(DatePeriod(days = 1))
                    var allForgiven = true
                    while (d < cur) {
                        if (!isForgivenDay(d, forgivenDates, ignoredWeekdays)) {
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
