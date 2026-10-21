package app.constancia

import app.constancia.domain.completionCountsByDay
import app.constancia.domain.dailyGoalMetDates
import app.constancia.domain.dailyGoalStreak
import app.constancia.domain.dailyGoalStreakInfo
import app.constancia.domain.dailyReviewSummary
import app.constancia.domain.dailyReviewText
import app.constancia.domain.goalForgivenDates
import app.constancia.domain.weekCompletionCount
import app.constancia.domain.weeklyGoalStreak
import app.constancia.domain.weeklyGoalStreakInfo
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

    // ---------- FASE D: dias de descanso da meta (R10.1) ----------
    // Out/2026: 16=sex, 17=sáb (ISO 6), 18=dom (ISO 7), 19=seg, 20=ter.

    @Test
    fun ignoreWeekdays_becomeForgivenDates() {
        val forgiven = goalForgivenDates(emptyList(), "2026-10-20", setOf(6, 7))
        // Sábados e domingos passados entram como perdoados...
        assertEquals(true, forgiven.contains("2026-10-17"))
        assertEquals(true, forgiven.contains("2026-10-18"))
        assertEquals(true, forgiven.contains("2026-10-10"))
        // ...dias úteis, não.
        assertEquals(false, forgiven.contains("2026-10-19"))
        assertEquals(false, forgiven.contains("2026-10-20"))
        // Sem a preferência, nenhum dia é perdoado (só pausas contam).
        assertEquals(true, goalForgivenDates(emptyList(), "2026-10-20").isEmpty())
    }

    @Test
    fun ignoreWeekdays_weekendOffKeepsDailyStreakAlive() {
        // Meta batida na sex, na seg e na ter; o fim de semana ficou off.
        val counts = mapOf(
            "2026-10-16" to 3, "2026-10-19" to 3, "2026-10-20" to 3,
        )
        val met = dailyGoalMetDates(counts, 3)
        // Sem descanso: sábado quebra -> sequência 2 (seg+ter).
        assertEquals(2, dailyGoalStreak(met, emptySet(), "2026-10-20"))
        // Com sáb+dom de descanso: atravessa perdoado -> 3 (sex+seg+ter).
        val forgiven = goalForgivenDates(emptyList(), "2026-10-20", setOf(6, 7))
        assertEquals(3, dailyGoalStreak(met, forgiven, "2026-10-20"))
    }

    // ---------- FASE D: sequência da meta como objeto (R10.2) ----------

    @Test
    fun dailyGoalStreakInfo_reportsStartEndAndBest() {
        val met = setOf(
            // Corrida antiga: seg 05/10 a sex 09/10 (5 dias).
            "2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08",
            "2026-10-09",
            // Corrida atual: sex 16, seg 19, ter 20 (finde perdoado).
            "2026-10-16", "2026-10-19", "2026-10-20",
        )
        val forgiven = setOf("2026-10-17", "2026-10-18")
        val info = dailyGoalStreakInfo(met, forgiven, "2026-10-20")
        assertEquals(3, info.count)
        assertEquals(LocalDate(2026, 10, 16), info.start)
        assertEquals(LocalDate(2026, 10, 20), info.end)
        assertEquals(5, info.best)
    }

    @Test
    fun weeklyGoalStreakInfo_reportsStartEndAndBest() {
        val counts = mapOf(
            "2026-09-30" to 5, // semana 28/09-04/10: batida
            "2026-10-07" to 5, // semana 05-11/10: batida
            "2026-10-14" to 2, // semana 12-18/10: não batida (quebra)
            "2026-10-19" to 5, "2026-10-20" to 1, // atual: 6 (batida)
        )
        val info = weeklyGoalStreakInfo(counts, 5, emptySet(), today)
        assertEquals(1, info.count)
        assertEquals(weeklyGoalStreak(counts, 5, emptySet(), today), info.count)
        // Extremos = segunda e domingo da semana atual (a corrida viva).
        assertEquals(LocalDate(2026, 10, 19), info.start)
        assertEquals(LocalDate(2026, 10, 25), info.end)
        // Recorde: as duas semanas seguidas de antes da quebra.
        assertEquals(2, info.best)
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
