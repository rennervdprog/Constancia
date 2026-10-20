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
import app.constancia.model.NewTask
import app.constancia.model.Session
import app.constancia.model.TaskProject
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import app.constancia.model.todayYmd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
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

        tasks.quickAdd("Comprar pão", dueDate = date)

        val list = tasks.tasksForDateFlow(date).first()
        assertEquals(1, list.size)
        assertEquals("Comprar pão", list[0].title)
        assertFalse(list[0].done)
    }

    @Test
    fun setTaskDoneUpdatesFlow() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        tasks.quickAdd("Ler 10 páginas", dueDate = date)

        val id = tasks.tasksForDateFlow(date).first().single().id
        tasks.setDone(id, true)

        assertTrue(tasks.tasksForDateFlow(date).first().single().done)
    }

    @Test
    fun addFullTaskWithSubtasksAppearsInAllFlow() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        tasks.add(
            NewTask(
                title = "Enviar a proposta",
                project = TaskProject.TRABALHO,
                priority = 1,
                dueDate = date,
                subtaskTitles = listOf("Rascunho", "Revisar", "Enviar"),
            ),
        )

        val all = tasks.allTasksFlow().first()
        assertEquals(1, all.size)
        val created = all.single()
        assertEquals(TaskProject.TRABALHO, created.project)
        assertEquals(1, created.priority)
        assertEquals(date, created.dueDate)
        assertEquals(3, created.subtasks.size)
        assertEquals("Rascunho", created.subtasks[0].title)
    }

    @Test
    fun completingAllSubtasksCompletesTask() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        val id = tasks.add(
            NewTask(
                title = "Faxina",
                dueDate = date,
                subtaskTitles = listOf("Cozinha", "Banheiro"),
            ),
        )
        val subs = tasks.allTasksFlow().first().single().subtasks
        tasks.setSubtaskDone(subs[0].id, true)
        assertFalse(tasks.allTasksFlow().first().single().done)
        tasks.setSubtaskDone(subs[1].id, true)
        assertTrue(tasks.allTasksFlow().first().single().done)
        assertEquals(id, tasks.allTasksFlow().first().single().id)
    }

    @Test
    fun completingRecurringTaskSpawnsNextOccurrence() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        val id = tasks.add(
            NewTask(
                title = "Regar as plantas",
                dueDate = date,
                repeatRule = "Todo dia",
            ),
        )
        tasks.setDone(id, true)

        val all = tasks.allTasksFlow().first()
        assertEquals(2, all.size)
        val next = all.first { !it.done }
        val expected = date.toLocalDate().plus(DatePeriod(days = 1)).toYmd()
        assertEquals(expected, next.dueDate)
        assertEquals("Todo dia", next.repeatRule)
    }

    @Test
    fun completingLinkedTaskMarksHabit() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        val tasks = TaskRepositoryImpl(db, checkins)
        habits.save(habit())
        val date = todayYmd(TimeZone.UTC)
        val id = tasks.add(
            NewTask(
                title = "Beber água (tarefa)",
                dueDate = date,
                habitId = "h1",
            ),
        )
        assertFalse(habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday)

        tasks.setDone(id, true)

        assertTrue(habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday)
    }

    @Test
    fun togglingHabitSyncsLinkedTasks() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        val tasks = TaskRepositoryImpl(db, checkins)
        habits.save(habit())
        val date = todayYmd(TimeZone.UTC)
        tasks.add(
            NewTask(
                title = "Beber água (tarefa)",
                dueDate = date,
                habitId = "h1",
            ),
        )
        assertFalse(tasks.allTasksFlow().first().single().done)

        checkins.toggle("h1", date)
        assertTrue(tasks.allTasksFlow().first().single().done)

        checkins.toggle("h1", date)
        assertFalse(tasks.allTasksFlow().first().single().done)
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

    @Test
    fun saveDiaryTurboFieldsRoundTrip() = runBlocking {
        val diary = DiaryRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        diary.save(
            dateYmd = date,
            mood = 4,
            note = "Corri de manhã",
            energy = 5,
            sleepHours = 7.5,
            tags = listOf("Treino", "Praia"),
            photoUri = "/data/fotos/hoje.jpg",
            prompt = "Qual foi o melhor momento de hoje?",
        )

        val entry = diary.entryForDateFlow(date).first()
        assertNotNull(entry)
        assertEquals(5, entry.energy)
        assertEquals(7.5, entry.sleepHours)
        assertEquals(listOf("Treino", "Praia"), entry.tags)
        assertEquals("/data/fotos/hoje.jpg", entry.photoUri)
        assertEquals("Qual foi o melhor momento de hoje?", entry.prompt)
        assertEquals(1, diary.allEntriesFlow().first().size)
    }

    // Migração 2 -> 3: banco antigo (sem as colunas novas) migra sem perder dados.
    @Test
    fun migrationToLatestAddsTaskAndDiaryColumns() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Schema antigo (v2): task_item e diary_entry sem as colunas turbinadas.
        driver.execute(
            null,
            "CREATE TABLE task_item(" +
                "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, due_time TEXT, " +
                "done INTEGER NOT NULL DEFAULT 0, date TEXT NOT NULL, " +
                "created_at TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE diary_entry(" +
                "id TEXT NOT NULL PRIMARY KEY, date TEXT NOT NULL UNIQUE, mood INTEGER, " +
                "note TEXT NOT NULL DEFAULT '', updated_at TEXT NOT NULL)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO task_item(id, title, due_time, done, date, created_at, sort_order) " +
                "VALUES('t1', 'Tarefa antiga', NULL, 0, '2026-10-20', 'agora', 0)",
            0,
        )

        ConstanciaDb.Schema.migrate(driver, 1, ConstanciaDb.Schema.version)

        val db = ConstanciaDb(driver)
        val row = db.constanciaDbQueries.selectTaskById("t1").executeAsOne()
        assertEquals("Tarefa antiga", row.title)
        // A migração preenche o prazo com o dia da tarefa e usa os defaults.
        assertEquals("2026-10-20", row.due_date)
        assertEquals("entrada", row.project)
        assertEquals(2L, row.priority)
    }
}
