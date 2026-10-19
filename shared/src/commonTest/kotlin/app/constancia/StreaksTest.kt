package app.constancia

import app.constancia.domain.currentStreak
import app.constancia.domain.doneDatesFor
import app.constancia.domain.forgivenDates
import app.constancia.domain.weekDoneCount
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.PauseInterval
import app.constancia.model.Session
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import kotlin.test.Test
import kotlin.test.assertEquals

private val TZ = TimeZone.UTC

private fun habit(
    frequencyType: FrequencyType = FrequencyType.DAILY,
    goalType: GoalType = GoalType.BINARY,
    targetQuantity: Double = 1.0,
    weeklyTarget: Int = 3,
    daysOfWeek: List<Int> = emptyList(),
) = Habit(
    id = "h1",
    name = "Teste",
    icon = "check",
    color = 0L,
    goalType = goalType,
    targetQuantity = targetQuantity,
    quantityUnit = "",
    frequencyType = frequencyType,
    daysOfWeek = daysOfWeek,
    weeklyTarget = weeklyTarget,
    session = Session.MANHA,
    reminderEnabled = false,
    reminderTime = "",
    paused = false,
    createdAt = "2026-01-01",
    sortOrder = 0,
)

/** Conjunto de datas consecutivas terminando em `end` (inclusive), `n` dias. */
private fun consecutive(end: String, n: Int): Set<String> {
    val endDate = end.toLocalDate()
    return (0 until n).map { endDate.minus(DatePeriod(days = it)).toYmd() }.toSet()
}

class StreaksTest {

    // ---------- DAILY ----------

    @Test
    fun dailyStreak_countsConsecutiveDaysIncludingToday() {
        val h = habit()
        val done = consecutive("2026-10-18", 5)
        assertEquals(5, currentStreak(h, done, "2026-10-18", TZ))
    }

    @Test
    fun dailyStreak_continuesFromYesterdayWhenTodayNotDone() {
        val h = habit()
        // Feito ontem e anteontem, hoje ainda não.
        val done = consecutive("2026-10-17", 2)
        assertEquals(2, currentStreak(h, done, "2026-10-18", TZ))
    }

    @Test
    fun dailyStreak_breaksOnGap() {
        val h = habit()
        val done = setOf("2026-10-18", "2026-10-17", "2026-10-15")
        assertEquals(2, currentStreak(h, done, "2026-10-18", TZ))
    }

    @Test
    fun dailyStreak_zeroWhenNothingDone() {
        val h = habit()
        assertEquals(0, currentStreak(h, emptySet(), "2026-10-18", TZ))
        // Só um dia antigo isolado não conta.
        assertEquals(0, currentStreak(h, setOf("2026-10-10"), "2026-10-18", TZ))
    }

    // ---------- QUANTITY ----------

    @Test
    fun doneDatesFor_respectsQuantityTarget() {
        val h = habit(goalType = GoalType.QUANTITY, targetQuantity = 8.0)
        val checkins = listOf(
            "2026-10-18" to 8.0, // conta
            "2026-10-17" to 5.0, // não conta (abaixo da meta)
            "2026-10-16" to 10.0, // conta
        )
        assertEquals(setOf("2026-10-18", "2026-10-16"), doneDatesFor(h, checkins))
    }

    @Test
    fun dailyStreak_quantity_onlyCountsDaysMeetingTarget() {
        val h = habit(goalType = GoalType.QUANTITY, targetQuantity = 2.0)
        val done = doneDatesFor(
            h,
            listOf("2026-10-18" to 2.0, "2026-10-17" to 1.0, "2026-10-16" to 3.0),
        )
        // 17/10 não atingiu a meta -> quebra.
        assertEquals(1, currentStreak(h, done, "2026-10-18", TZ))
    }

    // ---------- WEEKLY_X ----------

    @Test
    fun weeklyStreak_countsConsecutiveTargetWeeks() {
        val h = habit(frequencyType = FrequencyType.WEEKLY_X, weeklyTarget = 3)
        // Semana de 12/10 (seg) a 18/10 (dom): 3 feitos. Semana anterior: 3 feitos.
        val done = setOf(
            "2026-10-18", "2026-10-16", "2026-10-14",
            "2026-10-11", "2026-10-09", "2026-10-07",
        )
        assertEquals(2, currentStreak(h, done, "2026-10-18", TZ))
    }

    @Test
    fun weeklyStreak_currentWeekInProgressDoesNotBreak() {
        val h = habit(frequencyType = FrequencyType.WEEKLY_X, weeklyTarget = 3)
        // Semana passada bateu a meta; nesta semana (12-18/10) só 1 feito até agora.
        val done = setOf("2026-10-13", "2026-10-11", "2026-10-09", "2026-10-07")
        assertEquals(1, currentStreak(h, done, "2026-10-18", TZ))
    }

    @Test
    fun weeklyStreak_breaksWhenPastWeekMissedTarget() {
        val h = habit(frequencyType = FrequencyType.WEEKLY_X, weeklyTarget = 3)
        // Esta semana ok, mas a anterior só teve 1.
        val done = setOf("2026-10-18", "2026-10-16", "2026-10-14", "2026-10-08")
        assertEquals(1, currentStreak(h, done, "2026-10-18", TZ))
    }

    @Test
    fun weekDoneCount_countsCurrentWeekCheckins() {
        // Semana de 12/10 a 18/10.
        val done = setOf("2026-10-18", "2026-10-15", "2026-10-05")
        assertEquals(2, weekDoneCount(done, "2026-10-18"))
    }

    // ---------- PAUSA (streak congelado) ----------

    @Test
    fun pause_freezesStreakWhilePaused() {
        val h = habit()
        // 3 dias feitos até 15/10; pausa aberta desde 16/10; hoje 18/10.
        val done = consecutive("2026-10-15", 3)
        val intervals = listOf(PauseInterval("p1", "h1", "2026-10-16", null))
        val forgiven = forgivenDates(intervals, "2026-10-18")
        assertEquals(3, currentStreak(h, done, "2026-10-18", TZ, forgiven))
    }

    @Test
    fun pause_closedIntervalIsForgivenAfterResume() {
        val h = habit()
        // 4 dias feitos até 14/10; pausa 15-17/10 (já retomada); hoje 18/10 feito.
        val done = consecutive("2026-10-14", 4) + "2026-10-18"
        val intervals = listOf(PauseInterval("p1", "h1", "2026-10-15", "2026-10-17"))
        val forgiven = forgivenDates(intervals, "2026-10-18")
        assertEquals(5, currentStreak(h, done, "2026-10-18", TZ, forgiven))
    }

    @Test
    fun pause_doesNotResurrectBrokenStreak() {
        val h = habit()
        // Último feito há muito tempo; pausa recente não salva.
        val done = setOf("2026-10-01")
        val intervals = listOf(PauseInterval("p1", "h1", "2026-10-16", null))
        val forgiven = forgivenDates(intervals, "2026-10-18")
        assertEquals(0, currentStreak(h, done, "2026-10-18", TZ, forgiven))
    }

    @Test
    fun pause_weeklyStreakSurvivesFullyPausedWeek() {
        val h = habit(frequencyType = FrequencyType.WEEKLY_X, weeklyTarget = 3)
        // Semana 5-11/10: meta batida. Semana 12-18/10 toda pausada.
        val done = setOf("2026-10-11", "2026-10-09", "2026-10-07")
        val intervals = listOf(PauseInterval("p1", "h1", "2026-10-12", null))
        val forgiven = forgivenDates(intervals, "2026-10-18")
        assertEquals(2, currentStreak(h, done, "2026-10-18", TZ, forgiven))
    }

    @Test
    fun forgivenDates_expandsOpenIntervalUntilToday() {
        val intervals = listOf(PauseInterval("p1", "h1", "2026-10-16", null))
        assertEquals(
            setOf("2026-10-16", "2026-10-17", "2026-10-18"),
            forgivenDates(intervals, "2026-10-18"),
        )
    }

    @Test
    fun forgivenDates_expandsClosedInterval() {
        val intervals = listOf(PauseInterval("p1", "h1", "2026-10-10", "2026-10-12"))
        assertEquals(
            setOf("2026-10-10", "2026-10-11", "2026-10-12"),
            forgivenDates(intervals, "2026-10-18"),
        )
    }
}
