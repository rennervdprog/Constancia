package app.constancia

import app.constancia.domain.feitasTasks
import app.constancia.domain.orphanOccurrences
import app.constancia.domain.withOccurrenceHistory
import app.constancia.model.TaskItem
import app.constancia.model.TaskOccurrence
import app.constancia.model.TaskProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fase B (R1.1) — fusão tarefas vivas × histórico de ocorrências: a
 * ocorrência representada pela linha viva feita NÃO duplica; a órfã
 * (linha apagada/desmarcada) entra nas Feitas e nas contagens.
 */
class OccurrenceHistoryTest {

    private fun task(
        id: String,
        done: Boolean,
        dueDate: String? = "2026-10-20",
    ) = TaskItem(
        id = id,
        title = id,
        dueTime = null,
        done = done,
        date = dueDate ?: "2026-10-20",
        project = TaskProject.PESSOAL,
        priority = 2,
        dueDate = dueDate,
        completedAt = if (done) "2026-10-20T12:00:00Z" else null,
    )

    private fun occurrence(
        id: String,
        sourceTaskId: String,
        dueDate: String? = "2026-10-20",
    ) = TaskOccurrence(
        id = id,
        sourceTaskId = sourceTaskId,
        occurrenceDueDate = dueDate,
        title = sourceTaskId,
        project = TaskProject.PESSOAL,
        priority = 2,
        completedAt = "2026-10-20T12:00:00Z",
    )

    @Test
    fun occurrenceRepresentedByLiveDoneTaskIsNotOrphan() {
        val tasks = listOf(task("t1", done = true))
        val history = listOf(occurrence("h1", sourceTaskId = "t1"))
        assertTrue(orphanOccurrences(tasks, history).isEmpty())
        // E a Feitas mostra a ocorrência uma vez só (a linha viva).
        assertEquals(1, feitasTasks(tasks, history).size)
    }

    @Test
    fun occurrenceOfDeletedTaskAppearsInFeitasAndCounts() {
        val tasks = listOf(task("t2", done = false, dueDate = "2026-10-21"))
        val history = listOf(occurrence("h1", sourceTaskId = "apagada"))
        val orphans = orphanOccurrences(tasks, history)
        assertEquals(1, orphans.size)

        val feitas = feitasTasks(tasks, history)
        assertEquals(1, feitas.size)
        assertEquals("hist:h1", feitas.single().id)
        assertTrue(feitas.single().done)

        val merged = withOccurrenceHistory(tasks, history)
        assertEquals(2, merged.size)
        assertEquals(1, merged.count { it.done })
    }

    @Test
    fun occurrenceOfUnmarkedTaskBecomesOrphan() {
        // A linha viva existe mas foi desmarcada depois que o desfazer
        // expirou: o histórico preserva a conclusão.
        val tasks = listOf(task("t1", done = false))
        val history = listOf(occurrence("h1", sourceTaskId = "t1"))
        assertEquals(1, orphanOccurrences(tasks, history).size)
    }

    @Test
    fun occurrenceWithDifferentDueDateIsOrphan() {
        // A linha viva feita é de OUTRA ocorrência da mesma série.
        val tasks = listOf(task("t1", done = true, dueDate = "2026-10-21"))
        val history = listOf(occurrence("h1", sourceTaskId = "t1"))
        assertEquals(1, orphanOccurrences(tasks, history).size)
    }
}
