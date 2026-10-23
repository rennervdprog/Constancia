package app.constancia

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.constancia.AppDeps
import app.constancia.data.LocalSyncRepository
import app.constancia.data.PrefsRepository
import app.constancia.data.local.ChallengeRepositoryImpl
import app.constancia.data.local.CheckinRepositoryImpl
import app.constancia.data.local.DiaryRepositoryImpl
import app.constancia.data.local.HabitRepositoryImpl
import app.constancia.data.local.TaskRepositoryImpl
import app.constancia.db.ConstanciaDb
import app.constancia.model.FrequencyType
import app.constancia.model.GoalType
import app.constancia.model.CheckinStatus
import app.constancia.model.Habit
import app.constancia.model.NewTask
import app.constancia.model.ReminderSpec
import app.constancia.model.ReminderType
import app.constancia.model.Session
import app.constancia.model.TaskProject
import app.constancia.model.toLocalDate
import app.constancia.model.toYmd
import app.constancia.model.todayYmd
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
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
    // A base é a mesma do teste de migração existente (tabelas da v2
    // escritas à mão) e a cadeia sobe de uma vez: a 5.sqm roda por
    // último, sobre o estado v4 que as migrações 2-4 produziram — é a
    // transição 4->5 exercida (a chamada migrate em duas etapas não
    // reaplica arquivos neste setup do SQLDelight).
    // (Na v6, habit/checkin mínimos entraram na base: a 6.sqm faz
    // ALTER TABLE neles.)
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
            "CREATE TABLE habit(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL DEFAULT '', " +
                "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                "reminder_time TEXT NOT NULL DEFAULT '', " +
                "created_at TEXT NOT NULL DEFAULT '')",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE checkin(id TEXT NOT NULL PRIMARY KEY, " +
                "habit_id TEXT NOT NULL DEFAULT '', date TEXT NOT NULL DEFAULT '', " +
                "quantity REAL NOT NULL DEFAULT 1.0, created_at TEXT NOT NULL DEFAULT '')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO task_item(id, title, due_time, done, date, created_at, sort_order) " +
                "VALUES('t1', 'Tarefa da v4', NULL, 0, '2026-10-20', 'agora', 0)",
            0,
        )

        ConstanciaDb.Schema.migrate(driver, 1, ConstanciaDb.Schema.version)

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
        // habit/checkin mínimos: a 6.sqm faz ALTER TABLE neles.
        driver.execute(
            null,
            "CREATE TABLE habit(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL DEFAULT '', " +
                "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                "reminder_time TEXT NOT NULL DEFAULT '', " +
                "created_at TEXT NOT NULL DEFAULT '')",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE checkin(id TEXT NOT NULL PRIMARY KEY, " +
                "habit_id TEXT NOT NULL DEFAULT '', date TEXT NOT NULL DEFAULT '', " +
                "quantity REAL NOT NULL DEFAULT 1.0, created_at TEXT NOT NULL DEFAULT '')",
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

    // ---------- Hábitos avançados (schema v6) ----------

    // Item 4: pular grava um REGISTRO (status SKIPPED) — não é ausência:
    // o streak sobrevive ao dia pulado, e tocar de novo desfaz o pulo.
    @Test
    fun skipRecordsSkippedStatusAndKeepsStreak() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(habit())
        val today = todayYmd(TimeZone.UTC)
        val d1 = today.toLocalDate().minus(DatePeriod(days = 2)).toYmd()
        val d2 = today.toLocalDate().minus(DatePeriod(days = 1)).toYmd()
        checkins.toggle("h1", d1)
        checkins.toggle("h1", d2)

        checkins.skip("h1", today)
        val skipped = checkins.get("h1", today)
        assertNotNull(skipped) // existe registro: difere de não ter check-in
        assertEquals(CheckinStatus.SKIPPED, skipped.status)
        assertEquals(0.0, skipped.quantity)

        val status = habits.todayStatusFlow(TimeZone.UTC).first()
            .single { it.habit.id == "h1" }
        assertFalse(status.isDoneToday)
        assertEquals(2, status.currentStreak) // o pulo não quebrou a sequência

        // Tocar no dia pulado transforma o pulo em check-in feito.
        checkins.toggle("h1", today)
        val afterToggle = checkins.get("h1", today)
        assertEquals(CheckinStatus.DONE, afterToggle?.status)

        // Desfazer o pulo (unskip) remove só registros SKIPPED.
        checkins.skip("h1", today)
        checkins.unskip("h1", today)
        assertNull(checkins.get("h1", today))
    }

    // Item 1 (repositório): progresso parcial persiste e só a meta
    // conclui o dia; campos novos do hábito (passo/opt-ins) fazem round-trip.
    @Test
    fun quantityPartialProgressPersistsUntilGoal() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(
            habit(goalType = GoalType.QUANTITY, targetQuantity = 8.0)
                .copy(quantityStep = 2.0, showInCalendar = true),
        )
        val storedHabit = habits.getById("h1")
        assertEquals(2.0, storedHabit?.quantityStep)
        assertEquals(true, storedHabit?.showInCalendar)
        assertEquals(false, storedHabit?.showInToday)

        val today = todayYmd(TimeZone.UTC)
        checkins.setQuantity("h1", today, 6.0) // parcial: 6 de 8
        var status = habits.todayStatusFlow(TimeZone.UTC).first()
            .single { it.habit.id == "h1" }
        assertFalse(status.isDoneToday)
        assertEquals(6.0, status.todayCheckin?.quantity)

        checkins.setQuantity("h1", today, 8.0) // bateu a meta
        status = habits.todayStatusFlow(TimeZone.UTC).first()
            .single { it.habit.id == "h1" }
        assertTrue(status.isDoneToday)
    }

    // Item 3: log do hábito salva e lê nota + emoji; salvar de novo no
    // mesmo dia substitui (um registro por dia e hábito).
    @Test
    fun habitRecordSavesAndReadsNoteAndEmoji() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(habit())
        val today = todayYmd(TimeZone.UTC)

        assertNull(checkins.recordFor("h1", today))
        checkins.saveRecord("h1", today, "Corri leve, sem dor no joelho.", "🏃")
        val saved = checkins.recordFor("h1", today)
        assertNotNull(saved)
        assertEquals("Corri leve, sem dor no joelho.", saved.content)
        assertEquals("🏃", saved.emoji)
        assertEquals(1, checkins.recordsFlow("h1").first().size)
        assertEquals(1, checkins.allRecordsFlow().first().size)

        checkins.saveRecord("h1", today, "Nota corrigida.", null)
        val updated = checkins.recordFor("h1", today)
        assertEquals("Nota corrigida.", updated?.content)
        assertNull(updated?.emoji)
        assertEquals(1, checkins.recordsFlow("h1").first().size)
    }

    // Item 2: o ciclo conclui quando os dias feitos na janela batem a
    // meta (completed_at gravado, permanente) e o próximo pode começar.
    @Test
    fun cycleCompletesWhenTargetReachedAndNextStarts() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(habit())
        val today = todayYmd(TimeZone.UTC)
        val start = today.toLocalDate().minus(DatePeriod(days = 2)).toYmd()
        val d2 = today.toLocalDate().minus(DatePeriod(days = 1)).toYmd()

        val cycle = habits.startCycle("h1", targetDays = 3, startYmd = start)
        assertEquals(start, cycle.startDate)
        assertEquals(today, cycle.endDate) // janela de 3 dias inclusive

        checkins.toggle("h1", start)
        checkins.toggle("h1", d2)
        var cycles = habits.cyclesFlow("h1").first()
        assertEquals(1, cycles.size)
        assertNull(cycles.single().completedAt) // 2 de 3: ainda não

        checkins.toggle("h1", today) // terceiro dia: bate a meta
        cycles = habits.cyclesFlow("h1").first()
        assertNotNull(cycles.single().completedAt) // concluiu e registrou

        // Próximo ciclo: começa amanhã, independente do anterior.
        val tomorrow = today.toLocalDate().plus(DatePeriod(days = 1)).toYmd()
        habits.startCycle("h1", targetDays = 3, startYmd = tomorrow)
        cycles = habits.cyclesFlow("h1").first()
        assertEquals(2, cycles.size)
        assertNotNull(cycles.first().completedAt) // o concluído permanece
        assertNull(cycles.last().completedAt)
    }

    // Migração 5 -> 6: defaults seguros nas colunas novas,
    // status DONE nos check-ins antigos e as tabelas novas utilizáveis.
    @Test
    fun migration5To6AddsHabitColumnsAndTables() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Base no formato v5: habit e checkin completos + task/diary da
        // v2 (a cadeia de migrações 2-5 também roda sobre eles).
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
            "CREATE TABLE habit(" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, " +
                "color INTEGER NOT NULL, goal_type TEXT NOT NULL, " +
                "target_quantity REAL NOT NULL DEFAULT 1.0, " +
                "quantity_unit TEXT NOT NULL DEFAULT '', frequency_type TEXT NOT NULL, " +
                "days_of_week TEXT NOT NULL DEFAULT '', " +
                "weekly_target INTEGER NOT NULL DEFAULT 7, session TEXT NOT NULL, " +
                "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                "reminder_time TEXT NOT NULL DEFAULT '', " +
                "paused INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE checkin(" +
                "id TEXT NOT NULL PRIMARY KEY, habit_id TEXT NOT NULL, " +
                "date TEXT NOT NULL, quantity REAL NOT NULL DEFAULT 1.0, " +
                "created_at TEXT NOT NULL, UNIQUE(habit_id, date))",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO habit(id, name, icon, color, goal_type, target_quantity, " +
                "quantity_unit, frequency_type, days_of_week, weekly_target, session, " +
                "reminder_enabled, reminder_time, paused, archived, created_at, sort_order) " +
                "VALUES('h1', 'Beber água', 'water', 1, 'BINARY', 1.0, '', 'DAILY', '', 7, " +
                "'MANHA', 0, '', 0, 0, 'agora', 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO checkin(id, habit_id, date, quantity, created_at) " +
                "VALUES('c1', 'h1', '2026-10-20', 1.0, 'agora')",
            0,
        )

        ConstanciaDb.Schema.migrate(driver, 1, ConstanciaDb.Schema.version)

        val q = ConstanciaDb(driver).constanciaDbQueries
        val h = q.selectHabitById("h1").executeAsOne()
        assertEquals("Beber água", h.name) // dado antigo preservado
        assertEquals(1.0, h.quantity_step) // passo padrão
        assertEquals(0L, h.show_in_today) // opt-ins desligados por padrão
        assertEquals(0L, h.show_in_calendar)
        val c = q.selectCheckin("h1", "2026-10-20").executeAsOne()
        assertEquals("DONE", c.status) // check-ins antigos nascem DONE
        // Tabelas novas utilizáveis.
        q.upsertRecord("r1", "h1", "2026-10-20", "Primeira nota", "💧", "agora")
        assertEquals(
            "Primeira nota",
            q.selectRecord("h1", "2026-10-20").executeAsOne().content,
        )
        q.insertCycle("cy1", "h1", "2026-10-20", "2026-11-10", 21L, null, "agora")
        assertEquals(1, q.selectCyclesForHabit("h1").executeAsList().size)
    }

    // ---- Fase C: lembretes de verdade ----

    /** Prefs mínimo para os testes: só o lembrete automático interessa. */
    private class FakePrefs(autoMinutes: Int? = null) : PrefsRepository {
        private val auto = MutableStateFlow(autoMinutes)
        override fun darkThemeFlow(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setDarkTheme(dark: Boolean) {}
        override fun isProFlow(): Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setPro(pro: Boolean) {}
        override fun onboardingDoneFlow(): Flow<Boolean> =
            MutableStateFlow(true)
        override suspend fun setOnboardingDone() {}
        override fun taskDailyGoalFlow(): Flow<Int> = MutableStateFlow(0)
        override suspend fun setTaskDailyGoal(goal: Int) {}
        override fun taskWeeklyGoalFlow(): Flow<Int> = MutableStateFlow(0)
        override suspend fun setTaskWeeklyGoal(goal: Int) {}
        override fun dailyReviewEnabledFlow(): Flow<Boolean> =
            MutableStateFlow(false)
        override suspend fun setDailyReviewEnabled(enabled: Boolean) {}
        override fun dailyReviewTimeFlow(): Flow<String> =
            MutableStateFlow("08:00")
        override suspend fun setDailyReviewTime(time: String) {}
        override fun autoReminderMinutesFlow(): Flow<Int?> = auto
        override suspend fun setAutoReminderMinutes(minutes: Int?) {
            auto.value = minutes
        }
        // Fase D: estado real mínimo (o teste de férias exercita o fluxo).
        private val ignoreDays = MutableStateFlow<Set<Int>>(emptySet())
        private val vacation = MutableStateFlow(false)
        private val vacationIds = MutableStateFlow<Set<String>>(emptySet())
        private val vacationGoal = MutableStateFlow(false)
        override fun goalIgnoreWeekdaysFlow(): Flow<Set<Int>> = ignoreDays
        override suspend fun setGoalIgnoreWeekdays(days: Set<Int>) {
            ignoreDays.value = days
        }
        override fun vacationModeFlow(): Flow<Boolean> = vacation
        override suspend fun setVacationMode(enabled: Boolean) {
            vacation.value = enabled
        }
        override fun vacationHabitIdsFlow(): Flow<Set<String>> = vacationIds
        override suspend fun setVacationHabitIds(ids: Set<String>) {
            vacationIds.value = ids
        }
        override fun vacationGoalPauseOpenedFlow(): Flow<Boolean> =
            vacationGoal
        override suspend fun setVacationGoalPauseOpened(opened: Boolean) {
            vacationGoal.value = opened
        }
    }

    @Test
    fun migration7To8SeedsReminderTables() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Base no formato v6 (task_item e habit completos, sem as
        // tabelas de lembrete): a 7.sqm roda sozinha sobre ela.
        driver.execute(
            null,
            "CREATE TABLE task_item(" +
                "id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, due_time TEXT, " +
                "done INTEGER NOT NULL DEFAULT 0, date TEXT NOT NULL, " +
                "project TEXT NOT NULL DEFAULT 'entrada', " +
                "priority INTEGER NOT NULL DEFAULT 2, due_date TEXT, " +
                "session TEXT NOT NULL DEFAULT 'TARDE', " +
                "repeat_rule TEXT NOT NULL DEFAULT '', deadline_date TEXT, " +
                "duration_minutes INTEGER, completed_at TEXT, " +
                "reminder_minutes_before INTEGER, " +
                "repeat_spec TEXT NOT NULL DEFAULT '', " +
                "note TEXT NOT NULL DEFAULT '', habit_id TEXT, " +
                "created_at TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)",
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE habit(" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, " +
                "color INTEGER NOT NULL, goal_type TEXT NOT NULL, " +
                "target_quantity REAL NOT NULL DEFAULT 1.0, " +
                "quantity_unit TEXT NOT NULL DEFAULT '', " +
                "quantity_step REAL NOT NULL DEFAULT 1.0, " +
                "show_in_today INTEGER NOT NULL DEFAULT 0, " +
                "show_in_calendar INTEGER NOT NULL DEFAULT 0, " +
                "frequency_type TEXT NOT NULL, " +
                "days_of_week TEXT NOT NULL DEFAULT '', " +
                "weekly_target INTEGER NOT NULL DEFAULT 7, session TEXT NOT NULL, " +
                "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                "reminder_time TEXT NOT NULL DEFAULT '', " +
                "paused INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO task_item(id, title, date, reminder_minutes_before, created_at) " +
                "VALUES('t1', 'Com lembrete', '2026-10-20', 30, 'agora')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO task_item(id, title, date, created_at) " +
                "VALUES('t2', 'Sem lembrete', '2026-10-20', 'agora')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO habit(id, name, icon, color, goal_type, frequency_type, session, " +
                "reminder_enabled, reminder_time, created_at) " +
                "VALUES('h1', 'Beber água', 'water', 1, 'BINARY', 'DAILY', 'MANHA', " +
                "1, '07:30', 'agora')",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO habit(id, name, icon, color, goal_type, frequency_type, session, " +
                "reminder_enabled, reminder_time, created_at) " +
                "VALUES('h2', 'Ler', 'book', 1, 'BINARY', 'DAILY', 'NOITE', " +
                "0, '', 'agora')",
            0,
        )

        assertEquals(9L, ConstanciaDb.Schema.version)
        ConstanciaDb.Schema.migrate(driver, 7, ConstanciaDb.Schema.version)

        val q = ConstanciaDb(driver).constanciaDbQueries
        // O lembrete único da tarefa virou linha RELATIVE da lista.
        val t1 = q.selectRemindersForTask("t1").executeAsList()
        assertEquals(1, t1.size)
        assertEquals("RELATIVE", t1.single().type)
        assertEquals(30L, t1.single().minute_offset)
        assertEquals(0L, t1.single().auto)
        assertEquals(0, q.selectRemindersForTask("t2").executeAsList().size)
        // O horário único do hábito virou a primeira linha dele.
        val h1 = q.selectRemindersForHabit("h1").executeAsList()
        assertEquals(1, h1.size)
        assertEquals("07:30", h1.single().time)
        assertEquals(0, q.selectRemindersForHabit("h2").executeAsList().size)
    }

    // ---- Fase D: descanso por hábito (R13.1) + modo férias (R13.4) ----

    @Test
    fun migration8To9AddsIgnoreWeekdays() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Base no formato v8 (hábito completo, sem ignore_weekdays).
        driver.execute(
            null,
            "CREATE TABLE habit(" +
                "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, " +
                "color INTEGER NOT NULL, goal_type TEXT NOT NULL, " +
                "target_quantity REAL NOT NULL DEFAULT 1.0, " +
                "quantity_unit TEXT NOT NULL DEFAULT '', " +
                "quantity_step REAL NOT NULL DEFAULT 1.0, " +
                "show_in_today INTEGER NOT NULL DEFAULT 0, " +
                "show_in_calendar INTEGER NOT NULL DEFAULT 0, " +
                "frequency_type TEXT NOT NULL, " +
                "days_of_week TEXT NOT NULL DEFAULT '', " +
                "weekly_target INTEGER NOT NULL DEFAULT 7, session TEXT NOT NULL, " +
                "reminder_enabled INTEGER NOT NULL DEFAULT 0, " +
                "reminder_time TEXT NOT NULL DEFAULT '', " +
                "paused INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT NOT NULL, sort_order INTEGER NOT NULL DEFAULT 0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO habit(id, name, icon, color, goal_type, frequency_type, " +
                "session, created_at) " +
                "VALUES('h1', 'Meditar', 'lotus', 1, 'BINARY', 'DAILY', 'MANHA', 'agora')",
            0,
        )

        assertEquals(9L, ConstanciaDb.Schema.version)
        ConstanciaDb.Schema.migrate(driver, 8, ConstanciaDb.Schema.version)

        val q = ConstanciaDb(driver).constanciaDbQueries
        val h = q.selectHabitById("h1").executeAsOne()
        assertEquals("Meditar", h.name) // dado antigo preservado
        assertEquals("", h.ignore_weekdays) // descanso nasce vazio
    }

    @Test
    fun habitIgnoreWeekdaysRoundTrips() = runBlocking {
        val db = testDb()
        val habits = HabitRepositoryImpl(db, CheckinRepositoryImpl(db))
        habits.save(habit(id = "h1").copy(ignoreWeekdays = setOf(6, 7)))
        assertEquals(setOf(6, 7), habits.getById("h1")!!.ignoreWeekdays)
        // Atualizar sem descanso limpa a coluna (csv vazio).
        habits.save(habit(id = "h1"))
        assertEquals(emptySet(), habits.getById("h1")!!.ignoreWeekdays)
    }

    @Test
    fun vacationMode_pausesEverythingAndRestoresOnlyWhatItTouched() =
        runBlocking {
            val db = testDb()
            val checkins = CheckinRepositoryImpl(db)
            val habits = HabitRepositoryImpl(db, checkins)
            val prefs = FakePrefs()
            val tasks = TaskRepositoryImpl(db, checkins, null, prefs)
            val deps = AppDeps(
                habits = habits,
                checkins = checkins,
                tasks = tasks,
                diary = DiaryRepositoryImpl(db),
                challenges = ChallengeRepositoryImpl(db),
                sync = LocalSyncRepository(),
                prefs = prefs,
            )
            val today = todayYmd(TimeZone.UTC)
            habits.save(habit(id = "h1"))
            habits.save(habit(id = "h2"))
            habits.setPaused("h2", true) // pausa MANUAL anterior

            deps.setVacationMode(true, today)

            // Congelou os dois sistemas: hábitos pausados (com intervalo
            // aberto = dias perdoados) e pausa da meta aberta.
            assertTrue(habits.getById("h1")!!.paused)
            assertTrue(habits.getById("h2")!!.paused)
            assertTrue(habits.pauseIntervals("h1").any { it.endDate == null })
            assertTrue(tasks.goalPausesFlow().first().any { it.endDate == null })
            assertEquals(setOf("h1"), prefs.vacationHabitIdsFlow().first())
            assertTrue(prefs.vacationGoalPauseOpenedFlow().first())
            assertTrue(prefs.vacationModeFlow().first())

            deps.setVacationMode(false, today)

            // Descongelar restaura SÓ o que as férias tocaram.
            assertFalse(habits.getById("h1")!!.paused) // retomado
            assertTrue(habits.getById("h2")!!.paused) // manual: intocado
            assertTrue(
                tasks.goalPausesFlow().first().none { it.endDate == null },
            )
            assertFalse(prefs.vacationModeFlow().first())
        }

    @Test
    fun vacationMode_keepsPreexistingGoalPause() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        val prefs = FakePrefs()
        val tasks = TaskRepositoryImpl(db, checkins, null, prefs)
        val deps = AppDeps(
            habits = habits,
            checkins = checkins,
            tasks = tasks,
            diary = DiaryRepositoryImpl(db),
            challenges = ChallengeRepositoryImpl(db),
            sync = LocalSyncRepository(),
            prefs = prefs,
        )
        val today = todayYmd(TimeZone.UTC)
        habits.save(habit(id = "h1"))
        tasks.setGoalPaused(true, today) // pausa MANUAL da meta

        deps.setVacationMode(true, today)
        // As férias NÃO reivindicam a pausa que já existia.
        assertFalse(prefs.vacationGoalPauseOpenedFlow().first())
        assertTrue(habits.getById("h1")!!.paused)

        deps.setVacationMode(false, today)
        assertFalse(habits.getById("h1")!!.paused) // hábito retomado
        // A pausa manual da meta continua aberta.
        assertTrue(tasks.goalPausesFlow().first().any { it.endDate == null })
    }

    @Test
    fun taskRemindersListAndAutoMaterialization() = runBlocking {
        val db = testDb()
        val tasks = TaskRepositoryImpl(db, prefs = FakePrefs(autoMinutes = 30))
        val date = todayYmd(TimeZone.UTC)

        // R6.2: com data E horário e lista vazia, o automático nasce.
        val idAuto = tasks.add(
            NewTask(title = "Com horário", dueDate = date, dueTime = "15:30"),
        )
        val autoTask = assertNotNull(tasks.getById(idAuto))
        assertEquals(1, autoTask.reminders.size)
        assertTrue(autoTask.reminders.single().auto)
        assertEquals(30, autoTask.reminders.single().minuteOffset)
        assertEquals(30, autoTask.reminderMinutesBefore) // espelho legado

        // Sem horário: nada de automático.
        val idNoTime = tasks.add(NewTask(title = "Sem horário", dueDate = date))
        assertEquals(0, tasks.getById(idNoTime)?.reminders?.size)

        // Lista explícita vence: dois lembretes, nenhum automático.
        val idList = tasks.add(
            NewTask(
                title = "Com lista", dueDate = date, dueTime = "10:00",
                reminders = listOf(
                    ReminderSpec(ReminderType.RELATIVE, minuteOffset = 10),
                    ReminderSpec(ReminderType.ABSOLUTE, atMillis = 1792400400000L),
                ),
            ),
        )
        val listed = assertNotNull(tasks.getById(idList))
        assertEquals(2, listed.reminders.size)
        assertTrue(listed.reminders.none { it.auto })

        // setTaskReminders substitui a lista inteira (e o espelho).
        tasks.setTaskReminders(
            idList,
            listOf(ReminderSpec(ReminderType.RELATIVE, minuteOffset = 60)),
        )
        val replaced = assertNotNull(tasks.getById(idList))
        assertEquals(1, replaced.reminders.size)
        assertEquals(60, replaced.reminders.single().minuteOffset)
        assertEquals(60, replaced.reminderMinutesBefore)

        // O atalho legado continua funcionando sobre a lista.
        tasks.setReminder(idList, null)
        assertEquals(0, tasks.getById(idList)?.reminders?.size)
        assertNull(tasks.getById(idList)?.reminderMinutesBefore)

        // Os Flows carregam a lista junto da tarefa.
        val fromFlow = tasks.allTasksFlow().first()
            .first { it.id == idAuto }
        assertEquals(1, fromFlow.reminders.size)
    }

    @Test
    fun habitRemindersReplaceListAndMirror() = runBlocking {
        val db = testDb()
        val checkins = CheckinRepositoryImpl(db)
        val habits = HabitRepositoryImpl(db, checkins)
        habits.save(habit())

        // Vários horários por hábito; duplicado e inválido são filtrados.
        habits.setHabitReminders("h1", listOf("21:00", "07:30", "07:30", "abc"))
        val list = habits.habitRemindersFlow("h1").first()
        assertEquals(listOf("07:30", "21:00"), list.map { it.time })
        val saved = assertNotNull(habits.getById("h1"))
        assertTrue(saved.reminderEnabled)
        assertEquals("07:30", saved.reminderTime) // espelho = 1º horário

        habits.setHabitReminders("h1", emptyList())
        assertEquals(0, habits.habitRemindersFlow("h1").first().size)
        assertFalse(assertNotNull(habits.getById("h1")).reminderEnabled)
    }
}
