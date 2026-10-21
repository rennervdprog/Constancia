package app.constancia

import app.constancia.domain.autoReminderSpec
import app.constancia.domain.missedReminders
import app.constancia.domain.parseTimeOrNull
import app.constancia.domain.reminderInstantMillis
import app.constancia.domain.reminderLabel
import app.constancia.domain.taskReminderInstantMillis
import app.constancia.domain.taskReminderInstants
import app.constancia.model.ReminderType
import app.constancia.model.TaskItem
import app.constancia.model.TaskReminder
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Item 9 — instante absoluto do lembrete da tarefa. */
class RemindersTest {

    private val utc = TimeZone.of("UTC")

    private fun task(
        done: Boolean = false,
        dueDate: String? = "2026-10-20",
        dueTime: String? = "15:30",
        reminder: Int? = 0,
    ) = TaskItem(
        id = "t",
        title = "t",
        dueTime = dueTime,
        done = done,
        date = "2026-10-20",
        dueDate = dueDate,
        reminderMinutesBefore = reminder,
    )

    @Test
    fun atTimeReminder() {
        // 2026-10-20T15:30:00Z
        assertEquals(1792510200000L, reminderInstantMillis(task(), utc))
    }

    @Test
    fun relativeReminder() {
        assertEquals(
            1792510200000L - 30 * 60_000L,
            reminderInstantMillis(task(reminder = 30), utc),
        )
        assertEquals(
            1792510200000L - 1440 * 60_000L,
            reminderInstantMillis(task(reminder = 1440), utc),
        )
    }

    @Test
    fun noTimeDefaultsToNineAm() {
        // Sem horário: base 09:00 do dia; 1 dia antes = 2026-10-19T09:00Z.
        assertEquals(
            1792400400000L,
            reminderInstantMillis(task(dueTime = null, reminder = 1440), utc),
        )
    }

    @Test
    fun nothingToSchedule() {
        assertNull(reminderInstantMillis(task(done = true), utc))
        assertNull(reminderInstantMillis(task(dueDate = null), utc))
        assertNull(reminderInstantMillis(task(reminder = null), utc))
    }

    @Test
    fun parseTime() {
        assertEquals(LocalTime(9, 5), parseTimeOrNull("9:05"))
        assertEquals(LocalTime(23, 59), parseTimeOrNull("23:59"))
        assertNull(parseTimeOrNull("25:00"))
        assertNull(parseTimeOrNull("abc"))
        assertNull(parseTimeOrNull(null))
    }

    @Test
    fun labels() {
        assertEquals("Sem lembrete", reminderLabel(null))
        assertEquals("Na hora", reminderLabel(0))
        assertEquals("30 min antes", reminderLabel(30))
        assertEquals("1 dia antes", reminderLabel(1440))
    }

    // ---- Fase C: a lista de lembretes (R6.1) ----

    private fun reminder(
        id: String,
        type: ReminderType,
        offset: Int? = null,
        at: Long? = null,
    ) = TaskReminder(
        id = id, taskId = "t", type = type,
        minuteOffset = offset, atMillis = at,
    )

    @Test
    fun multipleInstantsPerTask() {
        // Uma tarefa, TRÊS lembretes -> três instantes distintos.
        val t = task()
        val reminders = listOf(
            reminder("r1", ReminderType.RELATIVE, offset = 0),
            reminder("r2", ReminderType.RELATIVE, offset = 30),
            reminder("r3", ReminderType.ABSOLUTE, at = 1792400400000L),
        )
        val instants = taskReminderInstants(t, reminders, utc)
        assertEquals(3, instants.size)
        assertEquals(
            setOf(1792510200000L, 1792510200000L - 30 * 60_000L, 1792400400000L),
            instants.map { it.second }.toSet(),
        )
        // Concluída, nenhum lembrete da lista agenda.
        assertEquals(
            0,
            taskReminderInstants(task(done = true), reminders, utc).size,
        )
    }

    @Test
    fun absoluteReminderIgnoresDueDate() {
        val t = task(dueDate = null, dueTime = null)
        val abs = reminder("r1", ReminderType.ABSOLUTE, at = 1792400400000L)
        assertEquals(
            1792400400000L,
            taskReminderInstantMillis(t, abs, utc),
        )
        // Relativo sem prazo não tem base: nada a agendar.
        val rel = reminder("r2", ReminderType.RELATIVE, offset = 10)
        assertNull(taskReminderInstantMillis(t, rel, utc))
    }

    @Test
    fun autoReminderOnlyWithTime() {
        // R6.2: automático só nasce com data E horário e lista vazia.
        val spec = autoReminderSpec(30, "2026-10-20", "15:30", existingCount = 0)
        assertEquals(30, spec?.minuteOffset)
        assertEquals(ReminderType.RELATIVE, spec?.type)
        assertNull(autoReminderSpec(30, "2026-10-20", null, existingCount = 0))
        assertNull(autoReminderSpec(30, null, "15:30", existingCount = 0))
        assertNull(autoReminderSpec(null, "2026-10-20", "15:30", existingCount = 0))
        assertNull(autoReminderSpec(30, "2026-10-20", "15:30", existingCount = 1))
    }

    @Test
    fun missedRemindersWindow() {
        // R6.3: janela de 24 h sobre tarefas pendentes.
        val now = 1792510200000L // 2026-10-20T15:30Z
        fun mkTask(id: String, due: String, time: String, done: Boolean = false) =
            TaskItem(
                id = id, title = id, dueTime = time, done = done,
                date = due, dueDate = due,
            )
        val recent = mkTask("recent", "2026-10-20", "14:30") // 1 h atrás
        val old = mkTask("old", "2026-10-19", "14:30") // 25 h atrás
        val future = mkTask("future", "2026-10-20", "16:30") // 1 h à frente
        val doneTask = mkTask("done", "2026-10-20", "14:30", done = true)
        fun rel(id: String, taskId: String) = TaskReminder(
            id = id, taskId = taskId,
            type = ReminderType.RELATIVE, minuteOffset = 0,
        )
        val reminders = listOf(
            rel("m1", "recent"), rel("m2", "old"),
            rel("m3", "future"), rel("m4", "done"),
        )
        val missed = missedReminders(
            listOf(recent, old, future, doneTask), reminders, now, utc,
        )
        assertEquals(1, missed.size)
        assertEquals("recent", missed.single().taskId)
        assertEquals(now - 3_600_000L, missed.single().instantMillis)
    }
}
