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
)

data class Checkin(
    val id: String,
    val habitId: String,
    val date: String, // yyyy-MM-dd
    val quantity: Double,
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
    ENTRADA("Entrada", 0xFF66766A),
    PESSOAL("Pessoal", 0xFF0B7A55),
    TRABALHO("Trabalho", 0xFF2563EB),
    CASA("Casa", 0xFFE86A17),
    SAUDE("Saúde", 0xFF0E7490),
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
    val repeatRule: String = "", // "" | "Todo dia" | "Seg a Sex" | "Semanal"
    val note: String = "",
    val habitId: String? = null, // tarefa vinculada a um hábito
    val subtasks: List<Subtask> = emptyList(),
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
