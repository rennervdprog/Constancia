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

    // Migração 4 -> 5 (Fase B): cria as tabelas do histórico de
    // ocorrências e das opções de vista sem perder as tarefas.
    // A base é montada como no teste de migração existente (tabelas da
    // v2 escritas à mão + migrate), parando na v4 antes do salto final.
    @Test
    fun migration4To5AddsHistoryAndViewOptions() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
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
                "VALUES('t1', 'Tarefa da v4', NULL, 0, '2026-10-20', 'agora', 0)",
            0,
        )
        // Sobe até a v4 (sem as tabelas da Fase B) e só então o salto 4->5.
        ConstanciaDb.Schema.migrate(driver, 1, 4)
        ConstanciaDb.Schema.migrate(driver, 4, 5)

        val db = ConstanciaDb(driver)
        val q = db.constanciaDbQueries
        assertEquals("Tarefa da v4", q.selectTaskById("t1").executeAsOne().title)
        assertTrue(q.selectOccurrenceHistory().executeAsList().isEmpty())
        assertTrue(q.selectViewOptions().executeAsList().isEmpty())
        q.upsertViewOptions("view:HOJE", "project", "DUE")
        val stored = q.selectViewOptions().executeAsList().single()
        assertEquals("view:HOJE", stored.view_key)
        assertEquals("project", stored.group_by)
        assertEquals("DUE", stored.sort_mode)
    }

    /** Retrato completo das tabelas que a conclusão/desfazer tocam. */
    private fun dumpDb(db: ConstanciaDb): String {
        val q = db.constanciaDbQueries
        return buildString {
            appendLine("tasks:")
            q.selectAllTasks().executeAsList()
                .forEach { appendLine(it.toString()) }
            appendLine("subtasks:")
            q.selectAllSubtasks().executeAsList()
                .forEach { appendLine(it.toString()) }
            appendLine("checkins:")
            q.selectCheckinsInRange("0000-01-01", "9999-12-31")
                .executeAsList().forEach { appendLine(it.toString()) }
            appendLine("history:")
            q.selectOccurrenceHistory().executeAsList()
                .forEach { appendLine(it.toString()) }
        }
    }

    // R1.1 (Fase B): concluir recorrente grava o snapshot no histórico
    // ANTES do spawn; concluir tarefa comum não grava nada.
    @Test
    fun completingRecurringTaskWritesHistorySnapshot() = runBlocking {
        val db = testDb()
        val tasks = TaskRepositoryImpl(db)
        val date = todayYmd(TimeZone.UTC)
        val recurringId = tasks.add(
            NewTask(
                title = "Regar as plantas",
                dueDate = date,
                priority = 1,
                repeatRule = "Todo dia",
                durationMinutes = 15,
            ),
        )
        val plainId = tasks.add(
            NewTask(title = "Tarefa única", dueDate = date),
        )

        tasks.setDone(recurringId, true)
        tasks.setDone(plainId, true)

        val history = tasks.occurrenceHistoryFlow().first()
        assertEquals(1, history.size)
        val snapshot = history.single()
        assertEquals(recurringId, snapshot.sourceTaskId)
        assertEquals(date, snapshot.occurrenceDueDate)
        assertEquals("Regar as plantas", snapshot.title)
        assertEquals(1, snapshot.priority)
        assertEquals(15, snapshot.durationMinutes)
        assertTrue(snapshot.completedAt.startsWith(date))
    }

    // Critério de pronto da Fase B (R2.2): concluir -> desfazer deixa o
    // banco IDÊNTICO ao de nunca ter concluído (tarefas, subtarefas,
    // check-ins e histórico); concluir de novo não acumula efeitos
    // (uma ocorrência, um snapshot, um check-in) e desfazer de novo
    // volta ao mesmo retrato inicial.
    @Test
    fun undoRecurringCompletionRestoresExactState() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        val tasks = TaskRepositoryImpl(db, checkins)
        habits.save(habit())
        val date = todayYmd(TimeZone.UTC)
        val id = tasks.add(
            NewTask(
                title = "Beber água (diária)",
                dueDate = date,
                repeatRule = "Todo dia",
                habitId = "h1",
                subtaskTitles = listOf("Encher a garrafa", "Beber"),
            ),
        )
        val before = dumpDb(db)

        // Conclui: marca a linha, gera a próxima ocorrência (amanhã),
        // grava o snapshot e marca o hábito vinculado.
        val reversal = tasks.setDone(id, true)
        assertNotNull(reversal)
        assertNotNull(reversal.spawnedTaskId)
        assertNotNull(reversal.historyId)
        assertNotNull(reversal.habitCheckin)
        val afterComplete = tasks.allTasksFlow().first()
        assertEquals(2, afterComplete.size)
        val spawned = afterComplete.first { !it.done }
        assertEquals(
            date.toLocalDate().plus(DatePeriod(days = 1)).toYmd(),
            spawned.dueDate,
        )
        assertEquals(1, tasks.occurrenceHistoryFlow().first().size)
        assertTrue(
            habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday,
        )

        // Desfaz pelo pacote: o banco volta a ser EXATAMENTE o inicial.
        tasks.undoCompletion(reversal)
        assertEquals(before, dumpDb(db))
        assertFalse(
            habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday,
        )

        // Concluir de novo: um único conjunto de efeitos, sem duplicar.
        val reversal2 = tasks.setDone(id, true)
        assertNotNull(reversal2)
        val redone = tasks.allTasksFlow().first()
        assertEquals(2, redone.size)
        assertEquals(
            date.toLocalDate().plus(DatePeriod(days = 1)).toYmd(),
            redone.first { !it.done }.dueDate,
        )
        assertEquals(1, tasks.occurrenceHistoryFlow().first().size)
        assertTrue(
            habits.todayStatusFlow(TimeZone.UTC).first().single().isDoneToday,
        )

        // E desfazer a segunda conclusão também restaura tudo.
        tasks.undoCompletion(reversal2)
        assertEquals(before, dumpDb(db))
    }

    // R2.2: desmarcar a tarefa (sem passar pelo desfazer explícito) com
    // reversão pendente desfaz a conclusão inteira — some a ocorrência
    // gerada, o snapshot e o check-in do hábito (fim da assimetria).
    @Test
    fun unmarkingCompletionRevertsSpawnHistoryAndCheckin() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        val tasks = TaskRepositoryImpl(db, checkins)
        habits.save(habit())
        val date = todayYmd(TimeZone.UTC)
        val id = tasks.add(
            NewTask(
                title = "Beber água (diária)",
                dueDate = date,
                repeatRule = "Todo dia",
                habitId = "h1",
            ),
        )
        val before = dumpDb(db)

        tasks.setDone(id, true)
        assertEquals(2, tasks.allTasksFlow().first().size)

        tasks.setDone(id, false)
        assertEquals(before, dumpDb(db))
    }

    // R2.2: concluir pela última subtarefa também gera pacote de
    // reversão — desmarcar a tarefa restaura a subtarefa e some com a
    // ocorrência gerada.
    @Test
    fun undoSubtaskDrivenCompletionRestoresSubtasks() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val date = todayYmd(TimeZone.UTC)
        val id = tasks.add(
            NewTask(
                title = "Faxina diária",
                dueDate = date,
                repeatRule = "Todo dia",
                subtaskTitles = listOf("Cozinha", "Banheiro"),
            ),
        )
        val subs = tasks.allTasksFlow().first().single().subtasks
        tasks.setSubtaskDone(subs[0].id, true)
        tasks.setSubtaskDone(subs[1].id, true)
        val completed = tasks.allTasksFlow().first()
        assertTrue(completed.first { it.id == id }.done)
        assertEquals(2, completed.size) // a próxima ocorrência nasceu

        tasks.setDone(id, false)

        val restored = tasks.allTasksFlow().first()
        assertEquals(1, restored.size) // a ocorrência gerada sumiu
        val task = restored.single()
        assertFalse(task.done)
        assertEquals(listOf(true, false), task.subtasks.map { it.done })
        assertTrue(tasks.occurrenceHistoryFlow().first().isEmpty())
    }

    // R8.1 (Fase B): opções de vista persistem por visão — gravou, leu
    // de volta; outra visão segue no padrão.
    @Test
    fun viewOptionsPersistPerView() = runBlocking {
        val tasks = TaskRepositoryImpl(testDb())
        val defaults = tasks.viewOptionsFlow("view:HOJE").first()
        assertEquals("none", defaults.groupBy)
        assertEquals("SMART", defaults.sortMode)

        tasks.setViewOptions("view:HOJE", "project", "DUE")

        val stored = tasks.viewOptionsFlow("view:HOJE").first()
        assertEquals("view:HOJE", stored.viewKey)
        assertEquals("project", stored.groupBy)
        assertEquals("DUE", stored.sortMode)
        val other = tasks.viewOptionsFlow("view:FEITAS").first()
        assertEquals("none", other.groupBy)
        assertEquals("SMART", other.sortMode)
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
