package app.constancia.data

import app.constancia.model.Challenge
import app.constancia.model.ChallengeStatus
import app.constancia.model.Checkin
import app.constancia.model.CompletionReversal
import app.constancia.model.DiaryEntry
import app.constancia.model.Habit
import app.constancia.model.HabitCycle
import app.constancia.model.HabitRecord
import app.constancia.model.HabitReminder
import app.constancia.model.HabitStatus
import app.constancia.model.GoalPause
import app.constancia.model.NewTask
import app.constancia.model.PauseInterval
import app.constancia.model.ReminderSpec
import app.constancia.model.SavedView
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.model.TaskOccurrence
import app.constancia.model.TaskProject
import app.constancia.model.TaskTemplate
import app.constancia.model.TaskViewOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Contratos da camada de dados (implementação SQLDelight em data/local/).
 * As telas dependem só destas interfaces — a troca futura para sync remoto
 * não muda nenhuma tela.
 */
interface HabitRepository {
    fun habitsFlow(): Flow<List<Habit>>
    suspend fun getById(id: String): Habit?
    suspend fun save(habit: Habit) // insert ou update pelo id
    suspend fun setPaused(id: String, paused: Boolean)
    suspend fun archive(id: String)
    suspend fun delete(id: String)
    /** Intervalos de pausa de um hábito (para congelar o streak). */
    suspend fun pauseIntervals(habitId: String): List<PauseInterval>
    /** Todos os intervalos de pausa, agrupados por hábito. */
    suspend fun allPauseIntervals(): Map<String, List<PauseInterval>>
    /** Hábitos com o status de hoje (check-in, streak, progresso semanal). */
    fun todayStatusFlow(timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<List<HabitStatus>>
    /** Mesmo cálculo de [todayStatusFlow], mas para uma data qualquer (semana navegável). */
    fun statusForDateFlow(dateYmd: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): Flow<List<HabitStatus>>

    // -- Backlog TickTick (schema v6) --
    /** Item 2: ciclos/temporadas de um hábito, do mais antigo ao atual. */
    fun cyclesFlow(habitId: String): Flow<List<HabitCycle>>
    /** Item 2: todos os ciclos (base das seções de ciclo nas telas). */
    fun allCyclesFlow(): Flow<List<HabitCycle>>
    /**
     * Item 2: começa um ciclo de [targetDays] dias a partir de
     * [startYmd] (a janela tem o mesmo comprimento da meta, como no
     * TickTick). Retorna o ciclo criado.
     */
    suspend fun startCycle(habitId: String, targetDays: Int, startYmd: String): HabitCycle

    // -- Fase C (absorvido do TickTick §9.4) --
    /** Lembretes (horários diários "HH:mm") de um hábito — vários por hábito. */
    fun habitRemindersFlow(habitId: String): Flow<List<HabitReminder>>
    /** Todos os lembretes de hábito (base das telas e do reagendamento). */
    fun allHabitRemindersFlow(): Flow<List<HabitReminder>>
    /**
     * Substitui os horários de lembrete do hábito: grava a lista,
     * atualiza o espelho (reminderEnabled/reminderTime) e agenda os
     * alarmes diários (hábito pausado não apita).
     */
    suspend fun setHabitReminders(habitId: String, times: List<String>)
}

interface CheckinRepository {
    suspend fun get(habitId: String, dateYmd: String): Checkin?
    suspend fun toggle(habitId: String, dateYmd: String, quantity: Double = 1.0): Boolean // retorna done após toggle
    suspend fun setQuantity(habitId: String, dateYmd: String, quantity: Double)
    suspend fun remove(habitId: String, dateYmd: String)
    fun checkinsInRangeFlow(fromYmd: String, toYmd: String): Flow<List<Checkin>>
    suspend fun checkinsForHabit(habitId: String): List<Checkin>

    // -- Backlog TickTick (schema v6) --
    /**
     * Item 4: marca o dia como PULADO — um registro (status SKIPPED),
     * não uma ausência: o dia não conta como feito, mas também não
     * quebra a sequência.
     */
    suspend fun skip(habitId: String, dateYmd: String)
    /** Item 4: desfaz o pulo (remove o registro de skip do dia). */
    suspend fun unskip(habitId: String, dateYmd: String)
    /** Item 3: salva (cria ou substitui) o registro do log do dia. */
    suspend fun saveRecord(habitId: String, dateYmd: String, content: String, emoji: String?)
    /** Item 3: registro do log de um dia, ou null. */
    suspend fun recordFor(habitId: String, dateYmd: String): HabitRecord?
    /** Item 3: log de um hábito, do mais recente ao mais antigo. */
    fun recordsFlow(habitId: String): Flow<List<HabitRecord>>
    /** Item 3: todos os registros de log (base futura da aba Padrões). */
    fun allRecordsFlow(): Flow<List<HabitRecord>>
}

interface TaskRepository {
    fun tasksForDateFlow(dateYmd: String): Flow<List<TaskItem>>
    /** Todas as tarefas (com subtarefas) — base das visões estilo Todoist. */
    fun allTasksFlow(): Flow<List<TaskItem>>
    suspend fun getById(id: String): TaskItem?
    /** Cria uma tarefa completa (com subtarefas). Retorna o id criado. */
    suspend fun add(task: NewTask): String
    /** Captura rápida: só o título; o resto assume os padrões da visão atual. */
    suspend fun quickAdd(
        title: String,
        dueDate: String?,
        project: TaskProject = TaskProject.ENTRADA,
        session: Session = Session.TARDE,
    ): String
    /**
     * Marca/desmarca a conclusão. R2.3 (Fase A): [completedOn] permite
     * concluir "como de" outro dia (ex.: ontem) — o instante gravado e a
     * âncora da próxima ocorrência usam essa data; null = agora.
     * R2.2 (Fase B): ao concluir, devolve o pacote de reversão da
     * conclusão (null ao desmarcar ou se já estava feita); ao desmarcar
     * uma tarefa com reversão pendente, a reversão é aplicada (a
     * ocorrência gerada e o check-in do hábito são desfeitos junto).
     */
    suspend fun setDone(id: String, done: Boolean, completedOn: LocalDate? = null): CompletionReversal?
    /**
     * R2.2 (Fase B): desfaz uma conclusão a partir do pacote devolvido
     * por [setDone]: apaga a ocorrência gerada, remove o snapshot do
     * histórico, reverte o check-in de hábito gerado e reabre a tarefa
     * com o estado anterior das subtarefas.
     */
    suspend fun undoCompletion(reversal: CompletionReversal)
    /**
     * R1.1 (Fase B): histórico de ocorrências concluídas de tarefas
     * recorrentes (snapshots), da conclusão mais recente à mais antiga.
     */
    fun occurrenceHistoryFlow(): Flow<List<TaskOccurrence>>
    /**
     * R8.1 (Fase B): opções de vista (agrupar/ordenar) persistidas da
     * visão [viewKey] ('view:HOJE', 'saved:<id>' etc.); sem registro,
     * devolve os padrões (groupBy "none", sortMode "SMART").
     */
    fun viewOptionsFlow(viewKey: String): Flow<TaskViewOptions>
    /** R8.1 (Fase B): grava as opções de vista da visão [viewKey]. */
    suspend fun setViewOptions(viewKey: String, groupBy: String, sortMode: String)
    suspend fun setPriority(id: String, priority: Int)
    suspend fun setDueDate(id: String, dueDate: String?)
    suspend fun addSubtask(taskId: String, title: String)
    suspend fun setSubtaskDone(subtaskId: String, done: Boolean)
    suspend fun delete(id: String)

    // -- Backlog Todoist Faixa 1/2 --
    /** Item 1: move TODAS as pendentes vencidas (dueDate < hoje) para o
     *  dia alvo. Retorna quantas foram movidas. */
    suspend fun rescheduleAllOverdue(todayYmd: String, targetYmd: String): Int
    /** Item 9: define/remove o lembrete da tarefa (null = sem lembrete). */
    suspend fun setReminder(id: String, minutesBefore: Int?)

    /**
     * R6.1 (Fase C): substitui a LISTA de lembretes da tarefa (vários,
     * relativos e absolutos) e reagenda todos. Lista vazia = nenhum.
     */
    suspend fun setTaskReminders(id: String, specs: List<ReminderSpec>)

    /** Item 10: vistas salvas nomeadas com cor. */
    fun savedViewsFlow(): Flow<List<SavedView>>
    suspend fun addSavedView(view: SavedView): String
    suspend fun deleteSavedView(id: String)

    /** Item 11: modelos de lista de tarefas. */
    fun templatesFlow(): Flow<List<TaskTemplate>>
    /** Salva um modelo a partir de itens (título/projeto/prioridade/sessão/nota). */
    suspend fun saveTemplate(name: String, items: List<NewTask>): String
    suspend fun deleteTemplate(id: String)
    /** Cria as tarefas do modelo (com o prazo dado; null = sem data). */
    suspend fun instantiateTemplate(templateId: String, dueDate: String?): Int
    /** Garante os modelos de fábrica (idempotente). */
    suspend fun ensureFactoryTemplates()

    /** Item 2: pausas da meta de tarefas (streak congelado). */
    fun goalPausesFlow(): Flow<List<GoalPause>>
    /** Liga/desliga o modo pausa da meta a partir de hoje. */
    suspend fun setGoalPaused(paused: Boolean, todayYmd: String)
}

interface DiaryRepository {
    fun entryForDateFlow(dateYmd: String): Flow<DiaryEntry?>
    suspend fun save(
        dateYmd: String,
        mood: Int?,
        note: String,
        energy: Int? = null,
        sleepHours: Double? = null,
        tags: List<String> = emptyList(),
        photoUri: String? = null,
        prompt: String? = null,
    )
    fun recentFlow(limit: Int): Flow<List<DiaryEntry>>
    /** Todos os registros, do mais recente ao mais antigo. */
    fun allEntriesFlow(): Flow<List<DiaryEntry>>
    /** Apaga o registro de um dia (botão "Excluir registro" do diário). */
    suspend fun delete(dateYmd: String)
}

interface ChallengeRepository {
    fun challengesFlow(): Flow<List<Challenge>>
    suspend fun start(id: String, startDateYmd: String)
    suspend fun setStatus(id: String, status: ChallengeStatus)
    /** Garante que os pacotes padrão existam (idempotente). */
    suspend fun ensureSeed()
}

/** Preferências simples: tema, status Pro, onboarding. */
interface PrefsRepository {
    fun darkThemeFlow(): Flow<Boolean>
    suspend fun setDarkTheme(dark: Boolean)
    fun isProFlow(): Flow<Boolean>
    suspend fun setPro(pro: Boolean)
    fun onboardingDoneFlow(): Flow<Boolean>
    suspend fun setOnboardingDone()

    // -- Backlog Todoist Faixa 1/2 --
    /** Item 2: meta diária de tarefas concluídas (0 = desligada). */
    fun taskDailyGoalFlow(): Flow<Int>
    suspend fun setTaskDailyGoal(goal: Int)
    /** Item 2: meta semanal de tarefas concluídas (0 = desligada). */
    fun taskWeeklyGoalFlow(): Flow<Int>
    suspend fun setTaskWeeklyGoal(goal: Int)
    /** Item 3: Daily Review — notificação diária de resumo do dia. */
    fun dailyReviewEnabledFlow(): Flow<Boolean>
    suspend fun setDailyReviewEnabled(enabled: Boolean)
    /** Item 3: horário do Daily Review, "HH:mm" (padrão "08:00"). */
    fun dailyReviewTimeFlow(): Flow<String>
    suspend fun setDailyReviewTime(time: String)

    // -- Fase C --
    /**
     * R6.2: lembrete automático de tarefa, em minutos antes do horário
     * (null = desligado). Tarefa nova com data e horário ganha esse
     * lembrete materializado, sem tocar no campo de lembrete.
     */
    fun autoReminderMinutesFlow(): Flow<Int?>
    suspend fun setAutoReminderMinutes(minutes: Int?)
}
