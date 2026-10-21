package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.db.ConstanciaDb
import app.constancia.domain.cycleDoneDays
import app.constancia.domain.doneDatesFor
import app.constancia.model.Checkin
import app.constancia.model.CheckinStatus
import app.constancia.model.GoalType
import app.constancia.model.HabitRecord
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
        val done = when {
            // Dia pulado: tocar de novo transforma o pulo em check-in feito.
            existing != null && existing.status == CheckinStatus.SKIPPED.name -> {
                queries.upsertCheckin(
                    newId(), habitId, dateYmd, quantity,
                    CheckinStatus.DONE.name, nowIso(),
                )
                isDoneNow(habitId, quantity)
            }
            existing != null -> {
                queries.deleteCheckin(habitId, dateYmd)
                false
            }
            else -> {
                queries.upsertCheckin(
                    newId(), habitId, dateYmd, quantity,
                    CheckinStatus.DONE.name, nowIso(),
                )
                // Para hábito de quantidade, "feito" exige bater a meta.
                isDoneNow(habitId, quantity)
            }
        }
        syncLinkedTasks(habitId, dateYmd, done)
        refreshCycleCompletion(habitId)
        done
    }

    override suspend fun setQuantity(
        habitId: String,
        dateYmd: String,
        quantity: Double,
    ): Unit = withContext(Dispatchers.Default) {
        // Registrar quantidade é progresso: o dia sai do estado "pulado".
        queries.upsertCheckin(
            newId(), habitId, dateYmd, quantity,
            CheckinStatus.DONE.name, nowIso(),
        )
        syncLinkedTasks(habitId, dateYmd, isDoneNow(habitId, quantity))
        refreshCycleCompletion(habitId)
    }

    override suspend fun remove(habitId: String, dateYmd: String): Unit =
        withContext(Dispatchers.Default) {
            queries.deleteCheckin(habitId, dateYmd)
            syncLinkedTasks(habitId, dateYmd, false)
            refreshCycleCompletion(habitId)
        }

    override suspend fun skip(habitId: String, dateYmd: String): Unit =
        withContext(Dispatchers.Default) {
            // Pular substitui qualquer progresso do dia: vira o registro
            // SKIPPED (quantidade 0), que o streak trata como perdoado.
            queries.upsertCheckin(
                newId(), habitId, dateYmd, 0.0,
                CheckinStatus.SKIPPED.name, nowIso(),
            )
            syncLinkedTasks(habitId, dateYmd, false)
            refreshCycleCompletion(habitId)
        }

    override suspend fun unskip(habitId: String, dateYmd: String): Unit =
        withContext(Dispatchers.Default) {
            val existing = queries.selectCheckin(habitId, dateYmd).executeAsOneOrNull()
            if (existing != null && existing.status == CheckinStatus.SKIPPED.name) {
                queries.deleteCheckin(habitId, dateYmd)
            }
        }

    override suspend fun saveRecord(
        habitId: String,
        dateYmd: String,
        content: String,
        emoji: String?,
    ): Unit = withContext(Dispatchers.Default) {
        queries.upsertRecord(newId(), habitId, dateYmd, content, emoji, nowIso())
    }

    override suspend fun recordFor(habitId: String, dateYmd: String): HabitRecord? =
        withContext(Dispatchers.Default) {
            queries.selectRecord(habitId, dateYmd).executeAsOneOrNull()?.toModel()
        }

    override fun recordsFlow(habitId: String): Flow<List<HabitRecord>> =
        queries.selectRecordsForHabit(habitId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override fun allRecordsFlow(): Flow<List<HabitRecord>> =
        queries.selectAllRecords()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

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
     * TickTick item 2: depois de qualquer escrita de check-in, confere os
     * ciclos do hábito — se os dias feitos dentro da janela bateram a
     * meta, o ciclo é marcado como concluído (permanente no histórico).
     */
    private fun refreshCycleCompletion(habitId: String) {
        val habitRow = queries.selectHabitById(habitId).executeAsOneOrNull() ?: return
        val habit = habitRow.toModel()
        val checkins = queries.selectCheckinsForHabit(habitId).executeAsList()
            .map { it.toModel() }
        // Dias pulados não contam como feitos (doneDatesFor já os exclui
        // pela quantidade 0, mas o filtro por status é a garantia).
        val doneDates = doneDatesFor(
            habit,
            checkins.filter { it.status == CheckinStatus.DONE }
                .map { it.date to it.quantity },
        )
        queries.selectCyclesForHabit(habitId).executeAsList()
            .filter { it.completed_at == null }
            .forEach { cycleRow ->
                if (cycleDoneDays(cycleRow.toModel(), doneDates) >= cycleRow.target_days) {
                    queries.completeCycle(nowIso(), cycleRow.id)
                }
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
