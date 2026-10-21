package app.constancia.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.random.Random

/** Sessão do dia em que o hábito aparece na tela Hoje. */
enum class Session { MANHA, TARDE, NOITE }

/** Tipo de meta: marcar feito (binário) ou atingir quantidade. */
enum class GoalType { BINARY, QUANTITY }

/** Frequência: todo dia, X vezes por semana, ou dias fixos da semana. */
enum class FrequencyType { DAILY, WEEKLY_X, DAYS_OF_WEEK }

data class Habit(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val goalType: GoalType,
    val targetQuantity: Double,
    val quantityUnit: String,
    val frequencyType: FrequencyType,
    val daysOfWeek: List<Int>, // 1=domingo .. 7=sábado; vazio = todos os dias
    val weeklyTarget: Int,
    val session: Session,
    val reminderEnabled: Boolean,
    val reminderTime: String,
    val paused: Boolean,
    val createdAt: String,
    val sortOrder: Int,
    // Backlog TickTick (schema v6):
    val quantityStep: Double = 1.0, // passo do stepper (item 1)
    val showInToday: Boolean = false, // opt-in: aparecer na Hoje mesmo fora dos dias devidos (item 4)
    val showInCalendar: Boolean = false, // opt-in: marcas de check-in no calendário do diário (item 4)
)

/** Status do check-in: DONE = feito/parcial; SKIPPED = pulado (registrado
 *  como dado — não quebra a sequência, difere da ausência de check-in). */
enum class CheckinStatus { DONE, SKIPPED }

data class Checkin(
    val id: String,
    val habitId: String,
    val date: String, // yyyy-MM-dd
    val quantity: Double,
    val status: CheckinStatus = CheckinStatus.DONE,
)

/**
 * TickTick item 3: registro do log do hábito (HabitRecord) — nota curta
 * e emoji opcionais de um dia, por hábito. Vive em tabela própria para
 * a aba Padrões cruzar humor-por-hábito no futuro.
 */
data class HabitRecord(
    val id: String,
    val habitId: String,
    val date: String, // yyyy-MM-dd
    val content: String,
    val emoji: String? = null,
)

/**
 * TickTick item 2: ciclo/temporada do hábito (HabitCycleModel) — janela
 * de dias com meta; ao bater a meta, [completedAt] é preenchido e o
 * ciclo fica permanente no histórico do hábito.
 */
data class HabitCycle(
    val id: String,
    val habitId: String,
    val startDate: String, // yyyy-MM-dd (inclusive)
    val endDate: String, // yyyy-MM-dd (inclusive)
    val targetDays: Int,
    val completedAt: String? = null, // instante ISO ou null = não concluído
)

/** Intervalo em que um hábito ficou pausado: dias perdoados no cálculo do streak. */
data class PauseInterval(
    val id: String,
    val habitId: String,
    val startDate: String, // yyyy-MM-dd (inclusive)
    val endDate: String?, // yyyy-MM-dd (inclusive) ou null = pausa aberta
)

data class HabitStatus(
    val habit: Habit,
    val todayCheckin: Checkin?,
    val isDoneToday: Boolean,
    val currentStreak: Int,
    val weekDone: Int, // check-ins na semana atual (para WEEKLY_X)
)

/** Listas/projetos das tarefas (estilo Todoist), com a cor de cada um. */
enum class TaskProject(val label: String, val colorHex: Long) {
    ENTRADA("Entrada", 0xFF7A6B90),
    PESSOAL("Pessoal", 0xFF0B7A55),
    TRABALHO("Trabalho", 0xFF2563EB),
    CASA("Casa", 0xFFD97706),
    SAUDE("Saúde", 0xFFDB2777),
}

/** Prioridades: 1 = alta (P1), 2 = média (P2), 3 = baixa (P3). */
data class Subtask(
    val id: String,
    val taskId: String,
    val title: String,
    val done: Boolean,
    val sortOrder: Int,
)

data class TaskItem(
    val id: String,
    val title: String,
    val dueTime: String?, // HH:mm ou null
    val done: Boolean,
    val date: String, // yyyy-MM-dd (dia de criação/pertencimento legado)
    val project: TaskProject = TaskProject.ENTRADA,
    val priority: Int = 2, // 1=P1 alta, 2=P2 média, 3=P3 baixa
    val dueDate: String? = null, // yyyy-MM-dd ou null = sem data (Entrada)
    val session: Session = Session.TARDE,
    val repeatRule: String = "", // "" | "Todo dia" | "Seg a Sex" | "Semanal" (rótulo legível)
    val note: String = "",
    val habitId: String? = null, // tarefa vinculada a um hábito
    val subtasks: List<Subtask> = emptyList(),
    // Backlog Todoist (schema v4):
    val deadlineDate: String? = null, // yyyy-MM-dd: prazo final, distinto da data agendada
    val durationMinutes: Int? = null, // duração estimada em minutos
    val completedAt: String? = null, // instante ISO da conclusão (metas/streak de tarefas)
    val reminderMinutesBefore: Int? = null, // null = sem lembrete; 0 = na hora; N = N min antes
    val repeatSpec: String = "", // regra de recorrência estruturada codificada (domain/RepeatRule)
)

/** Dados para criar uma tarefa nova (formulário completo ou captura rápida). */
data class NewTask(
    val title: String,
    val note: String = "",
    val project: TaskProject = TaskProject.ENTRADA,
    val priority: Int = 2,
    val dueDate: String? = null,
    val session: Session = Session.TARDE,
    val dueTime: String? = null,
    val repeatRule: String = "",
    val habitId: String? = null,
    val subtaskTitles: List<String> = emptyList(),
    // Backlog Todoist (schema v4):
    val deadlineDate: String? = null,
    val durationMinutes: Int? = null,
    val reminderMinutesBefore: Int? = null,
    val repeatSpec: String = "",
)

/** Vista salva de tarefas (item 10): combinação nomeada com cor, sem
 *  linguagem de query. period: all|today|week|overdue|nodate;
 *  groupBy: none|project|priority|date. */
data class SavedView(
    val id: String,
    val name: String,
    val colorHex: Long,
    val project: TaskProject? = null,
    val priority: Int? = null,
    val period: String = "all",
    val groupBy: String = "none",
    val sortOrder: Int = 0,
)

/** Item de um modelo de lista (item 11): a tarefa-modelo serializada. */
data class TaskTemplateItem(
    val id: String,
    val templateId: String,
    val title: String,
    val project: TaskProject = TaskProject.ENTRADA,
    val priority: Int = 2,
    val session: Session = Session.TARDE,
    val note: String = "",
    val sortOrder: Int = 0,
)

/** Modelo de lista de tarefas (item 11): "Rotina da manhã", etc. */
data class TaskTemplate(
    val id: String,
    val name: String,
    val isFactory: Boolean = false,
    val items: List<TaskTemplateItem> = emptyList(),
)

/** Intervalo de pausa da meta de tarefas (item 2): streak congelado. */
data class GoalPause(
    val id: String,
    val startDate: String, // yyyy-MM-dd (inclusive)
    val endDate: String?, // yyyy-MM-dd (inclusive) ou null = pausa aberta
)

/**
 * R1.1 (Fase B): snapshot de uma ocorrência concluída de tarefa
 * recorrente, gravado no histórico na hora da conclusão — antes de a
 * próxima ocorrência nascer. Preserva a conclusão mesmo se a linha viva
 * for apagada ou desmarcada depois do desfazer expirar.
 */
data class TaskOccurrence(
    val id: String,
    val sourceTaskId: String,
    val occurrenceDueDate: String?, // yyyy-MM-dd ou null (série sem prazo)
    val title: String,
    val project: TaskProject = TaskProject.ENTRADA,
    val priority: Int = 2,
    val deadlineDate: String? = null,
    val durationMinutes: Int? = null,
    val completedAt: String, // instante ISO da conclusão
)

/** Estado de uma subtarefa antes da conclusão (para o pacote de reversão). */
data class SubtaskState(
    val subtaskId: String,
    val done: Boolean,
)

/** Check-in de hábito gerado por uma conclusão de tarefa vinculada:
 *  [previousQuantity] é a quantidade de hoje ANTES da conclusão
 *  (null = não havia check-in; o desfazer apaga o check-in criado). */
data class HabitCheckinReversal(
    val habitId: String,
    val date: String, // yyyy-MM-dd do check-in afetado
    val previousQuantity: Double?,
)

/**
 * R2.2 (Fase B): pacote de reversão de UMA conclusão de tarefa — tudo o
 * que o desfazer precisa para deixar o banco como estava antes:
 * a ocorrência gerada (apagar), o snapshot no histórico (apagar), o
 * check-in de hábito gerado (reverter) e o estado das subtarefas.
 */
data class CompletionReversal(
    val taskId: String,
    val taskTitle: String,
    val spawnedTaskId: String? = null,
    val historyId: String? = null,
    val habitCheckin: HabitCheckinReversal? = null,
    val subtaskStates: List<SubtaskState> = emptyList(),
)

/**
 * R8.1 (Fase B): opções de vista persistidas (agrupar/ordenar) de UMA
 * visão. groupBy no formato das vistas salvas (none|project|priority|
 * date); sortMode é o nome do enum TaskSortMode (SMART|PRIORITY|DUE).
 */
data class TaskViewOptions(
    val viewKey: String,
    val groupBy: String = "none",
    val sortMode: String = "SMART",
)

data class DiaryEntry(
    val id: String,
    val date: String, // yyyy-MM-dd
    val mood: Int?, // 1..5 ou null
    val note: String,
    val energy: Int? = null, // 1..5 ou null
    val sleepHours: Double? = null, // horas de sono da última noite
    val tags: List<String> = emptyList(), // etiquetas do dia
    val photoUri: String? = null, // caminho local da foto do dia
    val prompt: String? = null, // pergunta do dia respondida
)

enum class ChallengeStatus { AVAILABLE, ACTIVE, DONE, GIVEN_UP }

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val durationDays: Int,
    val habitName: String,
    val habitIcon: String,
    val startDate: String?,
    val status: ChallengeStatus,
    val isPro: Boolean,
)

/** Gera um id único sem depender de java.util.UUID (minSdk 24, commonMain). */
fun newId(): String =
    "${Clock.System.now().toEpochMilliseconds()}-${Random.nextLong().toString(16)}"

fun todayYmd(timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
    Clock.System.todayIn(timeZone).toYmd()

fun LocalDate.toYmd(): String {
    val m = monthNumber.toString().padStart(2, '0')
    val d = dayOfMonth.toString().padStart(2, '0')
    return "$year-$m-$d"
}

/** Converte "yyyy-MM-dd" em LocalDate sem String.format nem java.time. */
fun String.toLocalDate(): LocalDate {
    val parts = split("-")
    return LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
}

/**
 * Número do dia na semana (ISO-8601: 1=segunda .. 7=domingo).
 * Calculado via epoch days para não depender de API interna do kotlinx-datetime
 * (DayOfWeek.isoDayNumber é internal na 0.6.1). 1970-01-01 (epoch 0) foi quinta-feira (4).
 */
fun LocalDate.isoDayNumberCompat(): Int =
    (((toEpochDays() + 3) % 7 + 7) % 7 + 1)

/** isoDayNumberCompat(): 1=segunda..7=domingo -> 1=domingo..7=sábado (padrão do app). */
fun isoToAppDay(isoDayNumber: Int): Int = if (isoDayNumber == 7) 1 else isoDayNumber + 1
