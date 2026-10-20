package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.db.ConstanciaDb
import app.constancia.model.Checkin
import app.constancia.model.GoalType
import app.constancia.model.newId
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import app.constancia.model.todayYmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus

class CheckinRepositoryImpl(private val db: ConstanciaDb) : CheckinRepository {

    private val queries get() = db.constanciaDbQueries

    override suspend fun get(habitId: String, dateYmd: String): Checkin? =
        withContext(Dispatchers.Default) {
            queries.selectCheckin(habitId, dateYmd).executeAsOneOrNull()?.toModel()
        }

    // Retorna true se o dia ficou marcado como feito apos o toggle.
    override suspend fun toggle(
        habitId: String,
        dateYmd: String,
        quantity: Double,
    ): Boolean = withContext(Dispatchers.Default) {
        val existing = queries.selectCheckin(habitId, dateYmd).executeAsOneOrNull()
        val done = if (existing != null) {
            queries.deleteCheckin(habitId, dateYmd)
            false
        } else {
            queries.upsertCheckin(newId(), habitId, dateYmd, quantity, nowIso())
            // Para hábito de quantidade, "feito" exige bater a meta.
            isDoneNow(habitId, quantity)
        }
        syncLinkedTasks(habitId, dateYmd, done)
        done
    }

    override suspend fun setQuantity(
        habitId: String,
        dateYmd: String,
        quantity: Double,
    ): Unit = withContext(Dispatchers.Default) {
        queries.upsertCheckin(newId(), habitId, dateYmd, quantity, nowIso())
        syncLinkedTasks(habitId, dateYmd, isDoneNow(habitId, quantity))
    }

    override suspend fun remove(habitId: String, dateYmd: String): Unit =
        withContext(Dispatchers.Default) {
            queries.deleteCheckin(habitId, dateYmd)
            syncLinkedTasks(habitId, dateYmd, false)
        }

    override fun checkinsInRangeFlow(fromYmd: String, toYmd: String): Flow<List<Checkin>> =
        queries.selectCheckinsInRange(fromYmd, toYmd)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun checkinsForHabit(habitId: String): List<Checkin> =
        withContext(Dispatchers.Default) {
            queries.selectCheckinsForHabit(habitId).executeAsList().map { it.toModel() }
        }

    private fun nowIso(): String = Clock.System.now().toString()

    /** O dia conta como feito com esta quantidade? (meta do hábito no banco). */
    private fun isDoneNow(habitId: String, quantity: Double): Boolean {
        val habit = queries.selectHabitById(habitId).executeAsOneOrNull() ?: return quantity >= 1.0
        return if (habit.goal_type == GoalType.QUANTITY.name) {
            quantity >= habit.target_quantity
        } else {
            quantity >= 1.0
        }
    }

    /**
     * Vínculo hábito -> tarefa (igual ao protótipo): mudar o check-in de HOJE
     * marca/desmarca as tarefas vinculadas com prazo hoje ou ontem.
     * Check-ins de outros dias não mexem nas tarefas.
     */
    private fun syncLinkedTasks(habitId: String, dateYmd: String, done: Boolean) {
        val today = todayYmd()
        if (dateYmd != today) return
        val yesterday = today.toLocalDate().minus(DatePeriod(days = 1)).toYmd()
        queries.setLinkedTasksDone(done.toFlag(), habitId, today, yesterday)
    }
}
