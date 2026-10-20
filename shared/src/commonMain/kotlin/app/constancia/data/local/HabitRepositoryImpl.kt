package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.data.HabitRepository
import app.constancia.db.ConstanciaDb
import app.constancia.domain.currentStreak
import app.constancia.domain.doneDatesFor
import app.constancia.domain.forgivenDates
import app.constancia.domain.weekDoneCount
import app.constancia.model.Checkin
import app.constancia.model.Habit
import app.constancia.model.HabitStatus
import app.constancia.model.PauseInterval
import app.constancia.model.newId
import app.constancia.model.todayYmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

// Limites para carregar "todos" os check-ins no cálculo do status.
private const val MIN_YMD = "0000-01-01"
private const val MAX_YMD = "9999-12-31"

class HabitRepositoryImpl(
    private val db: ConstanciaDb,
    private val checkins: CheckinRepository,
) : HabitRepository {

    private val queries get() = db.constanciaDbQueries

    override fun habitsFlow(): Flow<List<Habit>> =
        queries.selectHabits()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun getById(id: String): Habit? = withContext(Dispatchers.Default) {
        queries.selectHabitById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun save(habit: Habit): Unit = withContext(Dispatchers.Default) {
        val createdAt = habit.createdAt.ifBlank { Clock.System.now().toString() }
        val exists = queries.selectHabitById(habit.id).executeAsOneOrNull() != null
        if (!exists) {
            queries.insertHabit(
                habit.id,
                habit.name,
                habit.icon,
                habit.color,
                habit.goalType.name,
                habit.targetQuantity,
                habit.quantityUnit,
                habit.frequencyType.name,
                habit.daysOfWeekCsv(),
                habit.weeklyTarget.toLong(),
                habit.session.name,
                habit.reminderEnabled.toFlag(),
                habit.reminderTime,
                habit.paused.toFlag(),
                0, // archived
                createdAt,
                habit.sortOrder.toLong(),
            )
        } else {
            // updateHabit nao toca paused/archived/created_at (vias proprias).
            queries.updateHabit(
                habit.name,
                habit.icon,
                habit.color,
                habit.goalType.name,
                habit.targetQuantity,
                habit.quantityUnit,
                habit.frequencyType.name,
                habit.daysOfWeekCsv(),
                habit.weeklyTarget.toLong(),
                habit.session.name,
                habit.reminderEnabled.toFlag(),
                habit.reminderTime,
                habit.sortOrder.toLong(),
                habit.id,
            )
        }
    }

    override suspend fun setPaused(id: String, paused: Boolean): Unit =
        withContext(Dispatchers.Default) {
            val today = todayYmd()
            queries.setHabitPaused(paused.toFlag(), id)
            if (paused) {
                // Abre um intervalo de pausa: dias perdoados no streak até retomar.
                queries.openPauseInterval(newId(), id, today)
            } else {
                // Fecha intervalos abertos — o período pausado fica perdoado.
                queries.closeOpenPauseIntervals(today, id)
            }
        }

    override suspend fun pauseIntervals(habitId: String): List<PauseInterval> =
        withContext(Dispatchers.Default) {
            queries.selectPauseIntervalsForHabit(habitId).executeAsList().map { it.toModel() }
        }

    override suspend fun allPauseIntervals(): Map<String, List<PauseInterval>> =
        withContext(Dispatchers.Default) {
            queries.selectAllPauseIntervals().executeAsList().map { it.toModel() }.groupBy { it.habitId }
        }

    override suspend fun archive(id: String): Unit = withContext(Dispatchers.Default) {
        queries.archiveHabit(id)
    }

    override suspend fun delete(id: String): Unit = withContext(Dispatchers.Default) {
        queries.deleteHabit(id)
    }

    override fun todayStatusFlow(timeZone: TimeZone): Flow<List<HabitStatus>> {
        // Observa TUDO que afeta o status: hábitos, check-ins e pausas.
        // (Bug corrigido 2026-10-19: antes observava só a tabela habit, então
        // tocar no check-in gravava no banco mas a tela nunca atualizava.)
        val checkinsFlow = checkins.checkinsInRangeFlow(MIN_YMD, MAX_YMD)
        val pausesFlow: Flow<List<PauseInterval>> = queries.selectAllPauseIntervals()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }
        return combineTodayStatus(habitsFlow(), checkinsFlow, pausesFlow, timeZone)
            .flowOn(Dispatchers.Default)
    }

    override fun statusForDateFlow(dateYmd: String, timeZone: TimeZone): Flow<List<HabitStatus>> {
        val checkinsFlow = checkins.checkinsInRangeFlow(MIN_YMD, MAX_YMD)
        val pausesFlow: Flow<List<PauseInterval>> = queries.selectAllPauseIntervals()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }
        return combine(habitsFlow(), checkinsFlow, pausesFlow) { habits, checkins, pauses ->
            computeTodayStatuses(habits, checkins, pauses, dateYmd, timeZone)
        }.flowOn(Dispatchers.Default)
    }
}

/**
 * Junta os 3 fluxos e recalcula o status de hoje a cada mudança em qualquer
 * um deles. Extraído para ser testável sem banco.
 */
internal fun combineTodayStatus(
    habitsFlow: Flow<List<Habit>>,
    checkinsFlow: Flow<List<Checkin>>,
    pausesFlow: Flow<List<PauseInterval>>,
    timeZone: TimeZone,
): Flow<List<HabitStatus>> =
    combine(habitsFlow, checkinsFlow, pausesFlow) { habits, checkins, pauses ->
        computeTodayStatuses(habits, checkins, pauses, todayYmd(timeZone), timeZone)
    }

/**
 * Cálculo puro do status de hoje a partir de snapshots — síncrono e 100%
 * testável. Também elimina as N+1 queries da implementação anterior.
 */
internal fun computeTodayStatuses(
    habits: List<Habit>,
    checkins: List<Checkin>,
    pauses: List<PauseInterval>,
    today: String,
    timeZone: TimeZone,
): List<HabitStatus> {
    val checkinsByHabit = checkins.groupBy { it.habitId }
    val pausesByHabit = pauses.groupBy { it.habitId }
    return habits.map { habit ->
        val hcs = checkinsByHabit[habit.id].orEmpty()
        val todayCheckin = hcs.firstOrNull { it.date == today }
        val doneDates = doneDatesFor(habit, hcs.map { it.date to it.quantity })
        val forgiven = forgivenDates(pausesByHabit[habit.id].orEmpty(), today)
        HabitStatus(
            habit = habit,
            todayCheckin = todayCheckin,
            isDoneToday = todayCheckin != null && doneDates.contains(today),
            currentStreak = currentStreak(habit, doneDates, today, timeZone, forgiven),
            weekDone = weekDoneCount(doneDates, today),
        )
    }
}
