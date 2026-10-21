package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.data.TaskRepository
import app.constancia.db.ConstanciaDb
import app.constancia.domain.encodeRepeatSpec
import app.constancia.domain.effectiveRepeatSpec
import app.constancia.domain.nextOccurrence
import app.constancia.domain.nextRepeatDate
import app.constancia.domain.repeatLabel
import app.constancia.domain.reminderInstantMillis
import app.constancia.model.GoalPause
import app.constancia.notify.NotificationScheduler
import app.constancia.model.GoalType
import app.constancia.model.NewTask
import app.constancia.model.SavedView
import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.TaskTemplate
import app.constancia.model.newId
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import app.constancia.model.todayYmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class TaskRepositoryImpl(
    private val db: ConstanciaDb,
    private val checkins: CheckinRepository? = null,
    private val notifier: NotificationScheduler? = null,
) : TaskRepository {

    private val queries get() = db.constanciaDbQueries

    /**
     * Item 9: mantém o alarme da tarefa sincronizado com o estado dela
     * (agenda quando há lembrete futuro; cancela quando conclui, apaga
     * ou o instante já passou). Sem notifier (testes), não faz nada.
     */
    private suspend fun refreshReminder(id: String) {
        val scheduler = notifier ?: return
        val task = getById(id) ?: return
        val trigger = reminderInstantMillis(task)
        if (trigger != null &&
            trigger > Clock.System.now().toEpochMilliseconds()
        ) {
            scheduler.scheduleTaskReminder(task.id, task.title, trigger)
        } else {
            scheduler.cancelTaskReminder(task.id)
        }
    }

    override fun tasksForDateFlow(dateYmd: String): Flow<List<TaskItem>> =
        combine(
            queries.selectTasksForDate(dateYmd, dateYmd).asFlow().mapToList(Dispatchers.Default),
            allSubtasksFlow(),
        ) { rows, subs -> rows.map { it.toModel(subs[it.id].orEmpty()) } }

    override fun allTasksFlow(): Flow<List<TaskItem>> =
        combine(
            queries.selectAllTasks().asFlow().mapToList(Dispatchers.Default),
            allSubtasksFlow(),
        ) { rows, subs -> rows.map { it.toModel(subs[it.id].orEmpty()) } }

    private fun allSubtasksFlow(): Flow<Map<String, List<Subtask>>> =
        queries.selectAllSubtasks()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() }.groupBy { it.taskId } }

    override suspend fun getById(id: String): TaskItem? = withContext(Dispatchers.Default) {
        val row = queries.selectTaskById(id).executeAsOneOrNull() ?: return@withContext null
        val subs = queries.selectAllSubtasks().executeAsList()
            .map { it.toModel() }
            .filter { it.taskId == id }
        row.toModel(subs)
    }

    override suspend fun add(task: NewTask): String = withContext(Dispatchers.Default) {
        val id = newId()
        val now = Clock.System.now().toString()
        // O "date" da tarefa é o dia do prazo; sem prazo, é o dia da criação.
        val belongsDate = task.dueDate ?: todayYmd()
        queries.insertTask(
            id,
            task.title,
            task.dueTime,
            0, // done
            belongsDate,
            task.project.toDbValue(),
            task.priority.toLong(),
            task.dueDate,
            task.session.name,
            task.repeatRule,
            task.deadlineDate,
            task.durationMinutes?.toLong(),
            null, // completed_at
            task.reminderMinutesBefore?.toLong(),
            task.repeatSpec,
            task.note,
            task.habitId,
            now,
            0, // sort_order
        )
        task.subtaskTitles.forEachIndexed { index, title ->
            queries.insertSubtask(newId(), id, title, 0, index.toLong(), now)
        }
        refreshReminder(id)
        id
    }

    override suspend fun quickAdd(
        title: String,
        dueDate: String?,
        project: TaskProject,
        session: Session,
    ): String = add(
        NewTask(
            title = title,
            project = project,
            dueDate = dueDate,
            session = session,
        ),
    )

    override suspend fun setDone(id: String, done: Boolean) {
        withContext(Dispatchers.Default) {
            queries.setTaskDone(
                done.toFlag(),
                if (done) Clock.System.now().toString() else null,
                id,
            )
        }
        refreshReminder(id)
        if (done) {
            val task = getById(id) ?: return
            spawnNextOccurrence(task)
            markLinkedHabit(task)
        }
    }

    override suspend fun setPriority(id: String, priority: Int): Unit =
        withContext(Dispatchers.Default) {
            queries.setTaskPriority(priority.toLong(), id)
        }

    override suspend fun setDueDate(id: String, dueDate: String?) {
        withContext(Dispatchers.Default) {
            val current = queries.selectTaskById(id).executeAsOneOrNull()
            // Sem prazo novo, o "date" mantém o dia atual da tarefa.
            queries.setTaskDueDate(dueDate, dueDate ?: (current?.date ?: todayYmd()), id)
        }
        refreshReminder(id)
    }

    override suspend fun addSubtask(taskId: String, title: String): Unit =
        withContext(Dispatchers.Default) {
            val count = queries.selectAllSubtasks().executeAsList()
                .count { it.task_id == taskId }
            queries.insertSubtask(
                newId(), taskId, title, 0, count.toLong(), Clock.System.now().toString(),
            )
        }

    override suspend fun setSubtaskDone(subtaskId: String, done: Boolean) {
        var completedTaskId: String? = null
        withContext(Dispatchers.Default) {
            queries.setSubtaskDone(done.toFlag(), subtaskId)
            // Todas as subtarefas feitas -> a tarefa conclui junto (protótipo).
            val sub = queries.selectAllSubtasks().executeAsList()
                .firstOrNull { it.id == subtaskId } ?: return@withContext
            val siblings = queries.selectAllSubtasks().executeAsList()
                .filter { it.task_id == sub.task_id }
            if (siblings.isNotEmpty() && siblings.all { it.done.isSet() }) {
                val taskRow = queries.selectTaskById(sub.task_id).executeAsOneOrNull()
                if (taskRow != null && !taskRow.done.isSet()) {
                    queries.setTaskDone(
                        1,
                        Clock.System.now().toString(),
                        sub.task_id,
                    )
                    completedTaskId = sub.task_id
                }
            }
        }
        // Efeitos colaterais da conclusão (recorrência + vínculo com hábito).
        val taskId = completedTaskId ?: return
        val task = getById(taskId) ?: return
        spawnNextOccurrence(task)
        markLinkedHabit(task)
        refreshReminder(taskId)
    }

    override suspend fun delete(id: String) {
        withContext(Dispatchers.Default) {
            queries.deleteTask(id)
        }
        notifier?.cancelTaskReminder(id)
    }

    // -- Backlog Todoist Faixa 1/2 --

    override suspend fun rescheduleAllOverdue(
        todayYmd: String,
        targetYmd: String,
    ): Int = withContext(Dispatchers.Default) {
        val count = queries.countOverdueTasks(todayYmd).executeAsOne().toInt()
        if (count > 0) queries.rescheduleOverdueTasks(targetYmd, targetYmd, todayYmd)
        count
    }

    override suspend fun setReminder(id: String, minutesBefore: Int?) {
        withContext(Dispatchers.Default) {
            queries.setTaskReminder(minutesBefore?.toLong(), id)
        }
        refreshReminder(id)
    }

    override fun savedViewsFlow(): Flow<List<SavedView>> =
        queries.selectSavedViews().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun addSavedView(view: SavedView): String =
        withContext(Dispatchers.Default) {
            val id = if (view.id.isBlank()) newId() else view.id
            val count = queries.selectSavedViews().executeAsList().size
            queries.insertSavedView(
                id,
                view.name,
                view.colorHex,
                view.project?.toDbValue(),
                view.priority?.toLong(),
                view.period,
                view.groupBy,
                count.toLong(),
                Clock.System.now().toString(),
            )
            id
        }

    override suspend fun deleteSavedView(id: String): Unit =
        withContext(Dispatchers.Default) {
            queries.deleteSavedView(id)
        }

    override fun templatesFlow(): Flow<List<TaskTemplate>> =
        combine(
            queries.selectTemplates().asFlow().mapToList(Dispatchers.Default),
            queries.selectTemplateItems().asFlow().mapToList(Dispatchers.Default),
        ) { templates, items ->
            val byTemplate = items.map { it.toModel() }.groupBy { it.templateId }
            templates.map { row -> row.toModel(byTemplate[row.id].orEmpty()) }
        }

    override suspend fun saveTemplate(name: String, items: List<NewTask>): String =
        withContext(Dispatchers.Default) {
            val id = newId()
            val now = Clock.System.now().toString()
            queries.insertTemplate(id, name, 0, now)
            items.forEachIndexed { index, item ->
                queries.insertTemplateItem(
                    newId(), id, item.title, item.project.toDbValue(),
                    item.priority.toLong(), item.session.name, item.note,
                    index.toLong(), now,
                )
            }
            id
        }

    override suspend fun deleteTemplate(id: String): Unit =
        withContext(Dispatchers.Default) {
            queries.deleteTemplateItems(id)
            queries.deleteTemplate(id)
        }

    override suspend fun instantiateTemplate(
        templateId: String,
        dueDate: String?,
    ): Int {
        val template = withContext(Dispatchers.Default) {
            val row = queries.selectTemplates().executeAsList()
                .firstOrNull { it.id == templateId } ?: return@withContext null
            val items = queries.selectTemplateItems().executeAsList()
                .filter { it.template_id == templateId }
                .map { it.toModel() }
            row.toModel(items)
        } ?: return 0
        template.items.forEach { item ->
            add(
                NewTask(
                    title = item.title,
                    note = item.note,
                    project = item.project,
                    priority = item.priority,
                    dueDate = dueDate,
                    session = item.session,
                ),
            )
        }
        return template.items.size
    }

    override suspend fun ensureFactoryTemplates(): Unit =
        withContext(Dispatchers.Default) {
            val existing = queries.countFactoryTemplates().executeAsOne()
            if (existing > 0) return@withContext
            val now = Clock.System.now().toString()
            FACTORY_TEMPLATES.forEach { (name, items) ->
                val templateId = newId()
                queries.insertTemplate(templateId, name, 1, now)
                items.forEachIndexed { index, item ->
                    queries.insertTemplateItem(
                        newId(), templateId, item.title,
                        item.project.toDbValue(), item.priority.toLong(),
                        item.session.name, item.note, index.toLong(), now,
                    )
                }
            }
        }

    override fun goalPausesFlow(): Flow<List<GoalPause>> =
        queries.selectGoalPauses().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun setGoalPaused(paused: Boolean, todayYmd: String): Unit =
        withContext(Dispatchers.Default) {
            if (paused) {
                val open = queries.selectGoalPauses().executeAsList()
                    .any { it.end_date == null }
                if (!open) queries.insertGoalPause(newId(), todayYmd)
            } else {
                // Fecha na véspera: hoje já volta a contar para a meta.
                val yesterday = todayYmd.toLocalDate()
                    .minus(DatePeriod(days = 1)).toYmd()
                queries.closeOpenGoalPauses(yesterday)
            }
        }

    /** Modelos de fábrica do item 11 (semeados uma única vez). */
    private companion object {
        val FACTORY_TEMPLATES: List<Pair<String, List<NewTask>>> = listOf(
            "Rotina da manhã" to listOf(
                NewTask(
                    title = "Beber um copo de água",
                    project = TaskProject.SAUDE,
                    session = Session.MANHA,
                ),
                NewTask(
                    title = "Arrumar a cama",
                    project = TaskProject.CASA,
                    session = Session.MANHA,
                ),
                NewTask(
                    title = "Alongar por 5 minutos",
                    project = TaskProject.SAUDE,
                    session = Session.MANHA,
                ),
                NewTask(
                    title = "Revisar o dia de hoje",
                    project = TaskProject.PESSOAL,
                    session = Session.MANHA,
                ),
            ),
            "Lista de compras" to listOf(
                NewTask(
                    title = "Anotar o que falta na despensa",
                    project = TaskProject.CASA,
                    session = Session.TARDE,
                ),
                NewTask(
                    title = "Conferir a geladeira",
                    project = TaskProject.CASA,
                    session = Session.TARDE,
                ),
                NewTask(
                    title = "Montar a lista da semana",
                    project = TaskProject.CASA,
                    session = Session.TARDE,
                ),
                NewTask(
                    title = "Ir ao mercado",
                    project = TaskProject.CASA,
                    priority = 1,
                    session = Session.TARDE,
                ),
            ),
            "Fechamento do mês" to listOf(
                NewTask(
                    title = "Conferir o extrato",
                    project = TaskProject.TRABALHO,
                    session = Session.TARDE,
                ),
                NewTask(
                    title = "Pagar as contas do mês",
                    project = TaskProject.TRABALHO,
                    priority = 1,
                    session = Session.TARDE,
                ),
                NewTask(
                    title = "Revisar as assinaturas",
                    project = TaskProject.TRABALHO,
                    session = Session.TARDE,
                ),
                NewTask(
                    title = "Guardar os comprovantes",
                    project = TaskProject.TRABALHO,
                    session = Session.TARDE,
                ),
            ),
        )
    }

    /**
     * Tarefa recorrente concluída -> cria a próxima ocorrência (mesmos
     * campos, subtarefas zeradas), se ainda não existir uma igual no dia.
     * O prazo final acompanha deslocado pelo mesmo intervalo do prazo.
     * Com regra estruturada (item 7), a próxima sai de nextOccurrence
     * (base prazo × conclusão, término) e a série passa a carregar a
     * spec normalizada; sem spec, vale o nextRepeatDate legado.
     */
    private suspend fun spawnNextOccurrence(task: TaskItem) {
        val due = task.dueDate ?: return
        val dueDate = due.toLocalDate()
        val spec = effectiveRepeatSpec(task.repeatSpec, task.repeatRule, dueDate)
        val next = if (spec != null) {
            nextOccurrence(spec, dueDate, todayYmd().toLocalDate())
        } else {
            nextRepeatDate(task.repeatRule, dueDate)
        } ?: return
        val nextYmd = next.toYmd()
        val already = withContext(Dispatchers.Default) {
            queries.selectTasksForDate(nextYmd, nextYmd).executeAsList().any {
                it.title == task.title && it.repeat_rule == task.repeatRule &&
                    it.habit_id == task.habitId
            }
        }
        if (already) return
        val shiftDays = next.toEpochDays() - dueDate.toEpochDays()
        val nextDeadline = task.deadlineDate?.toLocalDate()
            ?.plus(DatePeriod(days = shiftDays.toInt()))?.toYmd()
        add(
            NewTask(
                title = task.title,
                note = task.note,
                project = task.project,
                priority = task.priority,
                dueDate = nextYmd,
                session = task.session,
                dueTime = task.dueTime,
                repeatRule = if (spec != null) repeatLabel(spec) else task.repeatRule,
                habitId = task.habitId,
                subtaskTitles = task.subtasks.map { it.title },
                deadlineDate = nextDeadline,
                durationMinutes = task.durationMinutes,
                reminderMinutesBefore = task.reminderMinutesBefore,
                repeatSpec = if (spec != null) encodeRepeatSpec(spec) else "",
            ),
        )
    }

    /**
     * Vínculo tarefa -> hábito (igual ao protótipo): concluir a tarefa com
     * prazo hoje/ontem marca o hábito de hoje. Desmarcar a tarefa NÃO
     * desmarca o hábito.
     */
    private suspend fun markLinkedHabit(task: TaskItem) {
        val repo = checkins ?: return
        val habitId = task.habitId ?: return
        val today = todayYmd()
        val yesterday = today.toLocalDate().minus(DatePeriod(days = 1)).toYmd()
        if (task.dueDate != today && task.dueDate != yesterday) return
        val habitRow = withContext(Dispatchers.Default) {
            queries.selectHabitById(habitId).executeAsOneOrNull()
        } ?: return
        val current = repo.get(habitId, today)
        val isQuantity = habitRow.goal_type == GoalType.QUANTITY.name
        val target = habitRow.target_quantity
        val doneNow = current != null &&
            (if (isQuantity) current.quantity >= target else current.quantity >= 1.0)
        if (doneNow) return
        if (current == null) {
            repo.toggle(habitId, today, if (isQuantity) target else 1.0)
        } else if (isQuantity) {
            repo.setQuantity(habitId, today, target)
        }
    }
}
