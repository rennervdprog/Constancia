package app.constancia

import app.constancia.domain.REPEAT_BIWEEKLY
import app.constancia.domain.REPEAT_MONTHLY
import app.constancia.domain.REPEAT_YEARLY
import app.constancia.domain.RescheduleTarget
import app.constancia.domain.TaskGroupBy
import app.constancia.domain.groupTasks
import app.constancia.domain.nextRepeatDate
import app.constancia.domain.rescheduleDate
import app.constancia.domain.savedViewTasks
import app.constancia.domain.sortSmart
import app.constancia.model.SavedView
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** Itens 1, 4, 5 e 10 — reagendamento, agrupar/ordenar, recorrências
 *  simples novas e o filtro das vistas salvas. */
class TaskLogic2Test {

    private val today = LocalDate(2026, 10, 20) // terça-feira

    private fun task(
        id: String,
        done: Boolean = false,
        project: TaskProject = TaskProject.PESSOAL,
        priority: Int = 2,
        dueDate: String? = null,
    ) = TaskItem(
        id = id,
        title = id,
        dueTime = null,
        done = done,
        date = dueDate ?: "2026-10-20",
        project = project,
        priority = priority,
        dueDate = dueDate,
        session = Session.TARDE,
    )

    // Item 5 ------------------------------------------------------------
    @Test
    fun nextRepeatMonthlyYearlyBiweekly() {
        assertEquals(
            LocalDate(2026, 11, 20),
            nextRepeatDate(REPEAT_MONTHLY, today),
        )
        assertEquals(
            LocalDate(2026, 2, 28), // clamp: 31 de jan -> 28 de fev
            nextRepeatDate(REPEAT_MONTHLY, LocalDate(2026, 1, 31)),
        )
        assertEquals(
            LocalDate(2027, 10, 20),
            nextRepeatDate(REPEAT_YEARLY, today),
        )
        assertEquals(
            LocalDate(2025, 2, 28), // 29/2 -> 28/2 em ano comum
            nextRepeatDate(REPEAT_YEARLY, LocalDate(2024, 2, 29)),
        )
        assertEquals(
            LocalDate(2026, 11, 3),
            nextRepeatDate(REPEAT_BIWEEKLY, today),
        )
    }

    // Item 1 ------------------------------------------------------------
    @Test
    fun rescheduleTargets() {
        assertEquals(
            LocalDate(2026, 10, 24),
            rescheduleDate(RescheduleTarget.WEEKEND, today),
        )
        assertEquals( // sábado -> o próprio sábado
            LocalDate(2026, 10, 24),
            rescheduleDate(RescheduleTarget.WEEKEND, LocalDate(2026, 10, 24)),
        )
        assertEquals( // domingo -> sábado seguinte
            LocalDate(2026, 10, 31),
            rescheduleDate(RescheduleTarget.WEEKEND, LocalDate(2026, 10, 25)),
        )
        assertEquals(
            LocalDate(2026, 10, 26),
            rescheduleDate(RescheduleTarget.NEXT_WEEK, today),
        )
        assertEquals( // segunda -> a outra segunda
            LocalDate(2026, 10, 26),
            rescheduleDate(RescheduleTarget.NEXT_WEEK, LocalDate(2026, 10, 19)),
        )
        assertEquals(
            LocalDate(2026, 10, 23),
            rescheduleDate(RescheduleTarget.PLUS_3_DAYS, today),
        )
        assertEquals(
            LocalDate(2026, 10, 27),
            rescheduleDate(RescheduleTarget.PLUS_1_WEEK, today),
        )
    }

    // Item 4 ------------------------------------------------------------
    @Test
    fun smartSortBuckets() {
        val mixed = listOf(
            task("semdata", dueDate = null),
            task("hoje", dueDate = "2026-10-20"),
            task("atrasada", dueDate = "2026-10-18"),
            task("futura", dueDate = "2026-10-25"),
            task("feita", done = true, dueDate = "2026-10-18"),
        )
        assertEquals(
            listOf("atrasada", "hoje", "futura", "semdata", "feita"),
            sortSmart(mixed, today).map { it.id },
        )
    }

    @Test
    fun groupByDateProjectPriority() {
        val tasks = listOf(
            task("a", dueDate = "2026-10-18"),
            task("b", dueDate = "2026-10-20"),
            task("c", dueDate = "2026-10-25"),
            task("d", dueDate = null, project = TaskProject.CASA, priority = 1),
        )
        assertEquals(
            listOf("Atrasadas", "Hoje", "dom, 25 de out", "Sem data"),
            groupTasks(tasks, TaskGroupBy.DATE, today).map { it.title },
        )
        assertEquals(
            listOf("Pessoal", "Casa"),
            groupTasks(tasks, TaskGroupBy.PROJECT, today).map { it.title },
        )
        assertEquals(
            listOf("P1 · Alta", "P2 · Média"),
            groupTasks(tasks, TaskGroupBy.PRIORITY, today).map { it.title },
        )
    }

    // Item 10 -----------------------------------------------------------
    @Test
    fun savedViewFilters() {
        val tasks = listOf(
            task("t1", project = TaskProject.TRABALHO, priority = 1, dueDate = "2026-10-20"),
            task("t2", project = TaskProject.TRABALHO, priority = 2, dueDate = "2026-10-22"),
            task("t3", project = TaskProject.CASA, priority = 1, dueDate = "2026-10-18"),
            task("t4", project = TaskProject.TRABALHO, priority = 1, dueDate = null),
            task("t5", project = TaskProject.TRABALHO, priority = 1, dueDate = "2026-11-20"),
        )
        val view = SavedView(
            id = "v1", name = "Trabalho P1", colorHex = 0,
            project = TaskProject.TRABALHO, priority = 1, period = "week",
        )
        assertEquals(
            listOf("t1"),
            savedViewTasks(tasks, view, today).map { it.id },
        )
        val overdueView = view.copy(period = "overdue", project = null, priority = null)
        assertEquals(
            listOf("t3"),
            savedViewTasks(tasks, overdueView, today).map { it.id },
        )
        val allView = view.copy(period = "all", project = null, priority = null)
        assertEquals(5, savedViewTasks(tasks, allView, today).size)
    }
}
