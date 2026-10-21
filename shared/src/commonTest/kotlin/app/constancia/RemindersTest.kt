package app.constancia

import app.constancia.domain.parseTimeOrNull
import app.constancia.domain.reminderInstantMillis
import app.constancia.domain.reminderLabel
import app.constancia.model.TaskItem
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
}
