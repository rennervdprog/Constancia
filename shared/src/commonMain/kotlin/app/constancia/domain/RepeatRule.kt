package app.constancia.domain

import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Item 7 — recorrência estruturada (substitui a string solta como fonte
 * da verdade; a string `repeatRule` segue existindo como RÓTULO legível).
 *
 * Modelo (espelha o "Custom repeat" do Todoist):
 * - frequência: diária, semanal, mensal ou anual;
 * - intervalo: a cada N (dias/semanas/meses/anos);
 * - dias da semana (semanal): conjunto ISO 1=seg..7=dom; vazio = mesmo
 *   dia da semana do prazo;
 * - mensal por dia do mês (monthDay) ou por semana do mês
 *   ("2ª segunda-feira": monthWeek 1..4, ou -1 = última, + monthWeekday);
 * - anual guarda também o mês (month);
 * - base: a partir da data marcada (DUE_DATE) ou da conclusão
 *   (COMPLETION — concluir atrasado empurra a série);
 * - término: nunca ou em uma data (endsOn).
 *
 * Codificação persistida em task_item.repeat_spec:
 *   v1|FREQ|intervalo|diasCsv|monthDay|monthWeek|monthWeekday|month|BASE|endsOn
 * Campos vazios viram "-". Ex.: "v1|WEEKLY|1|1,2,3,4,5|-|-|-|-|DUE_DATE|-".
 */
enum class RepeatFreq { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class RepeatBase { DUE_DATE, COMPLETION }

data class RepeatRule(
    val freq: RepeatFreq,
    val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(),
    val monthDay: Int? = null,
    val monthWeek: Int? = null,
    val monthWeekday: Int? = null,
    val month: Int? = null,
    val base: RepeatBase = RepeatBase.DUE_DATE,
    val endsOn: LocalDate? = null,
) {
    init {
        require(interval >= 1) { "intervalo deve ser >= 1" }
    }
}

/** Codifica a regra para persistência (formato v1 acima). */
fun encodeRepeatSpec(rule: RepeatRule): String {
    fun opt(value: Int?): String = value?.toString() ?: "-"
    val days = if (rule.weekdays.isEmpty()) "-"
    else rule.weekdays.sorted().joinToString(",")
    return listOf(
        "v1",
        rule.freq.name,
        rule.interval.toString(),
        days,
        opt(rule.monthDay),
        opt(rule.monthWeek),
        opt(rule.monthWeekday),
        opt(rule.month),
        rule.base.name,
        rule.endsOn?.toYmd() ?: "-",
    ).joinToString("|")
}

/** Decodifica a regra persistida; null se vazia ou inválida. */
fun parseRepeatSpec(spec: String): RepeatRule? {
    if (spec.isBlank()) return null
    val parts = spec.split("|")
    if (parts.size != 10 || parts[0] != "v1") return null
    val freq = RepeatFreq.entries.firstOrNull { it.name == parts[1] } ?: return null
    val interval = parts[2].toIntOrNull()?.takeIf { it >= 1 } ?: return null
    val weekdays = if (parts[3] == "-") emptySet()
    else parts[3].split(",").mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 1..7 }.toSet()
    fun opt(index: Int): Int? =
        if (parts[index] == "-") null else parts[index].toIntOrNull()
    val base = RepeatBase.entries.firstOrNull { it.name == parts[8] }
        ?: RepeatBase.DUE_DATE
    val endsOn = if (parts[9] == "-") null
    else runCatching { parts[9].toLocalDate() }.getOrNull()
    return RepeatRule(
        freq = freq,
        interval = interval,
        weekdays = weekdays,
        monthDay = opt(4),
        monthWeek = opt(5),
        monthWeekday = opt(6),
        month = opt(7),
        base = base,
        endsOn = endsOn,
    )
}

private val ISO_DOW_SHORT = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

/** Rótulo legível da regra (o que aparece no lugar do repeatRule). */
fun repeatLabel(rule: RepeatRule): String = when (rule.freq) {
    RepeatFreq.DAILY ->
        if (rule.interval == 1) "Todo dia" else "A cada ${rule.interval} dias"

    RepeatFreq.WEEKLY -> when {
        rule.weekdays == setOf(1, 2, 3, 4, 5) && rule.interval == 1 ->
            REPEAT_WEEKDAYS
        rule.weekdays.isEmpty() && rule.interval == 1 -> REPEAT_WEEKLY
        rule.weekdays.isEmpty() && rule.interval == 2 -> REPEAT_BIWEEKLY
        rule.weekdays.isEmpty() -> "A cada ${rule.interval} semanas"
        else -> {
            val days = rule.weekdays.sorted()
                .joinToString(", ") { ISO_DOW_SHORT[it - 1] }
            if (rule.interval == 1) "Toda $days"
            else "A cada ${rule.interval} sem · $days"
        }
    }

    RepeatFreq.MONTHLY -> when {
        rule.monthWeek != null && rule.monthWeekday != null -> {
            val name = WEEKDAYS_ISO_PT[rule.monthWeekday - 1]
                .substringBefore("-feira")
            val feminine = rule.monthWeekday in 1..5
            val ordinal = when (rule.monthWeek) {
                -1 -> if (feminine) "última" else "último"
                else -> rule.monthWeek.toString() +
                    if (feminine) "ª" else "º"
            }
            if (rule.interval == 1) {
                (if (feminine) "Toda " else "Todo ") +
                    ordinal + " " + name + " do mês"
            } else {
                "A cada ${rule.interval} meses · $ordinal $name"
            }
        }
        rule.monthDay != null ->
            if (rule.interval == 1) "Todo dia ${rule.monthDay} do mês"
            else "A cada ${rule.interval} meses · dia ${rule.monthDay}"
        else ->
            if (rule.interval == 1) REPEAT_MONTHLY
            else "A cada ${rule.interval} meses"
    }

    RepeatFreq.YEARLY ->
        if (rule.interval == 1) REPEAT_YEARLY
        else "A cada ${rule.interval} anos"
}

/** Converte um rótulo legado (pré-v4) na regra estruturada equivalente. */
fun legacyRepeatToSpec(repeatRule: String, dueDate: LocalDate): RepeatRule? =
    when (repeatRule) {
        REPEAT_DAILY -> RepeatRule(RepeatFreq.DAILY, 1)
        REPEAT_WEEKDAYS -> RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(1, 2, 3, 4, 5))
        REPEAT_WEEKLY -> RepeatRule(RepeatFreq.WEEKLY, 1)
        REPEAT_BIWEEKLY -> RepeatRule(RepeatFreq.WEEKLY, 2)
        REPEAT_MONTHLY -> RepeatRule(
            RepeatFreq.MONTHLY, 1, monthDay = dueDate.dayOfMonth,
        )
        REPEAT_YEARLY -> RepeatRule(
            RepeatFreq.YEARLY, 1,
            monthDay = dueDate.dayOfMonth, month = dueDate.monthNumber,
        )
        else -> null
    }

/** Regra efetiva de uma tarefa: a estruturada, ou a legada convertida. */
fun effectiveRepeatSpec(
    repeatSpec: String,
    repeatRule: String,
    dueDate: LocalDate,
): RepeatRule? = parseRepeatSpec(repeatSpec)
    ?: legacyRepeatToSpec(repeatRule, dueDate)

/** Segunda-feira da semana de uma data (semana ISO começa na segunda). */
private fun mondayOf(date: LocalDate): LocalDate =
    date.minus(DatePeriod(days = date.isoDayNumberCompat() - 1))

/** Último dia do mês de uma data. */
private fun lastDayOf(date: LocalDate): LocalDate =
    date.plus(DatePeriod(months = 1)).let {
        LocalDate(it.year, it.monthNumber, 1).minus(DatePeriod(days = 1))
    }

/** Dia do mês com clamp (31 em fevereiro vira 28/29). */
private fun clampedDate(year: Int, month: Int, day: Int): LocalDate {
    val first = LocalDate(year, month, 1)
    val last = lastDayOf(first).dayOfMonth
    return LocalDate(year, month, minOf(day, last))
}

/** A data da n-ésima ocorrência de um dia da semana no mês (nth -1 = última). */
fun nthWeekdayOfMonth(
    year: Int,
    month: Int,
    nth: Int,
    isoDow: Int,
): LocalDate {
    if (nth == -1) {
        var d = lastDayOf(LocalDate(year, month, 1))
        while (d.isoDayNumberCompat() != isoDow) {
            d = d.minus(DatePeriod(days = 1))
        }
        return d
    }
    var d = LocalDate(year, month, 1)
    while (d.isoDayNumberCompat() != isoDow) {
        d = d.plus(DatePeriod(days = 1))
    }
    return d.plus(DatePeriod(days = (nth - 1) * 7))
}

/** Um passo da recorrência: a próxima ocorrência ESTRITAMENTE após [from]. */
private fun stepOnce(rule: RepeatRule, from: LocalDate): LocalDate =
    when (rule.freq) {
        RepeatFreq.DAILY -> from.plus(DatePeriod(days = rule.interval))

        RepeatFreq.WEEKLY -> {
            if (rule.weekdays.isEmpty()) {
                from.plus(DatePeriod(days = 7 * rule.interval))
            } else {
                val anchorMonday = mondayOf(from)
                var d = from.plus(DatePeriod(days = 1))
                var guard = 0
                while (guard++ < 400) {
                    val weekOffset =
                        (mondayOf(d).toEpochDays() - anchorMonday.toEpochDays()) / 7
                    if (d.isoDayNumberCompat() in rule.weekdays &&
                        weekOffset % rule.interval.toLong() == 0L
                    ) {
                        break
                    }
                    d = d.plus(DatePeriod(days = 1))
                }
                d
            }
        }

        RepeatFreq.MONTHLY -> {
            var cursor = LocalDate(from.year, from.monthNumber, 1)
            var guard = 0
            var candidate: LocalDate
            do {
                candidate = if (rule.monthWeek != null && rule.monthWeekday != null) {
                    nthWeekdayOfMonth(
                        cursor.year, cursor.monthNumber,
                        rule.monthWeek, rule.monthWeekday,
                    )
                } else {
                    clampedDate(
                        cursor.year, cursor.monthNumber,
                        rule.monthDay ?: from.dayOfMonth,
                    )
                }
                if (candidate > from) break
                cursor = cursor.plus(DatePeriod(months = rule.interval))
            } while (guard++ < 240)
            candidate
        }

        RepeatFreq.YEARLY -> {
            val month = rule.month ?: from.monthNumber
            val day = rule.monthDay ?: from.dayOfMonth
            var year = from.year
            var candidate = clampedDate(year, month, day)
            var guard = 0
            while (candidate <= from && guard++ < 200) {
                year += rule.interval
                candidate = clampedDate(year, month, day)
            }
            candidate
        }
    }

/**
 * Próxima ocorrência da série. Com base na CONCLUSÃO, a âncora é a
 * conclusão (concluir atrasado empurra a série); com base no PRAZO, é o
 * prazo atual. Retorna null se a próxima cair depois do término.
 *
 * R4.1 (Fase A do Plano de Elevação): avança em LAÇO até a primeira
 * data ESTRITAMENTE depois de max(âncora, conclusão) — concluir uma
 * diária muitos dias atrasada NÃO pode gerar ocorrência já vencida
 * (antes era um passo só a partir da âncora). Guarda de 1000 iterações.
 */
fun nextOccurrence(
    rule: RepeatRule,
    dueDate: LocalDate,
    completedOn: LocalDate,
): LocalDate? {
    val anchor = if (rule.base == RepeatBase.COMPLETION) {
        maxOf(dueDate, completedOn)
    } else {
        dueDate
    }
    val threshold = maxOf(anchor, completedOn)
    var next = stepOnce(rule, anchor)
    var guard = 0
    while (next <= threshold && guard++ < 1000) {
        next = stepOnce(rule, next)
    }
    val ends = rule.endsOn
    return if (ends != null && next > ends) null else next
}
