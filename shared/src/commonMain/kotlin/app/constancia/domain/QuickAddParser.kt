package app.constancia.domain

import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.toYmd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Item 6 — mini-Dateist em português: reconhece no texto da captura
 * rápida data, hora, prioridade (p1..p3), projeto (#Projeto) e
 * recorrência, como o Dateist do Todoist. O parser é regex puro sobre o
 * texto normalizado (minúsculas, sem acentos — índices 1:1 com o
 * original), devolve os trechos reconhecidos (para destacar no campo e
 * permitir desfazer um a um) e o título limpo.
 *
 * Regras:
 * - vale o PRIMEIRO reconhecimento de cada tipo; os demais trechos
 *   iguais ficam como texto comum do título;
 * - em sobreposição, ganha o trecho que começa antes (e o mais longo);
 * - trechos ignorados pelo usuário (desfazer) voltam ao título.
 */
enum class QuickSpanKind { DATE, TIME, PRIORITY, PROJECT, REPEAT }

data class QuickSpan(
    val start: Int,
    val end: Int, // exclusivo
    val kind: QuickSpanKind,
    val text: String, // trecho como digitado
    val label: String, // rótulo do reconhecimento ("Amanhã", "P1", "#Casa"...)
) {
    /** Chave estável do trecho (para o "desfazer" da UI). */
    val key: String get() = "$kind:$start:$end"
}

data class QuickAddParse(
    val title: String,
    val spans: List<QuickSpan>,
    val dueDate: LocalDate? = null,
    val dueTime: String? = null, // "HH:mm"
    val priority: Int? = null,
    val project: TaskProject? = null,
    val repeat: RepeatRule? = null,
)

/** Remove acentos e baixa a caixa, mantendo os índices 1:1. */
private fun normalizePt(text: String): String {
    val sb = StringBuilder(text.length)
    for (c in text) {
        sb.append(
            when (c) {
                'á', 'à', 'â', 'ã', 'ä', 'Á', 'À', 'Â', 'Ã', 'Ä' -> 'a'
                'é', 'è', 'ê', 'ë', 'É', 'È', 'Ê', 'Ë' -> 'e'
                'í', 'ì', 'î', 'Í', 'Ì', 'Î' -> 'i'
                'ó', 'ò', 'ô', 'õ', 'Ó', 'Ò', 'Ô', 'Õ' -> 'o'
                'ú', 'ù', 'û', 'Ú', 'Ù', 'Û' -> 'u'
                'ç', 'Ç' -> 'c'
                'ñ', 'Ñ' -> 'n'
                else -> c.lowercaseChar()
            },
        )
    }
    return sb.toString()
}

private val WEEKDAY_ISO = mapOf(
    "segunda" to 1, "terca" to 2, "quarta" to 3, "quinta" to 4,
    "sexta" to 5, "sabado" to 6, "domingo" to 7,
)

private val MONTH_BY_NAME = mapOf(
    "janeiro" to 1, "jan" to 1, "fevereiro" to 2, "fev" to 2,
    "marco" to 3, "mar" to 3, "abril" to 4, "abr" to 4,
    "maio" to 5, "mai" to 5, "junho" to 6, "jun" to 6,
    "julho" to 7, "jul" to 7, "agosto" to 8, "ago" to 8,
    "setembro" to 9, "set" to 9, "outubro" to 10, "out" to 10,
    "novembro" to 11, "nov" to 11, "dezembro" to 12, "dez" to 12,
)

/** Próxima data com o dia da semana ISO dado (hoje conta, se for ele). */
private fun nextWeekday(today: LocalDate, isoDow: Int, strictFuture: Boolean): LocalDate {
    var delta = (isoDow - today.isoDayNumberCompat() + 7) % 7
    if (delta == 0 && strictFuture) delta = 7
    return today.plus(DatePeriod(days = delta))
}

/** Data com clamp de fim de mês; null se o dia/mês for inválido. */
private fun safeDate(year: Int, month: Int, day: Int): LocalDate? {
    if (month !in 1..12 || day !in 1..31) return null
    val first = runCatching { LocalDate(year, month, 1) }.getOrNull() ?: return null
    val last = first.plus(DatePeriod(months = 1))
        .let { LocalDate(it.year, it.monthNumber, 1) }
    val lastDay = (last.toEpochDays() - first.toEpochDays()).toInt()
    return LocalDate(year, month, minOf(day, lastDay))
}

private data class Candidate(
    val start: Int,
    val end: Int,
    val kind: QuickSpanKind,
    val label: String,
    val date: LocalDate? = null,
    val time: String? = null,
    val priority: Int? = null,
    val project: TaskProject? = null,
    val repeat: RepeatRule? = null,
)

fun parseQuickAdd(
    text: String,
    today: LocalDate,
    ignored: Set<String> = emptySet(),
): QuickAddParse {
    val norm = normalizePt(text)
    val candidates = mutableListOf<Candidate>()

    fun add(candidate: Candidate) {
        val key = "${candidate.kind}:${candidate.start}:${candidate.end}"
        if (key !in ignored) candidates.add(candidate)
    }

    // ---------------- Recorrência (trechos mais longos primeiro) -------
    val repeatPatterns: List<Pair<Regex, (MatchResult) -> RepeatRule?>> = listOf(
        Regex("""\ba cada (\d{1,2}) (dias|semanas|meses|anos)\b""") to { m ->
            val n = m.groupValues[1].toIntOrNull() ?: 1
            when (m.groupValues[2]) {
                "dias" -> RepeatRule(RepeatFreq.DAILY, n)
                "semanas" -> RepeatRule(RepeatFreq.WEEKLY, n)
                "meses" -> RepeatRule(RepeatFreq.MONTHLY, n)
                else -> RepeatRule(RepeatFreq.YEARLY, n)
            }
        },
        Regex("""\ba cada (dia|semana|mes|ano)\b""") to { m ->
            when (m.groupValues[1]) {
                "dia" -> RepeatRule(RepeatFreq.DAILY, 1)
                "semana" -> RepeatRule(RepeatFreq.WEEKLY, 1)
                "mes" -> RepeatRule(RepeatFreq.MONTHLY, 1)
                else -> RepeatRule(RepeatFreq.YEARLY, 1)
            }
        },
        Regex("""\b(seg a sex|segunda a sexta|dias uteis)\b""") to {
            RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(1, 2, 3, 4, 5))
        },
        Regex("""\b(todo dia|todos os dias|diariamente)\b""") to {
            RepeatRule(RepeatFreq.DAILY, 1)
        },
        Regex("""\b(toda semana|todas as semanas|semanalmente)\b""") to {
            RepeatRule(RepeatFreq.WEEKLY, 1)
        },
        Regex("""\b(todo mes|todos os meses|mensalmente)\b""") to {
            RepeatRule(RepeatFreq.MONTHLY, 1)
        },
        Regex("""\b(todo ano|todos os anos|anualmente)\b""") to {
            RepeatRule(RepeatFreq.YEARLY, 1)
        },
        Regex("""\btodas?\s+(as\s+)?(segunda|terca|quarta|quinta|sexta|sabado|domingo)s?\b""") to { m ->
            WEEKDAY_ISO[m.groupValues[2]]?.let { dow ->
                RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(dow))
            }
        },
        Regex("""\btodo (sabado|domingo)\b""") to { m ->
            WEEKDAY_ISO[m.groupValues[1]]?.let { dow ->
                RepeatRule(RepeatFreq.WEEKLY, 1, weekdays = setOf(dow))
            }
        },
    )
    for ((regex, build) in repeatPatterns) {
        for (m in regex.findAll(norm)) {
            val rule = build(m) ?: continue
            add(
                Candidate(
                    m.range.first, m.range.last + 1, QuickSpanKind.REPEAT,
                    label = repeatLabel(rule), repeat = rule,
                ),
            )
        }
    }

    // ---------------- Datas -------------------------------------------
    val datePatterns: List<Pair<Regex, (MatchResult) -> LocalDate?>> = listOf(
        Regex("""\bdepois de amanha\b""") to {
            today.plus(DatePeriod(days = 2))
        },
        Regex("""\bamanha\b""") to { today.plus(DatePeriod(days = 1)) },
        Regex("""\bhoje\b""") to { today },
        Regex("""\bsemana que vem\b""") to { today.plus(DatePeriod(days = 7)) },
        Regex("""\bfim de semana\b""") to {
            rescheduleDate(RescheduleTarget.WEEKEND, today)
        },
        Regex("""\bdia (\d{1,2})\b""") to { m ->
            val day = m.groupValues[1].toIntOrNull()
            if (day == null || day !in 1..31) {
                null
            } else {
                val thisMonth = safeDate(today.year, today.monthNumber, day)
                if (thisMonth != null && thisMonth >= today) {
                    thisMonth
                } else {
                    val next = today.plus(DatePeriod(months = 1))
                    safeDate(next.year, next.monthNumber, day)
                }
            }
        },
        Regex("""\b(\d{1,2}) de (janeiro|fevereiro|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro|jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)\b""") to { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = MONTH_BY_NAME[m.groupValues[2]]
            if (day == null || month == null) {
                null
            } else {
                val thisYear = safeDate(today.year, month, day)
                if (thisYear != null && thisYear >= today) {
                    thisYear
                } else {
                    safeDate(today.year + 1, month, day)
                }
            }
        },
        Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""") to { m ->
            val day = m.groupValues[1].toIntOrNull()
            val month = m.groupValues[2].toIntOrNull()
            val yearRaw = m.groupValues[3]
            if (day == null || month == null) {
                null
            } else if (yearRaw.isNotBlank()) {
                val year = yearRaw.toIntOrNull()?.let {
                    if (it < 100) 2000 + it else it
                }
                if (year == null) null else safeDate(year, month, day)
            } else {
                val thisYear = safeDate(today.year, month, day)
                if (thisYear != null && thisYear >= today) {
                    thisYear
                } else {
                    safeDate(today.year + 1, month, day)
                }
            }
        },
        Regex("""\b(segunda|terca|quarta|quinta|sexta|sabado|domingo)(-feira)?( que vem)?\b""") to { m ->
            val dow = WEEKDAY_ISO[m.groupValues[1]]
            if (dow == null) {
                null
            } else {
                val base = nextWeekday(today, dow, strictFuture = false)
                // "que vem": se o dia cair hoje, é o da semana que vem;
                // nos demais casos, o próximo já é o "que vem".
                if (m.groupValues[3].isNotBlank() && base == today) {
                    base.plus(DatePeriod(days = 7))
                } else {
                    base
                }
            }
        },
    )
    for ((regex, build) in datePatterns) {
        for (m in regex.findAll(norm)) {
            val date = build(m) ?: continue
            add(
                Candidate(
                    m.range.first, m.range.last + 1, QuickSpanKind.DATE,
                    label = dueDateLabel(date, today), date = date,
                ),
            )
        }
    }

    // ---------------- Hora --------------------------------------------
    val timeRegex = Regex(
        """(?:\bas\s+)?(\d{1,2}):(\d{2})\b|(?:\bas\s+)?(\d{1,2})h(\d{2})?\b|\bmeio[- ]dia\b""",
    )
    for (m in timeRegex.findAll(norm)) {
        val hour: Int
        val minute: Int
        if (m.value.startsWith("meio")) {
            hour = 12
            minute = 0
        } else {
            val h = (m.groupValues[1].ifBlank { m.groupValues[3] }).toIntOrNull()
                ?: continue
            val min = (m.groupValues[2].ifBlank { m.groupValues[4] })
                .toIntOrNull() ?: 0
            if (h !in 0..23 || min !in 0..59) continue
            hour = h
            minute = min
        }
        val label = hour.toString().padStart(2, '0') + ":" +
            minute.toString().padStart(2, '0')
        add(
            Candidate(
                m.range.first, m.range.last + 1, QuickSpanKind.TIME,
                label = label, time = label,
            ),
        )
    }

    // ---------------- Prioridade --------------------------------------
    for (m in Regex("""\bp([1-3])\b""").findAll(norm)) {
        val prio = m.groupValues[1].toIntOrNull() ?: continue
        add(
            Candidate(
                m.range.first, m.range.last + 1, QuickSpanKind.PRIORITY,
                label = "P$prio", priority = prio,
            ),
        )
    }

    // ---------------- Projeto -----------------------------------------
    val projectByNorm = TaskProject.entries.associateBy {
        normalizePt(it.label)
    }
    for (m in Regex("""#([a-z0-9_]+)""").findAll(norm)) {
        val project = projectByNorm[m.groupValues[1]] ?: continue
        add(
            Candidate(
                m.range.first, m.range.last + 1, QuickSpanKind.PROJECT,
                label = "#" + project.label, project = project,
            ),
        )
    }

    // ---------------- Resolução de conflitos ---------------------------
    val sorted = candidates.sortedWith(
        compareBy({ it.start }, { -(it.end - it.start) }),
    )
    val accepted = mutableListOf<Candidate>()
    val usedKinds = mutableSetOf<QuickSpanKind>()
    var lastEnd = -1
    for (candidate in sorted) {
        if (candidate.start < lastEnd) continue // sobrepõe um já aceito
        if (candidate.kind in usedKinds) continue // só o 1º de cada tipo
        accepted.add(candidate)
        usedKinds.add(candidate.kind)
        lastEnd = candidate.end
    }
    accepted.sortBy { it.start }

    // ---------------- Título limpo -------------------------------------
    val sb = StringBuilder()
    var cursor = 0
    for (span in accepted) {
        sb.append(text, cursor, span.start)
        cursor = span.end
    }
    sb.append(text, cursor, text.length)
    val title = cleanQuickTitle(sb.toString())

    val parsedDate = accepted.firstOrNull {
        it.kind == QuickSpanKind.DATE
    }?.date
    val parsedRepeat = accepted.firstOrNull {
        it.kind == QuickSpanKind.REPEAT
    }?.repeat
    // "dia 5 todo mês": a recorrência mensal/anual herda o dia (e o mês)
    // da data reconhecida no mesmo texto.
    val refinedRepeat = if (parsedRepeat != null && parsedDate != null) {
        when (parsedRepeat.freq) {
            RepeatFreq.MONTHLY ->
                if (parsedRepeat.monthDay == null &&
                    parsedRepeat.monthWeek == null
                ) {
                    parsedRepeat.copy(monthDay = parsedDate.dayOfMonth)
                } else {
                    parsedRepeat
                }
            RepeatFreq.YEARLY -> parsedRepeat.copy(
                month = parsedRepeat.month ?: parsedDate.monthNumber,
                monthDay = parsedRepeat.monthDay ?: parsedDate.dayOfMonth,
            )
            else -> parsedRepeat
        }
    } else {
        parsedRepeat
    }

    return QuickAddParse(
        title = title,
        spans = accepted.map {
            QuickSpan(
                start = it.start,
                end = it.end,
                kind = it.kind,
                text = text.substring(it.start, it.end),
                label = it.label,
            )
        },
        dueDate = parsedDate,
        dueTime = accepted.firstOrNull { it.kind == QuickSpanKind.TIME }?.time,
        priority = accepted.firstOrNull {
            it.kind == QuickSpanKind.PRIORITY
        }?.priority,
        project = accepted.firstOrNull {
            it.kind == QuickSpanKind.PROJECT
        }?.project,
        repeat = refinedRepeat,
    )
}

/** Palavras órfãs que a remoção de trechos costuma deixar nas bordas. */
private val EDGE_WORDS = setOf(
    "para", "pra", "pro", "em", "no", "na", "de", "do", "da", "a", "o",
    "e", "as", "os", "um", "uma",
)

/** Limpa o título após remover os trechos: espaços, pontuação e
 *  preposições órfãs nas bordas. */
fun cleanQuickTitle(raw: String): String {
    var title = raw.replace(Regex("""\s+"""), " ").trim()
        .trim(',', ';', '-', '–', '—', ':', ' ').trim()
    var changed = true
    while (changed && title.isNotEmpty()) {
        changed = false
        val words = title.split(" ")
        val first = normalizePt(words.first())
        val last = normalizePt(words.last())
        if (words.size > 1 && first in EDGE_WORDS) {
            title = words.drop(1).joinToString(" ")
            changed = true
        } else if (words.size > 1 && last in EDGE_WORDS) {
            title = words.dropLast(1).joinToString(" ")
            changed = true
        }
    }
    return title
}

/** Descrição curta do que foi reconhecido (para os chips da UI). */
fun quickSpanSummary(span: QuickSpan): String = when (span.kind) {
    QuickSpanKind.DATE -> span.label
    QuickSpanKind.TIME -> "às " + span.label
    QuickSpanKind.PRIORITY -> span.label
    QuickSpanKind.PROJECT -> span.label
    QuickSpanKind.REPEAT -> span.label
}

/** yyyy-MM-dd da data resolvida (atalho para a UI/testes). */
fun QuickAddParse.dueYmd(): String? = dueDate?.toYmd()
