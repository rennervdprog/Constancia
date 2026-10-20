package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.data.TaskRepository
import app.constancia.db.ConstanciaDb
import app.constancia.domain.nextRepeatDate
import app.constancia.model.GoalType
import app.constancia.model.NewTask
import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
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

class TaskRepositoryImpl(
    private val db: ConstanciaDb,
    private val checkins: CheckinRepository? = null,
) : TaskRepository {

    private val queries get() = db.constanciaDbQueries

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
            task.note,
            task.habitId,
            now,
            0, // sort_order
        )
        task.subtaskTitles.forEachIndexed { index, title ->
            queries.insertSubtask(newId(), id, title, 0, index.toLong(), now)
        }
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
            queries.setTaskDone(done.toFlag(), id)
        }
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

    override suspend fun setDueDate(id: String, dueDate: String?): Unit =
        withContext(Dispatchers.Default) {
            val current = queries.selectTaskById(id).executeAsOneOrNull()
            // Sem prazo novo, o "date" mantém o dia atual da tarefa.
            queries.setTaskDueDate(dueDate, dueDate ?: (current?.date ?: todayYmd()), id)
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
                    queries.setTaskDone(1, sub.task_id)
                    completedTaskId = sub.task_id
                }
            }
        }
        // Efeitos colaterais da conclusão (recorrência + vínculo com hábito).
        val taskId = completedTaskId ?: return
        val task = getById(taskId) ?: return
        spawnNextOccurrence(task)
        markLinkedHabit(task)
    }

    override suspend fun delete(id: String): Unit = withContext(Dispatchers.Default) {
        queries.deleteTask(id)
    }

    /**
     * Tarefa recorrente concluída -> cria a próxima ocorrência (mesmos
     * campos, subtarefas zeradas), se ainda não existir uma igual no dia.
     */
    private suspend fun spawnNextOccurrence(task: TaskItem) {
        val due = task.dueDate ?: return
        val next = nextRepeatDate(task.repeatRule, due.toLocalDate()) ?: return
        val nextYmd = next.toYmd()
        val already = withContext(Dispatchers.Default) {
            queries.selectTasksForDate(nextYmd, nextYmd).executeAsList().any {
                it.title == task.title && it.repeat_rule == task.repeatRule &&
                    it.habit_id == task.habitId
            }
        }
        if (already) return
        add(
            NewTask(
                title = task.title,
                note = task.note,
                project = task.project,
                priority = task.priority,
                dueDate = nextYmd,
                session = task.session,
                dueTime = task.dueTime,
                repeatRule = task.repeatRule,
                habitId = task.habitId,
                subtaskTitles = task.subtasks.map { it.title },
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
