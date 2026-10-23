package app.constancia.domain

import app.constancia.model.SavedView
import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskOccurrence
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
 * Lógica pura das tarefas (100% testável, sem Compose):
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
const val REPEAT_BIWEEKLY = "A cada 2 semanas"
const val REPEAT_MONTHLY = "Mensal"
const val REPEAT_YEARLY = "Anual"
val REPEAT_OPTIONS = listOf(
    "", REPEAT_DAILY, REPEAT_WEEKDAYS, REPEAT_WEEKLY,
    REPEAT_BIWEEKLY, REPEAT_MONTHLY, REPEAT_YEARLY,
)

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
 * "Todo dia" -> +1 dia; "Semanal" -> +7 dias; "Seg a Sex" -> próximo dia útil;
 * "A cada 2 semanas" -> +14 dias; "Mensal" -> mesmo dia do mês seguinte
 * (com clamp no fim do mês); "Anual" -> mesmo dia do ano seguinte.
 * Null quando não repete ou não tem prazo.
 *
 * Sem informação de "hoje", equivale a um passo a partir do prazo
 * (a ocorrência gerada é sempre depois do próprio prazo).
 */
fun nextRepeatDate(repeatRule: String, dueDate: LocalDate): LocalDate? =
    nextRepeatDate(repeatRule, dueDate, dueDate)

/**
 * R4.1 (Fase A): como [nextRepeatDate], mas avança em LAÇO até a
 * primeira data depois de max(prazo, hoje) — concluir atrasado além de
 * um período não gera ocorrência já vencida. Guarda de 1000 iterações.
 */
fun nextRepeatDate(
    repeatRule: String,
    dueDate: LocalDate,
    today: LocalDate,
): LocalDate? {
    var next = stepRepeatDate(repeatRule, dueDate) ?: return null
    val floor = maxOf(dueDate, today)
    var guard = 0
    while (next <= floor && guard++ < 1000) {
        next = stepRepeatDate(repeatRule, next) ?: return null
    }
    return next
}

/** Um passo da recorrência legada (rótulo solto) a partir de [from]. */
private fun stepRepeatDate(repeatRule: String, from: LocalDate): LocalDate? = when (repeatRule) {
    REPEAT_DAILY -> from.plus(DatePeriod(days = 1))
    REPEAT_WEEKLY -> from.plus(DatePeriod(days = 7))
    REPEAT_BIWEEKLY -> from.plus(DatePeriod(days = 14))
    REPEAT_MONTHLY -> from.plus(DatePeriod(months = 1))
    REPEAT_YEARLY -> from.plus(DatePeriod(years = 1))
    REPEAT_WEEKDAYS -> {
        var d = from.plus(DatePeriod(days = 1))
        // ISO: 6=sábado, 7=domingo.
        while (d.isoDayNumberCompat() >= 6) d = d.plus(DatePeriod(days = 1))
        d
    }
    else -> null
}

// ---------------------------------------------------------------------
// Item 1 — reagendamento rápido (chips) e em massa.
// ---------------------------------------------------------------------

/** Alvos dos chips de reagendamento rápido. */
enum class RescheduleTarget {
    WEEKEND, NEXT_WEEK, PLUS_3_DAYS, PLUS_1_WEEK,
    // Fase A (R5.2): fim da semana corrente e mesmo dia do mês que vem.
    LATER_THIS_WEEK, NEXT_MONTH,
}

/** Data resultante de um chip de reagendamento a partir de hoje. */
fun rescheduleDate(target: RescheduleTarget, today: LocalDate): LocalDate =
    when (target) {
        RescheduleTarget.PLUS_3_DAYS -> today.plus(DatePeriod(days = 3))
        RescheduleTarget.PLUS_1_WEEK -> today.plus(DatePeriod(days = 7))
        RescheduleTarget.WEEKEND -> {
            // Próximo sábado (hoje, se já for sábado). ISO: sábado = 6.
            val delta = (6 - today.isoDayNumberCompat() + 7) % 7
            today.plus(DatePeriod(days = delta))
        }
        RescheduleTarget.NEXT_WEEK -> {
            // Segunda-feira da semana que vem (sempre no futuro).
            val delta = (8 - today.isoDayNumberCompat()) % 7
            today.plus(DatePeriod(days = if (delta == 0) 7 else delta))
        }
        RescheduleTarget.LATER_THIS_WEEK -> {
            // Sexta-feira desta semana (hoje, se já for sexta; sábado e
            // domingo caem na sexta da semana seguinte). ISO: sexta = 5.
            val delta = (5 - today.isoDayNumberCompat() + 7) % 7
            today.plus(DatePeriod(days = delta))
        }
        RescheduleTarget.NEXT_MONTH ->
            // Mesmo dia do mês que vem (plus de meses já faz o clamp).
            today.plus(DatePeriod(months = 1))
    }

/**
 * R5.1 (Fase A): alvo calculado a partir de uma data-base arbitrária —
 * na UI, a DATA DA TAREFA ("+3 dias da data", "+1 mês da data"). Os
 * alvos relativos puros (+3d, +1sem, mês que vem) somam sobre a base;
 * os de semana (fim de semana, semana que vem, ainda esta semana) são
 * relativos à semana da própria base.
 */
fun rescheduleDateFrom(target: RescheduleTarget, base: LocalDate): LocalDate =
    when (target) {
        RescheduleTarget.PLUS_3_DAYS -> base.plus(DatePeriod(days = 3))
        RescheduleTarget.PLUS_1_WEEK -> base.plus(DatePeriod(days = 7))
        RescheduleTarget.NEXT_MONTH -> base.plus(DatePeriod(months = 1))
        else -> rescheduleDate(target, base)
    }

/** Rótulo curto de cada chip de reagendamento. */
fun rescheduleLabel(target: RescheduleTarget): String = when (target) {
    RescheduleTarget.WEEKEND -> "Fim de semana"
    RescheduleTarget.NEXT_WEEK -> "Semana que vem"
    RescheduleTarget.PLUS_3_DAYS -> "+3 dias"
    RescheduleTarget.PLUS_1_WEEK -> "+1 semana"
    RescheduleTarget.LATER_THIS_WEEK -> "Ainda esta semana"
    RescheduleTarget.NEXT_MONTH -> "Mês que vem"
}

// ---------------------------------------------------------------------
// Item 4 — agrupar e ordenar as listas.
// ---------------------------------------------------------------------

enum class TaskSortMode { SMART, PRIORITY, DUE }

enum class TaskGroupBy { NONE, PROJECT, PRIORITY, DATE }

data class TaskGroup(val title: String, val tasks: List<TaskItem>)

/** Rótulo curto da duração estimada (item 8): "15 min", "1 h", "1 h 30". */
fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60}"
}

/**
 * Ordenação "inteligente" das tarefas: pendentes antes das
 * feitas; entre as pendentes, atrasadas -> hoje -> futuras -> sem data;
 * dentro do balde, prazo, prioridade e horário.
 */
fun sortSmart(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> {
    val todayYmd = today.toYmd()
    fun bucket(t: TaskItem): Int = when {
        t.done -> 4
        t.dueDate == null -> 3
        t.dueDate < todayYmd -> 0
        t.dueDate == todayYmd -> 1
        else -> 2
    }
    return tasks.sortedWith(
        compareBy(
            { bucket(it) },
            { it.dueDate ?: "9999-99-99" },
            { it.priority },
            { timeSortKey(it.dueTime) },
        ),
    )
}

/** Ordenação por prazo puro (sem data por último; feitas por último). */
fun sortByDue(tasks: List<TaskItem>): List<TaskItem> =
    tasks.sortedWith(
        compareBy(
            { if (it.done) 1 else 0 },
            { it.dueDate ?: "9999-99-99" },
            { it.priority },
            { timeSortKey(it.dueTime) },
        ),
    )

/** Aplica o modo de ordenação escolhido sobre uma lista já filtrada. */
fun applySort(
    tasks: List<TaskItem>,
    mode: TaskSortMode,
    today: LocalDate,
): List<TaskItem> = when (mode) {
    TaskSortMode.SMART -> sortSmart(tasks, today)
    TaskSortMode.PRIORITY -> sortByPrio(tasks)
    TaskSortMode.DUE -> sortByDue(tasks)
}

/**
 * Agrupa uma lista (já ordenada) por projeto, prioridade ou data.
 * Grupos vazios são omitidos; a ordem dos grupos é a natural de cada eixo.
 */
fun groupTasks(
    tasks: List<TaskItem>,
    groupBy: TaskGroupBy,
    today: LocalDate,
): List<TaskGroup> = when (groupBy) {
    TaskGroupBy.NONE -> if (tasks.isEmpty()) emptyList()
    else listOf(TaskGroup("", tasks))

    TaskGroupBy.PROJECT -> TaskProject.entries.mapNotNull { project ->
        val inGroup = tasks.filter { it.project == project }
        if (inGroup.isEmpty()) null else TaskGroup(project.label, inGroup)
    }

    TaskGroupBy.PRIORITY -> (1..3).mapNotNull { prio ->
        val inGroup = tasks.filter { it.priority == prio }
        if (inGroup.isEmpty()) null
        else TaskGroup(prioCode(prio) + " · " + prioName(prio), inGroup)
    }

    TaskGroupBy.DATE -> {
        val todayYmd = today.toYmd()
        val groups = mutableListOf<TaskGroup>()
        val overdue = tasks.filter {
            it.dueDate != null && it.dueDate < todayYmd
        }
        if (overdue.isNotEmpty()) groups.add(TaskGroup("Atrasadas", overdue))
        val dueToday = tasks.filter { it.dueDate == todayYmd }
        if (dueToday.isNotEmpty()) groups.add(TaskGroup("Hoje", dueToday))
        val tomorrow = today.plus(DatePeriod(days = 1))
        val dueTomorrow = tasks.filter { it.dueDate == tomorrow.toYmd() }
        if (dueTomorrow.isNotEmpty()) groups.add(TaskGroup("Amanhã", dueTomorrow))
        val later = tasks.filter {
            it.dueDate != null && it.dueDate > tomorrow.toYmd()
        }
        later.mapNotNull { task -> task.dueDate?.let { it to task } }
            .groupBy({ it.first }, { it.second })
            .entries.sortedBy { it.key }
            .forEach { (ymd, inGroup) ->
                groups.add(
                    TaskGroup(dueDateLabel(ymd.toLocalDate(), today), inGroup),
                )
            }
        val noDate = tasks.filter { it.dueDate == null }
        if (noDate.isNotEmpty()) groups.add(TaskGroup("Sem data", noDate))
        groups
    }
}

// ---------------------------------------------------------------------
// Item 10 — filtro das vistas salvas.
// ---------------------------------------------------------------------

/** Tarefas que pertencem a uma vista salva (pendentes e feitas; a
 *  ordenação escolhida afunda as feitas no modo inteligente). */
fun savedViewTasks(
    tasks: List<TaskItem>,
    view: SavedView,
    today: LocalDate,
): List<TaskItem> {
    val todayYmd = today.toYmd()
    val weekEnd = today.plus(DatePeriod(days = 6)).toYmd()
    return tasks.filter { task ->
        (view.project == null || task.project == view.project) &&
            (view.priority == null || task.priority == view.priority) &&
            when (view.period) {
                "today" -> task.dueDate != null && task.dueDate <= todayYmd
                "week" -> task.dueDate != null &&
                    task.dueDate >= todayYmd && task.dueDate <= weekEnd
                "overdue" -> !task.done && task.dueDate != null &&
                    task.dueDate < todayYmd
                "nodate" -> task.dueDate == null
                else -> true // "all"
            }
    }
}

/** Converte o groupBy salvo (string) no enum de agrupamento. */
fun groupByOf(value: String): TaskGroupBy = when (value) {
    "project" -> TaskGroupBy.PROJECT
    "priority" -> TaskGroupBy.PRIORITY
    "date" -> TaskGroupBy.DATE
    else -> TaskGroupBy.NONE
}

/** Inverso de [groupByOf]: o valor persistido de cada agrupamento. */
fun groupByValue(groupBy: TaskGroupBy): String = when (groupBy) {
    TaskGroupBy.PROJECT -> "project"
    TaskGroupBy.PRIORITY -> "priority"
    TaskGroupBy.DATE -> "date"
    TaskGroupBy.NONE -> "none"
}

/** Converte o sortMode salvo (nome do enum) de volta ao enum. */
fun sortModeOf(value: String): TaskSortMode =
    TaskSortMode.entries.firstOrNull { it.name == value } ?: TaskSortMode.SMART

// ---------------------------------------------------------------------
// Fase B (R1.1) — histórico de ocorrências: a visão Feitas e os
// relatórios leem do histórico o que a linha viva não guarda mais.
// ---------------------------------------------------------------------

/**
 * Ocorrências do histórico que NÃO estão representadas por uma linha
 * viva concluída (mesma tarefa, mesmo prazo, ainda feita). São essas que
 * completam as listas/relatórios: a linha viva apagada (ou desmarcada
 * depois que o desfazer expirou) não some da memória do app.
 */
fun orphanOccurrences(
    tasks: List<TaskItem>,
    history: List<TaskOccurrence>,
): List<TaskOccurrence> = history.filter { occ ->
    tasks.none { task ->
        task.id == occ.sourceTaskId && task.done &&
            task.dueDate == occ.occurrenceDueDate
    }
}

/** Snapshot do histórico como tarefa feita (para listas e contagens). */
fun TaskOccurrence.asTaskItem(): TaskItem = TaskItem(
    id = "hist:" + id,
    title = title,
    dueTime = null,
    done = true,
    date = occurrenceDueDate ?: completedAt.take(10),
    project = project,
    priority = priority,
    dueDate = occurrenceDueDate,
    deadlineDate = deadlineDate,
    durationMinutes = durationMinutes,
    completedAt = completedAt,
)

/**
 * Tarefas + ocorrências órfãs do histórico como itens feitos — a base
 * dos relatórios e das metas: conclusões preservadas contam mesmo sem a
 * linha viva.
 */
fun withOccurrenceHistory(
    tasks: List<TaskItem>,
    history: List<TaskOccurrence>,
): List<TaskItem> = tasks + orphanOccurrences(tasks, history).map { it.asTaskItem() }

/** Visão Feitas: as feitas vivas + as órfãs do histórico (R1.1). */
fun feitasTasks(
    tasks: List<TaskItem>,
    history: List<TaskOccurrence>,
): List<TaskItem> = doneTasks(tasks) +
    orphanOccurrences(tasks, history).map { it.asTaskItem() }

/** Chave de ordenação por horário: "HH:mm"; sem horário vai para o fim ("99:99"). */
fun timeSortKey(time: String?): String =
    if (time.isNullOrBlank()) "99:99" else time

/** Sessão padrão da captura rápida da tela Hoje (igual ao protótipo: tarde). */
val QUICK_ADD_SESSION: Session = Session.TARDE
