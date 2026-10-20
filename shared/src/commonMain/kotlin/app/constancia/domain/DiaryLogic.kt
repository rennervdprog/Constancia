package app.constancia.domain

import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.Habit
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Lógica pura do diário turbinado (100% testável, sem Compose):
 * perguntas do dia, sequência de escrita, etiquetas, calendário de humor
 * e o cartão Padrões (diário × hábitos, calculado dos dados reais).
 */

val DIARY_TAGS = listOf(
    "Trabalho", "Família", "Treino", "Estudo",
    "Praia", "Estresse", "Descanso", "Saúde",
)

val DIARY_PROMPTS = listOf(
    "Qual foi o melhor momento de hoje, por menor que seja?",
    "O que você fez hoje que o você de um ano atrás agradeceria?",
    "O que pesou hoje — e o que ajudou a carregar?",
    "Se hoje tivesse um título de capítulo, qual seria?",
    "Do que você quer mais amanhã? E do que quer menos?",
)

val MOOD_LABELS = listOf("Péssimo", "Ruim", "Ok", "Bom", "Ótimo")

/** Cores do humor 1..5 (iguais ao protótipo). */
val MOOD_COLORS_HEX = listOf(0xFFC2402A, 0xFFE86A17, 0xFFB98A0C, 0xFF4C9A6C, 0xFF0B7A55)

val ENERGY_LABELS = listOf("", "Esgotado", "Baixa", "Normal", "Boa", "A mil")

/** Índice da pergunta do dia: gira pelo dia do mês (igual ao protótipo). */
fun promptIndexFor(date: LocalDate): Int = date.dayOfMonth % DIARY_PROMPTS.size

fun promptFor(date: LocalDate): String = DIARY_PROMPTS[promptIndexFor(date)]

fun nextPromptIndex(current: Int): Int = (current + 1) % DIARY_PROMPTS.size

/**
 * Sequência de dias seguidos escrevendo no diário. Se hoje ainda não tem
 * registro, a sequência pode vir de ontem (igual ao protótipo).
 */
fun diaryStreak(entryDates: Set<String>, today: LocalDate): Int {
    var cursor = if (entryDates.contains(today.toYmd())) {
        today
    } else {
        today.minus(DatePeriod(days = 1))
    }
    var streak = 0
    var guard = 0
    while (guard++ < 3700 && entryDates.contains(cursor.toYmd())) {
        streak++
        cursor = cursor.minus(DatePeriod(days = 1))
    }
    return streak
}

/** Rótulo de um dia no histórico/detalhe: "Hoje", "Ontem" ou "Segunda, 12 de out". */
fun diaryDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.minus(DatePeriod(days = 1)) -> "Ontem"
    else -> {
        val wd = DOW_FULL[appDayIndex(date)]
        val capitalized = wd.replaceFirstChar { it.uppercase() }
        "$capitalized, ${date.dayOfMonth} de ${MONTHS[date.monthNumber - 1]}"
    }
}

private fun appDayIndex(date: LocalDate): Int {
    // DOW_FULL é indexado 0=domingo..6=sábado (convenção do app).
    val iso = date.isoDayNumberCompat()
    return if (iso == 7) 0 else iso
}

/** Busca nos registros: casa com o texto, as etiquetas e o rótulo do dia. */
fun diaryMatchesQuery(entry: DiaryEntry, query: String, today: LocalDate): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val hay = (
        entry.note + " " + entry.tags.joinToString(" ") + " " +
            diaryDayLabel(entry.date.toLocalDate(), today)
        ).lowercase()
    return hay.contains(q)
}

// ---------------------------------------------------------------------------
// Cartão Padrões: humor com/sem o hábito mais presente nos dias registrados
// ---------------------------------------------------------------------------

data class DiaryPattern(
    val habit: Habit,
    val avgWith: Double,
    val avgWithout: Double,
    val countWith: Int,
    val countWithout: Int,
    val totalEntries: Int,
) {
    /** Título do cartão (textos do protótipo, com o nome do hábito no lugar de "treino"). */
    val headline: String
        get() {
            val diff = avgWith - avgWithout
            val name = habit.name.lowercase()
            return when {
                diff > 0.3 -> "Seu humor sobe nos dias de $name"
                diff < -0.3 -> "Seu humor cai nos dias de $name"
                else -> "${habit.name} e humor andam juntos por aqui"
            }
        }
}

/**
 * Escolhe o hábito com mais dias feitos entre os dias que têm registro de
 * humor e compara a média de humor com/sem ele. Só retorna resultado com
 * pelo menos 2 registros em cada grupo (regra do protótipo).
 */
fun computeDiaryPattern(
    entries: List<DiaryEntry>,
    habits: List<Habit>,
    checkins: List<Checkin>,
): DiaryPattern? {
    val moodEntries = entries.filter { it.mood != null }
    if (moodEntries.size < 4 || habits.isEmpty()) return null
    var best: DiaryPattern? = null
    var bestScore = -1
    for (habit in habits) {
        val doneDates = doneDatesFor(habit, checkins.filter { it.habitId == habit.id }
            .map { it.date to it.quantity })
        val with = moodEntries.filter { doneDates.contains(it.date) }
        val without = moodEntries.filter { !doneDates.contains(it.date) }
        if (with.size < 2 || without.size < 2) continue
        // Pontuação: prioriza quem tem mais dias "com" e grupos equilibrados.
        val score = with.size * 10 + minOf(with.size, without.size)
        if (score > bestScore) {
            bestScore = score
            best = DiaryPattern(
                habit = habit,
                avgWith = with.map { it.mood ?: 0 }.average(),
                avgWithout = without.map { it.mood ?: 0 }.average(),
                countWith = with.size,
                countWithout = without.size,
                totalEntries = moodEntries.size,
            )
        }
    }
    return best
}

// ---------------------------------------------------------------------------
// Semana (cartão "Sua semana" da tela Hoje) e mapa de calor (aba Hábitos)
// ---------------------------------------------------------------------------

/**
 * Fração de conclusão de cada dia da semana atual (segunda..domingo):
 * hábitos devidos feitos / hábitos devidos. -1.0 = sem hábitos devidos
 * naquele dia ou dia futuro.
 */
fun weekFractions(
    habits: List<Habit>,
    checkins: List<Checkin>,
    today: LocalDate,
    timeZone: kotlinx.datetime.TimeZone = kotlinx.datetime.TimeZone.currentSystemDefault(),
): List<Double> {
    val monday = weekStartOf(today)
    val checkinsByHabit = checkins.groupBy { it.habitId }
    val doneByHabit = habits.associate { h ->
        h.id to doneDatesFor(h, checkinsByHabit[h.id].orEmpty().map { it.date to it.quantity })
    }
    val activeFromByHabit = habits.associate { h ->
        h.id to runCatching { h.createdAt.take(10).toLocalDate() }.getOrNull()
    }
    return (0..6).map { offset ->
        val day = monday.plus(DatePeriod(days = offset))
        if (day > today) {
            -1.0
        } else {
            val due = habits.filter { isDueOn(it, day, activeFromByHabit[it.id]) }
            if (due.isEmpty()) {
                -1.0
            } else {
                val done = due.count { doneByHabit[it.habit.id].orEmpty().contains(day.toYmd()) }
                done.toDouble() / due.size
            }
        }
    }
}

enum class HeatCell { FUTURE, OFF, DONE, MISSED }

/** Domingo (início da semana na grade do mapa de calor) da semana de [date]. */
fun heatGridStart(today: LocalDate, weeks: Int = 18): LocalDate {
    // isoDayNumber: 1=seg..7=dom. Domingo da semana atual = hoje - (iso % 7).
    val daysSinceSunday = today.isoDayNumberCompat() % 7
    return today.minus(DatePeriod(days = daysSinceSunday + (weeks - 1) * 7))
}

/**
 * Grade do mapa de calor: [weeks] colunas (semanas, começando no domingo),
 * 7 linhas (dom..sáb). Estado de cada dia: futuro, não devido (off),
 * feito ou devido-não-feito.
 */
fun heatGrid(
    habit: Habit,
    checkins: List<Checkin>,
    today: LocalDate,
    weeks: Int = 18,
): List<List<HeatCell>> {
    val start = heatGridStart(today, weeks)
    val doneDates = doneDatesFor(habit, checkins.map { it.date to it.quantity })
    val activeFrom = runCatching { habit.createdAt.take(10).toLocalDate() }.getOrNull()
    return (0 until weeks).map { col ->
        (0..6).map { row ->
            val day = start.plus(DatePeriod(days = col * 7 + row))
            when {
                day > today -> HeatCell.FUTURE
                !isDueOn(habit, day, activeFrom) -> HeatCell.OFF
                doneDates.contains(day.toYmd()) -> HeatCell.DONE
                else -> HeatCell.MISSED
            }
        }
    }
}

/** Taxa de conclusão dos últimos 30 dias (0..1) sobre todos os hábitos. */
fun completionRateLast30(
    habits: List<Habit>,
    checkins: List<Checkin>,
    today: LocalDate,
): Double {
    val from = today.minus(DatePeriod(days = 29))
    val checkinsByHabit = checkins.groupBy { it.habitId }
    val doneByHabit = habits.associate { h ->
        h.id to doneDatesFor(h, checkinsByHabit[h.id].orEmpty().map { it.date to it.quantity })
    }
    val activeFromByHabit = habits.associate { h ->
        h.id to runCatching { h.createdAt.take(10).toLocalDate() }.getOrNull()
    }
    var due = 0
    var done = 0
    var day = from
    while (day <= today) {
        val ymd = day.toYmd()
        habits.forEach { h ->
            if (isDueOn(h, day, activeFromByHabit[h.id])) {
                due++
                if (doneByHabit[h.id].orEmpty().contains(ymd)) done++
            }
        }
        day = day.plus(DatePeriod(days = 1))
    }
    return if (due == 0) 0.0 else done.toDouble() / due
}
