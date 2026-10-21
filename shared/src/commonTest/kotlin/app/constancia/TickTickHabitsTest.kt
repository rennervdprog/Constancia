package app.constancia

import app.constancia.data.local.computeTodayStatuses
import app.constancia.domain.HeatCell
import app.constancia.domain.cycleDoneDays
import app.constancia.domain.cycleState
import app.constancia.domain.CycleState
import app.constancia.domain.currentStreak
import app.constancia.domain.doneDatesFor
import app.constancia.domain.donePairs
import app.constancia.domain.heatGrid
import app.constancia.domain.isCycleComplete
import app.constancia.domain.isGoalMet
import app.constancia.domain.nextCycleWindow
import app.constancia.domain.cycleStreak
import app.constancia.domain.skippedDatesFor
import app.constancia.model.Checkin
import app.constancia.model.CheckinStatus
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.HabitCycle
import app.constancia.model.Session
import app.constancia.model.toLocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val TT_TZ = TimeZone.UTC

private fun ttHabit(
    goalType: GoalType = GoalType.BINARY,
    targetQuantity: Double = 1.0,
    quantityStep: Double = 1.0,
) = Habit(
    id = "h1",
    name = "Beber água",
    icon = "water",
    color = 0L,
    goalType = goalType,
    targetQuantity = targetQuantity,
    quantityUnit = "copos",
    frequencyType = FrequencyType.DAILY,
    daysOfWeek = emptyList(),
    weeklyTarget = 3,
    session = Session.MANHA,
    reminderEnabled = false,
    reminderTime = "",
    paused = false,
    createdAt = "2026-01-01",
    sortOrder = 0,
    quantityStep = quantityStep,
)

private fun ttCheckin(
    date: String,
    qty: Double = 1.0,
    status: CheckinStatus = CheckinStatus.DONE,
) = Checkin(id = "c-$date", habitId = "h1", date = date, quantity = qty, status = status)

/**
 * Backlog TickTick (schema v6): meta numérica com passo e progresso
 * parcial (item 1), ciclos/temporadas (item 2) e skip como registro
 * (item 4) — lógica pura de domínio.
 */
class TickTickHabitsTest {

    // ---------- Item 1: meta numérica + passo + parcial ----------

    @Test
    fun partialProgressDoesNotCompleteGoal() {
        val h = ttHabit(goalType = GoalType.QUANTITY, targetQuantity = 8.0)
        assertFalse(isGoalMet(h, 5.0))
        assertFalse(isGoalMet(h, 7.5))
        // doneDatesFor também não conta o dia parcial como feito.
        val done = doneDatesFor(h, listOf("2026-10-19" to 5.0))
        assertTrue(done.isEmpty())
    }

    @Test
    fun reachingGoalCompletesTheDay() {
        val h = ttHabit(goalType = GoalType.QUANTITY, targetQuantity = 8.0)
        assertTrue(isGoalMet(h, 8.0))
        assertTrue(isGoalMet(h, 9.0))
        val done = doneDatesFor(h, listOf("2026-10-19" to 8.0))
        assertEquals(setOf("2026-10-19"), done)
    }

    @Test
    fun steppingByStepReachesGoalExactly() {
        // Passo de 2 em 2: 4 toques no "+" batem a meta de 8 copos.
        val h = ttHabit(
            goalType = GoalType.QUANTITY, targetQuantity = 8.0, quantityStep = 2.0,
        )
        var qty = 0.0
        repeat(3) { qty += h.quantityStep }
        assertFalse(isGoalMet(h, qty)) // 6 de 8: parcial
        qty += h.quantityStep
        assertEquals(8.0, qty)
        assertTrue(isGoalMet(h, qty))
    }

    @Test
    fun streakWithGoalIgnoresPartialDays() {
        val h = ttHabit(goalType = GoalType.QUANTITY, targetQuantity = 8.0)
        // Hoje bateu a meta; ontem foi parcial (não conta e quebra).
        val done = doneDatesFor(
            h,
            listOf("2026-10-19" to 8.0, "2026-10-18" to 3.0, "2026-10-17" to 8.0),
        )
        assertEquals(1, currentStreak(h, done, "2026-10-19", TT_TZ))
        // Com a meta batida nos 3 dias, o streak é 3.
        val full = doneDatesFor(
            h,
            listOf("2026-10-19" to 8.0, "2026-10-18" to 8.0, "2026-10-17" to 10.0),
        )
        assertEquals(3, currentStreak(h, full, "2026-10-19", TT_TZ))
    }

    @Test
    fun heatmapShowsPartialProgressCell() {
        val h = ttHabit(goalType = GoalType.QUANTITY, targetQuantity = 8.0)
        val today = "2026-10-21".toLocalDate()
        val grid = heatGrid(
            h,
            listOf(
                ttCheckin("2026-10-19", qty = 5.0), // parcial
                ttCheckin("2026-10-21", qty = 8.0), // meta batida hoje
            ),
            today,
        )
        val lastWeek = grid.last() // dom 18 .. sáb 24
        assertEquals(HeatCell.PARTIAL, lastWeek[1]) // segunda 19: progresso sem meta
        assertEquals(HeatCell.DONE, lastWeek[3]) // hoje: meta batida
    }

    // ---------- Item 4: skip como registro ----------

    @Test
    fun skippedDayIsARecordNotAnAbsence() {
        val checkins = listOf(ttCheckin("2026-10-18", status = CheckinStatus.SKIPPED))
        assertEquals(setOf("2026-10-18"), skippedDatesFor(checkins))
        // E o dia pulado NÃO entra nos dias feitos — quem manda é o
        // status (donePairs), não a quantidade gravada.
        val h = ttHabit()
        assertTrue(doneDatesFor(h, donePairs(checkins)).isEmpty())
    }

    @Test
    fun skipDoesNotBreakStreak() {
        val h = ttHabit()
        val checkins = listOf(
            ttCheckin("2026-10-17"),
            ttCheckin("2026-10-18", status = CheckinStatus.SKIPPED),
            ttCheckin("2026-10-19"),
        )
        val s = computeTodayStatuses(
            listOf(h), checkins, emptyList(), "2026-10-19", TT_TZ,
        ).single()
        assertTrue(s.isDoneToday)
        // 19 feito + 17 feito, com o 18 pulado no meio: sequência de 2.
        assertEquals(2, s.currentStreak)
    }

    @Test
    fun absenceBreaksStreakUnlikeSkip() {
        val h = ttHabit()
        // Mesmo desenho do teste do skip, mas SEM registro no dia 18.
        val checkins = listOf(ttCheckin("2026-10-17"), ttCheckin("2026-10-19"))
        val s = computeTodayStatuses(
            listOf(h), checkins, emptyList(), "2026-10-19", TT_TZ,
        ).single()
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun skippingTodayKeepsStreakFromYesterday() {
        val h = ttHabit()
        val checkins = listOf(
            ttCheckin("2026-10-17"),
            ttCheckin("2026-10-18"),
            ttCheckin("2026-10-19", status = CheckinStatus.SKIPPED),
        )
        val s = computeTodayStatuses(
            listOf(h), checkins, emptyList(), "2026-10-19", TT_TZ,
        ).single()
        assertFalse(s.isDoneToday) // pulado não é feito
        assertEquals(2, s.currentStreak) // mas a sequência sobrevive
    }

    @Test
    fun heatmapShowsSkippedCell() {
        val h = ttHabit()
        val today = "2026-10-21".toLocalDate()
        val grid = heatGrid(
            h,
            listOf(ttCheckin("2026-10-20", status = CheckinStatus.SKIPPED)),
            today,
        )
        assertEquals(HeatCell.SKIPPED, grid.last()[2]) // terça 20 pulada
    }

    // ---------- Item 2: ciclos/temporadas ----------

    private fun cycle(
        start: String = "2026-10-01",
        end: String = "2026-10-07",
        target: Int = 5,
        completedAt: String? = null,
    ) = HabitCycle(
        id = "cy1", habitId = "h1", startDate = start, endDate = end,
        targetDays = target, completedAt = completedAt,
    )

    @Test
    fun cycleCountsOnlyDaysInsideWindow() {
        val c = cycle()
        val done = setOf("2026-09-30", "2026-10-01", "2026-10-03", "2026-10-08")
        assertEquals(2, cycleDoneDays(c, done)) // só 01 e 03 estão na janela
    }

    @Test
    fun cycleCompletesWhenTargetReached() {
        val c = cycle(target = 3)
        val done = setOf("2026-10-01", "2026-10-02", "2026-10-04")
        assertTrue(isCycleComplete(c, done))
        assertEquals(CycleState.COMPLETED, cycleState(c, done, "2026-10-05"))
        // Antes da meta: segue ativo dentro da janela.
        assertEquals(
            CycleState.ACTIVE,
            cycleState(c, setOf("2026-10-01"), "2026-10-05"),
        )
    }

    @Test
    fun cycleExpiresAfterWindowWithoutTarget() {
        val c = cycle(target = 5)
        val done = setOf("2026-10-01", "2026-10-02")
        assertFalse(isCycleComplete(c, done))
        assertEquals(CycleState.EXPIRED, cycleState(c, done, "2026-10-08"))
    }

    @Test
    fun completedCycleStaysCompletedForever() {
        // Ciclo concluído (completedAt gravado) continua concluído mesmo
        // depois da janela — é o histórico permanente do hábito.
        val c = cycle(completedAt = "2026-10-05T10:00:00Z")
        assertEquals(CycleState.COMPLETED, cycleState(c, emptySet(), "2026-12-25"))
    }

    @Test
    fun cycleStreakCountsInsideWindowAndForgivesSkips() {
        val c = cycle(start = "2026-10-01", end = "2026-10-07")
        val done = setOf("2026-10-05", "2026-10-07")
        val forgiven = setOf("2026-10-06") // dia pulado
        assertEquals(2, cycleStreak(c, done, forgiven, "2026-10-07"))
        // Sem o perdão do pulo, a sequência do ciclo é só o dia de hoje.
        assertEquals(1, cycleStreak(c, done, emptySet(), "2026-10-07"))
        // Antes do início do ciclo, streak do ciclo é 0.
        assertEquals(0, cycleStreak(c, done, forgiven, "2026-09-30"))
    }

    @Test
    fun nextCycleWindowStartsWhereTold() {
        val (start, end) = nextCycleWindow("2026-10-21", 21)
        assertEquals("2026-10-21", start)
        assertEquals("2026-11-10", end) // 21 dias inclusive
        val (s2, e2) = nextCycleWindow("2026-10-21", 1)
        assertEquals(s2, e2) // ciclo de 1 dia: começa e termina no mesmo dia
    }
}
