package app.constancia.domain

import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.isoDayNumberCompat
import app.constancia.model.isoToAppDay
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Lógica pura das tarefas estilo Todoist (100% testável, sem Compose):
 * visões (Entrada/Hoje/Próximos/Feitas), contadores, ordenação por
 * prioridade, rótulos de prazo, recorrência e progresso de subtarefas.
 */

enum class TaskView { ENTRADA, HOJE, PROXIMOS, FEITAS }

data class TaskCounts(
    val entrada: Int,
    val hoje: Int,
    val proximos: Int,
    val feitas: Int,
)

data class SubProgress(val done: Int, val total: Int) {
    val pct: Int get() = if (total == 0) 0 else done * 100 / total
}

const val REPEAT_DAILY = "Todo dia"
const val REPEAT_WEEKDAYS = "Seg a Sex"
const val REPEAT_WEEKLY = "Semanal"
val REPEAT_OPTIONS = listOf("", REPEAT_DAILY, REPEAT_WEEKDAYS, REPEAT_WEEKLY)

/** "P1"/"P2"/"P3" a partir da prioridade 1..3. */
fun prioCode(priority: Int): String = "P" + priority.coerceIn(1, 3)

/** "Alta"/"Média"/"Baixa" a partir da prioridade 1..3. */
fun prioName(priority: Int): String = when (priority.coerceIn(1, 3)) {
    1 -> "Alta"
    2 -> "Média"
    else -> "Baixa"
}

/**
 * Ciclo do botão "Prioridade" da linha expandida (igual ao protótipo):
 * alta -> baixa, média -> alta, baixa -> média.
 */
fun cyclePriority(priority: Int): Int = when (priority) {
    1 -> 3
    2 -> 1
    else -> 2
}

/** Pendentes primeiro (feitas por último), depois prioridade, mantendo a ordem de entrada nos empates. */
fun sortByPrio(tasks: List<TaskItem>): List<TaskItem> =
    tasks.sortedWith(compareBy({ if (it.done) 1 else 0 }, { it.priority }))

fun subtaskProgress(subtasks: List<Subtask>): SubProgress? =
    if (subtasks.isEmpty()) null
    else SubProgress(subtasks.count { it.done }, subtasks.size)

/** Contadores dos chips de visão (sempre sobre TODAS as tarefas). */
fun taskCounts(tasks: List<TaskItem>, today: LocalDate): TaskCounts {
    val todayYmd = today.toYmd()
    val upcomingEnd = today.plus(DatePeriod(days = 6))
    return TaskCounts(
        entrada = tasks.count { it.project == TaskProject.ENTRADA && !it.done },
        hoje = tasks.count {
            !it.done && it.dueDate != null && it.dueDate <= todayYmd
        },
        proximos = tasks.count {
            !it.done && it.dueDate != null &&
                it.dueDate > todayYmd && it.dueDate.toLocalDate() <= upcomingEnd
        },
        feitas = tasks.count { it.done },
    )
}

/** Pendentes de um projeto (contador do chip do projeto). */
fun projectPendingCount(tasks: List<TaskItem>, project: TaskProject): Int =
    tasks.count { it.project == project && !it.done }

/** Visão Entrada: tarefas capturadas sem organizar (projeto Entrada). */
fun entradaTasks(tasks: List<TaskItem>): List<TaskItem> =
    sortByPrio(tasks.filter { it.project == TaskProject.ENTRADA })

/** Visão de um projeto: todas as tarefas dele, pendentes primeiro. */
fun projectTasks(tasks: List<TaskItem>, project: TaskProject): List<TaskItem> =
    sortByPrio(tasks.filter { it.project == project })

/** Seção "Atrasadas" da visão Hoje. */
fun overdueTasks(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> {
    val todayYmd = today.toYmd()
    return sortByPrio(tasks.filter { !it.done && it.dueDate != null && it.dueDate < todayYmd })
}

/** Seção "Para hoje" da visão Hoje. */
fun todayTasks(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> {
    val todayYmd = today.toYmd()
    return sortByPrio(tasks.filter { !it.done && it.dueDate == todayYmd })
}

/** Seção "Feitas hoje": concluídas com prazo ontem ou hoje. */
fun doneTodayTasks(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> {
    val todayYmd = today.toYmd()
    val yesterdayYmd = today.minus(DatePeriod(days = 1)).toYmd()
    return tasks.filter {
        it.done && (it.dueDate == todayYmd || it.dueDate == yesterdayYmd)
    }
}

/** Visão Feitas: todas as concluídas. */
fun doneTasks(tasks: List<TaskItem>): List<TaskItem> = tasks.filter { it.done }

/** Visão Próximos: grupos por data (amanhã..+6 dias), só dias com pendências. */
fun upcomingGroups(
    tasks: List<TaskItem>,
    today: LocalDate,
): List<Pair<LocalDate, List<TaskItem>>> {
    val groups = mutableListOf<Pair<LocalDate, List<TaskItem>>>()
    for (offset in 1..6) {
        val day = today.plus(DatePeriod(days = offset))
        val ymd = day.toYmd()
        val list = sortByPrio(tasks.filter { !it.done && it.dueDate == ymd })
        if (list.isNotEmpty()) groups.add(day to list)
    }
    return groups
}

/** Tarefas do fluxo do dia (Hoje): prazo hoje ou atrasadas, para misturar com os hábitos. */
fun dayFlowTasks(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> {
    val todayYmd = today.toYmd()
    return tasks.filter { it.dueDate != null && it.dueDate <= todayYmd }
}

/** Rótulo do prazo: "Hoje", "Amanhã", "Ontem" ou "seg, 12 de out". Null = sem data. */
fun dueLabel(task: TaskItem, today: LocalDate): String? {
    val due = task.dueDate ?: return null
    return dueDateLabel(due.toLocalDate(), today)
}

fun dueDateLabel(due: LocalDate, today: LocalDate): String = when (due) {
    today -> "Hoje"
    today.plus(DatePeriod(days = 1)) -> "Amanhã"
    today.minus(DatePeriod(days = 1)) -> "Ontem"
    else -> {
        val wd = DOW_SHORT[isoToAppDay(due.isoDayNumberCompat()) - 1].lowercase()
        "$wd, ${due.dayOfMonth} de ${MONTHS[due.monthNumber - 1]}"
    }
}

/** Título de um grupo da visão Próximos: "Amanhã · 21 de out" / "Quinta · 22 de out". */
fun upcomingGroupTitle(day: LocalDate, today: LocalDate): String {
    val name = if (day == today.plus(DatePeriod(days = 1))) {
        "Amanhã"
    } else {
        val full = DOW_FULL[isoToAppDay(day.isoDayNumberCompat()) - 1]
        full.replaceFirstChar { it.uppercase() }
    }
    return "$name · ${day.dayOfMonth} de ${MONTHS[day.monthNumber - 1]}"
}

/**
 * Próxima ocorrência de uma tarefa recorrente a partir do prazo atual.
 * "Todo dia" -> +1 dia; "Semanal" -> +7 dias; "Seg a Sex" -> próximo dia útil.
 * Null quando não repete ou não tem prazo.
 */
fun nextRepeatDate(repeatRule: String, dueDate: LocalDate): LocalDate? = when (repeatRule) {
    REPEAT_DAILY -> dueDate.plus(DatePeriod(days = 1))
    REPEAT_WEEKLY -> dueDate.plus(DatePeriod(days = 7))
    REPEAT_WEEKDAYS -> {
        var d = dueDate.plus(DatePeriod(days = 1))
        // ISO: 6=sábado, 7=domingo.
        while (d.isoDayNumberCompat() >= 6) d = d.plus(DatePeriod(days = 1))
        d
    }
    else -> null
}

/** Chave de ordenação por horário: "HH:mm"; sem horário vai para o fim ("99:99"). */
fun timeSortKey(time: String?): String =
    if (time.isNullOrBlank()) "99:99" else time

/** Sessão padrão da captura rápida da tela Hoje (igual ao protótipo: tarde). */
val QUICK_ADD_SESSION: Session = Session.TARDE
