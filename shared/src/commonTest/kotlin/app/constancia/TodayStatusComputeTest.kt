package app.constancia

import app.constancia.data.local.computeTodayStatuses
import app.constancia.model.Checkin
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.PauseInterval
import app.constancia.model.Session
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val STATUS_TZ = TimeZone.UTC
private const val STATUS_TODAY = "2026-10-19"

private fun statusHabit(
    id: String = "h1",
    goalType: GoalType = GoalType.BINARY,
    targetQuantity: Double = 1.0,
    frequencyType: FrequencyType = FrequencyType.DAILY,
    weeklyTarget: Int = 3,
) = Habit(
    id = id,
    name = "Teste",
    icon = "check",
    color = 0L,
    goalType = goalType,
    targetQuantity = targetQuantity,
    quantityUnit = "vezes",
    frequencyType = frequencyType,
    daysOfWeek = emptyList(),
    weeklyTarget = weeklyTarget,
    session = Session.MANHA,
    reminderEnabled = false,
    reminderTime = "",
    paused = false,
    createdAt = "",
    sortOrder = 0,
)

private fun statusCheckin(habitId: String, date: String, qty: Double = 1.0) =
    Checkin(id = "c-$habitId-$date", habitId = habitId, date = date, quantity = qty)

/**
 * Lógica pura por trás do todayStatusFlow: garante que mudanças em
 * check-ins/pausas se refletem no status (bug #2 — o Flow antigo só
 * observava a tabela habit e a tela nunca atualizava).
 */
class TodayStatusComputeTest {

    @Test
    fun toggleCheckinFlipsIsDoneToday() {
        val h = statusHabit()
        val before = computeTodayStatuses(
            listOf(h), emptyList(), emptyList(), STATUS_TODAY, STATUS_TZ,
        )
        assertFalse(before.single().isDoneToday)
        assertEquals(0, before.single().currentStreak)

        val after = computeTodayStatuses(
            listOf(h),
            listOf(statusCheckin("h1", STATUS_TODAY)),
            emptyList(),
            STATUS_TODAY,
            STATUS_TZ,
        )
        assertTrue(after.single().isDoneToday)
        assertEquals(1, after.single().currentStreak)

        // Toggle off (check-in removido) volta a false.
        val off = computeTodayStatuses(
            listOf(h), emptyList(), emptyList(), STATUS_TODAY, STATUS_TZ,
        )
        assertFalse(off.single().isDoneToday)
        assertEquals(0, off.single().currentStreak)
    }

    @Test
    fun consecutiveDaysBuildStreak() {
        val h = statusHabit()
        val checkins = listOf(
            statusCheckin("h1", "2026-10-17"),
            statusCheckin("h1", "2026-10-18"),
            statusCheckin("h1", STATUS_TODAY),
        )
        val s = computeTodayStatuses(
            listOf(h), checkins, emptyList(), STATUS_TODAY, STATUS_TZ,
        ).single()
        assertTrue(s.isDoneToday)
        assertEquals(3, s.currentStreak)
    }

    @Test
    fun gapBreaksStreak() {
        val h = statusHabit()
        val checkins = listOf(
            statusCheckin("h1", "2026-10-17"),
            statusCheckin("h1", STATUS_TODAY),
        )
        val s = computeTodayStatuses(
            listOf(h), checkins, emptyList(), STATUS_TODAY, STATUS_TZ,
        ).single()
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun quantityBelowTargetIsNotDone() {
        val h = statusHabit(goalType = GoalType.QUANTITY, targetQuantity = 8.0)
        val partial = computeTodayStatuses(
            listOf(h),
            listOf(statusCheckin("h1", STATUS_TODAY, qty = 5.0)),
            emptyList(),
            STATUS_TODAY,
            STATUS_TZ,
        ).single()
        assertFalse(partial.isDoneToday)

        val full = computeTodayStatuses(
            listOf(h),
            listOf(statusCheckin("h1", STATUS_TODAY, qty = 8.0)),
            emptyList(),
            STATUS_TODAY,
            STATUS_TZ,
        ).single()
        assertTrue(full.isDoneToday)
    }

    @Test
    fun pauseFreezesStreak() {
        val h = statusHabit()
        val checkins = listOf(statusCheckin("h1", "2026-10-17"))
        val pauses = listOf(PauseInterval("p1", "h1", "2026-10-18", STATUS_TODAY))
        val s = computeTodayStatuses(
            listOf(h), checkins, pauses, STATUS_TODAY, STATUS_TZ,
        ).single()
        // Ontem e hoje perdoados: o streak de 1 dia (dia 17) sobrevive.
        assertEquals(1, s.currentStreak)
        assertFalse(s.isDoneToday)
    }

    @Test
    fun weeklyCountsWeekDone() {
        val h = statusHabit(frequencyType = FrequencyType.WEEKLY_X, weeklyTarget = 3)
        // Semana de 2026-10-19 (seg) a 2026-10-25 (dom).
        val checkins = listOf(
            statusCheckin("h1", "2026-10-19"),
            statusCheckin("h1", "2026-10-21"),
        )
        val s = computeTodayStatuses(
            listOf(h), checkins, emptyList(), STATUS_TODAY, STATUS_TZ,
        ).single()
        assertEquals(2, s.weekDone)
    }

    @Test
    fun multipleHabitsAreIndependent() {
        val a = statusHabit("a")
        val b = statusHabit("b")
        val statuses = computeTodayStatuses(
            listOf(a, b),
            listOf(statusCheckin("a", STATUS_TODAY)),
            emptyList(),
            STATUS_TODAY,
            STATUS_TZ,
        )
        assertTrue(statuses.first { it.habit.id == "a" }.isDoneToday)
        assertFalse(statuses.first { it.habit.id == "b" }.isDoneToday)
    }

    @Test
    fun todayCheckinIsExposed() {
        val h = statusHabit()
        val s = computeTodayStatuses(
            listOf(h),
            listOf(statusCheckin("h1", STATUS_TODAY, qty = 2.0)),
            emptyList(),
            STATUS_TODAY,
            STATUS_TZ,
        ).single()
        assertEquals(2.0, s.todayCheckin?.quantity)
    }
}
