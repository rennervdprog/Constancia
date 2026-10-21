package app.constancia.domain

import app.constancia.model.ReminderSpec
import app.constancia.model.ReminderType
import app.constancia.model.TaskItem
import app.constancia.model.TaskReminder
import app.constancia.model.toLocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Item 9 — lembretes de tarefa: o instante absoluto em que o lembrete
 * deve disparar (epoch millis). A base é a data agendada + horário da
 * tarefa (sem horário, 09:00), menos o deslocamento relativo escolhido
 * (0 = na hora, 10/30/60/1440 min antes). Null = nada a agendar (sem
 * prazo, sem lembrete ou tarefa já concluída).
 */
const val REMINDER_DEFAULT_HOUR = 9

/** Opções de lembrete oferecidas na UI (minutos antes; null = nenhum). */
val REMINDER_OPTIONS: List<Pair<String, Int?>> = listOf(
    "Sem lembrete" to null,
    "Na hora" to 0,
    "10 min antes" to 10,
    "30 min antes" to 30,
    "1 h antes" to 60,
    "1 dia antes" to 1440,
)

/** "HH:mm" -> LocalTime, com tolerância a "H:mm"; null se inválido. */
fun parseTimeOrNull(text: String?): LocalTime? {
    if (text.isNullOrBlank()) return null
    val parts = text.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime(hour, minute)
}

/**
 * Máscara de digitação dos campos de horário (reteste 2026-10-21: o
 * campo "HH:mm" não formatava sozinho). Aceita só dígitos (máx. 4),
 * insere o ":" após a hora e trava as faixas na fonte: hora 00–23 e
 * minuto 00–59 já durante a digitação. Primeiro dígito 3–9 é hora de
 * um dígito só ("9" vira "09" e o próximo dígito já é minuto). O
 * texto resultante é sempre vazio, parcial válido ("9", "09:3") ou
 * um "HH:mm" completo que [parseTimeOrNull] aceita; apagar desfaz
 * dígito a dígito, sem ficar preso no ":".
 */
fun maskTimeText(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(4)
    if (digits.isEmpty()) return ""
    fun clampedMinute(two: String): String =
        minOf(two.toInt(), 59).toString().padStart(2, '0')
    return if (digits[0] in '3'..'9') {
        // Hora de um dígito: "9" -> "09", "93" -> "09:3", "930" -> "09:30".
        val hour = "0" + digits[0]
        when (val rest = digits.substring(1)) {
            "" -> hour
            else -> hour + ":" +
                if (rest.length == 2) clampedMinute(rest) else rest
        }
    } else {
        when {
            digits.length <= 2 -> {
                if (digits.length == 2) {
                    minOf(digits.toInt(), 23).toString().padStart(2, '0')
                } else {
                    digits
                }
            }
            digits.length == 3 -> {
                val hour = minOf(digits.substring(0, 2).toInt(), 23)
                hour.toString().padStart(2, '0') + ":" + digits[2]
            }
            else -> {
                val hour = minOf(digits.substring(0, 2).toInt(), 23)
                hour.toString().padStart(2, '0') + ":" +
                    clampedMinute(digits.substring(2, 4))
            }
        }
    }
}

fun reminderInstantMillis(
    task: TaskItem,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long? {
    if (task.done) return null
    val due = task.dueDate ?: return null
    val offset = task.reminderMinutesBefore ?: return null
    val time = parseTimeOrNull(task.dueTime)
        ?: LocalTime(REMINDER_DEFAULT_HOUR, 0)
    val base = LocalDateTime(due.toLocalDate(), time)
        .toInstant(timeZone)
        .toEpochMilliseconds()
    return base - offset.toLong() * 60_000L
}

/** Rótulo do lembrete escolhido ("Na hora", "30 min antes"...). */
fun reminderLabel(minutesBefore: Int?): String =
    REMINDER_OPTIONS.firstOrNull { it.second == minutesBefore }?.first
        ?: "Sem lembrete"

// ---------------------------------------------------------------------
// Fase C — lembretes de verdade (R6.1/R6.2/R6.3): a tarefa tem uma LISTA
// de lembretes (tabela task_reminder), cada um relativo à base da
// tarefa ou absoluto num instante fixo.
// ---------------------------------------------------------------------

/**
 * Instante absoluto (epoch millis) em que UM lembrete da lista dispara.
 * RELATIVE: base (prazo + horário, ou 09:00 sem horário) menos o
 * deslocamento. ABSOLUTE: o próprio instante gravado. Null = nada a
 * agendar (tarefa concluída ou lembrete incompleto).
 */
fun taskReminderInstantMillis(
    task: TaskItem,
    reminder: TaskReminder,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): Long? {
    if (task.done) return null
    return when (reminder.type) {
        ReminderType.ABSOLUTE -> reminder.atMillis
        ReminderType.RELATIVE -> {
            val due = task.dueDate ?: return null
            val offset = reminder.minuteOffset ?: return null
            val time = parseTimeOrNull(task.dueTime)
                ?: LocalTime(REMINDER_DEFAULT_HOUR, 0)
            LocalDateTime(due.toLocalDate(), time)
                .toInstant(timeZone)
                .toEpochMilliseconds() - offset.toLong() * 60_000L
        }
    }
}

/** Todos os instantes agendáveis da tarefa, um por lembrete da lista. */
fun taskReminderInstants(
    task: TaskItem,
    reminders: List<TaskReminder>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<Pair<TaskReminder, Long>> = reminders.mapNotNull { reminder ->
    taskReminderInstantMillis(task, reminder, timeZone)
        ?.let { reminder to it }
}

/** Rótulo curto de UM lembrete da lista ("30 min antes", "às 14:30"). */
fun taskReminderLabel(
    reminder: TaskReminder,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String = when (reminder.type) {
    ReminderType.RELATIVE -> reminderLabel(reminder.minuteOffset)
    ReminderType.ABSOLUTE -> {
        val at = reminder.atMillis
        if (at == null) {
            "Horário fixo"
        } else {
            val ldt = kotlinx.datetime.Instant.fromEpochMilliseconds(at)
                .toLocalDateTime(timeZone)
            "às " + ldt.hour.toString().padStart(2, '0') + ":" +
                ldt.minute.toString().padStart(2, '0')
        }
    }
}

/** Resumo dos lembretes da tarefa para a linha de metadados. */
fun remindersSummary(task: TaskItem): String? {
    if (task.reminders.isEmpty()) return null
    return task.reminders.joinToString(" · ") { taskReminderLabel(it) }
}

/**
 * R6.2 — lembrete automático: a especificação a materializar quando a
 * tarefa nasce, ou null. Só materializa quando: a preferência está
 * ligada (autoMinutes != null), a tarefa tem DATA e HORÁRIO (sem
 * horário não há "minutos antes" que faça sentido) e a lista está
 * vazia (quem escolheu lembrete não ganha um segundo por cima).
 */
fun autoReminderSpec(
    autoMinutes: Int?,
    dueDate: String?,
    dueTime: String?,
    existingCount: Int,
): ReminderSpec? {
    if (autoMinutes == null) return null
    if (dueDate == null || dueTime == null) return null
    if (existingCount > 0) return null
    return ReminderSpec(ReminderType.RELATIVE, minuteOffset = autoMinutes)
}

/** Janela do catch-up (R6.3): lembretes perdidos nas últimas 24 h. */
const val CATCH_UP_WINDOW_MILLIS: Long = 24L * 60L * 60_000L

/** Um lembrete cujo instante passou sem o aparelho disparar (R6.3). */
data class MissedReminder(
    val taskId: String,
    val taskTitle: String,
    val reminderId: String,
    val instantMillis: Long,
)

/**
 * R6.3 — catch-up: lembretes de tarefas AINDA PENDENTES cujo instante
 * caiu na janela (agora − 24h, agora]. Fora da janela (mais velhos ou
 * futuros) não entram: resumo de ontem, não arqueologia. Ordenados do
 * mais antigo ao mais recente.
 */
fun missedReminders(
    tasks: List<TaskItem>,
    reminders: List<TaskReminder>,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    windowMillis: Long = CATCH_UP_WINDOW_MILLIS,
): List<MissedReminder> {
    val byTask = reminders.groupBy { it.taskId }
    val floor = nowMillis - windowMillis
    return tasks.flatMap { task ->
        if (task.done) {
            emptyList()
        } else {
            byTask[task.id].orEmpty().mapNotNull { reminder ->
                val instant = taskReminderInstantMillis(task, reminder, timeZone)
                if (instant != null && instant > floor && instant <= nowMillis) {
                    MissedReminder(task.id, task.title, reminder.id, instant)
                } else {
                    null
                }
            }
        }
    }.sortedBy { it.instantMillis }
}
