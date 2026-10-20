package app.constancia

import app.constancia.domain.cyclePriority
import app.constancia.domain.doneTasks
import app.constancia.domain.doneTodayTasks
import app.constancia.domain.dueDateLabel
import app.constancia.domain.entradaTasks
import app.constancia.domain.nextRepeatDate
import app.constancia.domain.overdueTasks
import app.constancia.domain.prioCode
import app.constancia.domain.projectPendingCount
import app.constancia.domain.projectTasks
import app.constancia.domain.sortByPrio
import app.constancia.domain.subtaskProgress
import app.constancia.domain.taskCounts
import app.constancia.domain.todayTasks
import app.constancia.domain.upcomingGroupTitle
import app.constancia.domain.upcomingGroups
import app.constancia.model.Session
import app.constancia.model.Subtask
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskLogicTest {

    // Terça-feira, 20 de outubro de 2026.
    private val today = LocalDate(2026, 10, 20)

    private fun task(
        id: String,
        title: String = id,
        done: Boolean = false,
        project: TaskProject = TaskProject.PESSOAL,
        priority: Int = 2,
        dueDate: String? = null,
        session: Session = Session.TARDE,
        repeatRule: String = "",
        habitId: String? = null,
        subtasks: List<Subtask> = emptyList(),
    ) = TaskItem(
        id = id,
        title = title,
        dueTime = null,
        done = done,
        date = dueDate ?: "2026-10-20",
        project = project,
        priority = priority,
        dueDate = dueDate,
        session = session,
        repeatRule = repeatRule,
        habitId = habitId,
        subtasks = subtasks,
    )

    @Test
    fun countsSeparateViews() {
        val tasks = listOf(
            task("e1", project = TaskProject.ENTRADA, dueDate = null),
            task("e2", project = TaskProject.ENTRADA, dueDate = null, done = true),
            task("h1", dueDate = "2026-10-20"),
            task("h2", dueDate = "2026-10-19"), // atrasada conta em Hoje
            task("p1", dueDate = "2026-10-21"),
            task("p2", dueDate = "2026-10-26"), // +6 dias: ainda Próximos
            task("p3", dueDate = "2026-10-27"), // +7 dias: fora de Próximos
            task("f1", dueDate = "2026-10-18", done = true),
        )
        val counts = taskCounts(tasks, today)
        assertEquals(1, counts.entrada)
        assertEquals(2, counts.hoje)
        assertEquals(2, counts.proximos)
        assertEquals(2, counts.feitas)
    }

    @Test
    fun sortByPrioPutsDoneLastAndPriorityFirst() {
        val tasks = listOf(
            task("a", priority = 3),
            task("b", priority = 1, done = true),
            task("c", priority = 1),
            task("d", priority = 2),
        )
        assertEquals(listOf("c", "d", "a", "b"), sortByPrio(tasks).map { it.id })
    }

    @Test
    fun hojeSectionsSplitOverdueTodayAndDone() {
        val tasks = listOf(
            task("over", dueDate = "2026-10-19"),
            task("today1", dueDate = "2026-10-20"),
            task("doneToday", dueDate = "2026-10-20", done = true),
            task("doneOld", dueDate = "2026-10-10", done = true),
            task("future", dueDate = "2026-10-25"),
        )
        assertEquals(listOf("over"), overdueTasks(tasks, today).map { it.id })
        assertEquals(listOf("today1"), todayTasks(tasks, today).map { it.id })
        assertEquals(listOf("doneToday"), doneTodayTasks(tasks, today).map { it.id })
        assertEquals(
            listOf("doneToday", "doneOld"),
            doneTasks(tasks).map { it.id },
        )
    }

    @Test
    fun upcomingGroupsSkipEmptyDays() {
        val tasks = listOf(
            task("t1", dueDate = "2026-10-21"),
            task("t2", dueDate = "2026-10-23"),
            task("t3", dueDate = "2026-10-23", done = true),
            task("t4", dueDate = "2026-10-20"), // hoje não entra em Próximos
        )
        val groups = upcomingGroups(tasks, today)
        assertEquals(2, groups.size)
        assertEquals(LocalDate(2026, 10, 21), groups[0].first)
        assertEquals(listOf("t1"), groups[0].second.map { it.id })
        assertEquals(LocalDate(2026, 10, 23), groups[1].first)
        assertEquals(listOf("t2"), groups[1].second.map { it.id })
        assertEquals("Amanhã · 21 de out", upcomingGroupTitle(groups[0].first, today))
        assertEquals("Sexta · 23 de out", upcomingGroupTitle(groups[1].first, today))
    }

    @Test
    fun projectViewsAndCounts() {
        val tasks = listOf(
            task("w1", project = TaskProject.TRABALHO, priority = 1),
            task("w2", project = TaskProject.TRABALHO, done = true),
            task("c1", project = TaskProject.CASA),
            task("in", project = TaskProject.ENTRADA, dueDate = null),
        )
        assertEquals(1, projectPendingCount(tasks, TaskProject.TRABALHO))
        assertEquals(listOf("w1", "w2"), projectTasks(tasks, TaskProject.TRABALHO).map { it.id })
        assertEquals(listOf("in"), entradaTasks(tasks).map { it.id })
    }

    @Test
    fun dueLabels() {
        assertEquals("Hoje", dueDateLabel(LocalDate(2026, 10, 20), today))
        assertEquals("Amanhã", dueDateLabel(LocalDate(2026, 10, 21), today))
        assertEquals("Ontem", dueDateLabel(LocalDate(2026, 10, 19), today))
        assertEquals("sex, 23 de out", dueDateLabel(LocalDate(2026, 10, 23), today))
        assertEquals("P1", prioCode(1))
        assertEquals("P3", prioCode(3))
    }

    @Test
    fun repeatNextDates() {
        assertEquals(
            LocalDate(2026, 10, 21),
            nextRepeatDate("Todo dia", LocalDate(2026, 10, 20)),
        )
        assertEquals(
            LocalDate(2026, 10, 27),
            nextRepeatDate("Semanal", LocalDate(2026, 10, 20)),
        )
        // Sexta -> segunda (pula sábado e domingo).
        assertEquals(
            LocalDate(2026, 10, 26),
            nextRepeatDate("Seg a Sex", LocalDate(2026, 10, 23)),
        )
        // Quinta -> sexta.
        assertEquals(
            LocalDate(2026, 10, 23),
            nextRepeatDate("Seg a Sex", LocalDate(2026, 10, 22)),
        )
        assertNull(nextRepeatDate("", LocalDate(2026, 10, 20)))
    }

    @Test
    fun priorityCycleMatchesPrototype() {
        assertEquals(3, cyclePriority(1)) // alta -> baixa
        assertEquals(1, cyclePriority(2)) // média -> alta
        assertEquals(2, cyclePriority(3)) // baixa -> média
    }

    @Test
    fun subtaskProgressCounts() {
        assertNull(subtaskProgress(emptyList()))
        val subs = listOf(
            Subtask("s1", "t", "a", done = true, sortOrder = 0),
            Subtask("s2", "t", "b", done = false, sortOrder = 1),
            Subtask("s3", "t", "c", done = true, sortOrder = 2),
        )
        val progress = subtaskProgress(subs)
        assertTrue(progress != null)
        assertEquals(2, progress?.done)
        assertEquals(3, progress?.total)
        assertEquals(66, progress?.pct)
    }
}
