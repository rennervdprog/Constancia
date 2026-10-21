package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.data.PrefsRepository
import app.constancia.data.TaskRepository
import app.constancia.db.ConstanciaDb
import app.constancia.domain.autoReminderSpec
import app.constancia.domain.encodeRepeatSpec
import app.constancia.domain.effectiveRepeatSpec
import app.constancia.domain.nextOccurrence
import app.constancia.domain.nextRepeatDate
import app.constancia.domain.repeatLabel
import app.constancia.domain.taskReminderInstantMillis
import app.constancia.model.CompletionReversal
import app.constancia.model.GoalPause
import app.constancia.model.HabitCheckinReversal
import app.constancia.model.SubtaskState
import app.constancia.notify.NotificationScheduler
import app.constancia.model.GoalType
import app.constancia.model.NewTask
import app.constancia.model.ReminderSpec
import app.constancia.model.ReminderType
import app.constancia.model.SavedView
import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskOccurrence
import app.constancia.model.TaskReminder
import app.constancia.model.TaskViewOptions
import app.constancia.model.TaskProject
import app.constancia.model.TaskTemplate
import app.constancia.model.newId
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import app.constancia.model.todayYmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

class TaskRepositoryImpl(
    private val db: ConstanciaDb,
    private val checkins: CheckinRepository? = null,
    private val notifier: NotificationScheduler? = null,
    private val prefs: PrefsRepository? = null,
) : TaskRepository {

    private val queries get() = db.constanciaDbQueries

    /**
     * R2.2 (Fase B): pacotes de reversão das conclusões desta sessão,
     * por id de tarefa. É o que permite o desmarcar (em qualquer tela)
     * desfazer junto a ocorrência gerada e o check-in do hábito — sem
     * isto, desmarcar só desmarcava a linha (a assimetria da auditoria).
     */
    private val reversalsMutex = Mutex()
    private val pendingReversals = mutableMapOf<String, CompletionReversal>()

    /**
     * Fase C (R6.1): mantém os alarmes da tarefa sincronizados com a
     * LISTA de lembretes dela — cada lembrete tem o próprio alarme
     * (id da linha). Cancela e reagenda todos: futuro agenda; passado,
     * concluída ou sem instante, fica só cancelado. Sem notifier
     * (testes), não faz nada.
     */
    private suspend fun refreshReminders(id: String) {
        val scheduler = notifier ?: return
        val task = getById(id) ?: return
        val now = Clock.System.now().toEpochMilliseconds()
        task.reminders.forEach { reminder ->
            scheduler.cancelTaskReminder(reminder.id)
            val instant = taskReminderInstantMillis(task, reminder)
            if (instant != null && instant > now) {
                scheduler.scheduleTaskReminder(reminder.id, task.title, instant)
            }
        }
    }

    /** Cancela os alarmes de todos os lembretes atuais da tarefa. */
    private suspend fun cancelReminderAlarms(id: String) {
        val scheduler = notifier ?: return
        remindersForTask(id).forEach { scheduler.cancelTaskReminder(it.id) }
    }

    private fun remindersByTaskFlow(): Flow<Map<String, List<TaskReminder>>> =
        queries.selectAllTaskReminders()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() }.groupBy { it.taskId } }

    private suspend fun remindersForTask(id: String): List<TaskReminder> =
        withContext(Dispatchers.Default) {
            queries.selectRemindersForTask(id).executeAsList()
                .map { it.toModel() }
        }

    private suspend fun insertReminderRows(
        taskId: String,
        specs: List<ReminderSpec>,
        auto: Boolean = false,
    ) {
        val now = Clock.System.now().toString()
        withContext(Dispatchers.Default) {
            specs.forEach { spec ->
                queries.insertTaskReminder(
                    newId(), taskId, spec.type.name,
                    spec.minuteOffset?.toLong(), spec.atMillis,
                    if (auto) 1L else 0L, now,
                )
            }
        }
    }

    /** A coluna legada segue como espelho do 1º lembrete RELATIVE. */
    private suspend fun syncLegacyReminderColumn(taskId: String) {
        val firstRelative = remindersForTask(taskId)
            .firstOrNull { it.type == ReminderType.RELATIVE }?.minuteOffset
        withContext(Dispatchers.Default) {
            queries.setTaskReminder(firstRelative?.toLong(), taskId)
        }
    }

    override fun tasksForDateFlow(dateYmd: String): Flow<List<TaskItem>> =
        combine(
            queries.selectTasksForDate(dateYmd, dateYmd).asFlow().mapToList(Dispatchers.Default),
            allSubtasksFlow(),
            remindersByTaskFlow(),
        ) { rows, subs, reminders ->
            rows.map { it.toModel(subs[it.id].orEmpty(), reminders[it.id].orEmpty()) }
        }

    override fun allTasksFlow(): Flow<List<TaskItem>> =
        combine(
            queries.selectAllTasks().asFlow().mapToList(Dispatchers.Default),
            allSubtasksFlow(),
            remindersByTaskFlow(),
        ) { rows, subs, reminders ->
            rows.map { it.toModel(subs[it.id].orEmpty(), reminders[it.id].orEmpty()) }
        }

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
        val reminders = queries.selectRemindersForTask(id).executeAsList()
            .map { it.toModel() }
        row.toModel(subs, reminders)
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
        // Fase C (R6.1): a LISTA de lembretes nasce com a tarefa. Sem
        // lista explícita, o atalho legado (reminderMinutesBefore) vira
        // um lembrete relativo único — o mesmo comportamento de antes.
        val specs = task.reminders.ifEmpty {
            task.reminderMinutesBefore?.let {
                listOf(
                    ReminderSpec(ReminderType.RELATIVE, minuteOffset = it),
                )
            } ?: emptyList()
        }
        if (specs.isNotEmpty()) insertReminderRows(id, specs)
        // R6.2: lembrete automático materializado — tarefa com data e
        // horário e lista vazia ganha o lembrete da preferência.
        val auto = autoReminderSpec(
            prefs?.autoReminderMinutesFlow()?.first(),
            task.dueDate, task.dueTime, specs.size,
        )
        if (auto != null) insertReminderRows(id, listOf(auto), auto = true)
        if (specs.isNotEmpty() || auto != null) syncLegacyReminderColumn(id)
        refreshReminders(id)
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

    override suspend fun setDone(
        id: String,
        done: Boolean,
        completedOn: LocalDate?,
    ): CompletionReversal? {
        if (!done) {
            // Desmarcar com reversão pendente desfaz a conclusão inteira
            // (ocorrência gerada + check-in de hábito), não só a linha.
            val pending = reversalsMutex.withLock {
                pendingReversals.remove(id)
            }
            if (pending != null) {
                applyReversal(pending)
                return null
            }
            withContext(Dispatchers.Default) {
                queries.setTaskDone(false.toFlag(), null, id)
            }
            refreshReminders(id)
            return null
        }
        val before = getById(id) ?: return null
        if (before.done) return null
        val subtaskStates = before.subtasks.map {
            SubtaskState(it.id, it.done)
        }
        withContext(Dispatchers.Default) {
            queries.setTaskDone(
                true.toFlag(),
                completedAtString(completedOn),
                id,
            )
        }
        refreshReminders(id)
        val task = getById(id) ?: return null
        return recordCompletion(task, subtaskStates)
    }

    /**
     * Efeitos de uma conclusão já gravada (linha feita + completed_at):
     * snapshot no histórico (R1.1, ANTES do spawn), próxima ocorrência e
     * hábito vinculado — capturando tudo no pacote de reversão (R2.2).
     */
    private suspend fun recordCompletion(
        task: TaskItem,
        subtaskStates: List<SubtaskState>,
    ): CompletionReversal {
        val historyId = if (isRecurring(task)) {
            insertHistorySnapshot(task)
        } else {
            null
        }
        val spawnedId = spawnNextOccurrence(task)
        val habitReversal = markLinkedHabit(task)
        val reversal = CompletionReversal(
            taskId = task.id,
            taskTitle = task.title,
            spawnedTaskId = spawnedId,
            historyId = historyId,
            habitCheckin = habitReversal,
            subtaskStates = subtaskStates,
        )
        reversalsMutex.withLock { pendingReversals[task.id] = reversal }
        return reversal
    }

    /** Recorrente = carrega rótulo de repetição ou spec estruturada. */
    private fun isRecurring(task: TaskItem): Boolean =
        task.repeatRule.isNotBlank() || task.repeatSpec.isNotBlank()

    /** R1.1: grava o snapshot da ocorrência concluída no histórico. */
    private suspend fun insertHistorySnapshot(task: TaskItem): String {
        val historyId = newId()
        withContext(Dispatchers.Default) {
            queries.insertOccurrenceHistory(
                historyId,
                task.id,
                task.dueDate,
                task.title,
                task.project.toDbValue(),
                task.priority.toLong(),
                task.deadlineDate,
                task.durationMinutes?.toLong(),
                task.completedAt ?: completedAtString(null),
            )
        }
        return historyId
    }

    override suspend fun undoCompletion(reversal: CompletionReversal) {
        reversalsMutex.withLock {
            if (pendingReversals[reversal.taskId] == reversal) {
                pendingReversals.remove(reversal.taskId)
            }
        }
        applyReversal(reversal)
    }

    /**
     * R2.2: aplica o pacote de reversão — o banco volta ao estado de
     * antes da conclusão (tarefa, subtarefas, ocorrência gerada,
     * histórico e check-in de hábito).
     */
    private suspend fun applyReversal(reversal: CompletionReversal) {
        // 1) Ocorrência gerada: apaga (subtarefas primeiro) + lembretes.
        val spawnedId = reversal.spawnedTaskId
        if (spawnedId != null) {
            cancelReminderAlarms(spawnedId)
            withContext(Dispatchers.Default) {
                queries.deleteSubtasksForTask(spawnedId)
                queries.deleteTask(spawnedId)
            }
        }
        // 2) O snapshot do histórico sai junto com a conclusão.
        val historyId = reversal.historyId
        if (historyId != null) {
            withContext(Dispatchers.Default) {
                queries.deleteOccurrenceHistory(historyId)
            }
        }
        // 3) Check-in de hábito: volta à quantidade anterior (ou some,
        // se a conclusão o criou). Antes de reabrir a tarefa porque a
        // sincronia do check-in também mexe no "done" das vinculadas.
        val habitReversal = reversal.habitCheckin
        val checkinRepo = checkins
        if (habitReversal != null && checkinRepo != null) {
            val previous = habitReversal.previousQuantity
            if (previous == null) {
                checkinRepo.remove(habitReversal.habitId, habitReversal.date)
            } else {
                checkinRepo.setQuantity(
                    habitReversal.habitId,
                    habitReversal.date,
                    previous,
                )
            }
        }
        // 4) Subtarefas e a tarefa voltam ao estado anterior.
        withContext(Dispatchers.Default) {
            reversal.subtaskStates.forEach { state ->
                queries.setSubtaskDone(state.done.toFlag(), state.subtaskId)
            }
            queries.setTaskDone(false.toFlag(), null, reversal.taskId)
        }
        refreshReminders(reversal.taskId)
    }

    override fun occurrenceHistoryFlow(): Flow<List<TaskOccurrence>> =
        queries.selectOccurrenceHistory()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override fun viewOptionsFlow(viewKey: String): Flow<TaskViewOptions> =
        queries.selectViewOptions()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.firstOrNull { it.view_key == viewKey }?.toModel()
                    ?: TaskViewOptions(viewKey = viewKey)
            }

    override suspend fun setViewOptions(
        viewKey: String,
        groupBy: String,
        sortMode: String,
    ): Unit = withContext(Dispatchers.Default) {
        queries.upsertViewOptions(viewKey, groupBy, sortMode)
    }

    /**
     * Instante gravado em completed_at: o real (agora) ou, para o R2.3
     * ("concluir como de ontem"), o meio-dia UTC do dia pedido — as
     * metas leem o dia de conclusão pelos 10 primeiros caracteres da
     * string ISO, então o dia gravado é exatamente o dia pedido.
     */
    private fun completedAtString(completedOn: LocalDate?): String =
        if (completedOn == null) {
            Clock.System.now().toString()
        } else {
            LocalDateTime(completedOn, LocalTime(12, 0))
                .toInstant(TimeZone.UTC).toString()
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
        refreshReminders(id)
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
        var statesBefore: List<SubtaskState> = emptyList()
        withContext(Dispatchers.Default) {
            val beforeRows = queries.selectAllSubtasks().executeAsList()
            val sub = beforeRows.firstOrNull { it.id == subtaskId }
                ?: return@withContext
            // Estado das subtarefas ANTES do toque (pacote de reversão).
            statesBefore = beforeRows
                .filter { it.task_id == sub.task_id }
                .map { SubtaskState(it.id, it.done.isSet()) }
            queries.setSubtaskDone(done.toFlag(), subtaskId)
            // Todas as subtarefas feitas -> a tarefa conclui junto (protótipo).
            val siblings = queries.selectAllSubtasks().executeAsList()
                .filter { it.task_id == sub.task_id }
            if (siblings.isNotEmpty() && siblings.all { it.done.isSet() }) {
                val taskRow = queries.selectTaskById(sub.task_id).executeAsOneOrNull()
                if (taskRow != null && !taskRow.done.isSet()) {
                    queries.setTaskDone(
                        1,
                        completedAtString(null),
                        sub.task_id,
                    )
                    completedTaskId = sub.task_id
                }
            }
        }
        // Efeitos colaterais da conclusão (histórico, recorrência e
        // vínculo com hábito), com pacote de reversão como no setDone.
        val taskId = completedTaskId ?: return
        val task = getById(taskId) ?: return
        recordCompletion(task, statesBefore)
        refreshReminders(taskId)
    }

    override suspend fun delete(id: String) {
        reversalsMutex.withLock { pendingReversals.remove(id) }
        cancelReminderAlarms(id)
        withContext(Dispatchers.Default) {
            queries.deleteTask(id)
        }
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
        // Atalho legado de 1 lembrete: hoje ele escreve na LISTA (R6.1).
        setTaskReminders(
            id,
            minutesBefore?.let {
                listOf(ReminderSpec(ReminderType.RELATIVE, minuteOffset = it))
            } ?: emptyList(),
        )
    }

    override suspend fun setTaskReminders(
        id: String,
        specs: List<ReminderSpec>,
    ) {
        // Os alarmes das linhas antigas saem pelo id antes da troca.
        cancelReminderAlarms(id)
        withContext(Dispatchers.Default) {
            queries.deleteRemindersForTask(id)
        }
        if (specs.isNotEmpty()) insertReminderRows(id, specs)
        syncLegacyReminderColumn(id)
        refreshReminders(id)
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
     * Devolve o id da ocorrência criada (null = nada foi gerado) — é o
     * que o pacote de reversão (R2.2) apaga ao desfazer.
     */
    private suspend fun spawnNextOccurrence(task: TaskItem): String? {
        val due = task.dueDate ?: return null
        val dueDate = due.toLocalDate()
        val spec = effectiveRepeatSpec(task.repeatSpec, task.repeatRule, dueDate)
        // Dia efetivo da conclusão (completed_at pode ser "como de ontem"
        // pelo R2.3); é a referência do laço R4.1 e da base COMPLETION.
        val completedOn = task.completedAt?.take(10)?.let {
            runCatching { it.toLocalDate() }.getOrNull()
        } ?: todayYmd().toLocalDate()
        val next = if (spec != null) {
            nextOccurrence(spec, dueDate, completedOn)
        } else {
            nextRepeatDate(task.repeatRule, dueDate, completedOn)
        } ?: return null
        val nextYmd = next.toYmd()
        // Higiene (Fase A): a deduplicação compara a SPEC estruturada da
        // série (normalizada), não só o rótulo — rótulo legado divergente
        // não pode nem duplicar nem esconder a ocorrência.
        val expectedSpec = if (spec != null) {
            encodeRepeatSpec(spec)
        } else {
            task.repeatSpec
        }
        val already = withContext(Dispatchers.Default) {
            queries.selectTasksForDate(nextYmd, nextYmd).executeAsList().any {
                it.title == task.title && it.habit_id == task.habitId &&
                    it.repeat_spec == expectedSpec &&
                    (spec != null || it.repeat_rule == task.repeatRule)
            }
        }
        if (already) return null
        val shiftDays = next.toEpochDays() - dueDate.toEpochDays()
        val nextDeadline = task.deadlineDate?.toLocalDate()
            ?.plus(DatePeriod(days = shiftDays.toInt()))?.toYmd()
        return add(
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
                // Fase C: a próxima ocorrência herda os lembretes
                // RELATIVOS explícitos; o automático (auto = 1) não é
                // copiado — ele se materializa de novo no add() — e o
                // absoluto é do dia, não da série.
                reminders = task.reminders
                    .filter { !it.auto && it.type == ReminderType.RELATIVE }
                    .map {
                        ReminderSpec(it.type, it.minuteOffset, it.atMillis)
                    },
                repeatSpec = if (spec != null) encodeRepeatSpec(spec) else "",
            ),
        )
    }

    /**
     * Vínculo tarefa -> hábito (igual ao protótipo): concluir a tarefa com
     * prazo hoje/ontem marca o hábito de hoje. Devolve como reverter o
     * check-in (R2.2): a quantidade de hoje ANTES desta conclusão
     * (null no pacote = não havia check-in; null no retorno = o hábito
     * não foi tocado).
     */
    private suspend fun markLinkedHabit(task: TaskItem): HabitCheckinReversal? {
        val repo = checkins ?: return null
        val habitId = task.habitId ?: return null
        val today = todayYmd()
        val yesterday = today.toLocalDate().minus(DatePeriod(days = 1)).toYmd()
        if (task.dueDate != today && task.dueDate != yesterday) return null
        val habitRow = withContext(Dispatchers.Default) {
            queries.selectHabitById(habitId).executeAsOneOrNull()
        } ?: return null
        val current = repo.get(habitId, today)
        val isQuantity = habitRow.goal_type == GoalType.QUANTITY.name
        val target = habitRow.target_quantity
        val doneNow = current != null &&
            (if (isQuantity) current.quantity >= target else current.quantity >= 1.0)
        if (doneNow) return null
        if (current == null) {
            repo.toggle(habitId, today, if (isQuantity) target else 1.0)
            return HabitCheckinReversal(habitId, today, null)
        } else if (isQuantity) {
            repo.setQuantity(habitId, today, target)
            return HabitCheckinReversal(habitId, today, current.quantity)
        }
        return null
    }
}
