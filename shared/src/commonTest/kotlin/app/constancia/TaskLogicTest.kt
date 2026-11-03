package app.constancia

import app.constancia.domain.cyclePriority
import app.constancia.domain.dayFlowTasks
import app.constancia.domain.postponeTarget
import app.constancia.domain.doneTasks
import app.constancia.domain.doneTodayTasks
import app.constancia.domain.dueDateLabel
import app.constancia.domain.entradaTasks
import app.constancia.domain.nextRepeatDate
import app.constancia.domain.overdueTasks
import app.constancia.domain.prioCode
import app.constancia.domain.projectPendingCount
import app.constancia.domain.projectTasks
import app.constancia.domain.resolveQuickSession
import app.constancia.domain.habitMomentPast
import app.constancia.domain.minutesOfTimeText
import app.constancia.domain.sessionEndHour
import app.constancia.domain.sessionForTimeText
import app.constancia.domain.taskMomentPast
import app.constancia.domain.sortByPrio
import app.constancia.domain.subtaskProgress
import app.constancia.domain.taskCounts
import app.constancia.domain.taskMatchesQuery
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
import kotlin.test.assertFalse
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

    // ---------- Varredura 2026-10-30: A3 + M8 ----------

    @Test
    fun dayFlow_excludesTasksCompletedOnEarlierDays() {
        val tasks = listOf(
            task("pendente-hoje", dueDate = "2026-10-20"),
            task("atrasada", dueDate = "2026-10-18"),
            task("feita-hoje", dueDate = "2026-10-19", done = true)
                .copy(completedAt = "2026-10-20T09:00:00Z"),
            task("feita-ontem", dueDate = "2026-10-19", done = true)
                .copy(completedAt = "2026-10-19T18:00:00Z"),
            task("futura", dueDate = "2026-10-21"),
            task("sem-data", dueDate = null),
        )
        assertEquals(
            listOf("pendente-hoje", "atrasada", "feita-hoje"),
            dayFlowTasks(tasks, today).map { it.id },
        )
    }

    @Test
    fun postponeTarget_alwaysLandsTomorrowOrLater() {
        // Atrasada há 3 dias: vai PARA amanhã (não continua atrasada).
        assertEquals(LocalDate(2026, 10, 21), postponeTarget("2026-10-17", today))
        // Vence hoje: amanhã.
        assertEquals(LocalDate(2026, 10, 21), postponeTarget("2026-10-20", today))
        // Sem data: amanhã.
        assertEquals(LocalDate(2026, 10, 21), postponeTarget(null, today))
        // Já está amanhã: adia mais um dia a partir dela.
        assertEquals(LocalDate(2026, 10, 22), postponeTarget("2026-10-21", today))
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

    // ---- T1/T5/O2 (auditoria de tarefas 2026-10-30) ----------------

    @Test
    fun sessionForTimeTextDerivesFromValidTime() {
        assertEquals(Session.MANHA, sessionForTimeText("07:30"))
        assertEquals(Session.MANHA, sessionForTimeText("11:59"))
        // O caso do print do Renner: 15:14 é Tarde, não Manhã.
        assertEquals(Session.TARDE, sessionForTimeText("15:14"))
        assertEquals(Session.TARDE, sessionForTimeText("12:00"))
        assertEquals(Session.NOITE, sessionForTimeText("18:00"))
        assertEquals(Session.NOITE, sessionForTimeText("23:59"))
        assertNull(sessionForTimeText(null))
        assertNull(sessionForTimeText(""))
        assertNull(sessionForTimeText("29:61"))
        assertNull(sessionForTimeText("15"))
    }

    @Test
    fun resolveQuickSessionTimeBeatsDaypartBeatsNow() {
        // Horário reconhecido manda sobre a parte do dia dita.
        assertEquals(
            Session.TARDE,
            resolveQuickSession("15:00", Session.NOITE, nowHour = 9),
        )
        // Sem horário, a parte do dia do texto vale.
        assertEquals(
            Session.NOITE,
            resolveQuickSession(null, Session.NOITE, nowHour = 9),
        )
        // Sem nada, a sessão da hora atual.
        assertEquals(
            Session.MANHA,
            resolveQuickSession(null, null, nowHour = 9),
        )
        assertEquals(
            Session.NOITE,
            resolveQuickSession(null, null, nowHour = 21),
        )
    }

    @Test
    fun taskMatchesQueryIgnoresCaseAndAccents() {
        val t = TaskItem(
            id = "x",
            title = "Reunião com o cliente",
            dueTime = null,
            done = false,
            date = "2026-10-20",
            note = "Levar o contrato de manutenção",
        )
        assertTrue(taskMatchesQuery(t, "reuniao"))
        assertTrue(taskMatchesQuery(t, "CLIENTE"))
        // A anotação também é buscável.
        assertTrue(taskMatchesQuery(t, "manutencao"))
        assertTrue(taskMatchesQuery(t, "contrato"))
        assertFalse(taskMatchesQuery(t, "dentista"))
        assertFalse(taskMatchesQuery(t, ""))
        assertFalse(taskMatchesQuery(t, "   "))
    }

    // "Passou do horário" (2026-11-02): a régua que marca hábito/tarefa
    // de hoje como atrasado na exibição.

    @Test
    fun momentoDoHabitoSegueOFimDaSessao() {
        assertEquals(12, sessionEndHour(Session.MANHA))
        assertEquals(18, sessionEndHour(Session.TARDE))
        assertEquals(24, sessionEndHour(Session.NOITE))
        // Manhã às 22h (1320 min): passou — o caso relatado.
        assertTrue(habitMomentPast(Session.MANHA, 22 * 60))
        // Manhã às 11h59: ainda dentro da janela.
        assertFalse(habitMomentPast(Session.MANHA, 11 * 60 + 59))
        // Manhã às 12h00: a faixa virou.
        assertTrue(habitMomentPast(Session.MANHA, 12 * 60))
        // Tarde às 17h59 ok; às 18h passou. Noite nunca passa no dia.
        assertFalse(habitMomentPast(Session.TARDE, 17 * 60 + 59))
        assertTrue(habitMomentPast(Session.TARDE, 18 * 60))
        assertFalse(habitMomentPast(Session.NOITE, 23 * 60 + 59))
    }

    @Test
    fun momentoDaTarefaComHorarioSegueOHorario() {
        assertEquals(9 * 60 + 30, minutesOfTimeText("09:30"))
        assertEquals(null, minutesOfTimeText("9h"))
        assertEquals(null, minutesOfTimeText(null))
        // Tarefa das 09:00: às 09:00 ainda é a hora dela; às 09:01 passou.
        assertFalse(taskMomentPast(Session.MANHA, "09:00", 9 * 60))
        assertTrue(taskMomentPast(Session.MANHA, "09:00", 9 * 60 + 1))
        // Às 22h, tarefa da manhã com ou sem horário: passou.
        assertTrue(taskMomentPast(Session.MANHA, "09:00", 22 * 60))
        // Sem horário, vale o fim da sessão (mesma régua do hábito).
        assertFalse(taskMomentPast(Session.TARDE, null, 15 * 60))
        assertTrue(taskMomentPast(Session.TARDE, null, 19 * 60))
        // Horário manda sobre a sessão: tarefa da Noite às 23:30,
        // vista às 22h, ainda NÃO passou.
        assertFalse(taskMomentPast(Session.NOITE, "23:30", 22 * 60))
    }
}
