package app.constancia.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.constancia.data.CheckinRepository
import app.constancia.data.HabitRepository
import app.constancia.db.ConstanciaDb
import app.constancia.domain.doneDatesFor
import app.constancia.domain.donePairs
import app.constancia.domain.forgivenDates
import app.constancia.domain.habitStreakInfo
import app.constancia.domain.nextCycleWindow
import app.constancia.domain.skippedDatesFor
import app.constancia.domain.weekDoneCount
import app.constancia.model.Checkin
import app.constancia.model.Habit
import app.constancia.model.HabitCycle
import app.constancia.model.HabitReminder
import app.constancia.model.HabitStatus
import app.constancia.model.PauseInterval
import app.constancia.model.newId
import app.constancia.model.todayYmd
import app.constancia.domain.parseTimeOrNull
import app.constancia.notify.NotificationScheduler
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
    private val notifier: NotificationScheduler? = null,
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
                habit.quantityStep,
                habit.showInToday.toFlag(),
                habit.showInCalendar.toFlag(),
                habit.ignoreWeekdaysCsv(),
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
            // Fase C: hábito que já nasce com o horário legado ligado
            // ganha a primeira linha da lista de lembretes.
            if (habit.reminderEnabled &&
                parseTimeOrNull(habit.reminderTime) != null
            ) {
                queries.insertHabitReminder(
                    newId(), habit.id, habit.reminderTime,
                    Clock.System.now().toString(),
                )
            }
        } else {
            // updateHabit nao toca paused/archived/created_at (vias proprias).
            queries.updateHabit(
                habit.name,
                habit.icon,
                habit.color,
                habit.goalType.name,
                habit.targetQuantity,
                habit.quantityUnit,
                habit.quantityStep,
                habit.showInToday.toFlag(),
                habit.showInCalendar.toFlag(),
                habit.ignoreWeekdaysCsv(),
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
        // O nome pode ter mudado: os alarmes são repostos com o nome atual.
        refreshHabitReminderAlarms(habit.id)
    }

    override suspend fun setPaused(id: String, paused: Boolean) {
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
        // Hábito pausado não apita; ao retomar, os alarmes voltam.
        refreshHabitReminderAlarms(id)
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

    override fun cyclesFlow(habitId: String): Flow<List<HabitCycle>> =
        queries.selectCyclesForHabit(habitId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override fun allCyclesFlow(): Flow<List<HabitCycle>> =
        queries.selectAllCycles()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun startCycle(
        habitId: String,
        targetDays: Int,
        startYmd: String,
    ): HabitCycle = withContext(Dispatchers.Default) {
        val (start, end) = nextCycleWindow(startYmd, targetDays)
        val cycle = HabitCycle(
            id = newId(),
            habitId = habitId,
            startDate = start,
            endDate = end,
            targetDays = targetDays,
            completedAt = null,
        )
        queries.insertCycle(
            cycle.id, cycle.habitId, cycle.startDate, cycle.endDate,
            cycle.targetDays.toLong(), null, Clock.System.now().toString(),
        )
        cycle
    }

    override suspend fun delete(id: String) {
        // Os alarmes saem pelo id antes de as linhas sumirem (CASCADE).
        val scheduler = notifier
        if (scheduler != null) {
            remindersOf(id).forEach { scheduler.cancelHabitReminder(it.id) }
        }
        withContext(Dispatchers.Default) {
            queries.deleteHabit(id)
        }
    }

    // -- Fase C: lembretes múltiplos de hábito --

    override fun habitRemindersFlow(habitId: String): Flow<List<HabitReminder>> =
        queries.selectRemindersForHabit(habitId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override fun allHabitRemindersFlow(): Flow<List<HabitReminder>> =
        queries.selectAllHabitReminders()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun setHabitReminders(habitId: String, times: List<String>) {
        val scheduler = notifier
        if (scheduler != null) {
            remindersOf(habitId).forEach { scheduler.cancelHabitReminder(it.id) }
        }
        val clean = times
            .mapNotNull { parseTimeOrNull(it.trim()) }
            .map {
                it.hour.toString().padStart(2, '0') + ":" +
                    it.minute.toString().padStart(2, '0')
            }
            .distinct()
            .sorted()
        val now = Clock.System.now().toString()
        withContext(Dispatchers.Default) {
            queries.deleteRemindersForHabit(habitId)
            clean.forEach { time ->
                queries.insertHabitReminder(newId(), habitId, time, now)
            }
            // Espelho: ligado + primeiro horário (ordenação/exibição).
            queries.setHabitReminderMirror(
                if (clean.isEmpty()) 0L else 1L,
                clean.firstOrNull() ?: "",
                habitId,
            )
        }
        refreshHabitReminderAlarms(habitId)
    }

    private suspend fun remindersOf(habitId: String): List<HabitReminder> =
        withContext(Dispatchers.Default) {
            queries.selectRemindersForHabit(habitId).executeAsList()
                .map { it.toModel() }
        }

    /**
     * Repõe os alarmes diários do hábito a partir das linhas atuais
     * (cancela todos e agenda de novo). Hábito pausado/arquivado ou
     * inexistente fica sem alarme. Sem notifier (testes), nada faz.
     */
    private suspend fun refreshHabitReminderAlarms(habitId: String) {
        val scheduler = notifier ?: return
        val rows = remindersOf(habitId)
        rows.forEach { scheduler.cancelHabitReminder(it.id) }
        val habit = getById(habitId) ?: return
        if (habit.paused) return
        rows.forEach { row ->
            val time = parseTimeOrNull(row.time) ?: return@forEach
            scheduler.scheduleHabitReminder(
                row.id, habit.name, time.hour, time.minute,
            )
        }
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
        val doneDates = doneDatesFor(habit, donePairs(hcs))
        // Pausas E dias pulados (item 4) perdoam o streak.
        val forgiven = forgivenDates(pausesByHabit[habit.id].orEmpty(), today) +
            skippedDatesFor(hcs)
        // R13.2 (Fase D): a sequência como objeto (início/fim/recorde);
        // o descanso planejado (R13.1) entra pelo próprio hábito.
        val info = habitStreakInfo(habit, doneDates, forgiven, today)
        HabitStatus(
            habit = habit,
            todayCheckin = todayCheckin,
            isDoneToday = todayCheckin != null && doneDates.contains(today),
            currentStreak = info.count,
            weekDone = weekDoneCount(doneDates, today),
            streakInfo = info,
        )
    }
}
