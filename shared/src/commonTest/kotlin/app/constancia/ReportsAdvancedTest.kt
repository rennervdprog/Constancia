package app.constancia

import app.constancia.domain.ReportPeriod
import app.constancia.domain.computeReport
import app.constancia.domain.fmtDeltaPp
import app.constancia.domain.fmtDeltaPct
import app.constancia.domain.fmtSleep
import app.constancia.domain.morningHabitPattern
import app.constancia.domain.overdueXray
import app.constancia.domain.periodHighlights
import app.constancia.domain.periodReview
import app.constancia.domain.periodSummary
import app.constancia.domain.quarterWindow
import app.constancia.domain.sleepLagPattern
import app.constancia.domain.yearWindow
import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.toLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TZ2 = TimeZone.UTC
private val TODAY: LocalDate = "2026-10-20".toLocalDate() // terça-feira

private fun habit2(
    id: String = "h1",
    name: String = "Treino",
    session: Session = Session.MANHA,
    createdAt: String = "2026-01-01",
) = Habit(
    id = id,
    name = name,
    icon = "check",
    color = 0L,
    goalType = GoalType.BINARY,
    targetQuantity = 1.0,
    quantityUnit = "",
    frequencyType = FrequencyType.DAILY,
    daysOfWeek = emptyList(),
    weeklyTarget = 3,
    session = session,
    reminderEnabled = false,
    reminderTime = "",
    paused = false,
    createdAt = createdAt,
    sortOrder = 0,
)

private fun entry(
    date: String,
    mood: Int? = null,
    energy: Int? = null,
    sleep: Double? = null,
) = DiaryEntry(
    id = "d-$date",
    date = date,
    mood = mood,
    note = "",
    energy = energy,
    sleepHours = sleep,
)

private fun task(
    id: String,
    dueDate: String?,
    done: Boolean = false,
    date: String = "2026-10-01",
    project: TaskProject = TaskProject.TRABALHO,
    priority: Int = 2,
    dueTime: String? = null,
    session: Session = Session.TARDE,
    repeatRule: String = "",
) = TaskItem(
    id = id,
    title = "Tarefa $id",
    dueTime = dueTime,
    done = done,
    date = date,
    project = project,
    priority = priority,
    dueDate = dueDate,
    session = session,
    repeatRule = repeatRule,
)

private fun checkin2(habitId: String, date: String) =
    Checkin(id = "$habitId-$date", habitId = habitId, date = date, quantity = 1.0)

class ReportsAdvancedTest {

    // -- Formatação --------------------------------------------------------

    @Test
    fun fmtSleep_formatsHoursAndMinutes() {
        assertEquals("7h05", fmtSleep(7.09))
        assertEquals("6h30", fmtSleep(6.5))
        assertEquals("8h00", fmtSleep(8.0))
        assertEquals("7h00", fmtSleep(6.999)) // arredonda o minuto para 60
    }

    @Test
    fun fmtDelta_labels() {
        assertEquals("+9 p.p.", fmtDeltaPp(0.09))
        assertEquals("−4 p.p.", fmtDeltaPp(-0.04))
        assertEquals("0 p.p.", fmtDeltaPp(0.0))
        assertEquals("+12%", fmtDeltaPct(28.0, 25.0))
        assertEquals("−8%", fmtDeltaPct(23.0, 25.0))
        assertNull(fmtDeltaPct(5.0, 0.0))
    }

    // -- Sono × dia seguinte (lag) ------------------------------------------

    private fun sleepDiary(): List<DiaryEntry> = listOf(
        entry("2026-10-05", mood = 2, energy = 2, sleep = 5.5),
        entry("2026-10-06", mood = 4, energy = 4, sleep = 7.5),
        entry("2026-10-07", mood = 3, energy = 2, sleep = 6.0),
        entry("2026-10-08", mood = 5, energy = 5, sleep = 8.0),
        entry("2026-10-12", mood = 2, energy = 3, sleep = 6.0),
        entry("2026-10-13", mood = 4, energy = 4, sleep = 7.0),
        entry("2026-10-14", mood = 4, energy = 3, sleep = 7.2),
    )

    private fun sleepTasks(): List<TaskItem> = listOf(
        task("t1", "2026-10-05", done = true),
        task("t2", "2026-10-05"),
        task("t3", "2026-10-07"),
        task("t4", "2026-10-12", done = true),
        task("t5", "2026-10-06", done = true),
        task("t6", "2026-10-08", done = true),
        task("t7", "2026-10-08", done = true),
        task("t8", "2026-10-13"),
    )

    @Test
    fun sleepLag_groupsAndRatio() {
        val p = sleepLagPattern(sleepDiary(), sleepTasks(), TODAY)
        assertNotNull(p)
        assertEquals(3, p.shortNights)
        assertEquals(4, p.enoughNights)
        assertEquals(7, p.totalNights)
        assertEquals(7.0 / 3.0, p.moodAfterShort!!, 0.001)
        assertEquals(4.25, p.moodAfterEnough!!, 0.001)
        // Curtas: 2 atrasadas de 4 devidas; suficientes: 1 de 4 -> razão 2,0.
        assertEquals(0.5, p.overdueRateAfterShort, 0.001)
        assertEquals(0.25, p.overdueRateAfterEnough, 0.001)
        assertEquals(2.0, p.overdueRatio!!, 0.001)
        assertEquals(7.2, p.lastSleepHours!!, 0.001)
        assertEquals("2026-10-14".toLocalDate(), p.lastSleepDate)
    }

    @Test
    fun sleepLag_nullWithSmallSample() {
        // Só 4 noites com sono: abaixo do mínimo de 7 -> sem insight.
        assertNull(sleepLagPattern(sleepDiary().take(4), sleepTasks(), TODAY))
        // Grupos desbalanceados (1 curta): também some.
        val skewed = sleepDiary().map {
            if (it.date == "2026-10-07" || it.date == "2026-10-12") {
                it.copy(sleepHours = 7.0)
            } else {
                it
            }
        }
        assertNull(sleepLagPattern(skewed, sleepTasks(), TODAY))
    }

    // -- Hábito da manhã × dia -----------------------------------------------

    @Test
    fun morningHabit_comparesDaysWithAndWithout() {
        val treino = habit2()
        val diary = listOf(
            entry("2026-10-05", mood = 5, energy = 5),
            entry("2026-10-06", mood = 4, energy = 4),
            entry("2026-10-07", mood = 5, energy = 4),
            entry("2026-10-08", mood = 3, energy = 3),
            entry("2026-10-09", mood = 2, energy = 2),
            entry("2026-10-10", mood = 3, energy = 3),
        )
        val checkins = listOf("2026-10-05", "2026-10-06", "2026-10-07")
            .map { checkin2("h1", it) }
        val tasks = listOf(
            task("a", "2026-10-05", done = true),
            task("b", "2026-10-05", done = true),
            task("c", "2026-10-06", done = true),
            task("d", "2026-10-06"),
            task("e", "2026-10-07", done = true),
            task("f", "2026-10-07", done = true),
            task("g", "2026-10-08", done = true),
            task("h", "2026-10-08"),
            task("i", "2026-10-09"),
            task("j", "2026-10-09"),
            task("k", "2026-10-10", done = true),
            task("l", "2026-10-10"),
        )
        val p = morningHabitPattern(diary, listOf(treino), checkins, tasks, TODAY)
        assertNotNull(p)
        assertEquals(3, p.daysWith)
        assertEquals(3, p.daysWithout)
        assertEquals(14.0 / 3.0, p.moodWith, 0.001)
        assertEquals(8.0 / 3.0, p.moodWithout, 0.001)
        assertEquals(5.0 / 6.0, p.taskRateWith!!, 0.001)
        assertEquals(2.0 / 6.0, p.taskRateWithout!!, 0.001)
        assertEquals(1.5, p.taskRateDelta!!, 0.001)
    }

    @Test
    fun morningHabit_nullWhenGroupsTooSmall() {
        val treino = habit2()
        val diary = listOf(
            entry("2026-10-05", mood = 5),
            entry("2026-10-06", mood = 4),
            entry("2026-10-08", mood = 3),
            entry("2026-10-09", mood = 2),
            entry("2026-10-10", mood = 3),
            entry("2026-10-11", mood = 4),
        )
        // Só 2 dias com o hábito: grupo "com" abaixo do mínimo de 3.
        val checkins = listOf("2026-10-05", "2026-10-06").map { checkin2("h1", it) }
        assertNull(morningHabitPattern(diary, listOf(treino), checkins, emptyList(), TODAY))
        // Hábito de outra sessão não entra no padrão da manhã.
        val noite = habit2(id = "h2", name = "Leitura", session = Session.NOITE)
        val many = listOf(
            "2026-10-05", "2026-10-06", "2026-10-08",
            "2026-10-09", "2026-10-10", "2026-10-11",
        ).map { checkin2("h2", it) }
        assertNull(morningHabitPattern(diary, listOf(noite), many, emptyList(), TODAY))
    }

    // -- Resumo do período + revisão -----------------------------------------

    private fun weekReports(): Pair<app.constancia.domain.ReportData, app.constancia.domain.ReportData> {
        val h = habit2(createdAt = "2026-10-01")
        val curCheckins = (14..20).map { checkin2("h1", "2026-10-$it") }
        val prevCheckins = listOf("2026-10-08", "2026-10-10", "2026-10-12")
            .map { checkin2("h1", it) }
        val cur = computeReport(
            listOf(h), curCheckins, emptyList(), ReportPeriod.SEMANA, TZ2,
            todayOverride = TODAY,
        )
        val prev = computeReport(
            listOf(h), prevCheckins, emptyList(), ReportPeriod.SEMANA, TZ2,
            todayOverride = "2026-10-13".toLocalDate(),
        )
        return cur to prev
    }

    @Test
    fun periodSummary_combinedRateAndDelta() {
        val (cur, prev) = weekReports()
        val tasks = listOf(
            task("c1", "2026-10-15", done = true),
            task("c2", "2026-10-16", done = true),
            task("c3", "2026-10-17"),
            task("p1", "2026-10-08", done = true),
            task("p2", "2026-10-09"),
        )
        val s = periodSummary(tasks, emptyList(), cur, prev)
        assertEquals(2, s.tasksDone)
        assertEquals(3, s.tasksPlanned)
        assertEquals(7, s.checkins)
        // Combinada atual: (7 hábitos + 2 tarefas) / (7 + 3) = 0,9.
        assertEquals(0.9, s.combinedRate, 0.001)
        // Anterior: (3 + 1) / (7 + 2) = 4/9 -> delta ~0,456.
        assertEquals(0.9 - 4.0 / 9.0, s.combinedDelta!!, 0.001)
    }

    @Test
    fun periodReview_weekdayBarsVictoryAndLesson() {
        val (cur, prev) = weekReports()
        val tasks = listOf(
            task("c1", "2026-10-15", done = true), // quinta
            task("c2", "2026-10-15", done = true), // quinta
            task("c3", "2026-10-16", done = true), // sexta
            task("c4", "2026-10-19"), // segunda, atrasada
        )
        val r = periodReview(tasks, cur, prev)
        assertEquals(2, r.doneByWeekday[3]) // qui (índice ISO-1)
        assertEquals(1, r.doneByWeekday[4]) // sex
        assertEquals(0, r.doneByWeekday[0]) // seg
        val victory = r.victory
        assertNotNull(victory)
        assertTrue(victory.contains("quinta-feira"))
        assertTrue(victory.contains("2 tarefas"))
        val lesson = r.lesson
        assertNotNull(lesson)
        assertTrue(lesson.contains("segunda-feira"))
        // Anterior sem tarefas concluídas -> sem delta.
        assertNull(r.totalDeltaLabel)
    }

    @Test
    fun periodReview_completionSeriesSeparatesDueFromCompletion() {
        // Janela atual: 14/10 a 20/10 (semana de TODAY = ter 20/10).
        val (cur, prev) = weekReports()
        val tasks = listOf(
            // Prazo qui 15, concluída na qui 15: entra nas DUAS séries.
            task("t1", "2026-10-15", done = true)
                .copy(completedAt = "2026-10-15T10:00:00Z"),
            // Prazo qui 15, concluída só na seg 19: prazo=qui,
            // conclusão=seg, e conta como tardia.
            task("t2", "2026-10-15", done = true)
                .copy(completedAt = "2026-10-19T09:00:00Z"),
            // Prazo sáb 10 (FORA da janela), concluída sex 16 (dentro):
            // só aparece na série pela conclusão — também tardia.
            task("t3", "2026-10-10", done = true)
                .copy(completedAt = "2026-10-16T09:00:00Z"),
            // Pendente: não entra em série nenhuma.
            task("t4", "2026-10-19"),
        )
        val r = periodReview(tasks, cur, prev)
        // Série PELO PRAZO (a de sempre): as duas de quinta.
        assertEquals(2, r.doneByWeekday[3]) // qui
        // Série PELA CONCLUSÃO: seg 1, qui 1, sex 1.
        assertEquals(1, r.doneByCompletionWeekday[0]) // seg
        assertEquals(1, r.doneByCompletionWeekday[3]) // qui
        assertEquals(1, r.doneByCompletionWeekday[4]) // sex
        assertEquals(3, r.completionTotal)
        // Tardias: a de seg (prazo qui) e a de sex (prazo sáb anterior).
        assertEquals(2, r.lateCompletions)
    }

    // -- Raio-X de atrasos -----------------------------------------------------

    @Test
    fun overdueXray_groupsByPartProjectPriority() {
        val tasks = listOf(
            task("t1", "2026-10-18", dueTime = "21:30", priority = 1),
            task(
                "t2", "2026-10-19", dueTime = "09:00", priority = 2,
                project = TaskProject.CASA,
            ),
            task(
                "t3", "2026-10-10", priority = 1, session = Session.NOITE,
                repeatRule = "Todo dia",
            ),
            task("t4", "2026-10-19", done = true), // feita: fora
            task("t5", "2026-10-21"), // futura: fora
            task("t6", null), // sem prazo: fora
        )
        val x = overdueXray(tasks, TODAY)
        assertEquals(3, x.total)
        assertEquals("Noite", x.topDayPart?.label)
        assertEquals(2, x.topDayPart?.count)
        assertEquals(2.0 / 3.0, x.topDayPartShare, 0.001)
        assertEquals(
            listOf("Trabalho" to 2, "Casa" to 1),
            x.byProject.map { it.label to it.count },
        )
        assertEquals(
            listOf("P1" to 2, "P2" to 1),
            x.byPriority.map { it.label to it.count },
        )
        assertEquals(13.0 / 3.0, x.avgAgeDays, 0.001)
        assertEquals(10, x.oldestAgeDays)
        assertEquals(1, x.recurringCount)
    }

    @Test
    fun overdueXray_emptyWhenNothingOverdue() {
        val x = overdueXray(listOf(task("a", "2026-10-20")), TODAY)
        assertEquals(0, x.total)
        assertNull(x.topDayPart)
        assertEquals(0.0, x.avgAgeDays, 0.001)
    }

    // -- Ano em Revista ---------------------------------------------------------

    @Test
    fun yearAndQuarterWindows() {
        val (jan1, yEnd) = yearWindow(TODAY)
        assertEquals(LocalDate(2026, 1, 1), jan1)
        assertEquals(TODAY, yEnd)
        val (qStart, qEnd) = quarterWindow(TODAY)
        assertEquals(LocalDate(2026, 10, 1), qStart)
        assertEquals(TODAY, qEnd)
        val (q1Start, q1End) = quarterWindow(LocalDate(2026, 2, 10))
        assertEquals(LocalDate(2026, 1, 1), q1Start)
        assertEquals(LocalDate(2026, 2, 10), q1End)
    }

    @Test
    fun periodHighlights_anchorFacts() {
        val h = habit2()
        val checkins = (2..6).map { checkin2("h1", "2026-03-0$it") }
        val diary = listOf(
            entry("2026-01-05", mood = 2),
            entry("2026-01-06", mood = 2),
            entry("2026-01-07", mood = 2),
            entry("2026-03-02", mood = 5),
            entry("2026-03-03", mood = 5),
            entry("2026-03-04", mood = 5),
        )
        val tasks = listOf(
            task("y1", "2026-03-02", done = true),
            task("y2", "2026-03-03", done = true),
            task("y3", "2026-03-04"),
        )
        val report = computeReport(
            listOf(h), checkins, diary, ReportPeriod.ANO, TZ2, todayOverride = TODAY,
        )
        val hl = periodHighlights(
            listOf(h), checkins, tasks, diary,
            LocalDate(2026, 1, 1), TODAY, report,
        )
        assertEquals(5, hl.totalCheckins)
        assertEquals(5, hl.bestStreak)
        assertEquals("Treino", hl.bestStreakHabit)
        assertEquals(2, hl.tasksDone)
        assertEquals(3, hl.tasksPlanned)
        assertEquals(3, hl.happiestMonth)
        assertEquals(5.0, hl.happiestMonthAvg!!, 0.001)
        assertEquals(5.0, hl.moodByMonth[2]!!, 0.001)
        assertEquals(2.0, hl.moodByMonth[0]!!, 0.001)
        assertNull(hl.moodByMonth[5])
        assertNotNull(hl.mostProductiveWeekday)
    }
}
