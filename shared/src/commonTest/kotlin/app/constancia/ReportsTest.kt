package app.constancia

import app.constancia.domain.DayPoint
import app.constancia.domain.ReportPeriod
import app.constancia.domain.computeReport
import app.constancia.domain.isDueOn
import app.constancia.domain.rateOf
import app.constancia.domain.satisfiedDates
import app.constancia.model.Checkin
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.toLocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val TZ = TimeZone.UTC

private fun habit(
    id: String = "h1",
    frequencyType: FrequencyType = FrequencyType.DAILY,
    daysOfWeek: List<Int> = emptyList(),
    paused: Boolean = false,
    createdAt: String = "2026-10-01",
    weeklyTarget: Int = 3,
) = Habit(
    id = id,
    name = "Hábito $id",
    icon = "check",
    color = 0L,
    goalType = GoalType.BINARY,
    targetQuantity = 1.0,
    quantityUnit = "",
    frequencyType = frequencyType,
    daysOfWeek = daysOfWeek,
    weeklyTarget = weeklyTarget,
    session = Session.MANHA,
    reminderEnabled = false,
    reminderTime = "",
    paused = paused,
    createdAt = createdAt,
    sortOrder = 0,
)

private fun checkin(habitId: String, date: String) =
    Checkin(id = "$habitId-$date", habitId = habitId, date = date, quantity = 1.0)

class ReportsTest {

    @Test
    fun rateOf_computesDoneOverDue() {
        val pts = listOf(
            DayPoint("2026-10-16".toLocalDate(), due = 2, done = 2, checkins = 2),
            DayPoint("2026-10-17".toLocalDate(), due = 2, done = 1, checkins = 1),
            DayPoint("2026-10-18".toLocalDate(), due = 0, done = 0, checkins = 0),
        )
        assertEquals(0.75, rateOf(pts))
    }

    @Test
    fun rateOf_zeroWhenNothingDue() {
        assertEquals(0.0, rateOf(emptyList()))
    }

    @Test
    fun computeReport_completionRateAndPerfectDays() {
        val h = habit()
        // Período de 7 dias terminando em 18/10/2026 (domingo): dias 12..18.
        // Feito em 5 dos 7 dias.
        val checkins = listOf("2026-10-12", "2026-10-13", "2026-10-14", "2026-10-16", "2026-10-18")
            .map { checkin("h1", it) }
        val data = computeReport(listOf(h), checkins, emptyList(), ReportPeriod.SEMANA, TZ)
        assertEquals(7, data.days.size)
        assertEquals(5.0 / 7.0, data.completionRate, 0.0001)
        assertEquals(5, data.perfectDays)
        assertEquals(5, data.totalCheckins)
    }

    @Test
    fun computeReport_weekdayRatesSundayFirst() {
        val h = habit()
        // Só domingo (18/10/2026) com check-in.
        val checkins = listOf(checkin("h1", "2026-10-18"))
        val data = computeReport(listOf(h), checkins, emptyList(), ReportPeriod.SEMANA, TZ)
        // Índice 0 = domingo.
        assertEquals(1.0, data.weekdayRates[0], 0.0001)
        // Segunda (índice 1) teve dia devido sem check-in.
        assertEquals(0.0, data.weekdayRates[1], 0.0001)
    }

    @Test
    fun computeReport_pausedHabitExcludedFromDue() {
        val h = habit(paused = true)
        val data = computeReport(listOf(h), emptyList(), emptyList(), ReportPeriod.SEMANA, TZ)
        assertEquals(0.0, data.completionRate)
        assertEquals(0, data.days.sumOf { it.due })
    }

    @Test
    fun isDueOn_respectsDaysOfWeekSundayFirst() {
        // Hábito só de domingo (1) e quarta (4).
        val h = habit(frequencyType = FrequencyType.DAYS_OF_WEEK, daysOfWeek = listOf(1, 4))
        assertTrue(isDueOn(h, "2026-10-18".toLocalDate(), null)) // domingo
        assertTrue(isDueOn(h, "2026-10-21".toLocalDate(), null)) // quarta
        assertTrue(!isDueOn(h, "2026-10-19".toLocalDate(), null)) // segunda
    }

    @Test
    fun isDueOn_ignoresDaysBeforeCreation() {
        val h = habit(createdAt = "2026-10-15")
        assertTrue(!isDueOn(h, "2026-10-14".toLocalDate(), "2026-10-15".toLocalDate()))
        assertTrue(isDueOn(h, "2026-10-15".toLocalDate(), "2026-10-15".toLocalDate()))
    }

    @Test
    fun satisfiedDates_fillsWeekAfterWeeklyTargetMet() {
        val h = habit(frequencyType = FrequencyType.WEEKLY_X, weeklyTarget = 2)
        // Meta batida em 14 e 15/10 (semana de 12-18/10): resto da semana conta.
        val done = satisfiedDates(h, setOf("2026-10-14", "2026-10-15"))
        assertTrue(done.contains("2026-10-16"))
        assertTrue(done.contains("2026-10-18"))
        assertTrue(!done.contains("2026-10-19")) // semana seguinte não
    }

    @Test
    fun satisfiedDates_noFillWhenTargetNotMet() {
        val h = habit(frequencyType = FrequencyType.WEEKLY_X, weeklyTarget = 3)
        val done = satisfiedDates(h, setOf("2026-10-14", "2026-10-15"))
        assertEquals(setOf("2026-10-14", "2026-10-15"), done)
    }

    @Test
    fun satisfiedDates_dailyHabitUnchanged() {
        val h = habit()
        val done = setOf("2026-10-14")
        assertEquals(done, satisfiedDates(h, done))
    }
}
