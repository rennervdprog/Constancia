package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.TaskRepository
import app.constancia.db.ConstanciaDb
import app.constancia.model.TaskItem
import app.constancia.model.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class TaskRepositoryImpl(private val db: ConstanciaDb) : TaskRepository {

    private val queries get() = db.constanciaDbQueries

    override fun tasksForDateFlow(dateYmd: String): Flow<List<TaskItem>> =
        queries.selectTasksForDate(dateYmd)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun add(title: String, dateYmd: String, dueTime: String?): Unit =
        withContext(Dispatchers.Default) {
            queries.insertTask(
                newId(),
                title,
                dueTime,
                0, // done
                dateYmd,
                Clock.System.now().toString(),
                0, // sort_order
            )
        }

    override suspend fun setDone(id: String, done: Boolean): Unit =
        withContext(Dispatchers.Default) {
            queries.setTaskDone(done.toFlag(), id)
        }

    override suspend fun delete(id: String): Unit = withContext(Dispatchers.Default) {
        queries.deleteTask(id)
    }
}
