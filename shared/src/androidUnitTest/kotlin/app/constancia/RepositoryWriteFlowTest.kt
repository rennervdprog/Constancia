package app.constancia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.constancia.data.local.CheckinRepositoryImpl
import app.constancia.data.local.DiaryRepositoryImpl
import app.constancia.data.local.HabitRepositoryImpl
import app.constancia.data.local.TaskRepositoryImpl
import app.constancia.db.ConstanciaDb
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.Habit
import app.constancia.model.Session
import app.constancia.model.todayYmd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Testes contra o SQLDelight de verdade (driver JDBC em memória): provam que
 * os caminhos de escrita usados pelas telas funcionam de ponta a ponta —
 * escrever no banco faz o Flow correspondente reemitir (bugs #1, #2 e #4).
 * Roda no CI em :shared:testDebugUnitTest.
 */
class RepositoryWriteFlowTest {

    private fun testDb(): ConstanciaDb {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ConstanciaDb.Schema.create(driver)
        return ConstanciaDb(driver)
    }

    private fun habit(
        id: String = "h1",
        goalType: GoalType = GoalType.BINARY,
        targetQuantity: Double = 1.0,
    ) = Habit(
        id = id,
        name = "Beber água",
        icon = "water",
        color = 1L,
        goalType = goalType,
        targetQuantity = targetQuantity,
        quantityUnit = "copos",
        frequencyType = FrequencyType.DAILY,
        daysOfWeek = emptyList(),
        weeklyTarget = 3,
        session = Session.MANHA,
        reminderEnabled = false,
        reminderTime = "",
        paused = false,
        createdAt = "",
        sortOrder = 0,
    )

    // Bug #1: botão "+" de tarefa — adicionar persiste e o Flow reemite.
    @Test
    fun addTaskAppearsInTodayFlow() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        assertTrue(tasks.tasksForDateFlow(date).first().isEmpty())

        tasks.add("Comprar pão", date, null)

        val list = tasks.tasksForDateFlow(date).first()
        assertEquals(1, list.size)
        assertEquals("Comprar pão", list[0].title)
        assertFalse(list[0].done)
    }

    @Test
    fun setTaskDoneUpdatesFlow() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        tasks.add("Ler 10 páginas", date, null)

        val id = tasks.tasksForDateFlow(date).first().single().id
        tasks.setDone(id, true)

        assertTrue(tasks.tasksForDateFlow(date).first().single().done)
    }

    // Bug #2: check do hábito — toggle persiste e o todayStatusFlow reemite.
    @Test
    fun toggleCheckinUpdatesTodayStatusFlow() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(habit())
        val date = todayYmd(TimeZone.UTC)

        assertFalse(habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday)

        assertTrue(checkins.toggle("h1", date))

        val after = habits.todayStatusFlow(TimeZone.UTC).first().single()
        assertTrue(after.isDoneToday)
        assertEquals(1, after.currentStreak)

        // Toggle de novo desmarca.
        assertFalse(checkins.toggle("h1", date))
        assertFalse(habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday)
    }

    @Test
    fun setQuantityUpdatesTodayStatusFlow() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(habit(goalType = GoalType.QUANTITY, targetQuantity = 8.0))
        val date = todayYmd(TimeZone.UTC)

        checkins.setQuantity("h1", date, 5.0)
        assertFalse(habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday)

        checkins.setQuantity("h1", date, 8.0)
        assertTrue(habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday)
    }

    // Bug #4: diário — salvar persiste e o Flow reemite.
    @Test
    fun saveDiaryAppearsInEntryFlow() = runBlocking {
        val diary = DiaryRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        assertNull(diary.entryForDateFlow(date).first())

        diary.save(date, 4, "Dia produtivo")

        val entry = diary.entryForDateFlow(date).first()
        assertNotNull(entry)
        assertEquals(4, entry.mood)
        assertEquals("Dia produtivo", entry.note)
        assertEquals(1, diary.recentFlow(30).first().size)
    }

    @Test
    fun saveDiaryTwiceKeepsSingleEntry() = runBlocking {
        val diary = DiaryRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        diary.save(date, 2, "Dia ruim")
        diary.save(date, 5, "Melhorou")

        val entry = diary.entryForDateFlow(date).first()
        assertNotNull(entry)
        assertEquals(5, entry.mood)
        assertEquals("Melhorou", entry.note)
        assertEquals(1, diary.recentFlow(30).first().size)
    }
}
