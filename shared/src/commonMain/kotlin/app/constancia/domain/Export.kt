package app.constancia.domain

import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.Habit
import app.constancia.model.TaskItem

/** Monta o JSON da exportação (sem libs extras — construção manual segura). */
fun buildExportJson(
    habits: List<Habit>,
    checkins: List<Checkin>,
    tasks: List<TaskItem>,
    diary: List<DiaryEntry>,
): String {
    fun esc(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    val sb = StringBuilder()
    sb.append("{\"app\":\"constancia\",\"version\":1,")
    sb.append("\"habits\":[")
    sb.append(habits.joinToString(",") {
        "{\"id\":\"${esc(it.id)}\",\"name\":\"${esc(it.name)}\"," +
            "\"goalType\":\"${it.goalType}\",\"targetQuantity\":${it.targetQuantity}," +
            "\"frequencyType\":\"${it.frequencyType}\",\"session\":\"${it.session}\"," +
            "\"paused\":${it.paused},\"createdAt\":\"${esc(it.createdAt)}\"}"
    })
    sb.append("],\"checkins\":[")
    sb.append(checkins.joinToString(",") {
        "{\"habitId\":\"${esc(it.habitId)}\",\"date\":\"${it.date}\"," +
            "\"quantity\":${it.quantity}}"
    })
    sb.append("],\"tasks\":[")
    sb.append(tasks.joinToString(",") {
        "{\"title\":\"${esc(it.title)}\",\"date\":\"${it.date}\"," +
            "\"done\":${it.done},\"dueTime\":${it.dueTime?.let { t -> "\"${esc(t)}\"" } ?: "null"}}"
    })
    sb.append("],\"diary\":[")
    sb.append(diary.joinToString(",") {
        "{\"date\":\"${it.date}\",\"mood\":${it.mood ?: "null"}," +
            "\"note\":\"${esc(it.note)}\"}"
    })
    sb.append("]}")
    return sb.toString()
}

/** Monta o CSV da exportação (check-ins, o dado mais valioso). */
fun buildExportCsv(habits: List<Habit>, checkins: List<Checkin>): String {
    val names = habits.associate { it.id to it.name.replace(";", ",") }
    val sb = StringBuilder("data;habito;quantidade\n")
    checkins.sortedBy { it.date }.forEach {
        sb.append("${it.date};${names[it.habitId] ?: it.habitId};${it.quantity}\n")
    }
    return sb.toString()
}
