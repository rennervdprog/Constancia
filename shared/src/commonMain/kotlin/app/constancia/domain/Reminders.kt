package app.constancia.domain

import app.constancia.model.TaskItem
import app.constancia.model.toLocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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
