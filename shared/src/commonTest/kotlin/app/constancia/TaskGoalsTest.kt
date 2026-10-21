package app.constancia

import app.constancia.domain.completionCountsByDay
import app.constancia.domain.dailyGoalMetDates
import app.constancia.domain.dailyGoalStreak
import app.constancia.domain.dailyReviewSummary
import app.constancia.domain.dailyReviewText
import app.constancia.domain.goalForgivenDates
import app.constancia.domain.weekCompletionCount
import app.constancia.domain.weeklyGoalStreak
import app.constancia.model.GoalPause
import app.constancia.model.TaskItem
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Item 2 (metas de tarefas, sem Karma) e item 3 (resumo do Daily Review). */
class TaskGoalsTest {

    private val today = LocalDate(2026, 10, 20) // terça-feira

    private fun task(
        id: String,
        done: Boolean,
        date: String,
        completedAt: String? = null,
        dueDate: String? = null,
    ) = TaskItem(
        id = id,
        title = id,
        dueTime = null,
        done = done,
        date = date,
        dueDate = dueDate,
        completedAt = completedAt,
    )

    @Test
    fun countsUseCompletedAtWithLegacyFallback() {
        val tasks = listOf(
            task("a", true, "2026-10-19", completedAt = "2026-10-19T08:00:00Z"),
            task("b", true, "2026-10-18", completedAt = "2026-10-19T09:00:00Z"),
            task("c", true, "2026-10-10"), // sem completedAt: vale o date
            task("d", false, "2026-10-19"),
        )
        val counts = completionCountsByDay(tasks)
        assertEquals(2, counts["2026-10-19"])
        assertEquals(1, counts["2026-10-10"])
        assertEquals(null, counts["2026-10-18"])
    }

    @Test
    fun dailyStreakCountsBackwardsFromToday() {
        val counts = mapOf(
            "2026-10-20" to 2, "2026-10-19" to 3, "2026-10-18" to 1,
        )
        val met = dailyGoalMetDates(counts, 2)
        assertEquals(2, dailyGoalStreak(met, emptySet(), "2026-10-20"))
    }

    @Test
    fun dailyStreakSurvivesTodayInProgress() {
        val counts = mapOf("2026-10-19" to 2, "2026-10-18" to 2)
        val met = dailyGoalMetDates(counts, 2)
        // Hoje ainda sem bater: a sequência de ontem continua valendo.
        assertEquals(2, dailyGoalStreak(met, emptySet(), "2026-10-20"))
    }

    @Test
    fun pauseForgivesDaysAndSavesStreak() {
        val counts = mapOf("2026-10-20" to 3, "2026-10-17" to 3)
        val met = dailyGoalMetDates(counts, 3)
        assertEquals(1, dailyGoalStreak(met, emptySet(), "2026-10-20"))
        val forgiven = goalForgivenDates(
            listOf(GoalPause("p", "2026-10-18", "2026-10-19")),
            "2026-10-20",
        )
        assertEquals(2, dailyGoalStreak(met, forgiven, "2026-10-20"))
    }

    @Test
    fun weeklyProgressAndStreak() {
        val counts = mapOf(
            "2026-10-19" to 3, "2026-10-20" to 2, // semana atual: 5
            "2026-10-12" to 4, "2026-10-15" to 2, // semana passada: 6
            "2026-10-06" to 1, // retrasada: 1 (quebra)
        )
        assertEquals(5, weekCompletionCount(counts, today))
        assertEquals(2, weeklyGoalStreak(counts, 5, emptySet(), today))
        assertEquals(0, weeklyGoalStreak(counts, 7, emptySet(), today))
    }

    @Test
    fun weeklyFrozenWeekDoesNotBreak() {
        val counts = mapOf(
            "2026-10-19" to 5, // atual batida
            "2026-10-05" to 5, // retrasada batida; a do meio é pausa
        )
        val forgiven = (12..18).map { "2026-10-" + it.toString().padStart(2, '0') }.toSet()
        assertEquals(2, weeklyGoalStreak(counts, 5, forgiven, today))
    }

    @Test
    fun dailyReviewSummaryAndText() {
        val tasks = listOf(
            task("h1", false, "2026-10-20", dueDate = "2026-10-20"),
            task("h2", false, "2026-10-20", dueDate = "2026-10-20"),
            task("at", false, "2026-10-15", dueDate = "2026-10-15"),
            task(
                "ok", true, "2026-10-19",
                completedAt = "2026-10-19T20:00:00Z", dueDate = "2026-10-19",
            ),
        )
        val summary = dailyReviewSummary(tasks, today)
        assertEquals(2, summary.dueToday)
        assertEquals(1, summary.overdue)
        assertEquals(1, summary.doneYesterday)
        assertEquals(
            "2 tarefas para hoje · 1 atrasada pedindo atenção · ontem você concluiu 1",
            dailyReviewText(summary).second,
        )
        assertEquals(
            "Nenhuma tarefa para hoje",
            dailyReviewText(dailyReviewSummary(emptyList(), today)).second,
        )
    }
}
