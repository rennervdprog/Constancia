package app.constancia.data

import app.constancia.model.Challenge
import app.constancia.model.ChallengeStatus
import app.constancia.model.Checkin
import app.constancia.model.DiaryEntry
import app.constancia.model.Habit
import app.constancia.model.HabitStatus
import app.constancia.model.GoalPause
import app.constancia.model.NewTask
import app.constancia.model.PauseInterval
import app.constancia.model.SavedView
import app.constancia.model.Session
import app.constancia.model.TaskItem
import app.constancia.model.TaskProject
import app.constancia.model.TaskTemplate
import kotlinx.coroutines.flow.Flow
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
}

interface CheckinRepository {
    suspend fun get(habitId: String, dateYmd: String): Checkin?
    suspend fun toggle(habitId: String, dateYmd: String, quantity: Double = 1.0): Boolean // retorna done após toggle
    suspend fun setQuantity(habitId: String, dateYmd: String, quantity: Double)
    suspend fun remove(habitId: String, dateYmd: String)
    fun checkinsInRangeFlow(fromYmd: String, toYmd: String): Flow<List<Checkin>>
    suspend fun checkinsForHabit(habitId: String): List<Checkin>
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
    suspend fun setDone(id: String, done: Boolean)
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
}
